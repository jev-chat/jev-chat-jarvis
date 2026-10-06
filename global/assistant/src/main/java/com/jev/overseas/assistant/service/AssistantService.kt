package com.jev.overseas.assistant.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.app.Dialog
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import com.jev.overseas.assistant.DiagnosticLog
import com.jev.overseas.assistant.MainActivity
import com.jev.overseas.assistant.R
import com.jev.overseas.assistant.Settings
import com.jev.overseas.assistant.ui.PanelView
import com.jev.overseas.assistant.ui.Ui
import com.jev.overseas.core.session.AssistantSession
import com.jev.overseas.core.session.Effect
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * The assistant. Window events are used only to
 * show the bubble while WhatsApp is on screen and hide it otherwise. The chat is
 * read only after the user taps the bubble. Both windows are
 * TYPE_ACCESSIBILITY_OVERLAY, so no "draw over other apps" grant is needed, and
 * they are not part of WhatsApp's node tree, so they never affect what is read.
 */
class AssistantService : AccessibilityService(), PanelView.Host {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var worker: ExecutorService
    private lateinit var settings: Settings
    private lateinit var diagnostics: DiagnosticLog
    private lateinit var wm: WindowManager
    private lateinit var ui: Ui

    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    /** The panel window, while it is on screen. */
    private var panel: Dialog? = null
    /** The panel view of the current round, kept while folded so typed text and open sections survive. */
    private var panelView: PanelView? = null
    private var session: AssistantSession? = null
    /** When the panel was last folded into the bubble with a round kept, for the expiry below. */
    private var foldedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        worker = Executors.newSingleThreadExecutor()
        settings = Settings(this)
        diagnostics = DiagnosticLog(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ui = Ui(ContextThemeWrapper(this, R.style.Theme_Jev))
        Log.i(TAG, "service connected")
        main.postDelayed({ updateBubble() }, 300)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                main.removeCallbacks(checkWindows)
                main.postDelayed(checkWindows, 150)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        removePanel()
        hideBubble()
        if (::worker.isInitialized) worker.shutdownNow()
        super.onDestroy()
    }

    private val checkWindows = Runnable { updateBubble() }

    /** The bubble only exists while a WhatsApp window is on screen; leaving WhatsApp folds everything away. */
    private fun updateBubble() {
        if (!::settings.isInitialized) return
        if (WhatsAppWindow.isShowing(this)) {
            if (panel == null) showBubble()
        } else {
            // Leaving WhatsApp folds the panel; the round is kept for a while (see openPanel).
            if (panel != null) foldPanel(showBubble = false)
            hideBubble()
        }
    }

