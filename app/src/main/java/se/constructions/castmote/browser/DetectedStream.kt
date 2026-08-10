package se.constructions.castmote.browser

/** A castable manifest seen on the current page. */
data class DetectedStream(
    val url: String,
    val kind: StreamKind,
    val pageUrl: String,
)
