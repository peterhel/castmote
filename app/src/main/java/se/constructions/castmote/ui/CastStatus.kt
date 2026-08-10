package se.constructions.castmote.ui

/** Status of the most recent cast-a-link action, surfaced under the URL field. */
sealed interface CastStatus {
    data object Idle : CastStatus
    data object Resolving : CastStatus
    data class Error(val message: String) : CastStatus
}