    // ------------------------------------------------------------------ bubble

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (bubble != null) return
        val size = ui.dp(BUBBLE_DP)
        val view = FrameLayout(ui.context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ui.base)
                setStroke(ui.dp(2), ui.accent)
            }
            // An accent dot means a round is kept and will come back on tap.
            val kept = session?.hasRound == true
            val dot = View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (kept) ui.accent else ui.ok) } }
            addView(dot, FrameLayout.LayoutParams(ui.dp(14), ui.dp(14), Gravity.CENTER))
            contentDescription = getString(R.string.app_name)
            elevation = ui.dp(6).toFloat()
        }
        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (settings.bubbleOnLeft) ui.dp(6) else metrics.widthPixels - size - ui.dp(6)
            y = (metrics.heightPixels * settings.bubbleY).toInt()
        }
        view.setOnTouchListener(BubbleDrag(params, size))
        runCatching { wm.addView(view, params) }
            .onSuccess { bubble = view; bubbleParams = params }
            .onFailure { Log.w(TAG, "bubble failed: ${it.javaClass.simpleName}") }
    }

    private fun hideBubble() {
        val view = bubble ?: return
        bubble = null
        runCatching { wm.removeView(view) }
    }

    /** Drag with the finger; let go and it snaps to the nearer edge. A tap without movement opens the panel. */
    private inner class BubbleDrag(private val params: WindowManager.LayoutParams, private val size: Int) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@AssistantService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        v.performClick()
                        openPanel()
                    } else {
                        val metrics = resources.displayMetrics
                        val left = params.x + size / 2 < metrics.widthPixels / 2
                        params.x = if (left) ui.dp(6) else metrics.widthPixels - size - ui.dp(6)
                        params.y = params.y.coerceIn(0, metrics.heightPixels - size)
                        runCatching { wm.updateViewLayout(v, params) }
                        settings.bubbleOnLeft = left
                        settings.bubbleY = params.y / metrics.heightPixels.toFloat()
                    }
                }
            }
            return true
        }
    }

    // ------------------------------------------------------------------ panel

    private fun openPanel() {
        if (panel != null) return
        main.removeCallbacks(expireRound)
        val existing = session
        // A kept round expires: after a long fold the chat is forgotten (S11) and the next tap starts fresh.
        if (existing != null && existing.hasRound && SystemClock.elapsedRealtime() - foldedAt > KEEP_ROUND_MS) {
            existing.close()
            panelView = null
        }
        val s = existing ?: newSession().also { session = it }
        val view = panelView ?: PanelView(ui.context, s, this).also { panelView = it }
        val dialog = PanelDialog(view)
        runCatching { dialog.show() }
            .onSuccess {
                panel = dialog
                hideBubble()
                view.render(s.current)
                view.requestFocus() // the card, not a text field: no cursor until the user taps one
                s.open(autoAnalyse = settings.analyseWhenOpened)
            }
            .onFailure { Log.w(TAG, "panel failed: ${it.javaClass.simpleName}") }
    }

    /**
     * The card's window. A Dialog rather than a bare view added to the window
     * manager: only a real window gives text fields the system's own selection,
     * copy, cut and paste menu. Still TYPE_ACCESSIBILITY_OVERLAY, not dimmed, not
     * touch-modal; a touch outside folds the card.
     */
    private inner class PanelDialog(private val view: PanelView) : Dialog(ui.context, R.style.Theme_Jev_Panel) {
        init {
            (view.parent as? ViewGroup)?.removeView(view)
            setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setCanceledOnTouchOutside(false)
            window?.apply {
                setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
                setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                attributes = attributes.apply {
                    width = resources.displayMetrics.widthPixels - ui.dp(16)
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    y = statusBarHeight() + ui.dp(4)
                    windowAnimations = android.R.style.Animation_Dialog
                }
            }
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                view.onOutsideTouch()
                return true
            }
            return super.dispatchTouchEvent(ev)
        }

        @Deprecated("Back is handled by the panel")
        override fun onBackPressed() = view.handleBack()
    }

    private fun newSession() = AssistantSession(
        reader = WhatsAppReader(this),
        assistantFor = { settings.assistant() },
        background = worker,
        emit = { state -> main.post { panelView?.render(state) } },
        target = WhatsAppInput(this),
        fillEnabled = true,
        effects = { effect -> main.post { if (effect is Effect.Collapse) foldPanel(showBubble = true) } },
        journal = diagnostics,
        memory = settings.chatMemory,
    )

    private fun removePanel() {
        val dialog = panel ?: return
        panel = null
        runCatching { dialog.dismiss() }
        (panelView?.parent as? ViewGroup)?.removeView(panelView)
    }

    /** Back, the fold button, Fill/Copy and leaving WhatsApp: the panel goes, the round stays. */
    private fun foldPanel(showBubble: Boolean) {
        removePanel()
        foldedAt = SystemClock.elapsedRealtime()
        // The kept round, chat text included, is forgotten on time even if the bubble is never tapped again.
        main.removeCallbacks(expireRound)
        main.postDelayed(expireRound, KEEP_ROUND_MS)
        if (showBubble && WhatsAppWindow.isShowing(this)) showBubble()
    }

    private val expireRound = Runnable {
        if (panel != null) return@Runnable
        session?.takeIf { it.hasRound }?.close()
        panelView = null
        // Redraw the bubble so its dot no longer says a round is kept.
        if (bubble != null) { hideBubble(); updateBubble() }
    }

    /** S11: closing forgets the chat. */
    private fun closePanel(showBubbleAfter: Boolean) {
        session?.close()
        removePanel()
        panelView = null
        if (showBubbleAfter && WhatsAppWindow.isShowing(this)) showBubble()
    }

    // ------------------------------------------------------------------ PanelView.Host

    override fun closePanel() = closePanel(showBubbleAfter = true)

    override fun minimizePanel() = foldPanel(showBubble = true)

    override fun openSettings() {
        closePanel(showBubbleAfter = false)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun shareReport(includeText: Boolean) {
        val s = session
        val report = diagnostics.report(s?.roundId, if (includeText) s?.roundText() else null)
        foldPanel(showBubble = true)
        diagnostics.share(this, report)
    }

    override val jevModel: String get() = settings.jevModel
    override val draftModel: String get() = settings.draftModel

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else ui.dp(24)
    }

    companion object {
        private const val TAG = "JEV"
        private const val BUBBLE_DP = 52
        private const val KEEP_ROUND_MS = 15 * 60 * 1000L
    }
}
