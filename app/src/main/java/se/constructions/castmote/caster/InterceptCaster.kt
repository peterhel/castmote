package se.constructions.castmote.caster

import se.constructions.castmote.browser.StreamKind
import se.constructions.castmote.resolver.DefaultHlsProbe
import se.constructions.castmote.resolver.HlsProbe

/**
 * Default strategy for non-DRM sites: cast a sniffed manifest to the default media receiver.
 * Picks the most-recently-detected manifest (the UI may pass a single chosen one) and, for HLS,
 * probes it for fMP4/CMAF so the receiver gets the right segment-format hint.
 */
class InterceptCaster(private val probe: HlsProbe = DefaultHlsProbe) : CasterStrategy {
    override fun matches(host: String): Boolean = true

    override fun canCast(req: CastRequest): Boolean = req.streams.isNotEmpty()

    override suspend fun cast(req: CastRequest, sink: CastSink): CastOutcome {
        val stream = req.streams.lastOrNull() ?: return CastOutcome.NothingCastable
        val fmp4 = stream.kind == StreamKind.HLS && probe.isFmp4(stream.url)
        sink.castStream(stream.url, stream.kind, fmp4)
        return CastOutcome.Cast
    }
}
