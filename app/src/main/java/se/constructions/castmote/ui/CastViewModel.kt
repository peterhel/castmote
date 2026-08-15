package se.constructions.castmote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import se.constructions.castmote.controller.CastController
import se.constructions.castmote.controller.MediaStatus
import se.constructions.castmote.controller.ReceiverStatus
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import se.constructions.castmote.discovery.CastDevice
import se.constructions.castmote.discovery.CastDiscovery
import se.constructions.castmote.discovery.ManualDevices
import se.constructions.castmote.discovery.PrefsManualHostStore
import se.constructions.castmote.browser.DetectedStream
import se.constructions.castmote.browser.StreamKind
import se.constructions.castmote.browser.StreamSniffer
import se.constructions.castmote.caster.CastOutcome
import se.constructions.castmote.caster.CastRequest
import se.constructions.castmote.caster.CastSink
import se.constructions.castmote.caster.CasterRegistry
import se.constructions.castmote.caster.InterceptCaster
import se.constructions.castmote.history.CastHistory
import se.constructions.castmote.media.MediaControlService
import se.constructions.castmote.media.NowPlaying
import se.constructions.castmote.protocol.CastIds
import se.constructions.castmote.history.HistoryEntry
import se.constructions.castmote.history.PrefsHistoryStore
import se.constructions.castmote.resolver.CastUrlUseCase
import se.constructions.castmote.resolver.SvtVideo
import se.constructions.castmote.resolver.YtDlpStreamResolver
import se.constructions.castmote.youtube.PrefsCookieStore
import se.constructions.castmote.youtube.YouTubeAuth
import se.constructions.castmote.youtube.YouTubeException
import se.constructions.castmote.youtube.YouTubeUrl

class CastViewModel(app: Application) : AndroidViewModel(app) {

    private val discovery = CastDiscovery(app)
    private val manualDevices = ManualDevices(PrefsManualHostStore(app))
    private val _manualDevices = MutableStateFlow(manualDevices.devices())

