package se.constructions.castmote.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import se.constructions.castmote.proto.CastMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Orchestrates one CASTV2 session over a [CastChannel]:
 * opens virtual connections, runs the heartbeat, correlates requestIds to responses,
 * and exposes the latest receiver/media status payloads.
 */
class CastConnection(
    private val channel: CastChannel,
    private val scope: CoroutineScope,
    private val heartbeatIntervalMs: Long = 5_000,
    private val requestTimeoutMs: Long = 5_000,
) {
    private val nextRequestId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val typeWaiters = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val jobs = mutableListOf<Job>()

    private val _receiverStatus = MutableStateFlow<JsonObject?>(null)
    val receiverStatus: StateFlow<JsonObject?> = _receiverStatus.asStateFlow()

    private val _mediaStatus = MutableStateFlow<JsonObject?>(null)
    val mediaStatus: StateFlow<JsonObject?> = _mediaStatus.asStateFlow()

    fun start() {
        jobs += scope.launch {
            channel.incoming.collect { message ->
                // Isolate per-message failures: one malformed inbound message must not
                // tear down the collector (which would stop heartbeat replies and time out
                // all future requests).
                runCatching { handle(message) }
            }
        }
        jobs += scope.launch {
            try {
                virtualConnect(CastIds.RECEIVER)
                while (isActive) {
                    channel.send(envelope(Namespaces.HEARTBEAT, CastIds.RECEIVER, Payloads.ping()))
                    delay(heartbeatIntervalMs)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Socket died (e.g. Chromecast disconnected) — stop the heartbeat instead of
                // letting the broken-pipe write crash the app. TlsCastChannel.closed surfaces
                // the disconnect to the controller/UI.
            }
        }
    }

    suspend fun virtualConnect(destinationId: String) {
        channel.send(envelope(Namespaces.CONNECTION, destinationId, Payloads.connect()))
    }

    /** Sends a request to [destinationId] and suspends until the matching response arrives. */
    suspend fun request(
        namespace: String,
        destinationId: String,
        buildPayload: (requestId: Int) -> String,
    ): JsonObject {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        try {
            channel.send(envelope(namespace, destinationId, buildPayload(id)))
            return withTimeout(requestTimeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    /** Sends a raw payload on [namespace] to [destinationId] with no requestId correlation. */
    suspend fun sendOn(namespace: String, destinationId: String, payload: String) {
        channel.send(envelope(namespace, destinationId, payload))
    }

    /** Suspends until the next inbound message on [namespace] whose JSON `type` == [type]. */
    suspend fun awaitMessageOfType(namespace: String, type: String, timeoutMs: Long = 8_000): JsonObject {
        val key = "$namespace|$type"
        val deferred = CompletableDeferred<JsonObject>()
        typeWaiters[key] = deferred
        try {
            return withTimeout(timeoutMs) { deferred.await() }
        } finally {
            typeWaiters.remove(key)
        }
    }

    /**
     * Registers a waiter for a [responseType] on [namespace], THEN sends [payload] — so a fast
     * reply can't arrive before we're listening. Use this for request/response exchanges that
     * aren't requestId-correlated (e.g. the YouTube mdx getMdxSessionStatus → mdxSessionStatus).
     */
    suspend fun sendAndAwaitType(
        namespace: String,
        destinationId: String,
        payload: String,
        responseType: String,
        timeoutMs: Long = 8_000,
    ): JsonObject {
        val key = "$namespace|$responseType"
        val deferred = CompletableDeferred<JsonObject>()
        typeWaiters[key] = deferred
        try {
            channel.send(envelope(namespace, destinationId, payload))
            return withTimeout(timeoutMs) { deferred.await() }
        } finally {
            typeWaiters.remove(key)
        }
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        channel.close()
    }

    private suspend fun handle(message: CastMessage) {
        val payload = parse(message.payload_utf8) ?: return
        val type = payload["type"]?.jsonPrimitive?.contentOrNull

        if (message.namespace == Namespaces.HEARTBEAT) {
            if (type == "PING") {
                channel.send(envelope(Namespaces.HEARTBEAT, CastIds.RECEIVER, Payloads.pong()))
            }
            return
        }

        when (type) {
            "RECEIVER_STATUS" -> payload["status"]?.let { _receiverStatus.value = it.jsonObject }
            "MEDIA_STATUS" -> _mediaStatus.value = payload
        }

        if (type != null) {
            typeWaiters.remove("${message.namespace}|$type")?.complete(payload)
        }

        val requestId = payload["requestId"]?.jsonPrimitive?.intOrNull
        if (requestId != null) {
            pending.remove(requestId)?.complete(payload)
        }
    }

    private fun parse(s: String?): JsonObject? =
        if (s.isNullOrEmpty()) null else runCatching { Json.parseToJsonElement(s).jsonObject }.getOrNull()

    private fun envelope(namespace: String, destinationId: String, payload: String) = CastMessage(
        protocol_version = CastMessage.ProtocolVersion.CASTV2_1_0,
        source_id = CastIds.SENDER,
        destination_id = destinationId,
        namespace = namespace,
        payload_type = CastMessage.PayloadType.STRING,
        payload_utf8 = payload,
    )
}
