package com.uniplanner.app.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class InkCodecTest {
    @Test
    fun roundTrip() {
        val s = InkStroke(InkStroke.MARKER, 0x80FFEB3B.toInt(), 12.5f, floatArrayOf(10f, 20.25f, 11.5f, 19f, 300f, 0f))
        val back = InkCodec.decode(InkCodec.encode(listOf(s, InkStroke(0, -16777216, 2f, floatArrayOf(1f, 1f)))))
        assertEquals(2, back.size)
        assertEquals(s.tool, back[0].tool)
        assertEquals(s.color, back[0].color)
        assertEquals(12.5f, back[0].width, 0.01f)
        assertEquals(s.xy.toList(), back[0].xy.toList())
        assertEquals(-16777216, back[1].color)
        assertEquals(emptyList<InkStroke>(), InkCodec.decode(""))
    }
}
