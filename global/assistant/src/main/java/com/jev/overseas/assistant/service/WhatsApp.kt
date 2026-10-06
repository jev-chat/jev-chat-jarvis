package com.jev.overseas.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jev.overseas.a11y.NodeCollector
import com.jev.overseas.a11y.RowStability
import com.jev.overseas.a11y.Settle
import com.jev.overseas.core.dump.ScreenDump
import com.jev.overseas.core.dump.ScreenDumpFormat
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.model.MessageRecord
import com.jev.overseas.core.model.PageKind
import com.jev.overseas.core.model.ScreenRead
import com.jev.overseas.core.stitch.Boundary
import com.jev.overseas.core.stitch.Stitcher
import com.jev.overseas.core.session.BoxState
import com.jev.overseas.core.session.ChatReader
import com.jev.overseas.core.session.EditableBox
import com.jev.overseas.core.session.FillProcedure
import com.jev.overseas.core.session.ReadReport
import com.jev.overseas.core.session.ReadResult
import com.jev.overseas.core.session.ReplyTarget
import com.jev.overseas.core.whatsapp.WhatsAppAdapter
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** WhatsApp's application window, found among all windows (our own panel may hold focus). */
object WhatsAppWindow {
    const val PACKAGE = "com.whatsapp"
    const val ID_LIST = "android:id/list"
    const val ID_TITLE = "com.whatsapp:id/conversation_contact_name"
    const val ID_ENTRY = "com.whatsapp:id/entry"

    fun root(service: AccessibilityService): AccessibilityNodeInfo? = runCatching {
        service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == PACKAGE } }
    }.getOrNull()

    fun isShowing(service: AccessibilityService): Boolean = runCatching {
        service.windows.any { w ->
            w.type == AccessibilityWindowInfo.TYPE_APPLICATION && w.root?.packageName?.toString() == PACKAGE
        }
    }.getOrDefault(false)
}

/**
 * Reads the chat. Runs on the worker thread.
 * Nothing is written to disk, and only counts go to logcat.
 *
 * Always starts from the newest message: if the user has scrolled up, the list
 * is scrolled down first. For analysis it then reads earlier screens until it
 * has [Conversation.DEFAULT_WINDOW] messages (all the engine uses), the start
 * of the chat, [MAX_SCREENS] screens or [MAX_READ_MS], joins them with
 * [Stitcher] and scrolls back to the bottom, checking that it got there. If the
 * chat changes while it reads, it stops and scrolls nothing else.
 */
class WhatsAppReader(private val service: AccessibilityService) : ChatReader {

    private class Screen(val root: AccessibilityNodeInfo, val read: ScreenRead, val title: String)

    private sealed class Got {
        class Ok(val screen: Screen, val scrolls: Int) : Got()
        class Fail(val result: ReadResult) : Got()
    }

    override fun readLatest(): ReadResult = read(1)

    override fun readCurrent(): ReadResult = read(MAX_SCREENS)

    override fun readMore(): ReadResult = read(WIDE_SCREENS, Conversation.WIDE_WINDOW, WIDE_READ_MS)

    /**
     * Earlier messages from the last full read of a chat, in memory only. Older
     * messages do not change, so a later read joins the new bottom screen onto
     * this instead of scrolling up again. Cleared on close, after [KEEP_MS], or
     * when the bottom no longer joins on.
     */
    private class Kept(val title: String, val rows: List<MessageRecord>, val screens: Int, val stop: String, val at: Long)

    @Volatile private var kept: Kept? = null

    override fun forget() { kept = null; lastBottom = null }

    /** The bottom screen of the last quick read, so an analysis right after it does not read it twice. */
    private class Recent(val got: Got.Ok, val at: Long)
    @Volatile private var lastBottom: Recent? = null

