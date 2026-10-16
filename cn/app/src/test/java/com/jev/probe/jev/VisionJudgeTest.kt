package com.jev.probe.jev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vision-judge sizing + request extras. Network is not involved: [JudgeRequest]
 * is the extracted body/headers builder that [JudgeClient] splices into the POST.
 */
class VisionJudgeTest {

    @Test fun targetSizeScalesPortraitLongSideTo1568() {
        val s = VisionImage.targetSize(1080, 2400)
        assertNotNull(s)
        assertEquals(706, s!!.first)
        assertEquals(1568, s.second)
        assertEquals(1080 / 2400.0, s.first / s.second.toDouble(), 0.002)
    }

    @Test fun targetSizeLeaves800x1200Unchanged() {
        assertEquals(800 to 1200, VisionImage.targetSize(800, 1200))
    }

    @Test fun targetSizeRejectsTiny() {
        assertNull(VisionImage.targetSize(100, 100))
    }

    @Test fun targetSizeScalesLandscapeLongSideTo1568() {
        val s = VisionImage.targetSize(2400, 1080)
        assertNotNull(s)
        assertEquals(1568, s!!.first)
        assertEquals(706, s.second)
    }

    @Test fun bodyWithoutImageHasNoAssetsOrScreenshot() {
        val built = JudgeRequest.build(emptyMap(), jpeg = null)
        assertFalse(built.hasImage)
        assertNull(built.asset)
        assertNull(built.screenshotRef)
        assertNull(built.screenshotNote)
        assertFalse(built.headers.containsKey(JudgeRequest.HEADER_IMAGES))
    }

    @Test fun bodyWithoutImageKeepsCallerHeadersUntouched() {
        val base = mapOf("HTTP-Referer" to "https://jev-assistant.local", "X-Title" to "Jev Assistant")
        val built = JudgeRequest.build(base, jpeg = null)
        assertEquals(base, built.headers)
    }

    @Test fun bodyWithImageHasAssetsRefAndJpgBase64() {
        val jpeg = byteArrayOf(0x00, 0x01)
        val ref = "jev-shot-deadbeef"
        val built = JudgeRequest.build(emptyMap(), jpeg, ref)
        assertTrue(built.hasImage)
        assertEquals(ref, built.screenshotRef)
        assertEquals(JudgeRequest.SCREENSHOT_NOTE, built.screenshotNote)
        val asset = built.asset!!
        assertEquals(ref, asset.ref)
        assertEquals("image", asset.type)
        assertEquals("jpg", asset.format)
        val b64 = asset.dataBase64
        assertEquals(JudgeRequest.encodeBase64(jpeg), b64)
        assertTrue(b64.endsWith("="))
        assertFalse(b64.contains("\n"))
        assertFalse(b64.contains("\r"))
        assertFalse(b64.startsWith("data:"))
    }

    @Test fun headersIncludeDangerZoneOnlyWithImage() {
        val base = mapOf("HTTP-Referer" to "https://jev-assistant.local")
        val with = JudgeRequest.headers(base, true)
        assertEquals("true", with[JudgeRequest.HEADER_IMAGES])
        assertEquals("https://jev-assistant.local", with["HTTP-Referer"])
        val without = JudgeRequest.headers(base, false)
        assertFalse(without.containsKey(JudgeRequest.HEADER_IMAGES))
        assertEquals(base, without)
        assertFalse(JudgeRequest.build(emptyMap(), jpeg = null).headers.containsKey(JudgeRequest.HEADER_IMAGES))
        assertEquals("true", JudgeRequest.build(emptyMap(), byteArrayOf(1), "jev-shot-x").headers[JudgeRequest.HEADER_IMAGES])
    }

    @Test fun refNameUsesFixedPrefix() {
        val name = JudgeRequest.newRefName()
        assertTrue(name.startsWith(JudgeRequest.REF_PREFIX))
        assertEquals(JudgeRequest.REF_PREFIX.length + 8, name.length)
    }

    @Test fun snapshotSignatureIgnoresImageBytes() {
        val a = com.jev.probe.core.ChatSnapshot("t", listOf(com.jev.probe.core.Msg("other", "hi")))
        val b = com.jev.probe.core.ChatSnapshot(
            "t", listOf(com.jev.probe.core.Msg("other", "hi")), image = byteArrayOf(1, 2, 3))
        assertEquals(a.signature(), b.signature())
        assertEquals("other:hi", a.signature())
    }
}
