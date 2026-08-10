package se.constructions.castmote.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAuthTest {

    private class MemStore(var value: String? = null) : CookieStore {
        override fun get() = value
        override fun set(v: String?) { value = v }
    }

    @Test fun parsesSapisidIncludingSlash() {
        val ck = "VISITOR_INFO1_LIVE=abc; SAPISID=TESTSAPISID123/AbCdEf; HSID=x"
        assertEquals("TESTSAPISID123/AbCdEf", YouTubeAuth.parseSapisid(ck))
        assertNull(YouTubeAuth.parseSapisid("VISITOR_INFO1_LIVE=abc; HSID=x"))
    }

    @Test fun sapisidHashMatchesKnownVector() {
        assertEquals(
            "0cdad21f75ef78393921a20a0e1b34e4cb6a5e24",
            YouTubeAuth.sapisidHash("TESTSAPISID123", "https://www.youtube.com", 1700000000L),
        )
    }

    @Test fun authHeadersNullWhenSignedOutOrNoSapisid() {
        assertNull(YouTubeAuth(MemStore(null)).authHeaders())
        assertNull(YouTubeAuth(MemStore("VISITOR_INFO1_LIVE=abc")).authHeaders())
    }

    @Test fun authHeadersBuildsCookieAuthorizationAndOrigin() {
        val store = MemStore("SAPISID=TESTSAPISID123; HSID=x")
        val auth = YouTubeAuth(store, clock = { 1700000000L })
        assertTrue(auth.isSignedIn())
        val h = auth.authHeaders()!!
        assertEquals("SAPISID=TESTSAPISID123; HSID=x", h["Cookie"])
        assertEquals("SAPISIDHASH 1700000000_0cdad21f75ef78393921a20a0e1b34e4cb6a5e24", h["Authorization"])
        assertEquals("https://www.youtube.com", h["X-Origin"])
    }

    @Test fun signedInViaSecureVariantWithoutBareSapisid() {
        // WebView captures often lack a bare SAPISID, carrying only the __Secure-*PAPISID forms.
        val store = MemStore("VISITOR_INFO1_LIVE=abc; __Secure-3PAPISID=TESTSAPISID123; __Secure-1PAPISID=TESTSAPISID123")
        val auth = YouTubeAuth(store, clock = { 1700000000L })
        assertTrue(auth.isSignedIn())
        assertEquals(
            "SAPISID1PHASH 1700000000_0cdad21f75ef78393921a20a0e1b34e4cb6a5e24 " +
                "SAPISID3PHASH 1700000000_0cdad21f75ef78393921a20a0e1b34e4cb6a5e24",
            auth.authHeaders()!!["Authorization"],
        )
    }

    @Test fun authorizationSendsAllThreeHashesWhenPresent() {
        val ck = "SAPISID=TESTSAPISID123; __Secure-1PAPISID=TESTSAPISID123; __Secure-3PAPISID=TESTSAPISID123"
        assertEquals(
            "SAPISIDHASH 1700000000_0cdad21f75ef78393921a20a0e1b34e4cb6a5e24 " +
                "SAPISID1PHASH 1700000000_0cdad21f75ef78393921a20a0e1b34e4cb6a5e24 " +
                "SAPISID3PHASH 1700000000_0cdad21f75ef78393921a20a0e1b34e4cb6a5e24",
            YouTubeAuth.buildAuthorization(ck, "https://www.youtube.com", 1700000000L),
        )
    }

    @Test fun debugCookieKeysListsNamesNotValues() {
        assertEquals("SAPISID,HSID", YouTubeAuth(MemStore("SAPISID=secret; HSID=alsosecret")).debugCookieKeys())
    }

    @Test fun saveAndSignOut() {
        val store = MemStore()
        val auth = YouTubeAuth(store)
        auth.save("SAPISID=abc")
        assertTrue(auth.isSignedIn())
        auth.signOut()
        assertNull(store.value)
    }
}
