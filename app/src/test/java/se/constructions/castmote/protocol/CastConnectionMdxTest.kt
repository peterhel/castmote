package se.constructions.castmote.protocol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.constructions.castmote.proto.CastMessage

@OptIn(ExperimentalCoroutinesApi::class)
class CastConnectionMdxTest {
    private fun msg(ns: String, payload: String) = CastMessage(
        protocol_version = CastMessage.ProtocolVersion.CASTV2_1_0,
        source_id = "receiver-0", destination_id = "sender-0",
        namespace = ns, payload_type = CastMessage.PayloadType.STRING, payload_utf8 = payload,
    )

    @Test
    fun awaitMessageOfTypeResolvesOnMatchingMessage() = runTest(UnconfinedTestDispatcher()) {
        val ch = FakeCastChannel()
        val conn = CastConnection(ch, this)
        conn.start()
        val deferred = async { conn.awaitMessageOfType(Namespaces.MDX, "mdxSessionStatus") }
        conn.sendOn(Namespaces.MDX, "web-1", Payloads.getMdxSessionStatus())
        ch.inject(msg(Namespaces.MDX, """{"type":"mdxSessionStatus","data":{"screenId":"SCREEN123"}}"""))
        val result = deferred.await()
        assertEquals("SCREEN123", result["data"]!!.jsonObject["screenId"]!!.jsonPrimitive.content)
        assertTrue(ch.sent.any { it.namespace == Namespaces.MDX })
        conn.close()
    }

    @Test
    fun sendAndAwaitTypeRegistersBeforeSendingThenResolves() = runTest(UnconfinedTestDispatcher()) {
        val ch = FakeCastChannel()
        val conn = CastConnection(ch, this)
        conn.start()
        val deferred = async {
            conn.sendAndAwaitType(Namespaces.MDX, "web-1", Payloads.getMdxSessionStatus(), "mdxSessionStatus")
        }
        ch.inject(msg(Namespaces.MDX, """{"type":"mdxSessionStatus","data":{"screenId":"S2"}}"""))
        val result = deferred.await()
        assertEquals("S2", result["data"]!!.jsonObject["screenId"]!!.jsonPrimitive.content)
        assertTrue(ch.sent.any { it.namespace == Namespaces.MDX })
        conn.close()
    }
}
