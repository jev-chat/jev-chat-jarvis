package com.jev.overseas.a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.jev.overseas.core.dump.NodeRecord
import kotlin.math.abs

/**
 * The tree is not read atomically: a parent is copied before its children.
 * After a walk, re-read the main list's rows and report how far they moved, so a
 * capture taken while the list was still scrolling can be told apart. The result
 * goes into the dump header as "stability", which [com.jev.overseas.core.whatsapp.WhatsAppAdapter] reads.
 */
object RowStability {

    fun measure(root: AccessibilityNodeInfo, raw: List<NodeRecord>, mainList: NodeRecord?): Map<String, Any?> {
        val listId = mainList?.viewId ?: return linkedMapOf("checked" to false)
        val live = runCatching { root.findAccessibilityNodeInfosByViewId(listId) }.getOrNull()
            ?.firstOrNull { it.isScrollable } ?: return linkedMapOf("checked" to false)
        if (!live.refresh()) return linkedMapOf("checked" to false)
        val recorded = raw.filter { it.parent == mainList.index }.sortedBy { it.childIndex }
        var compared = 0
        var moved = 0
        var maxDelta = 0
        val rect = Rect()
        for (row in recorded) {
            val child = runCatching { live.getChild(row.childIndex) }.getOrNull() ?: continue
            child.refresh()
            child.getBoundsInScreen(rect)
            compared++
            val delta = maxOf(abs(rect.top - row.bounds[1]), abs(rect.bottom - row.bounds[3]))
            if (delta > 0) moved++
            if (delta > maxDelta) maxDelta = delta
        }
        return linkedMapOf(
            "checked" to true,
            "rowCountAtStart" to recorded.size,
            "rowCountAtEnd" to live.childCount,
            "rowsCompared" to compared,
            "rowsMoved" to moved,
            "maxDeltaPx" to maxDelta,
        )
    }

    /** The largest scrollable node: the message list on a chat page. */
    fun mainList(raw: List<NodeRecord>): NodeRecord? = raw.filter { it.isScrollable }.maxByOrNull { area(it) }

    private fun area(n: NodeRecord): Long =
        (n.bounds[2] - n.bounds[0]).toLong().coerceAtLeast(0) * (n.bounds[3] - n.bounds[1]).toLong().coerceAtLeast(0)
}

/**
 * Waits until the message list stops moving: the row bounds are sampled every
 * [INTERVAL_MS] and two equal samples in a row count as settled. Gives up after
 * [MAX_MS] and returns false.
 */
object Settle {

    const val INTERVAL_MS = 100L
    const val MAX_MS = 1500L

    fun waitForList(root: AccessibilityNodeInfo, listViewId: String, sleep: (Long) -> Unit = { Thread.sleep(it) }): Boolean {
        var previous: List<Int>? = null
        var waited = 0L
        while (waited <= MAX_MS) {
            val sample = sample(root, listViewId)
            if (sample != null && sample == previous) return true
            previous = sample
            sleep(INTERVAL_MS)
            waited += INTERVAL_MS
        }
        return false
    }

    private fun sample(root: AccessibilityNodeInfo, listViewId: String): List<Int>? {
        val list = runCatching { root.findAccessibilityNodeInfosByViewId(listViewId) }.getOrNull()
            ?.firstOrNull { it.isScrollable } ?: return null
        list.refresh()
        val rect = Rect()
        val out = ArrayList<Int>(list.childCount * 2 + 1)
        out.add(list.childCount)
        for (i in 0 until list.childCount) {
            val child = runCatching { list.getChild(i) }.getOrNull() ?: continue
            child.getBoundsInScreen(rect)
            out.add(rect.top); out.add(rect.bottom)
        }
        return out
    }
}
