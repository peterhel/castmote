package se.constructions.castmote.resolver

/** A directly-castable stream produced by the resolver. */
data class ResolvedStream(
    val streamUrl: String,
    val contentType: String,
    val title: String?,
    val isLive: Boolean = false,
)
