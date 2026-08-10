package se.constructions.castmote.resolver

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastUrlUseCaseTest {

    private class FakeResolver(
        val result: ResolvedStream? = null,
        val error: String? = null,
    ) : StreamResolver {
        var calledWith: String? = null
        override suspend fun resolve(url: String): ResolvedStream {
            calledWith = url
            error?.let { throw ResolverException(it) }
            return result!!
        }
    }

    // Avoid network in unit tests; tests that care about fmp4 set their own probe.
    private val noFmp4 = HlsProbe { false }

    @Test
    fun directMediaSkipsResolverAndUsesGuessedType() = runBlocking {
        val fake = FakeResolver()
        val useCase = CastUrlUseCase(fake, noFmp4)
        assertFalse(useCase.needsResolving("https://x/clip.mp4"))
        val r = useCase.prepare("https://x/clip.mp4") as CastUrlUseCase.Result.Ready
        assertEquals("https://x/clip.mp4", r.streamUrl)
        assertEquals("video/mp4", r.contentType)
        assertNull(fake.calledWith)
    }

    @Test
    fun pageUrlResolvesViaResolver() = runBlocking {
        val fake = FakeResolver(
            result = ResolvedStream("https://x/m.m3u8", "application/vnd.apple.mpegurl", "T"),
        )
        val useCase = CastUrlUseCase(fake, noFmp4)
        assertTrue(useCase.needsResolving("https://svtplay.se/video/1"))
        val r = useCase.prepare("https://svtplay.se/video/1") as CastUrlUseCase.Result.Ready
        assertEquals("https://x/m.m3u8", r.streamUrl)
        assertEquals("T", r.title)
        assertEquals("https://svtplay.se/video/1", fake.calledWith)
    }

    @Test
    fun hlsStreamIsProbedForFmp4() = runBlocking {
        val fake = FakeResolver(result = ResolvedStream("https://x/master.m3u8", "application/vnd.apple.mpegurl", "T"))
        val useCase = CastUrlUseCase(fake, HlsProbe { it == "https://x/master.m3u8" })
        val r = useCase.prepare("https://site/video") as CastUrlUseCase.Result.Ready
        assertTrue(r.hlsFmp4)
    }

    @Test
    fun nonHlsIsNotProbed() = runBlocking {
        // Probe returning true must be ignored for a non-HLS (mp4) stream.
        val useCase = CastUrlUseCase(FakeResolver(), HlsProbe { true })
        val r = useCase.prepare("https://x/clip.mp4") as CastUrlUseCase.Result.Ready
        assertFalse(r.hlsFmp4)
    }

    @Test
    fun resolverFailureBecomesFailedResult() = runBlocking {
        val useCase = CastUrlUseCase(FakeResolver(error = "Can't cast this link (DRM)"), noFmp4)
        val r = useCase.prepare("https://netflix.com/title/1")
        assertTrue(r is CastUrlUseCase.Result.Failed)
        assertEquals("Can't cast this link (DRM)", (r as CastUrlUseCase.Result.Failed).message)
    }

    @Test
    fun youtubeUrlResolvesToDirectStreamWithStartTime() = runBlocking {
        val fake = FakeResolver(result = ResolvedStream("https://gv/itag18.mp4", "video/mp4", "Rick Astley"))
        val useCase = CastUrlUseCase(fake, noFmp4)
        assertTrue(useCase.needsResolving("https://youtu.be/LvrQ-NltbXA"))
        val r = useCase.prepare("https://youtu.be/LvrQ-NltbXA?t=42") as CastUrlUseCase.Result.Ready
        assertEquals("https://gv/itag18.mp4", r.streamUrl)
        assertEquals("Rick Astley", r.title)
        assertEquals(42, r.startSeconds)
        assertEquals("https://youtu.be/LvrQ-NltbXA?t=42", fake.calledWith)
    }

    @Test
    fun invalidYoutubeUrlFails() = runBlocking {
        val r = CastUrlUseCase(FakeResolver(), noFmp4).prepare("https://youtube.com/feed/subscriptions")
        assertTrue(r is CastUrlUseCase.Result.Failed)
    }
}
