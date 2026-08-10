package se.constructions.castmote.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.constructions.castmote.proto.CastMessage
import java.io.OutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * CASTV2 channel over a TLS socket to a Chromecast (port 8009). The device presents a
 * self-signed certificate with no usable PKI, so on the LAN we trust it unconditionally.
 */
class TlsCastChannel(
    private val host: String,
    private val port: Int = 8009,
    private val scope: CoroutineScope,
) : CastChannel {

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var output: OutputStream? = null
    private var readJob: Job? = null
    private val _incoming = MutableSharedFlow<CastMessage>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()
    private val _closed = MutableStateFlow(false)
    /** Flips to true when the socket dies or is closed. */
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    /** Opens the socket and starts the read loop. Call once before [send]. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(TrustAllCerts), SecureRandom())
        val s = context.socketFactory.createSocket(host, port) as SSLSocket
        try {
            s.startHandshake()
        } catch (e: Exception) {
            runCatching { s.close() }
            throw e
        }
        socket = s
        output = s.outputStream
        readJob = scope.launch(Dispatchers.IO) { readLoop(s) }
    }

    private suspend fun readLoop(s: SSLSocket) {
        // Don't pump frames until a collector has subscribed: the SharedFlow has no replay,
        // so anything emitted before CastConnection subscribes would be silently dropped.
        _incoming.subscriptionCount.first { it > 0 }
        val input = s.inputStream
        try {
            while (true) {
                val frame = Framing.readFrame(input) ?: break
                _incoming.emit(CastMessage.ADAPTER.decode(frame))
            }
        } catch (_: Exception) {
            // socket closed or read error → stop the loop
        } finally {
            _closed.value = true
        }
    }

    override suspend fun send(message: CastMessage) = withContext(Dispatchers.IO) {
        val out = output ?: error("channel not connected")
        try {
            out.write(Framing.encode(message.encode()))
            out.flush()
        } catch (e: java.io.IOException) {
            // The socket can go half-open (writes break while the blocked read sees nothing),
            // so a write failure is our only signal it's dead. Surface it so the controller/UI
            // marks the device disconnected and can reconnect, then rethrow for the caller.
            _closed.value = true
            throw e
        }
    }

    override fun close() {
        readJob?.cancel()
        readJob = null
        runCatching { socket?.close() }
        socket = null
        output = null
        _closed.value = true
    }

    private object TrustAllCerts : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
