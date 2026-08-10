package se.constructions.castmote.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualDevicesTest {

    private class MemStore(var value: String? = null) : ManualHostStore {
        override fun get() = value
        override fun set(v: String?) { value = v }
    }

    /** A store that has been edited (so we're past the seeded DEFAULTS). */
    private fun emptyStore() = MemStore("[]")

    @Test fun unsetStoreShowsSeededDefaults() {
        val m = ManualDevices(MemStore(null))
        assertEquals(ManualDevices.DEFAULTS, m.entries())
    }

    @Test fun editedThenEmptyStaysEmpty() {
        assertEquals(emptyList<ManualEntry>(), ManualDevices(emptyStore()).entries())
    }

    @Test fun addPersistsMostRecentFirstDeduped() {
        val store = emptyStore()
        val m = ManualDevices(store)
        m.add("192.168.1.10")
        m.add("192.168.1.11")
        m.add("192.168.1.10")
        assertEquals(listOf("192.168.1.10", "192.168.1.11"), m.entries().map { it.host })
    }

    @Test fun addKeepsExistingNameWhenNoneGiven() {
        val store = emptyStore()
        val m = ManualDevices(store)
        m.add("192.168.1.10", "Speaker B")
        m.add("192.168.1.10") // reconnect without a name
        assertEquals("Speaker B", m.entries().first().name)
    }

    @Test fun normalizeTrimsAndRejectsBlankWhitespaceOrUrl() {
        assertEquals("10.0.0.5", ManualDevices.normalize("  10.0.0.5 "))
        assertEquals("chromecast.local", ManualDevices.normalize("chromecast.local"))
        assertNull(ManualDevices.normalize("   "))
        assertNull(ManualDevices.normalize("1.2.3.4 5.6.7.8"))
        assertNull(ManualDevices.normalize("https://m.youtube.com/watch?v=abc"))
        assertNull(ManualDevices.normalize("192.168.1.5/path"))
    }

    @Test fun entriesHideBadHostsLikeStoredUrls() {
        // A URL that slipped into storage before validation should not show as a device.
        val store = MemStore("""[{"host":"https://m.youtube.com/watch?v=x","name":null},{"host":"192.168.1.10","name":"Speaker B"}]""")
        assertEquals(listOf("192.168.1.10"), ManualDevices(store).entries().map { it.host })
    }

    @Test fun addRejectsUnusableHost() {
        assertFalse(ManualDevices(emptyStore()).add("   "))
    }

    @Test fun removeDropsHost() {
        val store = emptyStore()
        val m = ManualDevices(store)
        m.add("a.local")
        m.add("b.local")
        m.remove("a.local")
        assertEquals(listOf("b.local"), m.entries().map { it.host })
    }

    @Test fun toDeviceUsesNameAndShowsHostAsSubtitle() {
        val named = ManualDevices.toDevice("192.168.1.10", "Speaker B")
        assertEquals("Speaker B", named.friendlyName)
        assertEquals("192.168.1.10", named.model)
        assertEquals(8009, named.port)
        assertTrue(ManualDevices.isManual(named))

        val unnamed = ManualDevices.toDevice("192.168.1.10")
        assertEquals("192.168.1.10", unnamed.friendlyName)
        assertEquals("", unnamed.model)
    }

    @Test fun discoveredDeviceIsNotManual() {
        val d = CastDevice("uuid", "Speaker A", "Chromecast", "192.168.1.10", 8009)
        assertFalse(ManualDevices.isManual(d))
    }
}
