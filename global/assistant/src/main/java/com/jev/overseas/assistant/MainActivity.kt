package com.jev.overseas.assistant

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jev.overseas.assistant.service.AssistantService
import com.jev.overseas.assistant.ui.Ui
import com.jev.overseas.core.net.ModelException
import com.jev.overseas.core.net.OpenRouterGateway
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Setup and settings on one screen: a header, the three setup steps in one card,
 * then cards for the models, replies, diagnostics and about (status and privacy).
 * The page keeps clear of the status bar, the navigation bar and the keyboard.
 */
open class MainActivity : AppCompatActivity() {

    protected lateinit var settings: Settings
    protected lateinit var ui: Ui
    private val worker = Executors.newSingleThreadExecutor()

    /** The whole page inside the scroll view, laid out at full height. */
    protected lateinit var page: LinearLayout

    private lateinit var keyStatus: TextView
    private lateinit var removeKey: TextView
    private lateinit var keyInput: EditText
    private lateinit var testResult: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var keyBadge: FrameLayout
    private lateinit var serviceBadge: FrameLayout
    private lateinit var progress: TextView
    private lateinit var spellingRow: LinearLayout
    private lateinit var autoRow: LinearLayout
    private lateinit var diagRow: LinearLayout
    private lateinit var diagCount: TextView
    private lateinit var diagnostics: DiagnosticLog
    private lateinit var chatsCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        diagnostics = DiagnosticLog(this)
        ui = Ui(this)
        page = ui.column(paddingDp = 16, gapDp = 12)

        page.addView(header())
        page.addView(setupCard(), full())
        page.addView(heading(getString(R.string.settings_heading)))
        page.addView(modelsCard(), full())
        page.addView(repliesCard(), full())
        page.addView(diagnosticsCard(), full())
        page.addView(aboutCard(), full())

