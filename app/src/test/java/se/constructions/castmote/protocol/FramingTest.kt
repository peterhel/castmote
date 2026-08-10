package se.constructions.castmote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class FramingTest {
    @Test
    fun encodePrependsFourByteBigEndianLength() {
        val body = byteArrayOf(1, 2, 3)
        val framed = Framing.encode(body)
        assertArrayEquals(byteArrayOf(0, 0, 0, 3, 1, 2, 3), framed)
    }

    @Test
    fun readFrameReadsOneFrame() {
        val framed = Framing.encode(byteArrayOf(9, 8, 7))
        val frame = Framing.readFrame(ByteArrayInputStream(framed))
        assertArrayEquals(byteArrayOf(9, 8, 7), frame)
    }

    @Test
    fun readFrameReassemblesTwoConcatenatedFrames() {
        val a = Framing.encode(byteArrayOf(1))
        val b = Framing.encode(byteArrayOf(2, 2))
        val input = ByteArrayInputStream(a + b)
        assertArrayEquals(byteArrayOf(1), Framing.readFrame(input))
        assertArrayEquals(byteArrayOf(2, 2), Framing.readFrame(input))
    }

    @Test
    fun readFrameReturnsNullAtEndOfStream() {
        assertNull(Framing.readFrame(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun encodeLargeLengthIsBigEndian() {
        val framed = Framing.encode(ByteArray(258))
        assertEquals(0, framed[0].toInt())
        assertEquals(0, framed[1].toInt())
        assertEquals(1, framed[2].toInt())   // 256
        assertEquals(2, framed[3].toInt())   // + 2 = 258
    }
}
