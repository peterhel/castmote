package se.constructions.castmote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekBarTest {
    @Test fun secondsUnderAMinute() = assertEquals("0:07", formatTime(7.0))
    @Test fun minutesAndSeconds() = assertEquals("5:09", formatTime(309.4))
    @Test fun hoursPadMinutesAndSeconds() = assertEquals("1:36:00", formatTime(5760.0))
    @Test fun negativeAndNaNClampToZero() {
        assertEquals("0:00", formatTime(-3.0))
        assertEquals("0:00", formatTime(Double.NaN))
    }
}
