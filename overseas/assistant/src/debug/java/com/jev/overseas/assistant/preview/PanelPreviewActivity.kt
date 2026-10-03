package com.jev.overseas.assistant.preview

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.jev.overseas.assistant.ui.PanelView
import com.jev.overseas.assistant.ui.Ui
import com.jev.overseas.core.engine.Assistant
import com.jev.overseas.core.engine.ChatMessage
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.Sender
import com.jev.overseas.core.net.Answer
import com.jev.overseas.core.net.ChatResult
import com.jev.overseas.core.net.DecisionsResult
import com.jev.overseas.core.net.ModelConfig
import com.jev.overseas.core.net.ModelException
import com.jev.overseas.core.net.ModelGateway
import com.jev.overseas.core.net.Usage
import com.jev.overseas.core.scene.FriendsScene
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared
import com.jev.overseas.core.scene.WorkScene
import com.jev.overseas.core.session.AssistantSession
import com.jev.overseas.core.session.BoxState
import com.jev.overseas.core.session.ChatReader
import com.jev.overseas.core.session.ReadReport
import com.jev.overseas.core.session.ReadResult
import com.jev.overseas.core.session.ReplyTarget
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Debug only. Shows the real panel with scripted
 * model answers, so every state can be checked without WhatsApp, a key or cost.
 * Pick a scenario from the row at the top, or start one directly:
 * `adb shell am start -n com.jev.overseas/com.jev.overseas.assistant.preview.PanelPreviewActivity --es scenario results`
 *
 * Add `--ez snapshot true` to also draw the panel alone, without the status bar or
 * anything the system draws on top, to `files/snapshots/<scenario>.png` (used for
 * the README images):
 * `adb exec-out run-as com.jev.overseas cat files/snapshots/results.png > results.png`
 */
class PanelPreviewActivity : Activity(), PanelView.Host {

