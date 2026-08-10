package se.constructions.castmote.resolver

import se.constructions.castmote.youtube.YouTubeUrl

/**
 * Prepares a user-entered URL for casting: direct media files pass through with a
 * guessed MIME type; page URLs go through the [StreamResolver].
 */
class CastUrlUseCase(
    private val resolver: StreamResolver,
    private val hlsProbe: HlsProbe = DefaultHlsProbe,
) {

    sealed interface Result {
        data class Ready(
            val streamUrl: String,
            val contentType: String,
            val title: String?,
            val startSeconds: Int = 0,
            val isLive: Boolean = false,
            val hlsFmp4: Boolean = false,
        ) : Result
        data class Failed(val message: String) : Result
    }

    private companion object {
        const val HLS_MIME = "application/vnd.apple.mpegurl"
    }

    fun needsResolving(url: String): Boolean =
        YouTubeUrl.isYouTubeUrl(url) || !UrlClassifier.isDirectMedia(url)

    suspend fun prepare(url: String): Result {
        // YouTube goes through yt-dlp like any other page: the direct stream it returns has no
        // ads (ads are inserted by the YouTube player, not the stream), so casting it to the
        // default media receiver is inherently ad-free. The `t=` timestamp becomes the start.
        if (YouTubeUrl.isYouTubeUrl(url)) {
            if (YouTubeUrl.parseVideoId(url) == null) return Result.Failed("That's not a YouTube link")
            return resolveToReady(url, YouTubeUrl.parseStartSeconds(url))
        }
        if (UrlClassifier.isDirectMedia(url)) {
            return ready(url, UrlClassifier.guessContentType(url), null, 0, false)
        }
        return resolveToReady(url, 0)
    }

    private suspend fun resolveToReady(url: String, startSeconds: Int): Result = try {
        val stream = resolver.resolve(url)
        // Live streams can't be seeked to an absolute past offset — play the live edge.
        val start = if (stream.isLive) 0 else startSeconds
        ready(stream.streamUrl, stream.contentType, stream.title, start, stream.isLive)
    } catch (e: ResolverException) {
        Result.Failed(e.message)
    }

    /** Builds a Ready, probing HLS streams for fMP4 segments so the receiver gets the right hint. */
    private suspend fun ready(
        streamUrl: String,
        contentType: String,
        title: String?,
        startSeconds: Int,
        isLive: Boolean,
    ): Result.Ready {
        // Only VOD HLS reaches the default receiver as a raw stream (live HLS is routed elsewhere).
        val fmp4 = !isLive && contentType == HLS_MIME && hlsProbe.isFmp4(streamUrl)
        return Result.Ready(streamUrl, contentType, title, startSeconds, isLive, fmp4)
    }
}
