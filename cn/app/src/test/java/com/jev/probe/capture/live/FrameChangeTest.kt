package com.jev.probe.capture.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameChangeTest {

    private val w = 32
    private val h = 64
    private val n = w * h

    private fun fill(v: Int) = IntArray(n) { v }

    @Test fun sameFrameIsNotChanged() {
        val pixels = fill(40)
        val a = FrameChange.signature(pixels, w, h)
        val b = FrameChange.signature(pixels, w, h)
        assertFalse(FrameChange.changed(a, b))
    }

    @Test fun blackAndWhiteAreChanged() {
        val a = FrameChange.signature(fill(0), w, h)
        val b = FrameChange.signature(fill(255), w, h)
        assertTrue(FrameChange.changed(a, b))
    }

    @Test fun fewPixelsAreNotChanged() {
        val p1 = fill(40)
        val p2 = p1.copyOf()
        p2[0] = 255
        p2[1] = 255
        p2[2] = 255
        p2[10] = 200
        p2[20] = 0
        val a = FrameChange.signature(p1, w, h)
        val b = FrameChange.signature(p2, w, h)
        assertFalse(FrameChange.changed(a, b))
    }

    @Test fun firstFrameIsChanged() {
        val cur = FrameChange.signature(fill(40), w, h)
        assertTrue(FrameChange.changed(null, cur))
    }

    @Test fun signatureLengthIsFixed() {
        val a = FrameChange.signature(fill(0), w, h)
        val b = FrameChange.signature(fill(255), w, h)
        val c = FrameChange.signature(IntArray(n) { it % 256 }, w, h)
        val expected = (n + 63) / 64
        assertEquals(expected, a.size)
        assertEquals(a.size, b.size)
        assertEquals(b.size, c.size)
        assertTrue(a.isNotEmpty())
    }
}