    private lateinit var ui: Ui
    private lateinit var holder: FrameLayout
    private var panel: PanelView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        val root = ui.column(paddingDp = 8, gapDp = 8).apply { setBackgroundColor(0xFF1B1F27.toInt()) }
        val chips = ui.row(6)
        for (name in SCENARIOS) chips.addView(ui.chip(name, false) { show(name) })
        root.addView(HorizontalScrollView(this).apply { addView(chips) })
        holder = FrameLayout(this)
        root.addView(holder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Edge to edge on Android 15+: keep the scenario row clear of the status bar.
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(ui.dp(8), bars.top + ui.dp(8), ui.dp(8), bars.bottom + ui.dp(8))
            insets
        }
        setContentView(root)
        val first = intent.getStringExtra("scenario") ?: "results"
        show(first)
        if (intent.getBooleanExtra("snapshot", false)) {
            holder.postDelayed({ panel?.let { snapshot(it, first) } }, SNAPSHOT_DELAY_MS)
        }
    }

    private fun snapshot(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File(filesDir, "snapshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(name: String) {
        holder.removeAllViews()
        val script = Script(name)
        val executor: Executor = if (name == "checking") Executors.newSingleThreadExecutor() else Executor { it.run() }
        lateinit var view: PanelView
        val session = AssistantSession(
            reader = script.reader,
            assistantFor = { if (name == "nokey") null else Assistant(script.gateway) },
            background = executor,
            emit = { state -> runOnUiThread { view.render(state) } },
            target = script.target,
            initialSelection = when (name) {
                "picker" -> null
                "boundary" -> Selection(Scene.FRIENDS, FriendsScene.REGULAR)
                else -> Selection(Scene.WORK, WorkScene.SENIOR)
            },
        )
        view = PanelView(this, session, this)
        panel = view
        holder.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.render(session.current)
        script.drive(session, view)
    }

    /** Scripted chat, model answers and the steps that lead to one state. */
    private inner class Script(val name: String) {
        var chat = conversation(
            "other" to "Hi, have you got a minute?",
            "self" to "Sure, go ahead.",
            "other" to "Can you take the Q3 deck?",
            "other" to "Wednesday if possible",
            "other" to "Let me know today",
        )
        val reader = object : ChatReader {
            override fun readCurrent(): ReadResult = when (name) {
                "notchat" -> ReadResult.NotConversation
                else -> ReadResult.Ok(chat, ReadReport(chat.messages.size, chat.latestTurn.size), "demo", chat.messages.joinToString { it.text }.hashCode().toString())
            }
        }
        val target = object : ReplyTarget {
            override fun boxState() = BoxState.EMPTY
            override fun fill(text: String) = true
            override fun copy(text: String) = Unit
        }
        val gateway = object : ModelGateway {
            override fun decisions(state: Map<String, Any?>, questions: Map<String, Any?>): DecisionsResult {
                if (name == "error") throw ModelException(ModelException.Kind.AUTH, 401, "HTTP 401: No auth credentials found")
                val reply = state["candidate_reply"] as String?
                if (reply != null && name == "checking") Thread.sleep(60_000)
                return if (reply == null) analysis(questions) else check(reply, questions)
            }

            override fun chat(system: String, user: String, temperature: Double, maxTokens: Int, json: Boolean): ChatResult {
                val (a, b) = when (name) {
                    "blocked" -> "Sure, I'll have it to you first thing Wednesday." to "Of course, Wednesday works and I'll loop Priya in too."
                    else -> "Yes, I can take it on. I can't do Wednesday though, Thursday is the earliest I can get it to you." to
                        "Happy to help with this. Wednesday is too tight, but I'll have it with you first thing Thursday."
                }
                return ChatResult("""{"replies":[{"label":"x","text":"$a"},{"label":"y","text":"$b"}]}""", USAGE)
            }
        }

        private fun analysis(questions: Map<String, Any?>): DecisionsResult {
            val yes = when (name) {
                "boundary" -> mapOf("K09" to 0.95)
                else -> mapOf("W01" to 0.93, "W10" to 0.88, "W11" to 0.41, "tone.impatience" to 0.35)
            }
            val friction = when (name) {
                "threat" -> listOf(0.0, 0.0, 0.0, 0.0, 0.1, 0.9)
                else -> listOf(0.2, 0.45, 0.3, 0.05, 0.0, 0.0)
            }
            return answers(questions, { yes[it] ?: 0.04 }, { id, size ->
                if (id == Shared.FRICTION_OTHER_ID) score(friction) else score(List(size) { if (it == 0) 1.0 else 0.0 })
            })
        }

        private fun check(reply: String, questions: Map<String, Any?>): DecisionsResult {
            val risky = reply.contains("first thing")
            return answers(questions, { id ->
                when {
                    name == "blocked" && id == "new_commitment" -> 0.86
                    name == "blocked" && id == "commits_others" && reply.contains("Priya") -> 0.91
                    risky && id == "new_commitment" -> 0.55
                    id == "new_commitment" -> 0.11
                    id.startsWith("include.") || id == "acknowledges" -> 0.93
                    else -> 0.04
                }
            }, { id, _ ->
                if (id == "g") score(if (risky) listOf(0.0, 0.0, 0.02, 0.06, 0.6, 0.32) else listOf(0.0, 0.0, 0.0, 0.04, 0.44, 0.52))
                else score(if (risky) listOf(0.0, 0.0, 0.0, 0.1, 0.62, 0.28) else listOf(0.0, 0.0, 0.0, 0.18, 0.74, 0.08))
            })
        }

        fun drive(session: AssistantSession, view: PanelView) {
            when (name) {
                "picker" -> session.open(autoAnalyse = true)
                "nokey", "notchat", "error", "threat", "boundary" -> session.analyse()
                "checking" -> Thread {
                    session.analyse()
                    while (session.current.phase != com.jev.overseas.core.session.Phase.ANALYSED) Thread.sleep(50)
                    runOnUiThread {
                        session.chooseStance(session.current.groups.first().options.first { it.id == "agree_condition" })
                        session.setDetails("Thursday works, Wednesday doesn't")
                        session.draft()
                    }
                }.start()
                "decide" -> {
                    session.analyse()
                    session.current.groups.firstOrNull()?.options?.firstOrNull { it.id == "agree_condition" }?.let(session::chooseStance)
                }
                else -> {
                    session.analyse()
                    val option = session.current.groups.first().options.first { it.id == "agree_condition" }
                    session.chooseStance(option)
                    session.setDetails("Thursday works, Wednesday doesn't")
                    session.draft()
                    when (name) {
                        "expanded" -> session.setExpanded(true)
                        "breakdown" -> view.showBreakdown(0)
                        "changed" -> {
                            chat = conversation(
                                "other" to "Hi, have you got a minute?", "self" to "Sure, go ahead.",
                                "other" to "Can you take the Q3 deck?", "other" to "Wednesday if possible",
                                "other" to "Let me know today", "other" to "Actually Thursday is fine too",
                            )
                            session.copy(0)
                        }
                        "close" -> {}
                    }
                }
            }
        }
    }

    private fun conversation(vararg m: Pair<String, String>) =
        Conversation(m.map { ChatMessage(if (it.first == "self") Sender.USER else Sender.OTHER, it.second) })

    private fun score(p: List<Double>) = Answer.Score(p.withIndex().sumOf { it.index * it.value }, p, 0.8)

    private fun answers(
        questions: Map<String, Any?>,
        noul: (String) -> Double,
        score: (String, Int) -> Answer.Score,
    ): DecisionsResult {
        val out = LinkedHashMap<String, Answer>()
        for ((id, q) in questions) {
            val m = q as Map<*, *>
            when (m["type"]) {
                "noul" -> out[id] = Answer.Noul(noul(id))
                "score" -> out[id] = score(id, (m["criteria"] as List<*>).size)
            }
        }
        return DecisionsResult(out, USAGE, "demo")
    }

    // ------------------------------------------------------------------ PanelView.Host

    override fun closePanel() = finish()
    override fun minimizePanel() = finish()
    override fun shareReport(includeText: Boolean) = Unit
    override fun openSettings() = Unit
    override val jevModel: String = ModelConfig.DEFAULT_JEV_MODEL
    override val draftModel: String = ModelConfig.DEFAULT_DRAFT_MODEL

    companion object {
        val SCENARIOS = listOf("results", "decide", "breakdown", "expanded", "changed", "checking", "blocked", "threat",
            "boundary", "error", "nokey", "notchat", "picker")
        private val USAGE = Usage(1000, 60, 0.00021)
        private const val SNAPSHOT_DELAY_MS = 2500L
    }
}
