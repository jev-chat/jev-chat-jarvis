package com.jev.probe.capture.live

import com.jev.probe.capture.OcrLayout
import com.jev.probe.core.Msg

/**
 * Smooths OCR jitter before [com.jev.probe.capture.NewMessageGate] sees the
 * list. Two bubbles that differ by a character or two of punctuation / a
 * near-identical glyph are treated as the previous round's text, so the gate
 * does not fire on every re-read of the same screen.
 *
 * Pure Kotlin, no Android types.
 */
object OcrDedupe {

    /**
     * For each current message, if a same-side previous message is similar
     * enough, reuse the previous text (so keys stay stable). Unmatched current
     * messages keep their OCR text. [previous] messages are matched at most once.
     */
    fun align(previous: List<Msg>, current: List<Msg>): List<Msg> {
        if (current.isEmpty()) return emptyList()
        if (previous.isEmpty()) return current
        val used = BooleanArray(previous.size)
        return current.map { cur ->
            var bestI = -1
            var bestSim = -1.0
            for (i in previous.indices) {
                if (used[i]) continue
                val prev = previous[i]
                if (prev.side != cur.side) continue
                val sim = similarity(prev.text, cur.text)
                if (sim >= OcrLayout.DEDUPE_SIMILARITY && sim > bestSim) {
                    bestSim = sim
                    bestI = i
                }
            }
            if (bestI >= 0) {
                used[bestI] = true
                Msg(cur.side, previous[bestI].text)
            } else {
                cur
            }
        }
    }

    internal fun similarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return 1.0
        val maxLen = maxOf(na.length, nb.length)
        if (maxLen == 0) return 1.0
        val dist = levenshtein(na, nb)
        return 1.0 - dist.toDouble() / maxLen
    }

    internal fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length)
        for (c in text) {
            if (c.isWhitespace()) continue
            if (c.isIgnorablePunct()) continue
            sb.append(c)
        }
        return sb.toString()
    }

    private fun Char.isIgnorablePunct(): Boolean {
        val t = Character.getType(this)
        return t == Character.CONNECTOR_PUNCTUATION.toInt() ||
            t == Character.DASH_PUNCTUATION.toInt() ||
            t == Character.START_PUNCTUATION.toInt() ||
            t == Character.END_PUNCTUATION.toInt() ||
            t == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            t == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            t == Character.OTHER_PUNCTUATION.toInt()
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..m) {
            cur[0] = i
            val ca = a[i - 1]
            for (j in 1..n) {
                val cost = if (ca == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[n]
    }
}
