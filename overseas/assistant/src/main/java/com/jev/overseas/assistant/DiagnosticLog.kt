package com.jev.overseas.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.jev.overseas.core.diagnostics.Diagnostics
import com.jev.overseas.core.diagnostics.Journal
import com.jev.overseas.core.json.MiniJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * The local diagnostic log (docs/DIAGNOSTICS.md). One JSON line per
 * event in app-private storage, the newest [KEEP] lines kept. Nothing leaves the
 * phone unless the user shares a report. Events never carry chat text; see
 * [Diagnostics].
 */
class DiagnosticLog(context: Context) : Journal {

    private val app = context.applicationContext
    private val settings = Settings(app)
    private val file = File(app.filesDir, "diagnostics/journal.jsonl")
    private val writer = Executors.newSingleThreadExecutor()

    override fun record(event: Map<String, Any?>) {
        if (!settings.keepDiagnostics) return
        val line = MiniJson.encode(linkedMapOf<String, Any?>("at" to now()).apply { putAll(event) })
        writer.execute {
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(line + "\n")
                trimIfNeeded()
            }
        }
    }

    fun clear() = writer.execute { runCatching { file.delete() } }

    fun lineCount(): Int = runCatching { file.readLines().size }.getOrDefault(0)

    /**
     * A Markdown report for a GitHub issue: versions, settings that matter, then
     * the most recent events. [roundText] is added only when the user ticked
     * "include the goal and replies".
     */
    fun report(roundId: String?, roundText: Map<String, Any?>?): String {
        val events = runCatching { file.readLines() }.getOrDefault(emptyList()).takeLast(REPORT_LINES)
        return buildString {
            append("### Jev diagnostic report\n\n")
            append("| | |\n|---|---|\n")
            append("| Schema | ${Diagnostics.SCHEMA} |\n")
            append("| App | ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}) |\n")
            append("| Android | ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL} |\n")
            append("| WhatsApp | ${whatsappVersion() ?: "not installed"} |\n")
            append("| Models | ${settings.jevModel}, ${settings.draftModel} |\n")
            append("| Spelling | ${settings.spelling} |\n")
            append("| Analyse when opened | ${settings.analyseWhenOpened} |\n")
            if (roundId != null) append("| Round in question | $roundId |\n")
            append("| Created | ${now()} |\n\n")
            append("Events: ${events.size} most recent. They hold ids, counts, scores, timings and error kinds; no chat text.\n\n")
            append("```jsonl\n")
            events.forEach { append(it).append('\n') }
            append("```\n")
            if (roundText != null) {
                append("\nThe user chose to include this round's goal and replies (not the chat):\n\n```json\n")
                append(MiniJson.encode(roundText)).append("\n```\n")
            }
        }
    }

    /** Opens the system share sheet with the report as text (copy, email, a GitHub app…). */
    fun share(context: Context, report: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Jev diagnostic report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(send, "Share Jev report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun trimIfNeeded() {
        if (file.length() < TRIM_BYTES) return
        val lines = file.readLines()
        if (lines.size > KEEP) file.writeText(lines.takeLast(KEEP).joinToString("\n", postfix = "\n"))
    }

    private fun whatsappVersion(): String? = runCatching {
        val pm = app.packageManager
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo("com.whatsapp", PackageManager.PackageInfoFlags.of(0)).versionName
        else @Suppress("DEPRECATION") pm.getPackageInfo("com.whatsapp", 0).versionName
    }.getOrNull()

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    companion object {
        const val KEEP = 1000
        const val REPORT_LINES = 300
        private const val TRIM_BYTES = 600_000L
    }
}
