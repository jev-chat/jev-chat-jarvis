package com.jev.overseas.probe

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.jev.overseas.a11y.NodeCollector
import com.jev.overseas.a11y.RowStability
import com.jev.overseas.core.dump.DumpJson
import com.jev.overseas.core.dump.DumpMode
import com.jev.overseas.core.dump.ScreenDump
import com.jev.overseas.core.dump.ScreenDumpFormat
import com.jev.overseas.probe.dump.Redactor
import com.jev.overseas.probe.dump.TestChatGuard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Read-only probe.
 *
 * What it does: while WhatsApp is in the foreground it shows one small button.
 * A tap copies the accessibility tree of the current screen into a ScreenDump
 * file in this app's private storage, and shows a few counts.
 *
 * What it never does: scroll, take screenshots, run OCR, call a model, use the
 * network (the app has no INTERNET permission), write to an input box, or act on
 * anything by itself. Events are used only to show or hide the button, and for
 * that only the foreground package name is looked at.
 *
 * Message text is stored only when the screen is the scripted test chat (see
 * [TestChatGuard]); anything else is stored as structure without text. Chat text
 * is never written to logcat.
 */
class ProbeService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var panel: LinearLayout? = null
    private var status: TextView? = null
    private var busy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "probe connected")
        main.postDelayed({ updateButton() }, 500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> updateButton()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hidePanel()
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------ button

    private fun updateButton() {
        val foreground = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (foreground == TARGET_PACKAGE) showPanel() else if (foreground != null && foreground != packageName) hidePanel()
    }

    private fun showPanel() {
        if (panel != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val button = TextView(this).apply {
            text = getString(R.string.button_read)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(0xE61B5E20.toInt())
            setOnClickListener { onReadTapped() }
        }
        val statusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(0xE6212121.toInt())
            maxWidth = dp(220)
            visibility = View.GONE
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(button)
            addView(statusView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not focusable: WhatsApp stays the active window while the button is tapped.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = (resources.displayMetrics.heightPixels * 0.22f).toInt()
        }
        runCatching { wm.addView(container, params) }
            .onSuccess { panel = container; status = statusView }
            .onFailure { Log.w(TAG, "overlay failed: ${it.javaClass.simpleName}") }
    }

    private fun hidePanel() {
        val view = panel ?: return
        panel = null
        status = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view) }
    }

    private fun showStatus(message: String) {
        val view = status ?: return
        view.text = message
        view.visibility = View.VISIBLE
        main.removeCallbacks(hideStatus)
        main.postDelayed(hideStatus, 8000)
    }

    private val hideStatus = Runnable { status?.visibility = View.GONE }

    // ------------------------------------------------------------ read

    private fun onReadTapped() {
        if (busy) return
        val root = targetRoot()
        if (root == null) { showStatus(getString(R.string.status_not_whatsapp)); return }
        busy = true
        showStatus(getString(R.string.status_reading))
        val windows = describeWindows()
        worker.execute {
            val outcome = runCatching { capture(root, windows) }
            main.post {
                busy = false
                outcome.onSuccess { showStatus(it) }
                    .onFailure {
                        Log.w(TAG, "capture failed: ${it.javaClass.simpleName}")
                        showStatus(getString(R.string.status_failed, it.javaClass.simpleName))
                    }
            }
        }
    }

    /** The WhatsApp application window, or null when WhatsApp is not the active app. */
    private fun targetRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active?.packageName?.toString() == TARGET_PACKAGE) return active
        return null
    }

    private fun capture(root: AccessibilityNodeInfo, windows: List<Map<String, Any?>>): String {
        val started = SystemClock.elapsedRealtime()
        val collected = NodeCollector.collect(root, resources)
        val raw = collected.nodes
        val metrics = resources.displayMetrics

        val conversationPage = raw.any { it.isEditable }
        val listTexts = raw.filter { it.inScrollable }
            .flatMap { listOfNotNull(it.text, it.contentDescription) }
        val guard = TestChatGuard.evaluate(listTexts)
        val mode = if (conversationPage && guard.passed) DumpMode.FULL else DumpMode.STRUCTURE
        val redaction = Redactor.redact(raw, mode, metrics.heightPixels)

        val scrollables = raw.filter { it.isScrollable }
        val mainList = RowStability.mainList(raw)
        val rows = mainList?.childCount ?: 0
        val textNodes = raw.count { it.inScrollable && it.textLength > 0 }

        val stability = RowStability.measure(root, raw, mainList)

        val number = nextNumber()
        val meta = linkedMapOf<String, Any?>(
            "schema" to ScreenDumpFormat.SCHEMA,
            "number" to number,
            "capturedAt" to isoNow(),
            "mode" to mode.name.lowercase(),
            "gate" to linkedMapOf(
                "conversationPage" to conversationPage,
                "testChatSpecificMatches" to guard.specificMatches,
                "testChatGenericMatches" to guard.genericMatches,
            ),
            "redaction" to linkedMapOf(
                "topBarMasks" to redaction.maskCount,
                "replacements" to redaction.replacements,
            ),
            "counts" to linkedMapOf(
                "nodes" to raw.size,
                "truncated" to collected.truncated,
                "scrollables" to scrollables.size,
                "mainListIndex" to mainList?.index,
                "mainListRows" to rows,
                "textNodesInScrollable" to textNodes,
            ),
            "stability" to stability,
            "target" to targetInfo(),
            "probe" to linkedMapOf("versionName" to BuildConfig.VERSION_NAME),
            "device" to linkedMapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "sdk" to Build.VERSION.SDK_INT,
                "release" to Build.VERSION.RELEASE,
                "display" to Build.DISPLAY,
            ),
            "screen" to linkedMapOf(
                "width" to metrics.widthPixels,
                "height" to metrics.heightPixels,
                "densityDpi" to metrics.densityDpi,
                "fontScale" to resources.configuration.fontScale,
                "locale" to resources.configuration.locales.get(0).toLanguageTag(),
            ),
            "windows" to windows,
            "elapsedMs" to (SystemClock.elapsedRealtime() - started),
        )
        val file = File(dumpDir(), "dump-%04d.json".format(Locale.US, number))
        file.writeText(DumpJson.encode(ScreenDump(meta, redaction.nodes)))
        // Counts only: chat text never goes to logcat.
        Log.i(TAG, "saved #$number mode=$mode nodes=${raw.size} rows=$rows scrollables=${scrollables.size}")

        return if (mode == DumpMode.FULL) {
            getString(R.string.status_saved_full, number, rows, textNodes, scrollables.size)
        } else {
            val reason = getString(
                if (!conversationPage) R.string.reason_not_chat_page else R.string.reason_not_test_chat
            )
            getString(R.string.status_saved_structure, number, reason, rows, scrollables.size)
        }
    }

    /** Window list for the header: ids, types and bounds only, no titles. */
    private fun describeWindows(): List<Map<String, Any?>> = runCatching {
        windows.map { w ->
            val b = Rect().also { w.getBoundsInScreen(it) }
            linkedMapOf<String, Any?>(
                "id" to w.id,
                "type" to w.type,
                "layer" to w.layer,
                "active" to w.isActive,
                "focused" to w.isFocused,
                "bounds" to listOf(b.left, b.top, b.right, b.bottom),
                "package" to runCatching { w.root?.packageName?.toString() }.getOrNull(),
            )
        }
    }.getOrDefault(emptyList())

    private fun targetInfo(): Map<String, Any?> {
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(TARGET_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(TARGET_PACKAGE, 0)
            }
        }.getOrNull()
        return linkedMapOf(
            "package" to TARGET_PACKAGE,
            "versionName" to info?.versionName,
            "versionCode" to info?.longVersionCode,
        )
    }

    private fun dumpDir(): File = File(filesDir, DUMP_DIR).apply { mkdirs() }

    private fun nextNumber(): Int {
        val existing = dumpDir().list().orEmpty()
            .mapNotNull { Regex("""dump-(\d+)\.json""").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
        return (existing.maxOrNull() ?: 0) + 1
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
    }

    companion object {
        private const val TAG = "JEVPROBE"
        const val TARGET_PACKAGE = "com.whatsapp"
        const val DUMP_DIR = "dumps"
    }
}
