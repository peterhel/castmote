package se.constructions.castmote.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class TxtRecordsTest {
    @Test
    fun extractsFriendlyNameModelAndId() {
        val record = TxtRecords.parse(
            mapOf("fn" to "Living Room TV", "md" to "Chromecast Ultra", "id" to "abcd1234"),
            host = "192.168.1.50",
            port = 8009,
        )
        assertEquals("Living Room TV", record.friendlyName)
        assertEquals("Chromecast Ultra", record.model)
        assertEquals("abcd1234", record.id)
        assertEquals("192.168.1.50", record.host)
        assertEquals(8009, record.port)
    }

    @Test
    fun fallsBackToHostWhenNoFriendlyName() {
        val record = TxtRecords.parse(emptyMap(), host = "192.168.1.51", port = 8009)
        assertEquals("192.168.1.51", record.friendlyName)
        assertEquals("192.168.1.51", record.id)
        assertEquals("", record.model)
    }
}
