package se.constructions.castmote.resolver

/** Turns a page URL into a castable [ResolvedStream] using on-device yt-dlp. */
interface StreamResolver {
    suspend fun resolve(url: String): ResolvedStream
}

/** Thrown on any resolve failure; [message] is safe to show the user. */
class ResolverException(override val message: String) : Exception(message)
