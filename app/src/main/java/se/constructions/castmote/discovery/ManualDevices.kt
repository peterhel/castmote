package se.constructions.castmote.discovery

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A saved Chromecast: its host plus an optional friendly name. */
@Serializable
data class ManualEntry(val host: String, val name: String? = null)

/** Persists saved Chromecasts (for networks where mDNS can't reach them, and as device history). */
interface ManualHostStore {
    fun get(): String?
    fun set(value: String?)
}

/** App-private SharedPreferences-backed [ManualHostStore]. */
class PrefsManualHostStore(context: Context) : ManualHostStore {
    private val prefs = context.applicationContext.getSharedPreferences("castmote_manual", Context.MODE_PRIVATE)
    override fun get(): String? = prefs.getString("hosts", null)
    override fun set(value: String?) {
        prefs.edit().apply { if (value == null) remove("hosts") else putString("hosts", value) }.apply()
    }
}

/**
 * The remembered Chromecasts — those added by IP and any the user has connected to. Needed when
 * the device is on another subnet or across a VPN, where mDNS discovery can't see it but the
 * device is still reachable by unicast. Until the user touches the list, it shows [DEFAULTS].
 */
class ManualDevices(private val store: ManualHostStore) {

    // Filter out anything that isn't a usable host (e.g. a URL pasted into the IP field by
    // mistake) so a bad entry self-heals instead of lingering in the list.
    fun entries(): List<ManualEntry> = parse(store.get()).filter { normalize(it.host) != null }

    fun devices(): List<CastDevice> = entries().map { toDevice(it.host, it.name) }

    /**
     * Remembers [host] (most-recent-first, deduplicated). A non-null [name] sets/updates the
     * friendly name; a null [name] keeps any name already saved. Returns false for an unusable host.
     */
    fun add(host: String, name: String? = null): Boolean {
        val h = normalize(host) ?: return false
        val existing = entries()
        val keptName = name?.takeIf { it.isNotBlank() } ?: existing.firstOrNull { it.host == h }?.name
        val updated = (listOf(ManualEntry(h, keptName)) + existing.filterNot { it.host == h }).take(MAX)
        store.set(encode(updated))
        return true
    }

    fun remove(host: String) {
        store.set(encode(entries().filterNot { it.host == host }))
    }

    companion object {
        const val MAX = 20
        const val DEFAULT_PORT = 8009

        /** Pre-seeded manual devices shown until the list is edited. Empty: mDNS discovery
         *  finds Chromecasts automatically, and users add unicast hosts via the IP field. */
        val DEFAULTS = emptyList<ManualEntry>()

        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(ManualEntry.serializer())

        /**
         * Trim and accept an IPv4 or hostname; reject blanks, whitespace, and anything with a
         * `/` (a URL or path pasted into the IP field — not a Chromecast host).
         */
        fun normalize(host: String): String? =
            host.trim().takeIf {
                it.isNotBlank() && it.none { c -> c.isWhitespace() } && !it.contains('/')
            }

        fun toDevice(host: String, name: String? = null) = CastDevice(
            id = "manual:$host",
            friendlyName = name?.takeIf { it.isNotBlank() } ?: host,
            model = if (name?.isNotBlank() == true) host else "",
            host = host,
            port = DEFAULT_PORT,
        )

        fun isManual(device: CastDevice): Boolean = device.id.startsWith("manual:")

        private fun encode(entries: List<ManualEntry>) = json.encodeToString(serializer, entries)

        private fun parse(s: String?): List<ManualEntry> =
            if (s.isNullOrBlank()) DEFAULTS
            else runCatching { json.decodeFromString(serializer, s) }.getOrDefault(DEFAULTS)
    }
}
