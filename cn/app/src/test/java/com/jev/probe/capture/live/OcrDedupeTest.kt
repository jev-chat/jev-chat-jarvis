package com.jev.probe.capture.live

import com.jev.probe.core.Msg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDedupeTest {

    private fun o(t: String) = Msg("other", t)
    private fun m(t: String) = Msg("me", t)

    @Test fun oneOrTwoCharJitterAlignsToPreviousText() {
        val prev = listOf(o("今晚一起吃饭吗我们七点见"))
        val curr = listOf(o("今晚一起吃饭嘛我们七点见")) // 吗 → 嘛
        val aligned = OcrDedupe.align(prev, curr)
        assertEquals(prev[0].text, aligned[0].text)
        assertEquals("other", aligned[0].side)
    }

    @Test fun punctuationAndSpaceJitterAligns() {
        val prev = listOf(m("好的，那就这样吧。"))
        val curr = listOf(m("好的 那就这样吧"))
        val aligned = OcrDedupe.align(prev, curr)
        assertEquals(prev[0].text, aligned[0].text)
    }

    @Test fun trulyDifferentKeepsCurrentText() {
        val prev = listOf(o("今晚一起吃饭吗我们七点见"))
        val curr = listOf(o("明天改去爬山吧带上水壶"))
        val aligned = OcrDedupe.align(prev, curr)
        assertEquals(curr[0].text, aligned[0].text)
    }

    @Test fun emptyLists() {
        assertTrue(OcrDedupe.align(emptyList(), emptyList()).isEmpty())
        assertTrue(OcrDedupe.align(listOf(o("x")), emptyList()).isEmpty())
        val curr = listOf(o("第一句比较长所以不会被误伤"))
        assertEquals(curr, OcrDedupe.align(emptyList(), curr))
    }

    @Test fun sameSideMatchIsOneToOne() {
        val prev = listOf(o("第一条消息内容足够长啊"), o("第二条消息内容足够长啊"))
        val curr = listOf(o("第一条消息内容足够长啊"), o("第二条消息内容足够长啊"))
        val aligned = OcrDedupe.align(prev, curr)
        assertEquals(prev.map { it.text }, aligned.map { it.text })
    }
}
