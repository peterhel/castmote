package se.constructions.castmote.youtube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YtLoungeTest {

    private val tokenJson =
        """{"screens":[{"screenId":"SCREEN123","loungeToken":"TOKEN_XYZ","refreshIntervalMs":1123200000}]}"""
    private val bindBody =
        "1443\n[[0,[\"c\",\"318EFD6EE8520B83\",\"\",8]]\n,[1,[\"S\",\"148BvwyoARSNLfifT_wABTcZ0Be3vjNw\"]]\n,[2,[\"loungeStatus\",{}]]]"

    private class FakeHttp(val responses: Map<String, HttpResponse>) : HttpPost {
        val calls = mutableListOf<Triple<String, Map<String, String>, String>>()
        override suspend fun post(url: String, headers: Map<String, String>, formBody: String): HttpResponse {
            calls.add(Triple(url, headers, formBody))
            return responses.entries.first { url.startsWith(it.key) }.value
        }
    }

    @Test fun parsesLoungeToken() {
        assertEquals("TOKEN_XYZ", YtLounge.parseLoungeToken(tokenJson))
    }

    @Test fun parsesBindSession() {
        val s = YtLounge.parseBindSession(bindBody)
        assertEquals("318EFD6EE8520B83", s.sid)
        assertEquals("148BvwyoARSNLfifT_wABTcZ0Be3vjNw", s.gsessionid)
    }

    @Test fun playRunsTheThreeStepsWithRightShapes() = runBlocking {
        val http = FakeHttp(mapOf(
            "https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch" to HttpResponse(200, tokenJson),
            "https://www.youtube.com/api/lounge/bc/bind" to HttpResponse(200, bindBody),
        ))
        YtLounge(http).play("SCREEN123", "9bZkp7q19f0")
        assertTrue(http.calls[0].third.contains("screen_ids=SCREEN123"))
        assertEquals("TOKEN_XYZ", http.calls[1].second["X-YouTube-LoungeId-Token"])
        assertTrue(http.calls[2].first.contains("SID=318EFD6EE8520B83"))
        assertTrue(http.calls[2].first.contains("gsessionid=148BvwyoARSNLfifT_wABTcZ0Be3vjNw"))
        assertTrue(http.calls[2].third.contains("req0_videoId=9bZkp7q19f0"))
        assertTrue(http.calls[2].third.contains("setPlaylist"))
    }

    @Test fun setPlaylistCarriesStartSeconds() = runBlocking {
        val http = FakeHttp(mapOf(
            "https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch" to HttpResponse(200, tokenJson),
            "https://www.youtube.com/api/lounge/bc/bind" to HttpResponse(200, bindBody),
        ))
        YtLounge(http).play("SCREEN123", "vid", 5760)
        assertTrue(http.calls[2].third.contains("req0_currentTime=5760"))
    }

    @Test fun authHeadersAreSentOnEveryLoungeCallWhenProvided() = runBlocking {
        val http = FakeHttp(mapOf(
            "https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch" to HttpResponse(200, tokenJson),
            "https://www.youtube.com/api/lounge/bc/bind" to HttpResponse(200, bindBody),
        ))
        val auth = mapOf("Cookie" to "SAPISID=x", "Authorization" to "SAPISIDHASH 1_a", "X-Origin" to "https://www.youtube.com")
        YtLounge(http).play("SCREEN123", "vid", 0, auth)
        http.calls.forEach { (_, headers, _) ->
            assertEquals("SAPISID=x", headers["Cookie"])
            assertEquals("SAPISIDHASH 1_a", headers["Authorization"])
        }
    }

    @Test fun noAuthHeadersWhenNull() = runBlocking {
        val http = FakeHttp(mapOf(
            "https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch" to HttpResponse(200, tokenJson),
            "https://www.youtube.com/api/lounge/bc/bind" to HttpResponse(200, bindBody),
        ))
        YtLounge(http).play("SCREEN123", "vid")
        http.calls.forEach { (_, headers, _) -> assertEquals(null, headers["Cookie"]) }
    }

    @Test fun httpErrorThrowsYouTubeException() {
        val http = FakeHttp(mapOf(
            "https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch" to HttpResponse(500, ""),
        ))
        try {
            runBlocking { YtLounge(http).play("SCREEN123", "vid") }
            fail("expected YouTubeException")
        } catch (e: YouTubeException) {
            assertTrue(e.message.isNotBlank())
        }
    }
}
