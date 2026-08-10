package se.constructions.castmote.youtube

/** Thrown on any YouTube cast failure; [message] is safe to show the user. */
class YouTubeException(override val message: String) : Exception(message)
