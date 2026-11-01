package com.jev.probe.capture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Prefs
import com.jev.probe.core.kb.ChatContext
import com.jev.probe.core.kb.ContextBuilder
import com.jev.probe.core.kb.KbStore
import com.jev.probe.jev.JevClient
import com.jev.probe.overlay.OverlayController
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The shared "we have a [ChatSnapshot]" pipeline: session identity,
 * [NewMessageGate], auto-analyze, in-flight cancel, Jev judge + replies,
 * knowledge context, overlay, "换一组", save-contact.
 *
 * [ChatCaptureService] (accessibility) and live-frame capture both call this
 * so the two paths cannot drift. Fill-into-input stays with the accessibility
 * service via [fillInput]; live mode falls back to the clipboard.
 */
internal class AnalysisCoordinator(
    private val appCtx: Context,
    private val prefs: Prefs,
    private val overlay: OverlayController,
    private val extraCurrent: (ConversationSession.Token) -> Boolean = { true },
    private val fillInput: ((ConversationSession.Token, String) -> Unit)? = null,
) {
    enum class Mode { TREE, OCR, LIVE, MANUAL }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private val analysisPool = Executors.newCachedThreadPool { r ->
        Thread(r, "jev-http").apply { isDaemon = true }
    }

    private val session = ConversationSession()
    private val gate = NewMessageGate()

    var currentSnapshot: ChatSnapshot? = null
        private set
    val target: ConversationSession.Target? get() = session.target

    private var lastAnalyzed: ChatSnapshot? = null
    private var lastCtx: ChatContext? = null
    private var lastSignature: String = ""
    private var activePkg: String? = null
    private var lastPkg: String = ""
    private var analyzing = false
    private var regenerating = false
    private var destroyed = false
    private var pendingSnapshot: ChatSnapshot? = null
    private val analysisTasks = ArrayList<Future<*>>()

    private val debounce = Runnable { runAnalysis() }

    fun token(): ConversationSession.Token? = session.token()
    fun accepts(token: ConversationSession.Token): Boolean = session.accepts(token)

    fun attachOverlay() {
        overlay.onManualAnalyze = {
            currentSnapshot?.let {
                pendingSnapshot = it
                runAnalysis()
            }
        }
        overlay.onRegenerate = { regenerateReplies() }
        overlay.onSaveContact = { saveContact(lastPkg) }
    }

    fun observeTarget(next: ConversationSession.Target?): Boolean {
        if (session.observe(next)) {
            cancelAnalysis()
            currentSnapshot = null
            activePkg = null
            lastSignature = ""
            gate.reset()
            lastAnalyzed = null
            lastCtx = null
            return true
        }
        return false
    }

    fun leaveConversation() {
        observeTarget(null)
        cancelAnalysis()
        currentSnapshot = null
    }

    fun cancelAnalysis() {
        session.invalidate()
        main.removeCallbacks(debounce)
        pendingSnapshot = null
        analyzing = false
        regenerating = false
        analysisTasks.forEach { it.cancel(true) }
        analysisTasks.clear()
        overlay.resetForNewConversation()
    }

    fun cancelInFlight() {
        session.invalidate()
        main.removeCallbacks(debounce)
        analyzing = false
        regenerating = false
        analysisTasks.forEach { it.cancel(true) }
        analysisTasks.clear()
    }

    /**
     * @param bindSession when true (live path), session identity is pkg +
     *        window + title from this snapshot. Accessibility already bound
     *        the session from the node tree and must pass false — a transient
     *        OCR title must not unbind a verified chat.
     */
    fun ingest(
        snapshot: ChatSnapshot,
        pkg: String,
        windowId: Int,
        mode: Mode,
        bindSession: Boolean = false,
    ) {
        if (destroyed || !prefs.enabled) return
        lastPkg = pkg
        if (bindSession) {
            observeTarget(
                ConversationSession.Target(pkg, windowId, snapshot.title, snapshot.signature()),
            )
        }
        if (snapshot.messages.isEmpty()) {
            if (mode == Mode.MANUAL) overlay.showError("这一屏没认出文字")
            return
        }
        if (!prefs.isAllowed(snapshot.title)) {
            leaveConversation()
            overlay.hide()
            return
        }
        overlay.setApp(pkg.ifEmpty { null })
        if (pkg.isNotEmpty() && pkg != activePkg) {
            activePkg = pkg
            lastSignature = ""
        }
        currentSnapshot = snapshot
        val sig = snapshot.signature()
        val showing = overlay.isShowing()
        val force = mode == Mode.MANUAL
        if (!force) {
            when (mode) {
                Mode.TREE -> if (sig == lastSignature && showing) return
                Mode.OCR, Mode.LIVE -> {
                    if (sig == lastSignature) {
                        if (!showing) overlay.showIdle(snapshot.title)
                        return
                    }
                }
                Mode.MANUAL -> Unit
            }
        }
        lastSignature = sig
        val verdict = gate.observe(snapshot.messages)
        if (!force) {
            when (verdict) {
                NewMessageGate.Verdict.NOTHING_NEW, NewMessageGate.Verdict.NEW_FROM_ME -> {
                    overlay.setNote(snapshot.note)
                    overlay.showIdle(snapshot.title)
                    return
                }
                NewMessageGate.Verdict.FIRST_LOOK, NewMessageGate.Verdict.NEW_FROM_OTHER -> Unit
            }
        }
        Log.d(
            TAG,
            "snapshot[$pkg] titleLen=${snapshot.title?.length ?: 0} n=${snapshot.messages.size} " +
                snapshot.messages.takeLast(6).joinToString(" | ") { "${it.side}:${it.text.length}" },
        )
        val autoOn = when (mode) {
            Mode.OCR -> prefs.ocrAutoAnalyze && prefs.autoAnalyze
            Mode.TREE, Mode.LIVE -> prefs.autoAnalyze
            Mode.MANUAL -> true
        }
        if (!force && (snapshot.latestFrom != "other" || !autoOn)) {
            overlay.setNote(snapshot.note)
            overlay.showIdle(snapshot.title)
            return
        }
        cancelInFlight()
        pendingSnapshot = snapshot
        main.removeCallbacks(debounce)
        if (mode == Mode.TREE) {
            main.postDelayed(debounce, TREE_DEBOUNCE_MS)
        } else {
            runAnalysis()
        }
    }

    fun regenerateReplies() {
        if (analyzing || regenerating || destroyed || !prefs.enabled) return
        val snapshot = lastAnalyzed ?: currentSnapshot ?: return
        val token = session.token() ?: return
        if (!isCurrent(token)) return
        if (!prefs.hasKey()) {
            overlay.showError("未设置判断接口密钥，去设置里填")
            return
        }
        regenerating = true
        val avoid = overlay.currentReplyTexts()
        overlay.beginRegenerate()
        val client = JevClient(prefs)
        val rel = prefs.relationship
        val ctx = lastCtx
        val count = prefs.candidateCount
        submitAnalysis {
            var replyError: String? = null
            val ranked = try {
                withHardTimeout(REPLY_TIMEOUT_MS, "回复接口") {
                    client.draftAndRank(snapshot, rel, ctx, count, avoid)
                }
            } catch (e: Exception) {
                replyError = replyErrorText(e)
                Log.w(TAG, "redraft failed: $replyError")
                emptyList()
            }
            main.post {
                regenerating = false
                if (isCurrent(token)) overlay.showReplies(ranked, replyError) { text -> fill(token, text) }
            }
        }
    }

    fun saveContact(pkg: String) {
        val title = currentSnapshot?.title
        when {
            title.isNullOrBlank() -> overlay.toast("当前会话没有标题，存不了")
            isTransientTitle(title) -> overlay.toast("当前会话标题还没加载出来，稍后再试")
            else -> submit {
                val msg = try {
                    KbStore.get(appCtx).saveOrMergeContact(title, pkg)
                } catch (e: Exception) {
                    "保存失败：${e.javaClass.simpleName}"
                }
                main.post { overlay.toast(msg) }
            }
        }
    }

    fun destroy() {
        destroyed = true
        leaveConversation()
        main.removeCallbacksAndMessages(null)
        overlay.onManualAnalyze = null
        overlay.onRegenerate = null
        overlay.onSaveContact = null
        worker.shutdownNow()
        analysisPool.shutdownNow()
    }

    private fun runAnalysis() {
        val snapshot = pendingSnapshot ?: return
        if (analyzing || destroyed || !prefs.enabled) {
            if (analyzing) Log.w(TAG, "round skipped: previous round still in flight")
            return
        }
        val previous = session.token() ?: return
        if (!isCurrent(previous)) return
        if (!prefs.hasKey()) {
            overlay.showError("未设置判断接口密钥，去设置里填")
            return
        }
        val token = session.begin() ?: return
        analyzing = true
        overlay.beginRound()
        overlay.setNote(snapshot.note)
        val count = prefs.candidateCount
        val client = JevClient(prefs)
        val rel = prefs.relationship
        submitAnalysis {
            val ctx = try {
                ContextBuilder.build(appCtx, snapshot, token.target.pkg, prefs)
            } catch (e: Exception) {
                Log.w(TAG, "context build failed: ${e.javaClass.simpleName}"); null
            }
            main.post {
                if (!isCurrent(token)) return@post
                lastAnalyzed = snapshot
                lastCtx = ctx
                overlay.setContextInfo(ctx?.notes?.size ?: 0, ctx?.history?.size ?: 0)
                var remaining = 2
                fun completed() {
                    remaining--
                    if (remaining == 0) {
                        analyzing = false
                        analysisTasks.clear()
                    }
                }
                submitAnalysis {
                    val judgment = try {
                        withHardTimeout(JUDGE_TIMEOUT_MS, "判断接口") { client.judge(snapshot, rel, ctx) }
                    } catch (e: Exception) {
                        Log.w(TAG, "judge round failed: ${e.message}")
                        null
                    }
                    main.post {
                        if (isCurrent(token)) {
                            when {
                                judgment == null -> overlay.showError("判断接口超时，请重试")
                                judgment.error != null -> overlay.showError(judgment.error)
                                else -> overlay.showJudgment(judgment)
                            }
                        }
                        completed()
                    }
                }
                submitAnalysis {
                    var replyError: String? = null
                    val t0 = SystemClock.elapsedRealtime()
                    val ranked = try {
                        withHardTimeout(REPLY_TIMEOUT_MS, "回复接口") {
                            client.draftAndRank(snapshot, rel, ctx, count)
                        }
                    } catch (e: Exception) {
                        replyError = replyErrorText(e)
                        Log.w(TAG, "reply failed after ${SystemClock.elapsedRealtime() - t0}ms: $replyError")
                        emptyList()
                    }
                    if (replyError == null) {
                        Log.i(TAG, "reply ok after ${SystemClock.elapsedRealtime() - t0}ms ranked=${ranked.size}")
                    }
                    main.post {
                        if (isCurrent(token)) {
                            overlay.showReplies(ranked, replyError) { text -> fill(token, text) }
                        }
                        completed()
                    }
                }
            }
        }
    }

    private fun isCurrent(token: ConversationSession.Token): Boolean {
        if (destroyed || !prefs.enabled || !session.accepts(token)) return false
        return extraCurrent(token)
    }

    private fun fill(token: ConversationSession.Token, text: String) {
        val custom = fillInput
        if (custom != null) {
            custom(token, text)
            return
        }
        val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("jev_reply", text))
        overlay.toast("已复制，长按输入框粘贴")
    }

    private fun <T> withHardTimeout(ms: Long, what: String, body: () -> T): T {
        val f = analysisPool.submit(Callable { body() })
        return try {
            f.get(ms, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            f.cancel(true)
            throw java.io.IOException("$what 超时（${ms / 1000}秒），请检查网络后重试")
        }
    }

    private fun submitAnalysis(task: () -> Unit) {
        try { analysisTasks.add(worker.submit(task)) } catch (_: RejectedExecutionException) { }
    }

    private fun submit(task: () -> Unit) {
        try { worker.execute(task) } catch (_: RejectedExecutionException) { }
    }

    private fun replyErrorText(e: Exception): String {
        val m = e.message ?: e.javaClass.simpleName
        return if (m.contains("超时")) "生成超时" else m
    }

    companion object {
        private const val TAG = "JEVASSIST"
        const val LIVE_PKG = "live"
        const val LIVE_WINDOW_ID = 0
        private const val JUDGE_TIMEOUT_MS = 30_000L
        private const val REPLY_TIMEOUT_MS = 30_000L
        private const val TREE_DEBOUNCE_MS = 800L
    }
}

/** Placeholder titles an app shows while a chat page is still connecting. */
internal fun isTransientTitle(t: String?): Boolean {
    val trimmed = t?.trim()?.removeSuffix("…")?.removeSuffix("...")?.trim()
    if (trimmed.isNullOrEmpty()) return true
    val lower = trimmed.lowercase()
    return TRANSIENT_TITLE_WORDS.any { lower.contains(it.lowercase()) }
}

private val TRANSIENT_TITLE_WORDS = listOf(
    "连接中", "正在连接", "未连接", "Connecting",
    "加载中", "Loading", "同步中", "Syncing",
)
