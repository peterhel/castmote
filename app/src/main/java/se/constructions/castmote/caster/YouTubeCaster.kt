package se.constructions.castmote.caster

import se.constructions.castmote.youtube.YouTubeUrl

/**
 * Known-host strategy: cast the current YouTube page by URL, delegating to the app's existing
 * routing (VOD → ad-free raw progressive on the default receiver; live → native YouTube app via
 * the lounge). Proof that a "known host, cast by page URL" strategy slots into the registry.
 */
class YouTubeCaster : CasterStrategy {
    // isYouTubeUrl parses the host out of a URL, so wrap the bare host in one.
    override fun matches(host: String): Boolean = YouTubeUrl.isYouTubeUrl("https://$host")

    override fun canCast(req: CastRequest): Boolean = YouTubeUrl.parseVideoId(req.pageUrl) != null

    override suspend fun cast(req: CastRequest, sink: CastSink): CastOutcome {
        if (YouTubeUrl.parseVideoId(req.pageUrl) == null) return CastOutcome.NothingCastable
        sink.castPage(req.pageUrl)
        return CastOutcome.Cast
    }
}
