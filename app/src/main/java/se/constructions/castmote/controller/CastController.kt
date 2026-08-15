package se.constructions.castmote.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import se.constructions.castmote.protocol.CastConnection
import se.constructions.castmote.protocol.CastIds
import se.constructions.castmote.protocol.Namespaces
import se.constructions.castmote.protocol.Payloads
import se.constructions.castmote.protocol.TlsCastChannel
import se.constructions.castmote.youtube.YouTubeException
import se.constructions.castmote.youtube.YtLounge

/** High-level remote for one connected device. Wraps [CastConnection] with typed commands. */
class CastController(
    host: String,
    parentScope: CoroutineScope,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val channel = TlsCastChannel(host, scope = scope)
    private val connection = CastConnection(channel, scope)
    private val ytLounge = YtLounge()

    private val _receiver = MutableStateFlow<ReceiverStatus?>(null)
    val receiver: StateFlow<ReceiverStatus?> = _receiver.asStateFlow()

    private val _media = MutableStateFlow<MediaStatus?>(null)
    val media: StateFlow<MediaStatus?> = _media.asStateFlow()

    private val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    private var mediaTransportId: String? = null

    suspend fun connect() {
        channel.connect()
        connection.start()
        connection.receiverStatus
            .onEach { json -> json?.let { onReceiverStatus(parseReceiverStatus(it)) } }
            .launchIn(scope)
        connection.mediaStatus
            .onEach { json -> json?.let { _media.value = parseMediaStatus(it) } }
            .launchIn(scope)
        channel.closed
            .onEach { isClosed -> if (isClosed) _connected.value = false }
            .launchIn(scope)
        scope.launchMediaPolling()
        refreshReceiver()
    }

    /**
     * Keeps [media] fresh. The receiver reports `currentTime` only when asked (or on state
     * changes), so without polling the progress position would sit frozen at the value from
     * the initial status. We re-request media status once a second whenever a media session
     * is active; the UI interpolates between these for a smooth bar.
     */
    private fun CoroutineScope.launchMediaPolling() {
        launch {
            while (isActive) {
                delay(1000)
                val transport = mediaTransportId ?: continue
                runCatching {
                    connection.request(Namespaces.MEDIA, transport) { Payloads.mediaGetStatus(it) }
                }
            }
        }
    }

    fun disconnect() {
        connection.close()
        scope.cancel()
    }

    suspend fun refreshReceiver() {
        connection.request(Namespaces.RECEIVER, CastIds.RECEIVER) { Payloads.getStatus(it) }
    }

    suspend fun setVolume(level: Double) {
        connection.request(Namespaces.RECEIVER, CastIds.RECEIVER) { Payloads.setVolume(it, level, null) }
    }

    suspend fun setMuted(muted: Boolean) {
        connection.request(Namespaces.RECEIVER, CastIds.RECEIVER) { Payloads.setVolume(it, null, muted) }
    }

    suspend fun stopApp() {
        val sessionId = _receiver.value?.sessionId ?: return
        connection.request(Namespaces.RECEIVER, CastIds.RECEIVER) { Payloads.stop(it, sessionId) }
    }

    /** Launches the default media receiver and casts the given URL, optionally seeking to [startSeconds]. */
    suspend fun castUrl(
        url: String,
        contentType: String,
        title: String?,
        startSeconds: Int = 0,
        isLive: Boolean = false,
        hlsFmp4: Boolean = false,
    ) {
        val transportId = ensureMediaApp(CastIds.DEFAULT_MEDIA_RECEIVER)
            ?: run { android.util.Log.e("CastmoteYT", "castUrl: no media transport (app didn't launch)"); return }
        val streamType = if (isLive) "LIVE" else "BUFFERED"
        val currentTime = if (isLive) 0.0 else startSeconds.toDouble()
        val resp = connection.request(Namespaces.MEDIA, transportId) {
            Payloads.load(it, url, contentType, title, currentTime, streamType, hlsFmp4)
        }
        android.util.Log.i("CastmoteYT", "LOAD($streamType fmp4=$hlsFmp4) response: ${resp.toString().take(240)}")
    }

    /**
     * Relaunches SVT's own receiver (app 95370A1C) and loads [playId], seeking to [startSeconds].
     * RE-based interop — throws [se.constructions.castmote.resolver.SvtException] on any API/protocol
     * change; the caller falls back to the default media receiver.
     */
    suspend fun castSvt(playId: String, startSeconds: Int = 0) {
        val resolved = se.constructions.castmote.resolver.SvtVideo.resolve(playId)
        val transportId = ensureMediaApp(CastIds.SVT_RECEIVER)
            ?: throw se.constructions.castmote.resolver.SvtException("Couldn't launch SVT on the TV")
        val resp = connection.request(Namespaces.MEDIA, transportId) {
            Payloads.loadSvt(it, playId, resolved.dittoUrl, resolved.title, startSeconds.toDouble(), resolved.response)
        }
        android.util.Log.i("CastmoteYT", "SVT LOAD @${startSeconds}s response: ${resp.toString().take(200)}")
    }

    /** Launches YouTube's receiver and plays [videoId] via the lounge protocol. */
    suspend fun castYouTube(videoId: String, startSeconds: Int = 0, authHeaders: Map<String, String>? = null) {
        // A freshly-launched YouTube app reports a screenId immediately, but its screen isn't
        // yet registered with the lounge servers, so the first setPlaylist no-ops (the old
        // "have to cast twice" bug). Detect the cold launch and retry until a media session
        // actually appears.
        val coldStart = _receiver.value?.appId != CastIds.YOUTUBE_RECEIVER
        if (coldStart) _media.value = null

        val transportId = ensureMediaApp(CastIds.YOUTUBE_RECEIVER)
            ?: throw YouTubeException("Couldn't launch YouTube on the TV")
        // sendAndAwaitType registers the waiter BEFORE sending so a fast reply isn't dropped.
        // Convert the withTimeout cancellation into a user-facing error (it is a
        // CancellationException subtype, which the ViewModel would otherwise rethrow silently).
        val status = try {
            connection.sendAndAwaitType(
                Namespaces.MDX, transportId, Payloads.getMdxSessionStatus(), "mdxSessionStatus",
            )
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw YouTubeException("Couldn't reach the YouTube app on the TV")
        }
        val screenId = status["data"]?.jsonObject?.get("screenId")?.jsonPrimitive?.contentOrNull
            ?: throw YouTubeException("Couldn't reach the YouTube app on the TV")

        if (!coldStart) {
            ytLounge.play(screenId, videoId, startSeconds, authHeaders)
            return
        }
        var lastError: YouTubeException? = null
        repeat(5) {
            runCatching { ytLounge.play(screenId, videoId, startSeconds, authHeaders) }
                .onFailure { lastError = it as? YouTubeException ?: YouTubeException("YouTube cast failed") }
            // The media-status poll updates _media once the screen registers and the video loads.
            val loaded = kotlinx.coroutines.withTimeoutOrNull(3000) {
                while (_media.value == null) delay(250)
                true
            }
            if (loaded == true) return
        }
        throw lastError ?: YouTubeException("YouTube didn't start playing")
    }

    suspend fun play() = withMediaSession { id, transport ->
        connection.request(Namespaces.MEDIA, transport) { Payloads.play(it, id) }
    }

    suspend fun pause() = withMediaSession { id, transport ->
        connection.request(Namespaces.MEDIA, transport) { Payloads.pause(it, id) }
    }

    suspend fun stopMedia() = withMediaSession { id, transport ->
        connection.request(Namespaces.MEDIA, transport) { Payloads.stopMedia(it, id) }
    }

    suspend fun seek(seconds: Double) = withMediaSession { id, transport ->
        connection.request(Namespaces.MEDIA, transport) { Payloads.seek(it, id, seconds) }
    }

    private fun onReceiverStatus(status: ReceiverStatus) {
        _receiver.value = status
        val transport = status.transportId
        if (transport != null && transport != mediaTransportId) {
            mediaTransportId = transport
            scope.launchConnectMedia(transport)
        }
        if (transport == null) {
            mediaTransportId = null
            _media.value = null
        }
    }

    private fun CoroutineScope.launchConnectMedia(transportId: String) {
        launch {
            connection.virtualConnect(transportId)
            connection.request(Namespaces.MEDIA, transportId) { Payloads.mediaGetStatus(it) }
        }
    }

    /** Ensures the named app is running and returns its media transportId. */
    private suspend fun ensureMediaApp(appId: String): String? {
        _receiver.value?.takeIf { it.appId == appId }?.transportId?.let { return it }
        connection.request(Namespaces.RECEIVER, CastIds.RECEIVER) { Payloads.launch(it, appId) }
        // Wait for the receiver status carrying the launched app's transportId.
        return withTimeoutOrNull(8_000) {
            var transport: String? = null
            while (transport == null) {
                transport = _receiver.value?.takeIf { it.appId == appId }?.transportId
                if (transport == null) kotlinx.coroutines.delay(200)
            }
            connection.virtualConnect(transport)
            transport
        }
    }

    private suspend fun withMediaSession(block: suspend (Int, String) -> Unit) {
        val id = _media.value?.mediaSessionId ?: return
        val transport = mediaTransportId ?: return
        block(id, transport)
    }
}
