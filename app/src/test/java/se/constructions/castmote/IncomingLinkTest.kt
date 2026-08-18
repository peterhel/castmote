package se.constructions.castmote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingLinkTest {
    private fun view(data: String?) = IncomingLink.urlFrom("android.intent.action.VIEW", data, null)
    private fun send(text: String?) = IncomingLink.urlFrom("android.intent.action.SEND", null, text)

    @Test fun schemeWrapsFullUrl() =
        assertEquals("https://youtu.be/x", view("castmote://https://youtu.be/x"))

    @Test fun schemeWithoutInnerSchemeAssumesHttps() =
        assertEquals("https://youtube.com/watch?v=x", view("castmote://youtube.com/watch?v=x"))

    @Test fun plainHttpsView() =
        assertEquals("https://www.svtplay.se/video/abc", view("https://www.svtplay.se/video/abc"))

    @Test fun shareUnwrapsSurroundingText() =
        assertEquals("https://youtu.be/x", send("Check this out: https://youtu.be/x"))

    @Test fun blankIsNull() {
        assertNull(view("castmote://"))
        assertNull(send("   "))
        assertNull(IncomingLink.urlFrom("android.intent.action.MAIN", null, null))
    }
}
