package se.constructions.castmote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SkipFormatTest {
    @Test fun formatsSecondsMinutesAndBoth() {
        assertEquals("30s", formatSkip(30))
        assertEquals("1m", formatSkip(60))
        assertEquals("1m30s", formatSkip(90))
        assertEquals("10m", formatSkip(600))
        assertEquals("20m", formatSkip(1200))
    }
}
