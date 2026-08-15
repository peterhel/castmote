package se.constructions.castmote.caster

import se.constructions.castmote.resolver.SvtVideo

/**
 * Known-host strategy for SVT Play: cast the current svtplay.se/video page by URL, which the app's
 * routing sends into SVT's own receiver (subtitles / DRM), not the sniffed-manifest default path.
 */
class SvtCaster : CasterStrategy {
    override fun matches(host: String): Boolean = host.removePrefix("www.").endsWith("svtplay.se")

    override fun canCast(req: CastRequest): Boolean = SvtVideo.parsePlayId(req.pageUrl) != null

    override suspend fun cast(req: CastRequest, sink: CastSink): CastOutcome {
        if (SvtVideo.parsePlayId(req.pageUrl) == null) return CastOutcome.NothingCastable
        sink.castPage(req.pageUrl)
        return CastOutcome.Cast
    }
}
