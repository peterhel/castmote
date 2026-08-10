package se.constructions.castmote.protocol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.constructions.castmote.proto.CastMessage

@OptIn(ExperimentalCoroutinesApi::class)
class CastConnectionTest {

    private fun message(namespace: String, payload: String) = CastMessage(
        protocol_version = CastMessage.ProtocolVersion.CASTV2_1_0,
        source_id = "receiver-0",
        destination_id = "sender-0",
        namespace = namespace,
        payload_type = CastMessage.PayloadType.STRING,
        payload_utf8 = payload,
    )

    /** A channel whose send always fails — simulates a dead socket (broken pipe). */
    private class ThrowingChannel : CastChannel {
        private val flow = MutableSharedFlow<CastMessage>()
        override val incoming = flow.asSharedFlow()
        override suspend fun send(message: CastMessage) { throw java.io.IOException("Broken pipe") }
        override fun close() {}
    }

    @Test
    fun heartbeatSendFailureDoesNotCrash() = runTest(UnconfinedTestDispatcher()) {
        // If the heartbeat's broken-pipe send propagated, runTest would fail with the IOException.
        val conn = CastConnection(ThrowingChannel(), this)
        conn.start()
        conn.close()
    }

    @Test
    fun startSendsVirtualConnectAndPing() = runTest(UnconfinedTestDispatcher()) {
        val ch = FakeCastChannel()
        val conn = CastConnection(ch, this)
        conn.start()

        val namespaces = ch.sent.map { it.namespace }
        assertTrue(namespaces.contains(Namespaces.CONNECTION))
        assertTrue(namespaces.contains(Namespaces.HEARTBEAT))
        conn.close()
    }

    @Test
    fun respondsToPingWithPong() = runTest(UnconfinedTestDispatcher()) {
        val ch = FakeCastChannel()
        val conn = CastConnection(ch, this)
        conn.start()
        ch.inject(message(Namespaces.HEARTBEAT, """{"type":"PING"}"""))

        val pongs = ch.sent.filter {
            it.namespace == Namespaces.HEARTBEAT &&
                Json.parseToJsonElement(it.payload_utf8!!).jsonObject["type"]!!.jsonPrimitive.content == "PONG"
        }
        assertTrue(pongs.isNotEmpty())
        conn.close()
    }

    @Test
    fun requestResolvesWhenResponseWithMatchingRequestIdArrives() = runTest(UnconfinedTestDispatcher()) {
        val ch = FakeCastChannel()
        val conn = CastConnection(ch, this)
        conn.start()

        val deferred = async {
            conn.request(Namespaces.RECEIVER, CastIds.RECEIVER) { Payloads.getStatus(it) }
        }

        // Find the requestId the connection actually sent.
        val request = ch.sent.last { it.namespace == Namespaces.RECEIVER }
        val sentId = Json.parseToJsonElement(request.payload_utf8!!).jsonObject["requestId"]!!.jsonPrimitive.content.toInt()

        val responsePayload = buildJsonObject {
            put("type", "RECEIVER_STATUS")
            put("requestId", sentId)
            put("answer", 42)
        }.toString()
        ch.inject(message(Namespaces.RECEIVER, responsePayload))

        val result = deferred.await()
        assertEquals(42, result["answer"]!!.jsonPrimitive.content.toInt())
        conn.close()
    }

    @Test
    fun malformedMessageDoesNotKillTheCollector() = runTest(UnconfinedTestDispatcher()) {
        val ch = FakeCastChannel()
        val conn = CastConnection(ch, this)
        conn.start()

        // A RECEIVER_STATUS whose "status" is a primitive, not an object → handle() would throw.
        ch.inject(message(Namespaces.RECEIVER, """{"type":"RECEIVER_STATUS","status":42}"""))
        // The collector must still be alive to answer a subsequent PING.
        ch.inject(message(Namespaces.HEARTBEAT, """{"type":"PING"}"""))

        val pongs = ch.sent.filter {
            it.namespace == Namespaces.HEARTBEAT &&
                Json.parseToJsonElement(it.payload_utf8!!).jsonObject["type"]!!.jsonPrimitive.content == "PONG"
        }
        assertTrue(pongs.isNotEmpty())
        conn.close()
    }
}
