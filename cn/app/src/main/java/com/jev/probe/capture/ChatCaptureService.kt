package com.jev.probe.capture

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jev.probe.capture.live.LiveFrameHub
import com.jev.probe.capture.ocr.MlKitOcr
import com.jev.probe.capture.ocr.OcrLine
import com.jev.probe.capture.ocr.ScreenCapture
import com.jev.probe.core.BubbleRect
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg
import com.jev.probe.core.Prefs
import com.jev.probe.overlay.OverlayController
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * The live capture service. It reads whichever
 * adapted chat app is in the foreground, detects a new incoming message from the
 * other person, runs Jev analysis off the main thread, and drives the floating
 * overlay.
 *
 * Per-app node rules live in [ChatAppAdapter] implementations; everything here
 * is app-agnostic.
 *
 * It never sends a message. The only write action is ACTION_SET_TEXT (or a
 * clipboard PASTE fallback) to fill the chat input box when the user taps
 * "填入"; the user still presses send.
 */
open class ChatCaptureService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)

    /** Adapted chat apps, keyed by package name. Apps in [BLOCKED_PKGS] are
     *  never read at all (see [maybeCapture] / [onAccessibilityEvent]). */
    private val adapters = listOf(QQAdapter(), XAdapter(), FeishuAdapter(), WhatsAppAdapter()).associateBy { it.pkg }

    /** Submit to the worker, ignoring rejection after the service is torn down
     *  (a stale overlay callback must never crash the process). */
    private fun submit(task: () -> Unit) {
        try { worker.execute(task) } catch (_: RejectedExecutionException) { }
    }
    private lateinit var prefs: Prefs
    private var overlay: OverlayController? = null
    private lateinit var coordinator: AnalysisCoordinator

    private var activePkg: String? = null
    private var destroyed = false
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enabled" || key == "whitelist") {
            main.post {
                leaveConversation()
                overlay?.hide()
            }
        }
    }

    private fun observeTarget(target: ConversationSession.Target?) {
        if (!::coordinator.isInitialized) return
        val changed = coordinator.observeTarget(target)
        if (changed) {
            activePkg = null
            lastOcrSignature = ""
        }
    }

    private fun leaveConversation() {
        if (::coordinator.isInitialized) coordinator.leaveConversation()
        activePkg = null
        lastOcrSignature = ""
    }

    private val liveHubListener = object : LiveFrameHub.Listener {
        override fun onLiveChanged(live: Boolean) {
            if (live) {
                leaveConversation()
                overlay?.hide()
            }
        }
    }

    /** Read the live target, never the previous chat's cached/stabilized title. */
    private fun targetFor(root: AccessibilityNodeInfo): ConversationSession.Target? {
        val pkg = root.packageName?.toString() ?: return null
        if (isBlocked(pkg) || pkg == packageName || pkg == "com.android.systemui" ||
            pkg.contains("launcher", true) || pkg == "com.miui.home") return null
        val adapter = adapters[pkg]
        var messagesSignature: String? = null
        val title = if (adapter != null) {
            val snapshot = adapter.extract(root, resources) ?: return null
            messagesSignature = snapshot.takeIf { it.messages.isNotEmpty() }?.signature()
            // A loading/unknown title cannot prove which conversation is open —
            // unless the SAME app+window already has a known one: animated
            // headers swap in skeleton/transient titles for a beat (#68), and
            // unbinding the session there killed in-flight analyses. Keep the
            // previously verified title instead.
            val fresh = snapshot.title?.takeUnless { isTransientTitle(it) }
            if (fresh != null) fresh
            else {
                val known = coordinator.target
                if (known != null && known.pkg == pkg && known.windowId == root.windowId &&
                    !known.title.isNullOrBlank()) known.title else return null
            }
        } else {
            findTitleInActionBar(root, Int.MAX_VALUE, resources.displayMetrics.widthPixels,
                resources, 0.15, 0.85)
        }
        return ConversationSession.Target(pkg, root.windowId, title, messagesSignature)
    }

    private fun isCurrent(token: ConversationSession.Token): Boolean {
        if (destroyed || !prefs.enabled || !coordinator.accepts(token)) return false
        return extraCurrent(token)
    }

    /**
     * #18: WINDOW_STATE_CHANGED blips — the notification shade, a permission
     * dialog, a split-screen handle — are transient, but the drop branch below
     * used to tear the bubble down the instant one arrived, and an open panel
     * was lost mid-read. Hide only if the screen is STILL a drop-zone after a
     * short grace period; any chat-app event in between cancels the hide.
     */
    private val confirmAwayHide = Runnable {
        val fg = rootInActiveWindow?.packageName?.toString()
        val stillAway = fg == null || fg == packageName ||
            fg.contains("launcher", ignoreCase = true) ||
            fg == "com.miui.home" || fg == "com.android.systemui"
        if (stillAway) overlay?.hide()
    }
    private var foregroundPkg: String? = null

    // ---- OCR path (B stage). Everything here runs on the main thread: the
    // screenshot callback and the ML Kit callback are both posted back to it.
    private val screenCapture by lazy {
        ScreenCapture(this,
            hideOverlay = { overlay?.setHiddenForShot(true) },
            restoreOverlay = { overlay?.setHiddenForShot(false) })
    }
    private val ocr = MlKitOcr()
    private var ocrBusy = false

    /** What the screen looked like the last time we fired an automatic shot.
     *  See [ocrSignature]: this is the brake on the OCR path. */
    private var lastOcrSignature: String = ""

    /** Whether the "not supported" notice has been shown for the current visit
     *  to a blocked app. Reset whenever another foreground is seen, so it
     *  re-appears next visit but does not re-pop on every event. */
    private var blockedNoticeShown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        getSharedPreferences(Prefs.PREFS_MAIN, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferencesListener)
        overlay = OverlayController(this)
        coordinator = AnalysisCoordinator(
            appCtx = this,
            prefs = prefs,
            overlay = overlay!!,
            extraCurrent = { token -> extraCurrent(token) },
            fillInput = { token, text -> fillInput(token, text) },
        )
        coordinator.attachOverlay()
        // Bubble menu: one manual screenshot + OCR, for any app at all.
        overlay?.onOcrCapture = { ocrCaptureManual() }
        LiveFrameHub.observe(liveHubListener)
        // Keep the process at foreground importance so MIUI does not freeze us.
        runCatching { KeepAliveService.start(this) }
        // Load the bundled OCR model now, off the main thread: the first
        // recognize() otherwise pays for it inside the screenshot callback.
        submit { MlKitOcr.warmUp() }
        // HyperOS may kill and restart us. On (re)connect, proactively re-show the
        // bubble for whatever chat is already open, so it comes back on its own
        // instead of waiting for the user to scroll.
        main.postDelayed({ if (prefs.enabled) runCatching { maybeCapture() } }, 900)
        Log.i(TAG, "capture service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (LiveFrameHub.isLive) return
        if (!prefs.enabled) { leaveConversation(); overlay?.hide(); return }

        val type = event.eventType
        // #18-4: our own overlay redraws ("generating…" → judgment → candidates)
        // emit WINDOW_CONTENT_CHANGED / VIEW_SCROLLED carrying our package name.
        // Reacting to them woke the service in a loop: re-read → re-analyze →
        // redraw → …, which pinned the panel on "generating" forever. Window-state
        // events keep flowing: the fg == packageName branch above still hides the
        // bubble inside our own settings screens.
        if (event.packageName?.toString() == packageName &&
            (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
             type == AccessibilityEvent.TYPE_VIEW_SCROLLED)) return
        // Decide "did we leave the chat app" from the REAL active window, not the
        // event's package. The event package can be an IME (e.g. com.tencent.wetype)
        // or the status bar while the chat app is still foreground — keying off it
        // made the bubble flicker (hide → re-show → hide…). rootInActiveWindow stays
        // on the chat app while the keyboard is up, so this is stable.
        //
        // An app with no adapter is NOT a reason to take the bubble away: the only
        // way into DingTalk / Telegram / anything else is the bubble menu's
        // "截屏识别一次", and a bubble that is gone cannot be tapped. So we park
        // the idle bubble there instead — still no automatic capture, no analysis.
        // The bubble does come off for places where it would only be in the way:
        // our own settings screens, the launcher, and the system UI.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val fg = rootInActiveWindow?.packageName?.toString()
            // Blocked apps: never read/screenshot/OCR/fill there, only show the
            // one-time "not supported" notice and stop. Checked before the
            // generic no-adapter branch.
            if (isBlocked(fg)) { foregroundPkg = fg; showBlocked(auto = true); return }
            if (fg != null && fg !in adapters) {
                val target = rootInActiveWindow?.let { targetFor(it) }
                if (coordinator.target != target) leaveConversation()
                foregroundPkg = fg
                blockedNoticeShown = false // left a blocked app → allow the notice again next visit
                val drop = fg == packageName ||
                    fg.contains("launcher", ignoreCase = true) ||
                    fg == "com.miui.home" ||
                    fg == "com.android.systemui"
                if (drop) {
                    // Grace period instead of an instant tear-down (see [confirmAwayHide]).
                    main.removeCallbacks(confirmAwayHide)
                    main.postDelayed(confirmAwayHide, FOREGROUND_CONFIRM_MS)
                } else {
                    main.removeCallbacks(confirmAwayHide)
                    overlay?.showIdle(null)
                }
                return
            }
        }

        when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> maybeCapture()
        }
    }

    private fun extraCurrent(token: ConversationSession.Token): Boolean {
        val live = rootInActiveWindow?.let { targetFor(it) }
        if (live != null && (!live.sameConversation(token.target) ||
            !prefs.isAllowed(coordinator.currentSnapshot?.title ?: live.title))) {
            leaveConversation()
            overlay?.hide()
            return false
        }
        return true
    }

    private fun maybeCapture() {
        if (LiveFrameHub.isLive) return
        // The chat app is back (or never left): a pending hide confirmation is moot.
        main.removeCallbacks(confirmAwayHide)
        val root = rootInActiveWindow ?: run { leaveConversation(); overlay?.hide(); return }
        val pkg = root.packageName?.toString()
        // Blocked apps — no tree read, no screenshot, no OCR, no fill. A
        // content-changed / scrolled event there only re-shows the one-time
        // notice (deduped); it must never reach an adapter or the OCR path.
        if (isBlocked(pkg)) { showBlocked(auto = true); return }
        blockedNoticeShown = false // any other foreground → allow the notice again next visit
        // Apps with no adapter are never handled automatically (v1.3 revision):
        // the only way in for them is the bubble menu's "截屏识别一次".
        val adapter = adapters[pkg] ?: run {
            if (coordinator.target != null && coordinator.target != targetFor(root)) leaveConversation()
            return
        }
        // Outside a chat window (the conversation list, a profile, settings…) the
        // adapter returns null. That is NOT a reason to show nothing: an adapted
        // app must behave at least as well as an unadapted one, which parks an idle
        // bubble so the menu stays reachable. Without this, opening QQ / Feishu on
        // their list screen produced no bubble at all.
        val rawSnapshot = adapter.extract(root, resources)
        if (rawSnapshot == null) { scheduleLeave(); return }
        val target = targetFor(root)
        if (target == null) { scheduleLeave(); return }
        main.removeCallbacks(confirmLeave)
        observeTarget(target)
        overlay?.setApp(pkg)
        // Use only this window's title; never inherit another conversation's title.
        val snapshot = rawSnapshot
        if (!prefs.isAllowed(snapshot.title)) { leaveConversation(); overlay?.hide(); return }
        // In a chat window but the tree holds no text (Feishu draws its bodies)
        // → screenshot + OCR, subject
        // to ScreenCapture's own >=1s throttle and failure backoff.
        if (snapshot.messages.isEmpty()) {
            // In a chat window, but the tree carries no text (Feishu draws its
            // message bodies). Park the bubble BEFORE attempting OCR, so the user
            // still has something to tap when OCR is off, deduped, or comes back
            // empty — previously all three cases left the screen with no bubble.
            if (overlay?.isShowing() != true) overlay?.showIdle(snapshot.title)
            if (prefs.ocrFallback && pkg !in NO_SCREENSHOT_PKGS) {
                // Gate BEFORE the shot, not after the OCR. Feishu's tree is empty
                // on every content-changed event, and a successful shot resets the
                // failure backoff — so without this the caret blinking or an
                // "online" badge flipping keeps a screenshot going out every
                // second forever. The picture can only differ if the bubbles moved
                // or the conversation changed, and that is exactly what the
                // signature measures.
                val sig = ocrSignature(pkg ?: "", snapshot.title, snapshot.bubbleRects)
                if (sig == lastOcrSignature && overlay?.isShowing() == true) return
                if (ocrBusy) return
                lastOcrSignature = sig
                ocrCapture(snapshot.title, snapshot.bubbleRects, pkg ?: "", manual = false)
            }
            return
        }

        if (pkg != activePkg) activePkg = pkg
        coordinator.ingest(snapshot, pkg ?: "", root.windowId, AnalysisCoordinator.Mode.TREE)
    }

    /**
     * A chat app's tree can briefly read as "not a chat" while its list re-lays
     * out (QQ while scrolling). Leave the conversation only if it still reads
     * that way after a short grace period; any good read in between cancels it.
     */
    private val confirmLeave = Runnable {
        val root = rootInActiveWindow
        val adapter = root?.packageName?.toString()?.let { adapters[it] }
        val gone = root == null || adapter == null ||
            adapter.extract(root, resources) == null || targetFor(root) == null
        if (gone) { leaveConversation(); overlay?.showIdle(null) }
    }

    private fun scheduleLeave() {
        if (coordinator.target == null) { leaveConversation(); overlay?.showIdle(null); return }
        main.removeCallbacks(confirmLeave)
        main.postDelayed(confirmLeave, LEAVE_CONFIRM_MS)
    }

    private fun isBlocked(pkg: String?): Boolean = pkg != null && pkg in BLOCKED_PKGS

    /**
     * Foreground is a blocked app: no node-tree read, no screenshot, no OCR, no
     * fill. We only surface a one-time "not supported" notice. [auto] events
     * (accessibility callbacks) show it once per visit — [blockedNoticeShown]
     * dedupes them; a manual bubble tap ([auto] = false) always shows it. Never
     * gated by the user's whitelist (it is an info notice, not a read).
     */
    private fun showBlocked(auto: Boolean) {
        leaveConversation()
        if (auto && blockedNoticeShown) return
        blockedNoticeShown = true
        // No popup panel there — take the overlay off entirely and show the
        // reason once as a small transient toast.
        overlay?.hide()
        overlay?.toast(BLOCKED_MSG)
    }

    // ------------------------------------------------------------------ OCR

    /**
     * Bubble menu → "截屏识别一次". Works on ANY app, adapted or not: one whole
     * screen shot, every line OCR'd, lines grouped into pseudo-bubbles by line
     * spacing. Nobody can tell who said what this way, so everything is filed as
     * the other person and the panel says so.
     */
    private fun ocrCaptureManual() {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: foregroundPkg ?: activePkg ?: ""
        // Blocked apps: a manual "截屏识别一次" must NOT take a screenshot there —
        // just show the notice (a manual tap always shows it).
        if (isBlocked(pkg)) { showBlocked(auto = false); return }
        if (pkg in NO_SCREENSHOT_PKGS) { overlay?.showError("这个应用里不使用截屏识别"); return }
        // Top bar text, if this app has one we can read; else the first OCR line.
        val title = root?.let {
            findTitleInActionBar(it, Int.MAX_VALUE, resources.displayMetrics.widthPixels, resources, 0.15, 0.85)
        }
        val target = root?.let { targetFor(it) } ?: run {
            overlay?.toast("无法确认当前会话，请等待标题加载后重试")
            return
        }
        observeTarget(target)
        ocrCapture(title, emptyList(), pkg, manual = true)
    }

    /**
     * What the screen would look like to a camera, as far as the tree can tell.
     *
     * Feishu: the conversation title plus every bubble rectangle and its side —
     * the bubbles move whenever the list scrolls or a message arrives, and stay
     * put when only chrome (caret, presence dot, timestamp) redraws. Apps that
     * give us no rectangles fall back to package + title, which at least stops a
     * burst of events on one screen from becoming a burst of screenshots.
     */
    private fun ocrSignature(pkg: String, title: String?, rects: List<BubbleRect>): String {
        if (rects.isEmpty()) return pkg + "|" + (title ?: "")
        return (title ?: "") + "|" + rects.joinToString(";") { br ->
            val r = br.rect
            "${r.left},${r.top},${r.right},${r.bottom},${br.side}"
        }
    }

    /**
     * Screenshot, then either OCR each known bubble rect (Feishu: the tree knows
     * where the bubbles are and who sent them, just not what they say) or OCR
     * the whole screen (everything else).
     */
    private fun ocrCapture(treeTitle: String?, rects: List<BubbleRect>, pkg: String, manual: Boolean) {
        if (ocrBusy || destroyed || !prefs.enabled) return
        val token = coordinator.token() ?: return
        if (!isCurrent(token)) return
        ocrBusy = true
        screenCapture.capture(shouldCapture = { isCurrent(token) }) { res ->
            if (!isCurrent(token)) {
                if (res is ScreenCapture.Result.Ok) res.bitmap.recycle()
                ocrBusy = false
                return@capture
            }
            when (res) {
                is ScreenCapture.Result.Failed -> {
                    ocrBusy = false
                    Log.i(TAG, "ocr: screenshot failed code=${res.code}")
                    // Nothing was read, so the signature must not claim this
                    // screen is done — the next event may retry, still held
                    // back by ScreenCapture's own throttle and failure backoff.
                    if (!manual) lastOcrSignature = ""
                    // Throttle/interval codes are transient timing, not
                    // something the user can act on — nagging would be constant.
                    val transient = res.code == ScreenCapture.CODE_THROTTLED || res.code == 3
                    if (manual || !transient) overlay?.showError(res.humanMessage)
                }
                is ScreenCapture.Result.Ok -> {
                    ocr.scaleX = res.scaleX; ocr.scaleY = res.scaleY
                    ocr.originX = res.originX; ocr.originY = res.originY
                    val jpeg = VisionJpeg.encode(res.bitmap, prefs)
                    if (rects.isNotEmpty() && !manual) {
                        // Re-measure inside the callback. The rects handed in were
                        // read before the 120ms overlay-hide wait and the shot
                        // itself; one scroll tick in between and we would crop the
                        // rows next to the ones in the picture. Fall back to the
                        // old rects only if the tree gives us nothing now.
                        val fresh = rootInActiveWindow?.let { collectFeishuBubbleRects(it, resources) }
                        ocrByRects(res.bitmap, if (fresh.isNullOrEmpty()) rects else fresh, treeTitle, pkg, token, jpeg)
                    } else ocrWholeScreen(res.bitmap, treeTitle, pkg, manual, token, jpeg)
                }
            }
        }
    }

    /** One OCR pass per bubble rectangle; each rect becomes exactly one message. */
    private fun ocrByRects(
        bmp: Bitmap,
        rects: List<BubbleRect>,
        title: String?,
        pkg: String,
        token: ConversationSession.Token,
        jpeg: ByteArray?
    ) {
        val sx = ocr.scaleX; val sy = ocr.scaleY
        // Screen -> bitmap: drop the window origin first. A window shot does not
        // start at (0,0) in split screen or when it excludes the status bar.
        val ox = ocr.originX; val oy = ocr.originY
        val out = arrayOfNulls<Msg>(rects.size)
        var remaining = rects.size
        rects.forEachIndexed { i, br ->
            val region = Rect(
                ((br.rect.left - ox) * sx).toInt(), ((br.rect.top - oy) * sy).toInt(),
                ((br.rect.right - ox) * sx).toInt(), ((br.rect.bottom - oy) * sy).toInt())
            ocr.recognize(bmp, region) { lines ->
                val text = cleanBubbleText(lines.joinToString(" ") { it.text })
                if (text.isNotEmpty()) out[i] = Msg(br.side, text)
                remaining--
                if (remaining == 0) {
                    runCatching { bmp.recycle() }
                    finishOcrSnapshot(ChatSnapshot(title, out.filterNotNull(), image = jpeg), pkg, manual = false, token = token)
                }
            }
        }
    }

    /** Whole screen minus the top bar and the input area, grouped by line gaps. */
    private fun ocrWholeScreen(
        bmp: Bitmap,
        treeTitle: String?,
        pkg: String,
        manual: Boolean,
        token: ConversationSession.Token,
        jpeg: ByteArray?
    ) {
        val region = Rect(0, (bmp.height * OcrLayout.TOP_CROP).toInt(), bmp.width, (bmp.height * OcrLayout.BOTTOM_CROP).toInt())
        ocr.recognize(bmp, region) { lines ->
            runCatching { bmp.recycle() }
            val msgs = groupOcrLines(lines)
            val title = treeTitle?.takeIf { it.isNotBlank() }
                ?: lines.firstOrNull()?.text?.trim()?.take(24)
            finishOcrSnapshot(ChatSnapshot(title, msgs, note = OCR_NOTE, image = jpeg), pkg, manual, token)
        }
    }

    /**
     * OCR lines → "bubbles": a gap larger than 1.2x the previous line's height
     * starts a new one. Side is unknowable from a flat screen read, so every
     * group is filed as the other person (and [OCR_NOTE] says so on the panel).
     */
    private fun groupOcrLines(lines: List<OcrLine>): List<Msg> {
        val usable = lines
            .filter { it.text.isNotBlank() && !PURE_TIME.matches(it.text.trim()) }
            .sortedBy { it.bounds.top }
        val out = ArrayList<Msg>()
        val buf = StringBuilder()
        var prev: OcrLine? = null
        for (l in usable) {
            val p = prev
            if (p != null) {
                val gap = l.bounds.top - p.bounds.bottom
                val lineHeight = maxOf(p.bounds.height(), 1)
                if (gap > lineHeight * 1.2f) {
                    if (buf.isNotEmpty()) { out.add(Msg("other", buf.toString())); buf.setLength(0) }
                }
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(l.text.trim())
            prev = l
        }
        if (buf.isNotEmpty()) out.add(Msg("other", buf.toString()))
        return out
    }

    /** Strip the read receipt and the timestamp Feishu glues onto a bubble. */
    private fun cleanBubbleText(raw: String): String {
        var t = raw.trim()
        var changed = true
        while (changed && t.isNotEmpty()) {
            changed = false
            for (tail in arrayOf("已读", "未读")) {
                if (t.endsWith(tail)) { t = t.removeSuffix(tail).trim(); changed = true }
            }
            TAIL_TIME.find(t)?.let { t = t.substring(0, it.range.first).trim(); changed = true }
        }
        return t
    }

    /** Shared tail of both OCR paths: dedupe, then analyze or park the bubble. */
    private fun finishOcrSnapshot(snapshot: ChatSnapshot, pkg: String, manual: Boolean, token: ConversationSession.Token) {
        ocrBusy = false
        if (!isCurrent(token)) return
        // Counts only — OCR'd chat text never goes to logcat.
        Log.i(TAG, "ocr[$pkg] msgs=${snapshot.messages.size} manual=$manual visionBytes=${snapshot.image?.size ?: 0}")
        if (pkg.isNotEmpty()) activePkg = pkg
        coordinator.ingest(
            snapshot,
            pkg,
            token.target.windowId,
            if (manual) AnalysisCoordinator.Mode.MANUAL else AnalysisCoordinator.Mode.OCR,
        )
    }

    /** Resolve only the originating chat's input, never an arbitrary foreground editor. */
    private fun inputFor(token: ConversationSession.Token): AccessibilityNodeInfo? {
        if (!isCurrent(token)) return null
        val root = rootInActiveWindow ?: return null
        if (targetFor(root) != token.target) return null
        val input = when (token.target.pkg) {
            "com.tencent.mobileqq" -> root.findAccessibilityNodeInfosByViewId("com.tencent.mobileqq:id/input").firstOrNull()
            "com.ss.android.lark" -> root.findAccessibilityNodeInfosByViewId("com.ss.android.lark:id/kb_rich_text_content").firstOrNull()
            "com.twitter.android" -> findEditable(root)
            else -> null // Unknown apps support explicit clipboard copy, not unverified writes.
        }
        // Re-read the node after SET_TEXT: the accessibility cache may still
        // contain the previous draft even though the write already succeeded.
        input ?: return null
        if (!input.refresh()) return null
        return input.takeIf { it.isVisibleToUser && it.isEnabled && !it.isPassword }
    }

    /** Fill without blocking the main thread; all retries re-resolve the original target. */
    private fun fillInput(token: ConversationSession.Token, text: String) {
        fun finish(ok: Boolean) {
            if (!isCurrent(token)) return
            if (ok) overlay?.toast("已填入，确认后自己发送")
            else { copyToClipboard(text); overlay?.toast("已复制，长按输入框粘贴") }
        }
        if (!isCurrent(token)) { overlay?.toast("会话已变化，请重新分析后填入"); return }
        if (inputFor(token) == null) { finish(false); return }
        GuardedInputWriter(
            resolve = {
                inputFor(token)?.let { node ->
                    object : GuardedInputWriter.Input {
                        override val text: String? get() = node.text?.toString()
                        override fun setText(text: String) = setTextRaw(node, text)
                        override fun focus() { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                        override fun paste() { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }
                    }
                }
            },
            later = { delay, action -> main.postDelayed({ action() }, delay) },
            copy = { copyToClipboard(it) },
            complete = { finish(it) }
        ).fill(text)
    }

    private fun setTextRaw(edit: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var guard = 0
        var found: AccessibilityNodeInfo? = null
        while (stack.isNotEmpty() && guard < 5000) {
            guard++
            val node = stack.removeLast()
            if (node.isEditable && node.isVisibleToUser && node.isEnabled && !node.isPassword) {
                if (found != null) return null // Ambiguous editor: clipboard only.
                found = node
            }
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it) }
        }
        return if (stack.isEmpty()) found else null
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("jev_reply", text))
    }

    override fun onInterrupt() {
        leaveConversation()
        overlay?.hide()
    }

    override fun onDestroy() {
        destroyed = true
        LiveFrameHub.remove(liveHubListener)
        getSharedPreferences(Prefs.PREFS_MAIN, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferencesListener)
        if (::coordinator.isInitialized) coordinator.destroy()
        leaveConversation()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
        overlay?.onOcrCapture = null
        overlay?.hide()
        overlay = null
        worker.shutdownNow()
    }

    companion object {
        private const val TAG = "JEVASSIST"

        /** Grace period before a "not a chat" read really leaves the conversation. */
        private const val LEAVE_CONFIRM_MS = 1200L

        /** Apps that are never read: they protect their content against screen
         *  reading or screenshots, so any capture attempt there is off-limits.
         *  No adapter, no capture, only a one-time "not supported" notice. */
        /** Supported apps that are read through the node tree only and never
         *  screenshotted: no OCR fallback, no manual "截屏识别一次". */
        private val NO_SCREENSHOT_PKGS = setOf(WhatsAppRules.PACKAGE)

        private val BLOCKED_PKGS = setOf("com.tencent.mm", "cn.soulapp.android")

        /** Shown once per visit to a blocked app. Plain words, full-width
         *  punctuation; steers the user to a supported app. */
        private const val BLOCKED_MSG =
            "这个应用暂不支持，请在别的软件上使用"

        /** How long a transient non-chat foreground must persist before the
         *  bubble is taken away (#18). */
        private const val FOREGROUND_CONFIRM_MS = 1500L

        /** Said on the panel whenever a snapshot came from flat-screen OCR. */
        private const val OCR_NOTE = "OCR 未分边，把全部消息当作对方所说"

        private val PURE_TIME = Regex("""\d{1,2}[:：]\d{2}""")
        private val TAIL_TIME = Regex("""\d{1,2}[:：]\d{2}$""")
    }
}
