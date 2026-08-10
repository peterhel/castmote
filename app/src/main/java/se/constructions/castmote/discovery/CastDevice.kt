package se.constructions.castmote.discovery

/** A Chromecast discovered on the LAN. */
data class CastDevice(
    val id: String,
    val friendlyName: String,
    val model: String,
    val host: String,
    val port: Int,
)
