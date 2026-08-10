package se.constructions.castmote.browser

/** A castable stream container, with the MIME type to send in the cast LOAD. */
enum class StreamKind(val contentType: String) {
    HLS("application/vnd.apple.mpegurl"),
    DASH("application/dash+xml"),
}
