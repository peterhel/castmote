package se.constructions.castmote.caster

import se.constructions.castmote.browser.DetectedStream

/** What the browser knows when the user taps Cast. */
data class CastRequest(
    val pageUrl: String,
    val host: String,
    val streams: List<DetectedStream>,
)

sealed interface CastOutcome {
    object Cast : CastOutcome
    object NothingCastable : CastOutcome
    data class Failed(val message: String) : CastOutcome
}

/** Decides, per host, how the current page is cast. */
interface CasterStrategy {
    /** Does this strategy handle the given host? */
    fun matches(host: String): Boolean

    /** Is there something castable right now (drives the Cast badge visibility)? */
    fun canCast(req: CastRequest): Boolean

    suspend fun cast(req: CastRequest, sink: CastSink): CastOutcome
}