    private fun read(maxScreens: Int, window: Int = Conversation.DEFAULT_WINDOW, maxMs: Long = MAX_READ_MS): ReadResult {
        val started = SystemClock.elapsedRealtime()
        val recent = lastBottom?.takeIf { maxScreens > 1 && started - it.at < REUSE_BOTTOM_MS }
        lastBottom = null
        val bottom = recent?.got ?: when (val got = bottomScreen()) {
            is Got.Fail -> return got.result
            is Got.Ok -> got
        }
        if (maxScreens == 1) lastBottom = Recent(bottom, SystemClock.elapsedRealtime())
        var rows = bottom.screen.read.rows
        Log.i(TAG, "read: screen 1: ${rows.size} rows")
        var screens = 1
        var stop = "current screen only"
        var continuous = true
        var returned = true
        var cached = false
        // A wider read never uses the cache: the point is to reach messages the last read did not.
        val earlier = kept?.takeIf { window == Conversation.DEFAULT_WINDOW && it.title == bottom.screen.title && SystemClock.elapsedRealtime() - it.at < KEEP_MS }
        val reuse = if (maxScreens > 1 && earlier != null) Stitcher.prepend(earlier.rows, rows) else null
        if (reuse != null && reuse.boundary == Boundary.OVERLAP) {
            // Same chat, recently read: only the bottom was read again. Reported as what it is.
            Log.i(TAG, "read: joined onto ${earlier!!.rows.size} kept rows, k=${reuse.overlap}")
            rows = reuse.rows
            stop = earlier.stop
            cached = true
        } else if (maxScreens > 1) {
            stop = "$maxScreens screens"
            var current = bottom.screen
            var scrolledUp = 0
            var noProgress = 0
            var attempts = 0
            while (screens < maxScreens && attempts++ < maxScreens * 2 + 4) {
                if (rows.count { it.isChatMessage } >= window) { stop = "enough messages"; break }
                if (SystemClock.elapsedRealtime() - started > maxMs) { stop = "time limit"; break }
                if (!current.read.canScrollToOlder) { stop = "start of the chat"; break }
                if (!scroll(current.root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) { stop = "could not scroll"; break }
                scrolledUp++
                val next = stableScreen() ?: run { stop = "unstable"; null } ?: break
                if (next.read.page == PageKind.GROUP_CONVERSATION && next.title == bottom.screen.title) {
                    // The bottom screen showed only the user's own messages; an earlier one shows it is a group.
                    Log.i(TAG, "read: group chat found after $scrolledUp scrolls")
                    backToBottom(bottom.screen, scrolledUp)
                    kept = null
                    return ReadResult.GroupChat
                }
                if (next.read.page != PageKind.CONVERSATION || next.title != bottom.screen.title) {
                    // Another chat or page is in front now: its list must not be scrolled, and nothing read is used.
                    Log.i(TAG, "read: chat changed after $scrolledUp scrolls")
                    kept = null
                    return ReadResult.ChatChanged
                }
                val r = next.read
                // The list did not move, or snapped back to rows already read: keep scrolling, join nothing.
                if (Stitcher.alreadyRead(r.rows, rows)) {
                    Log.i(TAG, "read: step $attempts: nothing new (older=${r.canScrollToOlder} newer=${r.canScrollToNewer})")
                    if (++noProgress >= 4) { stop = "no new messages"; break }
                    // WhatsApp may be loading older messages: give it a moment before the next scroll.
                    Thread.sleep(STALL_WAIT_MS)
                    current = next
                    continue
                }
                noProgress = 0
                val join = Stitcher.prepend(r.rows, rows)
                Log.i(TAG, "read: step $attempts: ${r.rows.size} rows, join=${join.boundary} k=${join.overlap} total=${join.rows.size}")
                if (join.boundary != Boundary.OVERLAP && join.boundary != Boundary.EDGE_ROW) {
                    // A gap: keep only the unbroken part below it, rather than joining across.
                    continuous = false
                    stop = "gap between screens"
                    break
                }
                rows = join.rows
                screens++
                current = next
            }
            if (scrolledUp > 0) returned = backToBottom(bottom.screen, scrolledUp)
            if (window == Conversation.DEFAULT_WINDOW) kept = if (continuous) Kept(bottom.screen.title, rows, screens, stop, SystemClock.elapsedRealtime()) else null
        }
        val conversation = Conversation.fromRows(rows, window)
        val newest = bottom.screen.read.messages
        val report = ReadReport(
            messages = conversation.messages.size,
            latestTurn = conversation.latestTurn.size,
            screens = screens,
            cached = cached,
            continuous = continuous,
            unreadable = rows.count { it.isChatMessage && !it.complete },
            stopReason = stop,
            scrolledToLatest = bottom.scrolls,
            returnedToBottom = returned,
        )
        // The last three messages fully on screen, sender and text only: the keyboard or a taller input box
        // changes what is visible and the time labels, never these.
        val fingerprint = hash(newest.filter { !it.clipped }.takeLast(FINGERPRINT_MESSAGES).joinToString("\u0001") {
            listOf(it.speaker, it.kind, it.text, it.edited).joinToString("\u0002")
        })
        Log.i(TAG, "read: ${report.messages} messages, ${report.latestTurn} new, $screens screens ($stop), " +
            "continuous=$continuous, returned=$returned, ${SystemClock.elapsedRealtime() - started} ms")
        return ReadResult.Ok(conversation, report, chatId = hash("chat:${bottom.screen.title}"), fingerprint = fingerprint)
    }

    /**
     * The bottom of the chat, read when the list has stopped moving. The user may
     * have scrolled up; the latest turn is at the bottom, so go there first.
     */
    private fun bottomScreen(): Got {
        var unstable = 0
        var scrolls = 0
        while (true) {
            val screen = screenNow() ?: return Got.Fail(ReadResult.Unavailable)
            if (screen.read.page == PageKind.GROUP_CONVERSATION) {
                Log.i(TAG, "read: group chat")
                return Got.Fail(ReadResult.GroupChat)
            }
            if (screen.read.page != PageKind.CONVERSATION) {
                Log.i(TAG, "read: not a conversation (${screen.read.page})")
                return Got.Fail(ReadResult.NotConversation)
            }
            if (screen.read.canScrollToNewer) {
                if (scrolls >= MAX_SCROLLS || !scroll(screen.root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    Log.i(TAG, "read: newest message not reached after $scrolls scrolls")
                    return Got.Fail(ReadResult.NotAtLatest)
                }
                scrolls++
                continue
            }
            if (!screen.read.stable) {
                Log.i(TAG, "read: unstable, attempt ${unstable + 1}: ${screen.read.notes.joinToString("; ")}")
                if (++unstable >= ATTEMPTS) return Got.Fail(ReadResult.Unstable)
                continue
            }
            return Got.Ok(screen, scrolls)
        }
    }

    /**
     * A settled, stable screen after a scroll, or null. The list keeps gliding for a
     * moment after a scroll; reading it at once three times in a row usually caught
     * it still moving, and the read stopped after one or two screens (device log
     * 2026-10-03: most reads ended "unstable" with 11 to 17 messages). So each retry
     * waits a little longer first.
     */
    private fun stableScreen(): Screen? {
        for (wait in SETTLE_WAITS_MS) {
            if (wait > 0) Thread.sleep(wait)
            val screen = screenNow() ?: return null
            if (screen.read.stable) return screen
            Log.i(TAG, "read: earlier screen unstable after ${wait} ms wait: ${screen.read.notes.joinToString("; ")}")
        }
        return null
    }

    private fun screenNow(): Screen? {
        val root = WhatsAppWindow.root(service) ?: return null
        Settle.waitForList(root, WhatsAppWindow.ID_LIST)
        val raw = NodeCollector.collect(root, service.resources).nodes
        val stability = RowStability.measure(root, raw, RowStability.mainList(raw))
        val dump = ScreenDump(linkedMapOf("schema" to ScreenDumpFormat.SCHEMA, "stability" to stability), raw)
        val title = raw.firstOrNull { it.viewId == WhatsAppWindow.ID_TITLE }?.text?.trim().orEmpty()
        val read = WhatsAppAdapter(otherPersonNames = setOfNotNull(title.ifEmpty { null }, WhatsAppAdapter.MASKED_TITLE)).read(dump)
        return Screen(root, read, title)
    }

    /**
     * Scrolls towards older ([AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD]) or newer
     * messages. Uses the directional up/down actions when the list offers them:
     * on the device, a plain "backward" from the middle of the chat sometimes
     * jumped to the bottom (2026-10-03).
     */
    private fun scroll(root: AccessibilityNodeInfo, action: Int): Boolean {
        val list = root.findAccessibilityNodeInfosByViewId(WhatsAppWindow.ID_LIST)?.firstOrNull { it.isScrollable } ?: return false
        list.refresh()
        val directional = if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
        }
        if (list.actionList.any { it.id == directional.id }) return list.performAction(directional.id)
        return list.performAction(action)
    }

    /**
     * Scrolls down until WhatsApp offers no further forward scroll, then checks the
     * last rows are the ones the read started from. False when it could not get
     * back (the report says so rather than pretending).
     */
    private fun backToBottom(start: Screen, scrolledUp: Int): Boolean {
        // Cheap loop: only the list's own actions say whether there is more below.
        repeat(scrolledUp * 2 + 4) {
            val root = WhatsAppWindow.root(service) ?: return false
            val list = root.findAccessibilityNodeInfosByViewId(WhatsAppWindow.ID_LIST)?.firstOrNull { it.isScrollable } ?: return false
            list.refresh()
            val more = list.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
            if (!more) {
                // At the bottom: one full read to confirm it is where the read started.
                val screen = stableScreen() ?: return false
                val want = start.read.rows.takeLast(3)
                val got = screen.read.rows.takeLast(3)
                val same = want.size == got.size && want.indices.all { Stitcher.same(want[it], got[it]) }
                if (!same) Log.i(TAG, "read: back at bottom but last rows differ (${want.map { it.kind }} vs ${got.map { it.kind }})")
                return same
            }
            if (!scroll(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return false
            Settle.waitForList(root, WhatsAppWindow.ID_LIST)
        }
        return false
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "JEV"
        private const val ATTEMPTS = 3
        /** Each scroll moves about one screen; 40 covers a long way up without hanging. */
        private const val MAX_SCROLLS = 40
        /** About five screens of context for an analysis. */
        const val MAX_SCREENS = 5
        /** "Read further back". */
        const val WIDE_SCREENS = 10
        private const val WIDE_READ_MS = 14_000L
        private const val STALL_WAIT_MS = 300L
        private const val KEEP_MS = 15 * 60 * 1000L
        /** A whole read, scrolling included, stops after this and uses what it has. */
        private const val MAX_READ_MS = 8_000L
        /** How recent a quick read must be for an analysis to start from its bottom screen. */
        private const val REUSE_BOTTOM_MS = 2_500L
        private const val FINGERPRINT_MESSAGES = 3
        /** Waits before each try at reading a screen after a scroll: about 1 s at most in all. */
        private val SETTLE_WAITS_MS = longArrayOf(0, 120, 250, 400)
    }
}

/**
 * Fill and Copy. Writes only with ACTION_SET_TEXT on
 * WhatsApp's message box. There is no code here, or anywhere in the app, that
 * touches the send button or uses the IME enter action.
 */
class WhatsAppInput(private val service: AccessibilityService) : ReplyTarget {

    private val main = Handler(Looper.getMainLooper())

    private fun entry(): AccessibilityNodeInfo? =
        WhatsAppWindow.root(service)?.findAccessibilityNodeInfosByViewId(WhatsAppWindow.ID_ENTRY)?.firstOrNull { it.isEditable }

    /**
     * WhatsApp reports its placeholder ("Message", or its translation) as the box's text and does
     * not flag it as a hint, so the text alone cannot tell an empty box. What does:
     * the camera and voice-note buttons beside the box are shown only while it is
     * empty and hidden as soon as anything is typed (recordings of 2.26.38.73 and a
     * live check on 2026-10-02).
     */
    override fun boxState(): BoxState {
        val root = WhatsAppWindow.root(service) ?: return BoxState.MISSING
        val node = root.findAccessibilityNodeInfosByViewId(WhatsAppWindow.ID_ENTRY)?.firstOrNull { it.isEditable } ?: return BoxState.MISSING
        node.refresh()
        val text = node.text?.toString().orEmpty()
        if (node.isShowingHintText || text.isBlank()) return BoxState.EMPTY
        val emptyMarkers = EMPTY_BOX_BUTTONS.any { id ->
            root.findAccessibilityNodeInfosByViewId(id)?.any { it.isVisibleToUser } == true
        }
        return if (emptyMarkers) BoxState.EMPTY else BoxState.HAS_TEXT
    }

    override fun fill(text: String): Boolean {
        val node = entry() ?: return false
        val ok = FillProcedure.fill(NodeBox(node), text)
        Log.i(TAG, "fill: ${if (ok) "ok" else "failed"}, ${text.length} chars")
        return ok
    }

    override fun copy(text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            setClip(text)
            return
        }
        // Clipboard writes belong on the main thread; wait briefly so the panel can say it is done.
        val done = CountDownLatch(1)
        main.post {
            setClip(text)
            done.countDown()
        }
        done.await(1, TimeUnit.SECONDS)
    }

    private fun setClip(text: String) {
        runCatching {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Reply", text))
        }
        Log.i(TAG, "copy: ${text.length} chars")
    }

    private class NodeBox(private val node: AccessibilityNodeInfo) : EditableBox {
        override fun setText(text: String): Boolean {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        override fun currentText(): String? {
            node.refresh()
            if (node.isShowingHintText) return null
            return node.text?.toString()
        }

        override fun focus(): Boolean = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    companion object {
        private const val TAG = "JEV"
        private val EMPTY_BOX_BUTTONS = listOf("com.whatsapp:id/camera_btn", "com.whatsapp:id/voice_note_btn")
    }
}
