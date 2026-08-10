package se.constructions.castmote.protocol

import java.io.InputStream

/** CASTV2 wire framing: a 4-byte big-endian length prefix followed by that many payload bytes. */
object Framing {

    fun encode(body: ByteArray): ByteArray {
        val len = body.size
        val out = ByteArray(4 + len)
        out[0] = (len ushr 24).toByte()
        out[1] = (len ushr 16).toByte()
        out[2] = (len ushr 8).toByte()
        out[3] = len.toByte()
        body.copyInto(out, destinationOffset = 4)
        return out
    }

    /** Reads one frame. Returns null at clean end-of-stream. Blocks until a full frame arrives. */
    fun readFrame(input: InputStream): ByteArray? {
        val header = readExactly(input, 4) ?: return null
        val len = ((header[0].toInt() and 0xff) shl 24) or
            ((header[1].toInt() and 0xff) shl 16) or
            ((header[2].toInt() and 0xff) shl 8) or
            (header[3].toInt() and 0xff)
        return readExactly(input, len) ?: throw java.io.EOFException("stream ended mid-frame")
    }

    /** Reads exactly [n] bytes (looping over partial reads). Null only if EOF before any byte. */
    private fun readExactly(input: InputStream, n: Int): ByteArray? {
        if (n == 0) return ByteArray(0)
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) return if (off == 0) null else throw java.io.EOFException("stream ended mid-frame")
            off += r
        }
        return buf
    }
}