        val scroll = ScrollView(this).apply {
            setBackgroundColor(ui.base)
            isFillViewport = true
            addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Edge to edge on Android 15+: keep the page out from under the status bar,
        // the navigation bar and the keyboard.
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ sections

    private fun header(): View = ui.column(gapDp = 6).apply {
        setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4))
        addView(ui.row(8).apply {
            addView(ui.label("●", 13f, ui.ok))
            addView(ui.label("JEV", 20f, ui.text, bold = true).apply { letterSpacing = 0.12f })
            addView(ui.label(getString(R.string.brand_suffix), 20f, ui.secondary))
            addView(ui.spacer())
            addView(ui.tag("v${BuildConfig.VERSION_NAME}", ui.muted))
        }, full())
        addView(ui.label(getString(R.string.app_tagline), 13f, ui.secondary))
    }

    private fun setupCard(): View = ui.card(paddingDp = 14, gapDp = 16).apply {
        progress = ui.label("", 12f, ui.muted)
        addView(ui.row(8).apply {
            addView(ui.label(getString(R.string.setup_title), 17f, ui.text, bold = true))
            addView(ui.spacer())
            addView(progress)
        }, full())

        // 1. Key
        keyBadge = badge()
        keyStatus = ui.label("", 13f, ui.secondary)
        keyInput = field(getString(R.string.key_hint)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        testResult = ui.label("", 13f, ui.secondary).apply { visibility = View.GONE }
        removeKey = ui.label(getString(R.string.remove), 13f, ui.accent).apply {
            setPadding(ui.dp(8), ui.dp(4), 0, ui.dp(4))
            setOnClickListener { settings.removeKey(); showTest(null); refresh() }
        }
        val keyRow = ui.row(8).apply {
            addView(keyStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(removeKey)
        }
        addView(step(keyBadge, getString(R.string.step_key), keyRow, keyInput, ui.row(8).apply {
            addView(ui.weighted(ui.primaryButton(getString(R.string.save)) { saveKey() }))
            addView(ui.weighted(ui.ghostButton(getString(R.string.test_connection), ui.accent) { testConnection() }))
        }, testResult), full())

        // 2. Service
        serviceBadge = badge()
        serviceStatus = ui.label("", 13f, ui.secondary)
        addView(step(serviceBadge, getString(R.string.step_service), serviceStatus,
            ui.ghostButton(getString(R.string.open_accessibility), ui.accent) {
                startActivity(Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            ui.label(getString(R.string.restricted_note), 12f, ui.muted),
        ), full())

        // 3. Try it
        addView(step(numberBadge(3, done = false), getString(R.string.step_try),
            ui.label(getString(R.string.try_note), 13f, ui.secondary)), full())
    }

    private fun modelsCard(): View = card(getString(R.string.models_title)).apply {
        val jev = field(getString(R.string.jev_model)).apply { setText(settings.jevModel) }
        val draft = field(getString(R.string.draft_model)).apply { setText(settings.draftModel) }
        addView(caption(getString(R.string.jev_model)))
        addView(jev, full())
        addView(caption(getString(R.string.draft_model)))
        addView(draft, full())
        addView(ui.row(8).apply {
            addView(ui.weighted(ui.primaryButton(getString(R.string.save)) {
                settings.jevModel = jev.text.toString(); settings.draftModel = draft.text.toString()
                jev.setText(settings.jevModel); draft.setText(settings.draftModel)
            }))
            addView(ui.ghostButton(getString(R.string.reset)) {
                settings.resetModels(); jev.setText(settings.jevModel); draft.setText(settings.draftModel)
            })
        }, full())
    }

    private fun repliesCard(): View = card(getString(R.string.replies_title)).apply {
        addView(caption(getString(R.string.spelling_title)))
        spellingRow = ui.row(8)
        addView(spellingRow)
        addView(caption(getString(R.string.analyse_when_opened)).apply { setPadding(0, ui.dp(6), 0, 0) })
        autoRow = ui.row(8)
        addView(autoRow)
        addView(ui.label(getString(R.string.analyse_when_opened_detail), 12f, ui.muted))
        chatsCount = ui.label("", 12f, ui.secondary)
        addView(chatsCount)
        addView(ui.ghostButton(getString(R.string.forget_chats)) { settings.forgetChats(); refresh() }, full())
    }

    /** A local log the user can attach to a bug report. */
    private fun diagnosticsCard(): View = card(getString(R.string.diagnostics_title)).apply {
        addView(ui.label(getString(R.string.diagnostics_detail), 12f, ui.muted))
        diagRow = ui.row(8)
        addView(diagRow)
        diagCount = ui.label("", 12f, ui.secondary)
        addView(diagCount)
        addView(ui.row(8).apply {
            addView(ui.weighted(ui.ghostButton(getString(R.string.share_report), ui.accent) {
                diagnostics.share(this@MainActivity, diagnostics.report(null, null))
            }))
            addView(ui.ghostButton(getString(R.string.clear_log)) { diagnostics.clear(); diagCount.postDelayed({ refresh() }, 300) })
        }, full())
    }

    private fun aboutCard(): View = card(getString(R.string.about_title)).apply {
        addView(caption(getString(R.string.status_title)))
        addView(ui.label(whatsappVersion(), 13f, ui.secondary))
        addView(ui.label("Jev ${BuildConfig.VERSION_NAME}", 13f, ui.secondary))
        addView(caption(getString(R.string.privacy_title)).apply { setPadding(0, ui.dp(6), 0, 0) })
        addView(ui.label(getString(R.string.privacy_text), 13f, ui.secondary))
    }

    // ------------------------------------------------------------------ state

    protected fun refresh() {
        val masked = maskedKey()
        keyStatus.text = if (masked != null) getString(R.string.key_saved, masked) else getString(R.string.key_missing)
        removeKey.visibility = if (masked != null) View.VISIBLE else View.GONE
        setBadge(keyBadge, 1, masked != null)
        val on = serviceEnabled()
        serviceStatus.text = getString(if (on) R.string.service_on else R.string.service_off)
        serviceStatus.setTextColor(if (on) ui.secondary else ui.warn)
        setBadge(serviceBadge, 2, on)
        val done = listOf(masked != null, on).count { it }
        progress.text = if (done == 2) getString(R.string.setup_ready) else getString(R.string.setup_progress, done)
        progress.setTextColor(if (done == 2) ui.ok else ui.muted)
        spellingRow.removeAllViews()
        for ((value, label) in listOf("AUTO" to R.string.spelling_auto, "US" to R.string.spelling_us, "UK" to R.string.spelling_uk)) {
            spellingRow.addView(ui.chip(getString(label), settings.spelling == value) { settings.spelling = value; refresh() })
        }
        diagRow.removeAllViews()
        diagRow.addView(ui.chip("Keep log", settings.keepDiagnostics) { settings.keepDiagnostics = true; refresh() })
        diagRow.addView(ui.chip("Off", !settings.keepDiagnostics) { settings.keepDiagnostics = false; refresh() })
        diagCount.text = getString(R.string.diagnostics_count, diagnostics.lineCount())
        chatsCount.text = getString(R.string.remembered_chats, settings.rememberedChats)
        autoRow.removeAllViews()
        autoRow.addView(ui.chip("On", settings.analyseWhenOpened) { settings.analyseWhenOpened = true; refresh() })
        autoRow.addView(ui.chip("Off", !settings.analyseWhenOpened) { settings.analyseWhenOpened = false; refresh() })
    }

    /** The saved key as "sk-or-…" plus its last four characters, or null. */
    protected open fun maskedKey(): String? = settings.maskedKey

    protected open fun serviceEnabled(): Boolean {
        val enabled = SystemSettings.Secure.getString(contentResolver, SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, AssistantService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun saveKey() {
        val text = keyInput.text.toString().trim()
        if (text.isEmpty()) return
        settings.saveKey(text)
        keyInput.setText("")
        showTest(null)
        refresh()
    }

    /** Free: asks OpenRouter about the key itself, no model is called. */
    private fun testConnection() {
        val config = settings.modelConfig() ?: run { showTest(getString(R.string.key_missing), ui.warn); return }
        showTest(getString(R.string.testing))
        worker.execute {
            val result = runCatching { OpenRouterGateway(config).keyInfo() }
            runOnUiThread {
                result.onSuccess { info ->
                    val remaining = info.limitRemaining?.let { getString(R.string.key_remaining, money(it)) } ?: ""
                    showTest(getString(R.string.key_ok, money(info.usage), remaining), ui.ok)
                }.onFailure { e ->
                    val reason = (e as? ModelException)?.message ?: e.javaClass.simpleName
                    showTest(getString(R.string.key_failed, reason), ui.warn)
                }
            }
        }
    }

    /** The result line under the key buttons; hidden when there is nothing to say. */
    private fun showTest(message: String?, color: Int = ui.secondary) {
        testResult.text = message ?: ""
        testResult.setTextColor(color)
        testResult.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun money(v: Double) = String.format(Locale.US, "%.4f", v)

    protected open fun whatsappVersion(): String = runCatching {
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo("com.whatsapp", PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") packageManager.getPackageInfo("com.whatsapp", 0)
        }
        getString(R.string.whatsapp_version, info.versionName)
    }.getOrElse { getString(R.string.whatsapp_missing) }

    // ------------------------------------------------------------------ builders

    /** One setup step: a badge on the left, the title and its content on the right. */
    private fun step(badge: View, title: String, vararg content: View): View = ui.row(12).apply {
        gravity = Gravity.TOP
        addView(badge)
        addView(ui.column(gapDp = 8).apply {
            addView(ui.label(title, 15f, ui.text, bold = true).apply { minHeight = ui.dp(24); gravity = Gravity.CENTER_VERTICAL })
            for (view in content) addView(view, full())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun badge(): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(ui.dp(24), ui.dp(24))
    }

    private fun numberBadge(n: Int, done: Boolean): FrameLayout = badge().also { setBadge(it, n, done) }

    /** A green tick when the step is done, otherwise its number in an accent ring. */
    private fun setBadge(badge: FrameLayout, n: Int, done: Boolean) {
        badge.removeAllViews()
        if (done) {
            badge.background = ui.rounded(ui.ok, 12)
            badge.addView(ImageView(this).apply { setImageResource(R.drawable.ic_check); setColorFilter(ui.onAccent) },
                FrameLayout.LayoutParams(ui.dp(14), ui.dp(14), Gravity.CENTER))
        } else {
            badge.background = ui.rounded(0x00000000, 12, ui.accent)
            badge.addView(ui.label(n.toString(), 12f, ui.accent, bold = true).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun card(title: String): LinearLayout = ui.card(paddingDp = 14, gapDp = 10).apply {
        addView(ui.label(title, 15f, ui.text, bold = true))
    }

    private fun heading(title: String): TextView =
        ui.label(title.uppercase(Locale.US), 11f, ui.muted, bold = true).apply {
            letterSpacing = 0.12f
            setPadding(ui.dp(2), ui.dp(8), 0, 0)
        }

    private fun caption(title: String): TextView = ui.label(title, 12f, ui.muted)

    private fun field(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(ui.text)
        setHintTextColor(ui.muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        background = ui.rounded(ui.raised, 10, ui.line)
        setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
        isSingleLine = true
    }

    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
