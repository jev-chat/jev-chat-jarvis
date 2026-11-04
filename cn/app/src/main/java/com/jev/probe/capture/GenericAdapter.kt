package com.jev.probe.capture

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg
import kotlin.math.abs

/**
 * Generic geometry adapter — the fallback for every app without a dedicated one.
 *
 * It writes no per-app node rules. Every chat list in the wild is laid out the
 * same way: the sender's avatar sits in a fixed column, the bubble sits beside
 * it. Incoming rows put that pair on the LEFT, outgoing rows on the RIGHT. So
 * the speaker of a row is the side of *its own avatar*, and finding the avatar
 * is a matter of position, not of resource ids, which is why this works on apps
 * nobody wrote an adapter for and keeps working when an app is redesigned.
 *
 * Avatar beats bubble margins, and the difference is not academic. A long
 * message defeats any margin rule: an incoming bubble wide enough to reach the
 * right third and an outgoing one wide enough to reach the left third both end
 * up with a tiny margin on BOTH sides, so "smaller margin wins" has nothing to
 * separate them. The avatar does not care how wide the bubble is — it is in the
 * row, at a fixed column. [sideFromAvatar] is therefore asked first, and
 * [sideOf] (the margin comparison) only answers for rows where the app draws no
 * avatar at all (SMS, some ROM skins, grouped consecutive messages).
 *
 * "Are we in a chat window" is answered by three hard signals, so contact
 * lists, search results and settings never look like a conversation:
 *  1. an editable node sitting in the lower half of the screen (a chat compose
 *     box lives at the bottom; a search field lives at the top)
 *  2. at least 2 lines of text in the middle band
 *  3. at least 2 of those lines are clearly narrower than the screen (bubbles
 *     are not full-width rows — list items are)
 *
 * Contract, as every [ChatAppAdapter] promises:
 * - `null`              → not a chat window; the service does nothing.
 * - messages non-empty  → normal capture. This adapter never returns an empty
 *   snapshot, so it never triggers the screenshot + OCR fallback (it has no
 *   bubble rectangles to OCR anyway).
 */
class GenericAdapter : ChatAppAdapter {

    override val pkg = "*"

    private class Line(
        val text: String,
        val top: Int,
        val left: Int,
        val right: Int,
        val bottom: Int
    )

    /** A sender's avatar: horizontally where its owner sits, vertically which row it belongs to. */
    private class Avatar(val centerX: Int, val top: Int, val bottom: Int)

    override fun extract(root: AccessibilityNodeInfo, res: Resources): ChatSnapshot? {
        val dm = res.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val dens = dm.density

        if (!hasBottomInput(root, h, 0)) return null

        val raw = ArrayList<Line>()
        val avatars = ArrayList<Avatar>()
        walk(root, raw, avatars, 0, w)
        if (raw.size < 2) return null
        raw.sortBy { it.top }

        // Title bar at the top, input box and keyboard at the bottom.
        val topLimit = (h * 0.085).toInt()
        val botLimit = (h * 0.87).toInt()
        val keep = raw.filter {
            it.top >= topLimit && it.top <= botLimit && it.text.length <= 800
        }
        if (keep.size < 2) return null

        val narrow = keep.count { (it.right - it.left) < w * 0.80 }
        if (narrow < 2) return null

        val merged = ArrayList<Line>()
        for (l in keep) {
            val p = merged.lastOrNull()
            if (p != null &&
                abs(((p.top + p.bottom) / 2) - ((l.top + l.bottom) / 2)) < (14 * dens).toInt()
            ) {
                if (!p.text.contains(l.text)) {
                    merged[merged.size - 1] = Line(
                        p.text + l.text,
                        p.top,
                        minOf(p.left, l.left),
                        maxOf(p.right, l.right),
                        maxOf(p.bottom, l.bottom)
                    )
                }
                continue
            }
            merged.add(l)
        }

        // Date dividers and system notices carry no speaker and no reply value;
        // left in, they read to the model as if someone had typed them.
        val body = merged.filterNot { isStampOrNotice(it, w) }

        val tail = body.takeLast(24)
        if (tail.size < 2) return null

        // Only avatars inside the message band can belong to a row: the back
        // arrow and the compose-bar buttons ("+", emoji) also look like small
        // squares parked in the outer columns.
        val rowAvatars = avatars.filter {
            val cy = (it.top + it.bottom) / 2
            cy >= topLimit && cy <= botLimit
        }

        val msgs = tail.map { l ->
            Msg(sideFromAvatar(l, rowAvatars, w) ?: sideOf(l, w), l.text)
        }

        val title = findTitleInActionBar(root, tail.first().top, w, res)
        return ChatSnapshot(title = title, messages = msgs)
    }

