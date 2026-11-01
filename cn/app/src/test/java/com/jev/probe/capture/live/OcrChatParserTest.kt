package com.jev.probe.capture.live

import com.jev.probe.capture.live.OcrChatParser.Box
import com.jev.probe.capture.live.OcrChatParser.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrChatParserTest {

    /** Easy percentages: top 12% = y<240, bottom 16% starts at y=1680. */
    private val W = 1000
    private val H = 2000

    private fun box(l: Int, t: Int, r: Int, b: Int) = Box(l, t, r, b)
    private fun line(text: String, l: Int, t: Int, r: Int, b: Int) = Line(text, box(l, t, r, b))
    private fun parse(vararg lines: Line, exclude: List<Box> = emptyList()) =
        OcrChatParser.parse(lines.toList(), W, H, exclude)

    private fun other(text: String, top: Int, height: Int = 40) =
        line(text, 40, top, 380, top + height)

    private fun me(text: String, top: Int, height: Int = 40) =
        line(text, 620, top, 960, top + height)

    @Test fun leftIsOtherRightIsMe() {
        val p = parse(
            other("你好", 400),
            me("在的", 500),
        )
        assertEquals(listOf("other", "me"), p.messages.map { it.side })
        assertEquals(listOf("你好", "在的"), p.messages.map { it.text })
        assertTrue(p.isChat)
    }

    @Test fun middleBandUsesLeftEdge() {
        // centerX = 500, in the 0.45..0.55 band; left=50 < 0.2*W → other
        // width 900 ≥ 60% so this is not a centered system hint.
        val leftish = line("靠左的中间", 50, 400, 950, 440)
        // centerX = 500; left=200 is not < 0.2*W → me. width 600 is not < 60%.
        val rightish = line("偏右的中间", 200, 520, 800, 560)
        val p = parse(leftish, rightish)
        assertEquals(2, p.messages.size)
        assertEquals("other", p.messages[0].side)
        assertEquals("me", p.messages[1].side)
    }

    @Test fun closeSameSideLinesMergeWithoutSpaceForCjk() {
        val p = parse(
            other("你好", 400, 40),
            other("今晚出来", 448, 40), // gap=8 < 0.8*40=32; same left → not a nickname
        )
        assertEquals(1, p.messages.size)
        assertEquals("other", p.messages[0].side)
        assertEquals("你好今晚出来", p.messages[0].text)
    }

    @Test fun englishLinesMergeWithSpace() {
        val p = parse(
            me("hello", 400, 40),
            me("world", 448, 40),
            other("ok then", 600, 40),
            other("see you", 648, 40),
        )
        assertEquals(listOf("hello world", "ok then see you"), p.messages.map { it.text })
    }

    @Test fun farSameSideLinesStayTwoBubbles() {
        val p = parse(
            other("第一条", 400, 40),
            other("第二条", 500, 40), // gap=60 > 0.8*40=32
        )
        assertEquals(2, p.messages.size)
        assertEquals(listOf("第一条", "第二条"), p.messages.map { it.text })
        assertTrue(p.isChat) // two other, no me
    }

    @Test fun timestampsAreDropped() {
        val p = parse(
            other("12:34", 400),
            other("昨天 12:34", 460),
            other("星期三 09:10", 520),
            other("2026年10月1日", 580),
            other("真消息", 700),
            me("回你", 800),
        )
        assertEquals(listOf("真消息", "回你"), p.messages.map { it.text })
    }

    @Test fun centeredSystemHintsAreDropped() {
        val system = line("你撤回了一条消息", 300, 600, 700, 640) // width 400 < 600, center 500
        val also = line("以下是新消息", 320, 700, 680, 740)
        val p = parse(
            other("还在吗", 400),
            system,
            also,
            me("在", 850),
        )
        assertEquals(listOf("还在吗", "在"), p.messages.map { it.text })
    }

    @Test fun titleTakenFromTopBandAndGroupCountStripped() {
        val clock = line("14:32", 40, 20, 140, 60) // timestamp, ignored as title
        val title = line("工作群 (12)", 350, 90, 650, 140)
        val p = parse(
            clock,
            title,
            other("大家好", 400),
            me("好", 500),
        )
        assertEquals("工作群", p.title)
        assertFalse(p.messages.any { it.text.contains("工作群") })
        assertFalse(p.messages.any { it.text.contains("14:32") })
    }

    @Test fun fullwidthGroupCountIsStripped() {
        val title = line("家庭群（99+）", 360, 80, 640, 130)
        val p = parse(title, other("一", 400), other("二", 500))
        assertEquals("家庭群", p.title)
    }

    @Test fun bottomInputAreaIsDropped() {
        val input = line("说点什么…", 80, 1850, 700, 1910)
        val send = line("发送", 820, 1860, 960, 1910)
        val p = parse(
            other("上面的话", 400),
            me("回一句", 500),
            input,
            send,
        )
        assertEquals(listOf("上面的话", "回一句"), p.messages.map { it.text })
    }

    @Test fun gateBothSidesIsChat() {
        val p = parse(other("a", 400), me("b", 500))
        assertTrue(p.isChat)
    }

    @Test fun gateTwoOtherWithoutMeIsChat() {
        val p = parse(other("连发一", 400), other("连发二", 500))
        assertTrue(p.isChat)
        assertEquals(2, p.messages.size)
    }

    @Test fun gateSingleOtherIsNotChat() {
        val p = parse(other("就这一句", 400))
        assertFalse(p.isChat)
        assertEquals(1, p.messages.size)
    }

    @Test fun gateOnlyMeIsNotChat() {
        val p = parse(me("我发了两条", 400), me("还没人回", 500))
        assertFalse(p.isChat)
    }

    @Test fun excludeRectsDropCoveredLines() {
        val overlay = box(20, 380, 400, 460)
        val p = parse(
            other("被挡住的", 400),
            me("看得见", 520),
            other("也看得见", 620),
            exclude = listOf(overlay),
        )
        assertEquals(listOf("看得见", "也看得见"), p.messages.map { it.text })
        assertTrue(p.isChat)
    }

    @Test fun groupNicknameMergesIntoNextSameSide() {
        val nick = line("小明", 40, 400, 140, 430)
        val body = line("今晚吃饭吗", 80, 440, 420, 490)
        val p = parse(nick, body, me("好啊", 560))
        assertEquals("other", p.messages[0].side)
        assertEquals("小明：今晚吃饭吗", p.messages[0].text)
        assertEquals("好啊", p.messages[1].text)
        assertTrue(p.isChat)
    }
}
