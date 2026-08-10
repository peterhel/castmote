package se.constructions.castmote.caster

/** Resolves a host to the first matching strategy, falling back to [fallback] (InterceptCaster). */
class CasterRegistry(
    private val strategies: List<CasterStrategy>,
    private val fallback: CasterStrategy,
) {
    fun resolve(host: String): CasterStrategy =
        strategies.firstOrNull { it.matches(host) } ?: fallback

    companion object {
        /** v1 registry: known native-host strategies first, InterceptCaster as the default. */
        fun default(probe: se.constructions.castmote.resolver.HlsProbe = se.constructions.castmote.resolver.DefaultHlsProbe) =
            CasterRegistry(
                strategies = listOf(YouTubeCaster()),
                fallback = InterceptCaster(probe),
            )
    }
}