    /**
     * Speaker taken from the avatar sharing this row — the row's own avatar,
     * found by vertical overlap, then read by which column it sits in.
     *
     * Null when the row has no avatar (or one that does not overlap it enough to
     * be the same row), and the caller falls back to [sideOf].
     */
    private fun sideFromAvatar(l: Line, avatars: List<Avatar>, w: Int): String? {
        var best: Avatar? = null
        var bestOverlap = 0
        for (a in avatars) {
            val overlap = minOf(l.bottom, a.bottom) - maxOf(l.top, a.top)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                best = a
            }
        }
        val a = best ?: return null
        // Half the avatar must lie inside the row: a taller bubble (three lines)
        // still contains all of it, a neighbouring row's avatar almost none.
        if (bestOverlap < (a.bottom - a.top) * 0.5f) return null
        return if (a.centerX < w * 0.5) "other" else "me"
    }

    /**
     * Fallback for rows without an avatar: which screen edge the bubble hugs.
     * The smaller margin wins; a genuine tie (only possible for a centred
     * element, which [isStampOrNotice] already removed) goes to the centre line.
     */
    private fun sideOf(l: Line, w: Int): String {
        val leftMargin = l.left
        val rightMargin = w - l.right
        return when {
            rightMargin < leftMargin -> "me"
            leftMargin < rightMargin -> "other"
            else -> if ((l.left + l.right) / 2 < w * 0.5) "other" else "me"
        }
    }

    /** Bare timestamps and centred system notices ("以下是新消息"). */
    private fun isStampOrNotice(l: Line, w: Int): Boolean {
        if (STAMP_RES.any { it.matches(l.text) }) return true
        // Centred, short, and inset on BOTH sides by more than a bubble ever is.
        val centred = l.left > w * 0.20 && l.right < w * 0.80 &&
            abs(l.left - (w - l.right)) < w * 0.12
        return centred && l.text.length <= 40
    }

    /**
     * An avatar: a square, text-less image of avatar size parked in one of the
     * two outer columns.
     *
     * The column test does the real work. Avatars sit at roughly 8% and 92% of
     * the width (40dp box, 12dp margin), while stickers, short voice bubbles and
     * thumbnails start at the bubble column and are pushed inwards — so a
     * centre past 22% / 78% excludes them without knowing anything about the app.
     */
    private fun isAvatarNode(node: AccessibilityNodeInfo, r: Rect, w: Int): Boolean {
        if (node.text != null) return false
        val nw = r.width()
        val nh = r.height()
        if (nw <= 0 || nh <= 0) return false
        val inColumn = r.centerX() < w * 0.22 || r.centerX() > w * 0.78
        if (!inColumn) return false
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && AVATAR_DESC.containsMatchIn(desc)) return true
        // No hint in the tree: judge by shape and size alone.
        if (nw < w * 0.06 || nw > w * 0.22) return false
        return abs(nw - nh) <= nw * 0.30
    }

    /** The compose box must be in the lower half: chat apps put it at the bottom, search fields at the top. */
    private fun hasBottomInput(node: AccessibilityNodeInfo?, h: Int, depth: Int): Boolean {
        if (node == null || depth > 45) return false
        if (node.isEditable) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.centerY() > h * 0.55f) return true
        }
        for (i in 0 until node.childCount) {
            if (hasBottomInput(node.getChild(i), h, depth + 1)) return true
        }
        return false
    }

    private fun walk(
        node: AccessibilityNodeInfo?,
        out: MutableList<Line>,
        avatars: MutableList<Avatar>,
        depth: Int,
        w: Int
    ) {
        if (node == null || depth > 45 || out.size > 400) return
        if (node.isEditable) return
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) {
            // Avatars are recorded for their position, never as message text.
            if (isAvatarNode(node, r, w)) {
                avatars.add(Avatar(r.centerX(), r.top, r.bottom))
                return
            }
            val desc = node.contentDescription?.toString()?.trim()
            val t = (node.text?.toString() ?: desc)?.trim()
            if (!t.isNullOrEmpty() && t.length <= 800 && r.left >= 0 && r.right <= w) {
                out.add(Line(t, r.top, r.left, r.right, r.bottom))
            }
        }
        for (i in 0 until node.childCount) walk(node.getChild(i), out, avatars, depth + 1, w)
    }

    companion object {
        /** Whole-string timestamps and date dividers, in the shapes these apps actually render. */
        private val STAMP_RES = listOf(
            Regex("""^\d{1,2}[:：]\d{2}$"""),
            Regex("""^(上午|下午|凌晨|早上|晚上|中午)\s*\d{1,2}[:：]\d{2}$"""),
            Regex("""^\d{1,2}[:：]\d{2}\s*(上午|下午|AM|PM|am|pm)$"""),
            Regex("""^(\d{4}年)?\d{1,2}月\d{1,2}日(\s*(上午|下午)?\s*\d{1,2}[:：]\d{2})?$"""),
            Regex("""^(昨天|今天|前天|大前天|星期[一二三四五六日天])(\s*\d{1,2}[:：]\d{2})?$""")
        )

        private val AVATAR_DESC = Regex("""^(头像|.*的头像|Avatar|.*'s avatar)$""", RegexOption.IGNORE_CASE)
    }
}
