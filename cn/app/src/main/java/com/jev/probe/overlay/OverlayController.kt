package com.jev.probe.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import com.jev.probe.capture.live.LiveCaptureService
import com.jev.probe.capture.live.LiveFrameHub
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jev.probe.core.Analysis
import com.jev.probe.core.Prefs
import com.jev.probe.core.RankedReply
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating overlay with two forms.
 *
 * - **Card** (inside an adapted chat): a small card that stays where the user put
 *   it. Collapsed it shows the risk level and the other person's intent; tapping
 *   the header expands it to the full read plus the candidate replies (copy /
 *   fill — never send) and a "换一组" button; tapping again collapses it. The
 *   header drags the card anywhere; the spot is remembered per app. New results
 *   replace the old ones in place — the card never pops open on its own and is
 *   never left blank while a round runs.
 * - **Bubble** (outside a chat window, or in an app without an adapter): the
 *   small "Jev" ball; tapping or long-pressing it opens the menu (截屏识别一次…).
 *
 * Long-pressing the card header opens the same menu.
 */
class OverlayController(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs(ctx)
    private val main = Handler(Looper.getMainLooper())

    private enum class Mode { BUBBLE, CARD }

    private var root: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var mode: Mode? = null

    // Card views (null while the card is not built).
    private var cardView: LinearLayout? = null
    private var riskPill: TextView? = null
    private var headText: TextView? = null
    private var statusText: TextView? = null
    private var chevron: TextView? = null
    private var bodyScroll: MaxHeightScroll? = null
    private var body: LinearLayout? = null
    private var menuView: View? = null
    private var expanded = false

    /** Header tap with nothing to show yet (or after an error): analyze the open chat. */
    var onManualAnalyze: (() -> Unit)? = null

    /** "换一组" / retry: draft a new set of candidates for the same judgment. */
    var onRegenerate: (() -> Unit)? = null

    /** Menu → file the open conversation as a knowledge-base contact. */
    var onSaveContact: (() -> Unit)? = null

    /** Menu → one manual screenshot + OCR of whatever app is open. */
    var onOcrCapture: (() -> Unit)? = null

    // ---- what the card currently shows (survives hide(); cleared by resetForNewConversation)
    private var app: String? = null
    private var judgment: Analysis? = null
    private var replies: List<RankedReply>? = null
    private var replyError: String? = null
    private var errorMsg: String? = null
    private var judgeWaiting = false
    private var repliesWaiting = false
    private var waitStart = 0L
    private var lastFill: ((String) -> Unit)? = null
    private var ctxNotes = 0
    private var ctxHistory = 0
    private var noteText: String? = null

    /** Text views showing an elapsed-seconds counter; the ticker updates them in place. */
    private val waitLabels = ArrayList<Pair<TextView, String>>()

    /** Whether the overlay window is currently on screen. */
    fun isShowing(): Boolean = root != null

    /**
     * Screen-space box of the visible overlay window, used by live OCR to
     * drop lines that are our own bubble/card. Null when hidden or not laid out.
     */
    fun visibleScreenRect(): Rect? {
        val r = root ?: return null
        if (r.visibility != View.VISIBLE) return null
        val loc = IntArray(2)
        r.getLocationOnScreen(loc)
        val w = r.width
        val h = r.height
        if (w <= 0 || h <= 0) return null
        return Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).roundToInt()

    private fun canOverlay(): Boolean = Settings.canDrawOverlays(ctx)

    private val screenW get() = ctx.resources.displayMetrics.widthPixels
    private val screenH get() = ctx.resources.displayMetrics.heightPixels
    private val cardW get() = minOf(screenW - dp(16), dp(380))

    /** Card background: white with the user's opacity so the chat shows through. */
    private fun panelBg(): Int {
        val a = (prefs.overlayOpacity / 100f * 255).roundToInt().coerceIn(150, 255)
        return Color.argb(a, 255, 255, 255)
    }

    private fun card(radius: Int, color: Int, stroke: Boolean = false, strokeColor: Int = Color.parseColor("#22000000"), strokeW: Int = 1) =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            if (stroke) setStroke(dp(strokeW), strokeColor)
        }

    // ---------------------------------------------------------------- window

    private fun ensureWindow(): Boolean {
        if (root != null) return true
        if (!canOverlay()) { android.util.Log.w("JEVASSIST", "overlay: canDrawOverlays=false"); return false }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        lp = params
        val r = FrameLayout(ctx)
        root = r
        mode = null
        try { wm.addView(r, params) } catch (e: Exception) {
            android.util.Log.e("JEVASSIST", "overlay addView failed: ${e.message}"); root = null; return false
        }
        return true
    }

    private fun updateLayout() { root?.let { r -> lp?.let { runCatching { wm.updateViewLayout(r, it) } } } }

    private fun setMode(m: Mode) {
        val r = root ?: return
        if (mode == m && r.childCount > 0) return
        mode = m
        dismissMenu()
        r.removeAllViews()
        clearCardRefs()
        val params = lp ?: return
        if (m == Mode.BUBBLE) {
            r.addView(buildBubble())
            params.x = if (prefs.bubbleX in 0..(screenW - dp(52))) prefs.bubbleX else dp(8)
            params.y = if (prefs.bubbleY >= 0) prefs.bubbleY else dp(150)
        } else {
            r.addView(buildCard())
            placeCard(params)
        }
        updateLayout()
    }

    private fun placeCard(params: WindowManager.LayoutParams) {
        val (x, y) = app?.let { prefs.cardPosition(it) } ?: (dp(8) to dp(92))
        params.x = x.coerceIn(dp(4), maxOf(dp(4), screenW - cardW - dp(4)))
        params.y = y.coerceIn(dp(24), screenH - dp(120))
    }

    private fun clearCardRefs() {
        cardView = null; riskPill = null; headText = null; statusText = null; chevron = null
        bodyScroll = null; body = null; expanded = false; waitLabels.clear()
    }

    // ---------------------------------------------------------------- bubble

    private fun buildBubble(): View {
        val b = TextView(ctx).apply {
            text = "Jev"; setTextColor(Color.WHITE); gravity = Gravity.CENTER; textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            alpha = 0.7f
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(235, 58, 122, 254)) }
            layoutParams = FrameLayout.LayoutParams(dp(52), dp(52))
        }
        attachDrag(b, widthPx = { dp(52) }, onTap = { showMenu(dp(56)) }) { x, y -> prefs.bubbleX = x; prefs.bubbleY = y }
        return b
    }

    // ------------------------------------------------------------------ card

    private fun buildCard(): View {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = card(16, panelBg(), stroke = true)
            elevation = dp(6).toFloat()
            layoutParams = FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }
        val pill = TextView(ctx).apply {
            textSize = 12f; setTypeface(typeface, Typeface.BOLD); setPadding(dp(8), dp(3), dp(8), dp(3))
            maxLines = 1
        }
        val head = TextView(ctx).apply {
            textSize = 14f; setTextColor(Color.parseColor("#111827")); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val status = TextView(ctx).apply { textSize = 12f; setTextColor(Color.parseColor("#9CA3AF")); setPadding(0, 0, dp(6), 0) }
        val chev = TextView(ctx).apply { textSize = 15f; setTextColor(Color.parseColor("#6B7280")) }
        header.addView(pill); header.addView(head); header.addView(status); header.addView(chev)
        attachDrag(header, widthPx = { cardW }, onTap = { onHeaderTap() }) { x, y -> app?.let { prefs.saveCardPosition(it, x, y) } }

        val scroll = MaxHeightScroll(ctx).apply { isVerticalScrollBarEnabled = false; visibility = View.GONE }
        val b = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(12)) }
        scroll.addView(b)
        c.addView(header); c.addView(scroll)

        cardView = c; riskPill = pill; headText = head; statusText = status; chevron = chev
        bodyScroll = scroll; body = b
        render()
        return c
    }

    private fun onHeaderTap() {
        val nothingYet = judgment == null && replies.isNullOrEmpty() && !judgeWaiting
        if (nothingYet || (judgment == null && errorMsg != null)) { onManualAnalyze?.invoke(); return }
        setExpanded(!expanded)
    }

    private fun setExpanded(v: Boolean) {
        val s = bodyScroll ?: return
        expanded = v
        if (v) {
            val y = lp?.y ?: dp(92)
            s.maxH = minOf((screenH * 0.5f).roundToInt(), screenH - y - dp(140)).coerceAtLeast(dp(180))
        }
        s.visibility = if (v) View.VISIBLE else View.GONE
        render()
        updateLayout()
    }

    // ---------------------------------------------------------------- gestures

    /** Drag anywhere, tap, or long-press for the menu. [onDrop] gets the final position. */
    private fun attachDrag(v: View, widthPx: () -> Int, onTap: () -> Unit, onDrop: (Int, Int) -> Unit) {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        var moved = false; var longFired = false
        val longPress = Runnable { if (!moved) { longFired = true; showMenu(dp(48)) } }
        v.setOnTouchListener { _, e ->
            val p = lp ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x; startY = p.y; touchX = e.rawX; touchY = e.rawY
                    moved = false; longFired = false
                    v.postDelayed(longPress, 550); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt(); val dy = (e.rawY - touchY).toInt()
                    if (!moved && (abs(dx) > dp(6) || abs(dy) > dp(6))) { moved = true; v.removeCallbacks(longPress) }
                    if (moved) {
                        // Keep clear of the side edges: the extreme edge is MIUI's back-gesture zone.
                        p.x = (startX + dx).coerceIn(dp(4), maxOf(dp(4), screenW - widthPx() - dp(4)))
                        p.y = (startY + dy).coerceIn(dp(24), screenH - dp(120))
                        updateLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    when {
                        longFired -> Unit
                        moved -> onDrop(p.x, p.y)
                        else -> onTap()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> { v.removeCallbacks(longPress); true }
                else -> false
            }
        }
    }

    private fun showMenu(top: Int) {
        val r = root ?: return
        dismissMenu()
        val menu = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = card(12, panelBg(), stroke = true)
            elevation = dp(8).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = FrameLayout.LayoutParams(dp(196), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top }
        }
        if (LiveFrameHub.isLive) {
            menu.addView(menuItem("立即分析这一屏") { dismissMenu(); LiveFrameHub.requestAnalyzeNow() })
            menu.addView(menuItem("停止直播") { dismissMenu(); LiveCaptureService.stop(ctx) })
        } else {
            menu.addView(menuItem("截屏识别一次") { dismissMenu(); onOcrCapture?.invoke() })
        }
        menu.addView(menuItem("把当前会话存为联系人") { dismissMenu(); onSaveContact?.invoke() })
        menu.addView(menuItem("打开设置") { dismissMenu(); openSettings() })
        menu.addView(menuItem("隐藏助手（本次）") { hide() })
        menu.addView(menuItem("取消") { dismissMenu() })
        menuView = menu
        r.addView(menu)
    }

    private fun dismissMenu() { menuView?.let { root?.removeView(it) }; menuView = null }

    private fun menuItem(label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; setTextColor(Color.parseColor("#111827")); textSize = 14f
        setPadding(dp(12), dp(10), dp(12), dp(10)); setOnClickListener { onClick() }
    }

    private fun openSettings() {
        runCatching {
            ctx.startActivity(Intent().setClassName(ctx, "com.jev.probe.SettingsActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (expanded) setExpanded(false)
    }

    // ------------------------------------------------------------ public API

    /** The chat app in the foreground; the card moves to the spot saved for it. */
    fun setApp(pkg: String?) {
        if (pkg == app) return
        app = pkg
        if (mode == Mode.CARD) lp?.let { placeCard(it); updateLayout() }
    }

    /**
     * [title] null = not inside a chat window: show the bubble. Otherwise show the
     * card with whatever it already holds for this conversation (nothing is cleared
     * here — that is [resetForNewConversation]'s job).
     */
    fun showIdle(title: String?) {
        if (!ensureWindow()) return
        setMode(if (title == null) Mode.BUBBLE else Mode.CARD)
        if (mode == Mode.CARD) render()
    }

    /** The conversation changed: drop everything that belonged to the previous one. */
    fun resetForNewConversation() {
        judgment = null; replies = null; replyError = null; errorMsg = null
        judgeWaiting = false; repliesWaiting = false; stopTicker()
        lastFill = null; noteText = null; ctxNotes = 0; ctxHistory = 0
        if (expanded) setExpanded(false)
        if (mode == Mode.CARD) render()
    }

    /** A new analysis round starts. The previous result stays on the card until the new one lands. */
    fun beginRound() {
        if (!ensureWindow()) return
        setMode(Mode.CARD)
        errorMsg = null; replyError = null
        judgeWaiting = true; repliesWaiting = true
        ctxNotes = 0; ctxHistory = 0
        startTicker()
        render()
    }

    /** "换一组" started: only the candidates are being redrafted. */
    fun beginRegenerate() {
        replyError = null
        repliesWaiting = true
        startTicker()
        render()
    }

    /** The candidate texts on the card now, so a redraft can avoid repeating them. */
    fun currentReplyTexts(): List<String> = replies.orEmpty().map { it.text }

    /** How many knowledge notes / history lines went into the pending analysis. */
    fun setContextInfo(notes: Int, history: Int) { ctxNotes = notes; ctxHistory = history }

    /** A caveat line for the card (OCR mode); null clears it. */
    fun setNote(note: String?) { noteText = note }

    /**
     * Take the overlay out of the picture for one screenshot. INVISIBLE, not
     * removed: the window (and everything on it) must survive the round trip.
     */
    fun setHiddenForShot(hidden: Boolean) { root?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE }

    fun showError(msg: String) {
        if (!ensureWindow()) return
        setMode(Mode.CARD)
        errorMsg = msg
        judgeWaiting = false; repliesWaiting = false; stopTicker()
        render()
    }

    fun showJudgment(a: Analysis) {
        judgment = a; errorMsg = null; judgeWaiting = false
        stopTickerIfIdle()
        render(); flashIfCollapsed()
    }

    /** Replies land on their own — whether or not the judgment has arrived yet. */
    fun showReplies(ranked: List<RankedReply>, error: String? = null, onFill: (String) -> Unit) {
        lastFill = onFill
        replies = ranked
        replyError = error
        repliesWaiting = false
        stopTickerIfIdle()
        render()
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun hide() {
        val r = root ?: return
        stopTicker()
        runCatching { wm.removeView(r) }
        root = null; mode = null; menuView = null
        clearCardRefs()
    }

    // ---------------------------------------------------------------- ticker

    private val ticker = object : Runnable {
        override fun run() {
            if (!judgeWaiting && !repliesWaiting) return
            val s = ((SystemClock.elapsedRealtime() - waitStart) / 1000).toInt()
            if (s >= UI_LIMIT_S) { giveUp(); return }
            statusText?.text = if (judgment == null && judgeWaiting) "${s}s" else "更新中 ${s}s"
            waitLabels.forEach { (tv, label) -> tv.text = "$label ${s}s" }
            main.postDelayed(this, 1000)
        }
    }

    private fun startTicker() {
        waitStart = SystemClock.elapsedRealtime()
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    private fun stopTicker() { main.removeCallbacks(ticker) }

    private fun stopTickerIfIdle() { if (!judgeWaiting && !repliesWaiting) stopTicker() }

    /** Nothing came back in time (or the round was dropped): never keep spinning. */
    private fun giveUp() {
        if (judgeWaiting) { judgeWaiting = false; if (judgment == null) errorMsg = "分析超时，点这里重试" }
        if (repliesWaiting) { repliesWaiting = false; if (replies.isNullOrEmpty()) replyError = "生成超时" }
        stopTicker()
        render()
    }

    private fun flashIfCollapsed() {
        val c = cardView ?: return
        if (expanded) return
        c.background = card(16, panelBg(), stroke = true, strokeColor = Color.parseColor("#3A7AFE"), strokeW = 2)
        main.postDelayed({ cardView?.background = card(16, panelBg(), stroke = true) }, 900)
    }

    // --------------------------------------------------------------- rendering

    private fun render() {
        renderHeader()
        if (expanded) renderBody()
    }

    private fun renderHeader() {
        val pill = riskPill ?: return
        val a = judgment
        val lvl = a?.dangerLevel?.score?.roundToInt()
        when {
            lvl != null -> {
                pill.text = "${dangerWord(lvl)} $lvl/${a.dangerLevel!!.maxLevel}"
                pill.setTextColor(Color.WHITE); pill.background = card(10, dangerColor(lvl))
            }
            errorMsg != null -> { pill.text = "出错"; pill.setTextColor(Color.WHITE); pill.background = card(10, Color.parseColor("#DC2626")) }
            else -> { pill.text = "Jev"; pill.setTextColor(Color.WHITE); pill.background = card(10, Color.parseColor("#3A7AFE")) }
        }
        headText?.text = when {
            a?.trueIntent != null -> INTENT[a.trueIntent.choice] ?: a.trueIntent.choice
            errorMsg != null -> errorMsg
            judgeWaiting -> "分析中…"
            a != null -> "已分析"
            else -> "点这里分析当前对话"
        }
        val waiting = judgeWaiting || repliesWaiting
        statusText?.visibility = if (waiting || (a != null && errorMsg != null)) View.VISIBLE else View.GONE
        if (!waiting && a != null && errorMsg != null) statusText?.text = "更新失败"
        val hasContent = a != null || !replies.isNullOrEmpty()
        chevron?.visibility = if (hasContent) View.VISIBLE else View.GONE
        chevron?.text = if (expanded) "▴" else "▾"
    }

    private fun renderBody() {
        val b = body ?: return
        b.removeAllViews()
        waitLabels.clear()
        b.addView(divider())
        b.addView(hint(if (ctxNotes == 0 && ctxHistory == 0) "未用知识库" else "知识库 $ctxNotes 条 · 历史 $ctxHistory 条"))
        noteText?.let { if (it.isNotBlank()) b.addView(hint(it)) }

        val a = judgment
        if (a != null) {
            a.trueIntent?.let {
                b.addView(line("对方真实意图：${INTENT[it.choice] ?: it.choice}", "#111827", 14f, true))
                b.addView(hint("把握 ${(it.confidence * 100).roundToInt()}%"))
            }
            val bits = ArrayList<String>()
            a.sheNeeds?.let { bits.add("要${NEEDS[it.choice] ?: it.choice}") }
            a.bestAction?.let { bits.add(ACTION[it.choice] ?: it.choice) }
            a.shouldReplyNow?.let { bits.add(if (it >= 0.5) "可给实质" else "先别给实质") }
            if (bits.isNotEmpty()) b.addView(line(bits.joinToString("  ·  "), "#374151", 13f))
            a.tensionResolved?.let { if (it >= 0.7) b.addView(line("✓ 紧张已缓解", "#16A34A", 12f)) }
        } else if (judgeWaiting) {
            b.addView(waitHint("分析中"))
        }
        errorMsg?.let { if (a != null) b.addView(line("这一轮更新失败：$it", "#DC2626", 12f)) }

        b.addView(divider())
        val list = replies.orEmpty()
        val ranked = list.size > 1
        b.addView(hint(if (ranked) "候选回复（Jev 排序）" else "候选回复"))
        when {
            list.isNotEmpty() -> list.forEachIndexed { i, r -> b.addView(replyCard(i + 1, r.text, (r.prob * 100).roundToInt(), ranked)) }
            repliesWaiting -> b.addView(waitHint("生成中"))
            else -> {
                val msg = when {
                    replyError == "生成超时" -> "生成超时"
                    replyError != null -> "回复接口出错：$replyError"
                    else -> "（未生成候选回复）"
                }
                b.addView(line(msg, "#DC2626", 12f))
            }
        }
        // Footer: status on the left, "换一组" (or 重试) on the right.
        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val left = TextView(ctx).apply {
            textSize = 12f; setTextColor(Color.parseColor("#9CA3AF"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        if (repliesWaiting && list.isNotEmpty()) { left.text = "换一组中…"; waitLabels.add(left to "换一组中") }
        else left.text = if (list.isNotEmpty()) "${list.size} 条候选" else ""
        footer.addView(left)
        if (!repliesWaiting && !judgeWaiting) {
            footer.addView(pill(if (list.isEmpty()) "重试" else "换一组", primary = false) { onRegenerate?.invoke() })
        }
        b.addView(footer)
    }

    private fun waitHint(label: String): TextView = hint("$label…").also { waitLabels.add(it to label) }

    private fun replyCard(rank: Int, text: String, pct: Int, ranked: Boolean): View {
        val top = rank == 1 && ranked
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = card(12, if (top) Color.parseColor("#EAF1FF") else Color.parseColor("#F3F4F6"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        if (ranked) c.addView(TextView(ctx).apply {
            this.text = "#$rank · ${pct}%"; setTextColor(Color.parseColor("#3A7AFE")); textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        })
        c.addView(TextView(ctx).apply {
            this.text = text; setTextColor(Color.parseColor("#111827")); textSize = 14f
            setPadding(0, dp(3), 0, dp(7)); setLineSpacing(dp(2).toFloat(), 1f)
            setTextIsSelectable(true)
        })
        val btns = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        btns.addView(pill("复制", false) { copy(text) })
        // Fill, then fold the card so the input box + keyboard are visible to review/send.
        btns.addView(pill("填入", true) { lastFill?.invoke(text); if (expanded) setExpanded(false) })
        c.addView(btns)
        return c
    }

    private fun pill(label: String, primary: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 13f; gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (primary) Color.WHITE else Color.parseColor("#3A7AFE"))
        background = card(18, if (primary) Color.parseColor("#3A7AFE") else Color.parseColor("#FFFFFF"), stroke = !primary)
        setPadding(dp(16), dp(6), dp(16), dp(6))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    // --------------------------------------------------------------- helpers

    private fun line(text: String, color: String, size: Float, bold: Boolean = false) =
        TextView(ctx).apply {
            this.text = text; setTextColor(Color.parseColor(color)); textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun hint(text: String) = line(text, "#9CA3AF", 12f)

    private fun divider() = View(ctx).apply {
        setBackgroundColor(Color.parseColor("#1F000000"))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(6); bottomMargin = dp(4)
        }
    }

    private fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("jev_reply", text))
        toast("已复制")
    }

    private fun dangerColor(lvl: Int): Int = when {
        lvl >= 6 -> Color.parseColor("#DC2626")
        lvl >= 3 -> Color.parseColor("#D97706")
        else -> Color.parseColor("#16A34A")
    }

    private fun dangerWord(lvl: Int): String = when {
        lvl >= 8 -> "很危险"
        lvl >= 6 -> "偏危险"
        lvl >= 3 -> "留神"
        else -> "安全"
    }

    /** A ScrollView that wraps its content but never grows past [maxH]. */
    private class MaxHeightScroll(ctx: Context) : ScrollView(ctx) {
        var maxH = Int.MAX_VALUE
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }
    }

    companion object {
        /** The card gives up on a round after this long, whatever the service is doing. */
        private const val UI_LIMIT_S = 40

        private val INTENT = mapOf(
            "confirm_you_care" to "确认你在不在乎", "vent_anger" to "在发泄情绪",
            "request_action" to "要你办事", "seek_explanation" to "要个解释",
            "casual_chat" to "随便聊聊", "close_topic" to "事情过去了")
        private val NEEDS = mapOf(
            "apology" to "道歉", "action" to "具体行动", "explanation" to "解释",
            "care" to "你的在乎", "nothing" to "（不用做什么）")
        private val ACTION = mapOf(
            "check_history" to "翻聊天记录", "apologize" to "先道歉", "give_commitment" to "给承诺",
            "explain" to "解释清楚", "acknowledge" to "接住情绪", "say_less" to "少说两句",
            "make_plan" to "定个安排")
    }
}
