package se.constructions.castmote.discovery

/** Builds a [CastDevice] from mDNS TXT key/values plus the resolved address. */
object TxtRecords {
    fun parse(txt: Map<String, String>, host: String, port: Int): CastDevice {
        val friendlyName = txt["fn"]?.takeIf { it.isNotBlank() } ?: host
        return CastDevice(
            id = txt["id"] ?: host,
            friendlyName = friendlyName,
            model = txt["md"] ?: "",
            host = host,
            port = port,
        )
    }
}
