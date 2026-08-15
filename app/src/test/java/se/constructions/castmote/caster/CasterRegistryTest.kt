package se.constructions.castmote.caster

import org.junit.Assert.assertTrue
import org.junit.Test

class CasterRegistryTest {
    private val registry = CasterRegistry.default()

    @Test fun youtubeHostResolvesToYouTubeCaster() {
        assertTrue(registry.resolve("m.youtube.com") is YouTubeCaster)
    }

    @Test fun svtHostResolvesToSvtCaster() {
        assertTrue(registry.resolve("svtplay.se") is SvtCaster)
        assertTrue(registry.resolve("www.svtplay.se") is SvtCaster)
    }

    @Test fun unknownHostFallsBackToInterceptCaster() {
        assertTrue(registry.resolve("ehftv.com") is InterceptCaster)
    }
}
