package com.jev.probe.capture.live

import com.jev.probe.capture.OcrLayout
import com.jev.probe.core.Msg
import kotlin.math.abs

/**
 * Turns a flat OCR line list into a chat snapshot. Pure Kotlin — no Android
 * types — so the rules are unit-tested on the JVM with handwritten boxes.
 *
 * Coordinates are in the same space as [imageWidth] × [imageHeight] (bitmap
 * pixels for a live frame). [exclude] is our overlay, also in that space.
 */
object OcrChatParser {

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        fun intersects(o: Box): Boolean =
            left < o.right && right > o.left && top < o.bottom && bottom > o.top
    }

    data class Line(val text: String, val box: Box)

    data class ParsedChat(
        val title: String?,
        val messages: List<Msg>,
        val isChat: Boolean,
    )

    fun parse(
        lines: List<Line>,
        imageWidth: Int,
        imageHeight: Int,
        exclude: List<Box> = emptyList(),
    ): ParsedChat {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return ParsedChat(null, emptyList(), false)
        }
        val topCut = imageHeight * OcrLayout.TOP_CROP
        val bottomCut = imageHeight * OcrLayout.BOTTOM_CROP

        val titleCandidates = ArrayList<Line>()
        val usable = ArrayList<Line>()
        for (raw in lines) {
            val text = raw.text.trim()
            if (text.isEmpty()) continue
            val line = if (text == raw.text) raw else Line(text, raw.box)
            if (exclude.any { it.intersects(line.box) }) continue
            val cy = line.box.centerY.toFloat()
            if (cy >= bottomCut) continue
            if (cy < topCut) {
                if (!isTimestamp(text)) titleCandidates.add(line)
                continue
            }
            if (isTimestamp(text)) continue
            if (isCenteredSystem(line, imageWidth)) continue
            usable.add(line)
        }

        val title = pickTitle(titleCandidates, imageWidth)
        usable.sortBy { it.box.top }
        val merged = mergeBubbles(usable, imageWidth)
        val withNicks = mergeNicknames(merged)
        val messages = withNicks.map { Msg(it.side, it.text) }
        return ParsedChat(title, messages, isChat(messages))
    }

    // ---------------------------------------------------------------- title

    private fun pickTitle(candidates: List<Line>, imageWidth: Int): String? {
        if (candidates.isEmpty()) return null
        val mid = imageWidth / 2
        val best = candidates.minWith(
            compareBy<Line> { it.box.top }.thenBy { abs(it.box.centerX - mid) },
        )
        return stripGroupCount(best.text).ifBlank { null }
    }

    internal fun stripGroupCount(raw: String): String =
        raw.trim().replace(GROUP_COUNT, "").trim()

    // ---------------------------------------------------------------- filters

    internal fun isTimestamp(text: String): Boolean = TIMESTAMP.matches(text.trim())

    private fun isCenteredSystem(line: Line, imageWidth: Int): Boolean {
        val mid = imageWidth / 2f
        val band = imageWidth * OcrLayout.SYSTEM_CENTER_BAND
        if (abs(line.box.centerX - mid) >= band) return false
        return line.box.width < imageWidth * OcrLayout.SYSTEM_MAX_WIDTH
    }

    // ---------------------------------------------------------------- side + merge

    internal fun assignSide(box: Box, imageWidth: Int): String {
        val cx = box.centerX.toFloat() / imageWidth
        return when {
            cx < OcrLayout.OTHER_MAX_CENTER -> "other"
            cx > OcrLayout.ME_MIN_CENTER -> "me"
            else -> if (box.left.toFloat() / imageWidth < OcrLayout.OTHER_LEFT_FALLBACK) "other" else "me"
        }
    }

    private data class Draft(
        val side: String,
        var text: String,
        var box: Box,
        var lineHeight: Int,
    )

    private fun mergeBubbles(lines: List<Line>, imageWidth: Int): MutableList<Draft> {
        val out = ArrayList<Draft>()
        for (line in lines) {
            val side = assignSide(line.box, imageWidth)
            val last = out.lastOrNull()
            val gap = if (last != null) line.box.top - last.box.bottom else Int.MAX_VALUE
            val close = last != null && last.side == side &&
                gap < last.lineHeight * OcrLayout.MERGE_GAP
            if (close && last != null) {
                last.text = if (isNicknameLine(last, line, side)) {
                    "${last.text}：${line.text}"
                } else {
                    joinText(last.text, line.text)
                }
                last.box = union(last.box, line.box)
                last.lineHeight = maxOf(1, line.box.height)
            } else {
                out.add(Draft(side, line.text, line.box, maxOf(1, line.box.height)))
            }
        }
        return out
    }

    /**
     * Nicknames that sat just far enough from the bubble to miss the 0.8-gap
     * merge still get folded: short line, same side, immediately above, closer
     * to the avatar than the body.
     */
    private fun mergeNicknames(drafts: MutableList<Draft>): List<Draft> {
        if (drafts.size < 2) return drafts
        val out = ArrayList<Draft>(drafts.size)
        var i = 0
        while (i < drafts.size) {
            val cur = drafts[i]
            val next = drafts.getOrNull(i + 1)
            if (next != null && isLooseNickname(cur, next)) {
                next.text = "${cur.text}：${next.text}"
                next.box = union(cur.box, next.box)
                out.add(next)
                i += 2
            } else {
                out.add(cur)
                i += 1
            }
        }
        return out
    }

    private fun isNicknameLine(prev: Draft, body: Line, side: String): Boolean {
        if (prev.text.length > OcrLayout.NICK_MAX_LEN) return false
        if (prev.text.contains('：') || prev.text.contains(':')) return false
        return moreOutward(prev.box, body.box, side)
    }

    private fun isLooseNickname(prev: Draft, next: Draft): Boolean {
        if (prev.side != next.side) return false
        if (prev.text.length > OcrLayout.NICK_MAX_LEN) return false
        if (prev.text.contains('：') || prev.text.contains(':')) return false
        val gap = next.box.top - prev.box.bottom
        if (gap < 0 || gap >= prev.lineHeight * 2f) return false
        return moreOutward(prev.box, next.box, prev.side)
    }

    private fun moreOutward(nick: Box, body: Box, side: String): Boolean =
        if (side == "other") nick.left < body.left else nick.right > body.right

    private fun union(a: Box, b: Box) = Box(
        minOf(a.left, b.left),
        minOf(a.top, b.top),
        maxOf(a.right, b.right),
        maxOf(a.bottom, b.bottom),
    )

    internal fun joinText(a: String, b: String): String {
        val left = a.trimEnd()
        val right = b.trimStart()
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left
        val cjk = left.last().isCjk() && right.first().isCjk()
        return if (cjk) left + right else "$left $right"
    }

    private fun Char.isCjk(): Boolean {
        val u = this.code
        return u in 0x2E80..0x9FFF || u in 0xF900..0xFAFF || u in 0xFF00..0xFFEF
    }

    // ---------------------------------------------------------------- gate

    internal fun isChat(messages: List<Msg>): Boolean {
        if (messages.isEmpty()) return false
        var other = 0
        var me = 0
        for (m in messages) {
            when (m.side) {
                "other" -> other++
                "me" -> me++
            }
        }
        if (other > 0 && me > 0) return true
        if (me == 0 && other >= 2) return true
        return false
    }

    // ---------------------------------------------------------------- patterns

    private val GROUP_COUNT = Regex("""\s*[(\uFF08]\d+\+?[)\uFF09]\s*$""")

    /**
     * Whole-line timestamps seen in QQ / 飞书 / WeChat-style headers.
     * Must match the entire trimmed line so a message mentioning a time stays.
     */
    private val TIMESTAMP = Regex(
        """^(?:""" +
            """(?:\d{4}年)?\d{1,2}月\d{1,2}日(?:\s*\d{1,2}[:：]\d{2})?""" +
            """|""" +
            """(?:星期|周)[一二三四五六日天](?:\s*\d{1,2}[:：]\d{2})?""" +
            """|""" +
            """(?:昨天|今天|前天|刚刚)(?:\s*\d{1,2}[:：]\d{2})?""" +
            """|""" +
            """(?:上午|下午|早上|晚上)\s*\d{1,2}[:：]\d{2}""" +
            """|""" +
            """\d{1,2}[:：]\d{2}""" +
            """)$""",
    )
}
