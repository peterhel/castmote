package se.constructions.castmote.protocol

import org.junit.Assert.assertEquals
import org.junit.Test
import se.constructions.castmote.proto.CastMessage

class CastMessageProtoTest {
    @Test
    fun encodesAndDecodesRoundTrip() {
        val msg = CastMessage(
            protocol_version = CastMessage.ProtocolVersion.CASTV2_1_0,
            source_id = "sender-0",
            destination_id = "receiver-0",
            namespace = "urn:x-cast:com.google.cast.tp.connection",
            payload_type = CastMessage.PayloadType.STRING,
            payload_utf8 = """{"type":"CONNECT"}""",
        )
        val bytes = msg.encode()
        val decoded = CastMessage.ADAPTER.decode(bytes)
        assertEquals(msg, decoded)
        assertEquals("""{"type":"CONNECT"}""", decoded.payload_utf8)
    }
}