    // mDNS-discovered devices plus any added by IP. Discovered entries override a manual one
    // with the same host (so the real friendly name wins once it resolves). onStart emits an
    // empty discovery list immediately so the saved/manual devices show even when mDNS finds
    // nothing (combine otherwise waits for both sources to emit).
    val devices: StateFlow<List<CastDevice>> =
        combine(discovery.devices().onStart { emit(emptyList()) }, _manualDevices) { discovered, manual ->
            val byHost = LinkedHashMap<String, CastDevice>()
            manual.forEach { byHost[it.host] = it }
            discovered.forEach { byHost[it.host] = it }
            byHost.values.toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun connectManual(host: String) {
        val device = ManualDevices.normalize(host)?.let { ManualDevices.toDevice(it) } ?: return
        connect(device) // connect() remembers it
    }

    fun removeManual(host: String) {
        manualDevices.remove(host)
        _manualDevices.value = manualDevices.devices()
    }

    /** Saves a connected device to history (keeps its friendly name when it has a real one). */
    private fun rememberDevice(device: CastDevice) {
        manualDevices.add(device.host, device.friendlyName.takeIf { it != device.host })
        _manualDevices.value = manualDevices.devices()
    }

    private val _connected = MutableStateFlow<CastDevice?>(null)
    val connected: StateFlow<CastDevice?> = _connected.asStateFlow()

    private var controller: CastController? = null
    private val collectorJobs = mutableListOf<Job>()

    private val _receiver = MutableStateFlow<ReceiverStatus?>(null)
    val receiver: StateFlow<ReceiverStatus?> = _receiver.asStateFlow()

    private val _media = MutableStateFlow<MediaStatus?>(null)
    val media: StateFlow<MediaStatus?> = _media.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val castUrlUseCase = CastUrlUseCase(YtDlpStreamResolver(app))

    private val _castStatus = MutableStateFlow<CastStatus>(CastStatus.Idle)
    val castStatus: StateFlow<CastStatus> = _castStatus.asStateFlow()

    private val castHistory = CastHistory(PrefsHistoryStore(app))
    private val _history = MutableStateFlow(castHistory.entries())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()
    fun clearHistory() {
        castHistory.clear()
        _history.value = emptyList()
    }

    private val youTubeAuth = YouTubeAuth(PrefsCookieStore(app))
    private val _youTubeSignedIn = MutableStateFlow(youTubeAuth.isSignedIn())
    val youTubeSignedIn: StateFlow<Boolean> = _youTubeSignedIn.asStateFlow()

    private val _showYouTubeLogin = MutableStateFlow(false)
    val showYouTubeLogin: StateFlow<Boolean> = _showYouTubeLogin.asStateFlow()
    fun openYouTubeLogin() { _showYouTubeLogin.value = true }
    fun closeYouTubeLogin() { _showYouTubeLogin.value = false }

    // --- Lock-screen / notification media control ---
    // Publish the current cast to NowPlaying (the MediaControlService renders it) and route the
    // notification's transport taps back to the live controller.
    private var mediaServiceStarted = false

    init {
        NowPlaying.onPlayPause = { playPause() }
        NowPlaying.onSeekTo = { ms -> seek(ms / 1000.0) }
        NowPlaying.onStop = { stopMedia() }
        viewModelScope.launch {
            combine(media, receiver, connected) { m, r, c ->
                c?.let { NowPlaying.playback(m, r, it.friendlyName) }
            }.collect { s ->
                NowPlaying.state.value = s
                if (s != null && !mediaServiceStarted) {
                    android.util.Log.i("CastmoteYT", "NowPlaying -> starting MediaControlService (title=${s.title} playing=${s.isPlaying})")
                    runCatching { MediaControlService.start(getApplication()) }
                        .onFailure { android.util.Log.e("CastmoteYT", "MediaControlService.start failed (${it.javaClass.simpleName}: ${it.message})") }
                    mediaServiceStarted = true
                } else if (s == null) {
                    mediaServiceStarted = false // the service self-stops when state goes null
                }
            }
        }
    }

    override fun onCleared() {
        NowPlaying.onPlayPause = null
        NowPlaying.onSeekTo = null
        NowPlaying.onStop = null
        NowPlaying.state.value = null
        super.onCleared()
    }

    // --- In-app browser cast ---
    val streamSniffer = StreamSniffer()
    private val casterRegistry = CasterRegistry.default()
    private val _browserPageUrl = MutableStateFlow("")
    private val _browserHost = MutableStateFlow("")

    /** Called by BrowserScreen whenever the WebView navigates to a new page. */
    fun onBrowserPage(url: String) {
        _browserPageUrl.value = url
        _browserHost.value = CastHistory.hostOf(url)
    }

    /** True when the resolved strategy reports something castable on the current page. */
    val browserCanCast: StateFlow<Boolean> =
        combine(_browserPageUrl, _browserHost, streamSniffer.streams) { page, host, streams ->
            if (page.isBlank()) false
            else casterRegistry.resolve(host).canCast(CastRequest(page, host, streams))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Sniffed streams to offer in the picker — only for the intercept path. Native-host casters
     * (YouTube) cast the current page directly, so they must not surface raw sniffed manifests as
     * pickable "options".
     */
    val browserStreams: StateFlow<List<DetectedStream>> =
        combine(_browserHost, streamSniffer.streams) { host, streams ->
            if (casterRegistry.resolve(host) is InterceptCaster) streams else emptyList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun toast(msg: String) {
        android.widget.Toast.makeText(getApplication<Application>(), msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private val castSink = object : CastSink {
        override suspend fun castStream(url: String, kind: StreamKind, hlsFmp4: Boolean) {
            castWithReconnect { it.castUrl(url, kind.contentType, null, 0, false, hlsFmp4) }
        }
        override suspend fun castPage(pageUrl: String) = doCastUrl(pageUrl)
    }

    /**
     * Cast the current browser page to [device], connecting to it on demand (no need to pre-connect
     * on the Remote tab). [chosen] forces a specific sniffed stream (else most-recent / page route).
     */
    fun castFromBrowser(device: CastDevice, chosen: DetectedStream? = null) {
        val page = _browserPageUrl.value.ifBlank { return }
        val host = _browserHost.value
        toast("Casting to ${device.friendlyName}…")
        viewModelScope.launch {
            try {
                val alreadyLive = controller?.let { _connected.value?.host == device.host && it.connected.value } == true
                if (!alreadyLive) establishConnection(device)
                val streams = chosen?.let { listOf(it) } ?: streamSniffer.streams.value
                when (casterRegistry.resolve(host).cast(CastRequest(page, host, streams), castSink)) {
                    is CastOutcome.NothingCastable -> toast("Nothing to cast on this page")
                    is CastOutcome.Failed -> toast("Cast failed")
                    is CastOutcome.Cast -> {
                        // Record the *page* URL (not the sniffed, short-lived stream) so Recent can
                        // relaunch it later and skip to the saved position — e.g. resume an SVT
                        // episode. Resume re-resolves via yt-dlp (works for DRM-free SVT/YouTube/etc).
                        recordHistory(page, null)
                        startedCastUrl = page
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _castStatus.value = CastStatus.Error("Cast failed: ${e.javaClass.simpleName}")
                toast("Cast failed")
            }
        }
    }

    fun connect(device: CastDevice) {
        rememberDevice(device)
        viewModelScope.launch {
            try {
                establishConnection(device)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("CastmoteYT", "connect to ${device.host} failed (${e.javaClass.simpleName}: ${e.message})", e)
                _online.value = false
            }
        }
    }

    /** Builds a controller, wires its flows, and suspends until the socket is up. */
    private suspend fun establishConnection(device: CastDevice): CastController {
        if (controller != null) disconnect()
        val c = CastController(device.host, viewModelScope)
        controller = c
        _connected.value = device
        _online.value = true
        collectorJobs += viewModelScope.launch { c.receiver.collect { _receiver.value = it } }
        collectorJobs += viewModelScope.launch { c.media.collect { _media.value = it; trackPosition(it) } }
        collectorJobs += viewModelScope.launch { c.connected.collect { _online.value = it } }
        android.util.Log.i("CastmoteYT", "connecting socket to ${device.host}:${device.port}")
        c.connect()
        android.util.Log.i("CastmoteYT", "socket connected to ${device.host}")
        return c
    }

    /** Returns a connected controller, transparently reconnecting if the socket has died. */
    private suspend fun liveController(): CastController? {
        val device = _connected.value ?: return null
        controller?.takeIf { it.connected.value }?.let { return it }
        android.util.Log.i("CastmoteYT", "liveController: no live controller, reconnecting to ${device.host}")
        return runCatching { establishConnection(device) }
            .onFailure { android.util.Log.e("CastmoteYT", "reconnect to ${device.host} failed (${it.javaClass.simpleName}: ${it.message})", it) }
            .getOrNull()
    }

    fun reconnect() {
        _connected.value?.let { connect(it) }
    }

    private var reconnectJob: Job? = null

    /**
     * Called on ON_RESUME. Backgrounding/locking the phone lets Doze drop the TCP socket, and
     * nothing re-establishes it until a command is sent — so the remote looks connected but is
     * dead. `connected.value` can't be trusted here: a socket that died silently while
     * backgrounded (no FIN/RST seen) still reports connected, and a controller whose initial
     * connect threw is left reporting its `true` default. So we actively probe with a round-trip
     * status request and only reconnect when it doesn't answer. A healthy link replies in well
     * under the timeout, so this never tears down a live connection.
     */
    fun reconnectIfNeeded() {
        val device = _connected.value ?: return
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            val c = controller
            if (c != null && c.connected.value) {
                val answered = withTimeoutOrNull(2500) { runCatching { c.refreshReceiver() }.isSuccess } == true
                if (answered) return@launch
            }
            runCatching { establishConnection(device) }
                .onFailure {
                    android.util.Log.w("CastmoteYT", "resume reconnect failed (${it.javaClass.simpleName})")
                    _online.value = false
                }
        }
    }

    fun disconnect() {
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        controller?.disconnect()
        controller = null
        _connected.value = null
        _receiver.value = null
        _media.value = null
        _castStatus.value = CastStatus.Idle
    }

    fun playPause() = withController {
        if (media.value?.playerState == "PLAYING") it.pause() else it.play()
    }

    fun stopMedia() = withController { it.stopMedia() }
    fun seek(seconds: Double) = withController { it.seek(seconds) }
    fun setVolume(level: Double) = withController { it.setVolume(level) }
    fun setMuted(muted: Boolean) = withController { it.setMuted(muted) }
    fun stopApp() = withController { it.stopApp() }

    fun castUrl(url: String) = launchCast(url, null)
    /** Resume a Recent entry: same app + stream, continuing from its saved position. */
    fun castEntry(entry: HistoryEntry) = launchCast(entry.url, entry.positionSeconds)

    private fun launchCast(url: String, resumeAt: Int?) {
        if (_connected.value == null) return
        viewModelScope.launch {
            try {
                doCastUrl(url, resumeAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: YouTubeException) {
                android.util.Log.w("CastmoteYT", "youtube cast failed: ${e.message}")
                _castStatus.value = CastStatus.Error(e.message)
            } catch (e: Exception) {
                android.util.Log.e("CastmoteYT", "cast failed (${e.javaClass.simpleName})", e)
                _castStatus.value = CastStatus.Error("Cast failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun doCastUrl(url: String, resumeAt: Int? = null) {
        // SVT: prefer relaunching into SVT's own app (subtitles / next-episode / DRM). If the
        // RE-based interop breaks, fall through to the default-receiver yt-dlp path (DRM-free only).
        SvtVideo.parsePlayId(url)?.let { playId ->
            android.util.Log.i("CastmoteYT", "native SVT attempt: playId=$playId resumeAt=$resumeAt url=$url")
            _castStatus.value = CastStatus.Resolving
            try {
                castWithReconnect { it.castSvt(playId, resumeAt ?: 0) }
                recordHistory(url, null)
                startedCastUrl = url
                _castStatus.value = CastStatus.Idle
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("CastmoteYT", "native SVT cast failed (${e.javaClass.simpleName}: ${e.message}); using default receiver")
            }
        }
        if (castUrlUseCase.needsResolving(url)) _castStatus.value = CastStatus.Resolving
        when (val r = castUrlUseCase.prepare(url)) {
            is CastUrlUseCase.Result.Ready -> {
                android.util.Log.i("CastmoteYT", "resolved -> ${r.contentType} live=${r.isLive} @${r.startSeconds}s url=${r.streamUrl.take(70)}")
                // This receiver can't sustain live HLS, so a live YouTube stream is cast via
                // the native YouTube app (lounge) instead of the raw stream. VOD stays on the
                // ad-free raw-stream path.
                val startSeconds = resumeAt ?: r.startSeconds
                val liveYouTubeId = if (r.isLive) YouTubeUrl.parseVideoId(url) else null
                if (liveYouTubeId != null) {
                    castWithReconnect { it.castYouTube(liveYouTubeId, startSeconds, youTubeAuth.authHeaders()) }
                } else {
                    castWithReconnect { it.castUrl(r.streamUrl, r.contentType, r.title, startSeconds, r.isLive, r.hlsFmp4) }
                }
                recordHistory(url, r.title)
                startedCastUrl = url
                _castStatus.value = CastStatus.Idle
            }
            is CastUrlUseCase.Result.Failed ->
                _castStatus.value = CastStatus.Error(r.message)
        }
    }

    /**
     * Runs a cast against a live controller. The socket can be half-open after idle time
     * (e.g. while signing into YouTube), so a broken-pipe [java.net.SocketException] on the
     * first send triggers one transparent reconnect-and-retry.
     */
    private fun recordHistory(url: String, title: String?) {
        castHistory.add(url, title)
        _history.value = castHistory.entries()
    }

    // Resume tracking. startedCastUrl is the page URL of a cast *Castmote* initiated (its media
    // contentId is an opaque stream URL, so we can't derive the page from it — we remember it).
    // trackedContentId guards against bleeding one video's position onto another when the session
    // changes. lastPersistedPos throttles writes.
    private var startedCastUrl: String? = null
    private var trackedContentId: String? = null
    private var lastPersistedPos = -100

    /**
     * A relaunchable page URL for the current session, or null if we can't resume it. Known
     * foreign receivers (SVT) expose the play-id as media contentId, which maps straight to a
     * page URL yt-dlp can re-resolve; our own casts fall back to the URL we launched.
     */
    private fun resumeUrlFor(receiver: ReceiverStatus?, media: MediaStatus): String? =
        when (receiver?.appId) {
            CastIds.SVT_RECEIVER -> media.contentId?.let { "https://www.svtplay.se/video/$it" }
            else -> startedCastUrl
        }

    /**
     * Persists playback position onto a Recent entry so a tap later relaunches and skips to it.
     * Auto-registers a foreign session (e.g. SVT started from the SVT app) the first time we see
     * it, then tracks it — Castmote reads the position off the receiver over the standard media
     * namespace even for a cast it didn't start.
     */
    private fun trackPosition(m: MediaStatus?) {
        if (m == null || m.playerState != "PLAYING") return
        val url = resumeUrlFor(_receiver.value, m) ?: return
        // A new session = a new, non-null contentId. The default receiver drops the `media` block
        // (contentId=null) on plain progress polls, so ignoring null avoids re-registering every tick.
        val cid = m.contentId
        if (cid != null && cid != trackedContentId) {
            trackedContentId = cid
            lastPersistedPos = -100
            if (castHistory.entries().none { it.url == url }) recordHistory(url, m.title)
        }
        val secs = m.currentTime.toInt()
        // ponytail: skip the first 10s (not worth a "resume 0:03") and throttle to 5s steps.
        if (secs < 10 || kotlin.math.abs(secs - lastPersistedPos) < 5) return
        lastPersistedPos = secs
        castHistory.updatePosition(url, secs)
        _history.value = castHistory.entries()
    }

    private suspend fun castWithReconnect(block: suspend (CastController) -> Unit) {
        val c = liveController() ?: throw YouTubeException("Not connected to the TV")
        try {
            block(c)
        } catch (e: java.net.SocketException) {
            val device = _connected.value ?: throw e
            block(establishConnection(device))
        }
    }

    fun saveYouTubeCookies(cookie: String) {
        // Only persist a cookie that actually carries auth — otherwise tapping "Done" before the
        // login page finishes would wipe a previously-good session with a credential-less cookie.
        if (YouTubeAuth.hasAnyApisid(cookie)) {
            youTubeAuth.save(cookie)
            _youTubeSignedIn.value = true
        }
        _showYouTubeLogin.value = false
    }

    fun signOutYouTube() {
        youTubeAuth.signOut()
        _youTubeSignedIn.value = false
    }

    private fun withController(block: suspend (CastController) -> Unit) {
        val c = controller ?: return
        launchCommand { block(c) }
    }

    private fun launchCommand(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Command failed (request timeout / disconnect). v1: ignored; could surface to UI later.
            }
        }
    }
}
