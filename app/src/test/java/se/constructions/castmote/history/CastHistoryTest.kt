package se.constructions.castmote.history

import org.junit.Assert.assertEquals
import org.junit.Test

class CastHistoryTest {

    private class MemStore(var value: String? = null) : HistoryStore {
        override fun get() = value
        override fun set(v: String?) { value = v }
    }

    private fun history(store: HistoryStore, t: Long = 1L) = CastHistory(store) { t }

    @Test fun emptyByDefault() {
        assertEquals(emptyList<HistoryEntry>(), CastHistory(MemStore()).entries())
    }

    @Test fun addStoresMostRecentFirst() {
        val store = MemStore()
        history(store, 1).add("https://a.com/1", "A")
        history(store, 2).add("https://b.com/2", "B")
        assertEquals(listOf("https://b.com/2", "https://a.com/1"), history(store).entries().map { it.url })
    }

    @Test fun reCastingMovesToTopWithoutDuplicating() {
        val store = MemStore()
        history(store, 1).add("https://a.com/1", "A")
        history(store, 2).add("https://b.com/2", "B")
        history(store, 3).add("https://a.com/1", "A")
        val urls = history(store).entries().map { it.url }
        assertEquals(listOf("https://a.com/1", "https://b.com/2"), urls)
    }

    @Test fun cappedAtMax() {
        val store = MemStore()
        repeat(CastHistory.MAX + 5) { i -> history(store, i.toLong()).add("https://s.com/$i", null) }
        assertEquals(CastHistory.MAX, history(store).entries().size)
    }

    @Test fun clearEmptiesHistory() {
        val store = MemStore()
        history(store).add("https://a.com/1", "A")
        history(store).clear()
        assertEquals(emptyList<HistoryEntry>(), history(store).entries())
    }

    @Test fun hostStripsWwwAndScheme() {
        assertEquals("youtube.com", CastHistory.hostOf("https://www.youtube.com/watch?v=abc"))
        assertEquals("svtplay.se", CastHistory.hostOf("https://svtplay.se/video/x"))
        assertEquals("youtu.be", CastHistory.hostOf("https://youtu.be/abc"))
    }

    @Test fun faviconUrlTargetsHost() {
        assertEquals(
            "https://www.google.com/s2/favicons?sz=64&domain=youtube.com",
            CastHistory.faviconUrl("youtube.com"),
        )
    }
}
