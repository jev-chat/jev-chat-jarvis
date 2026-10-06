package com.jev.overseas.assistant.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.jev.overseas.assistant.R
import com.jev.overseas.core.engine.Analysis
import com.jev.overseas.core.engine.AnalysisEngine
import com.jev.overseas.core.engine.CandidateState
import com.jev.overseas.core.engine.CheckOutcome
import com.jev.overseas.core.engine.CheckResult
import com.jev.overseas.core.engine.Draft
import com.jev.overseas.core.engine.GoalSwitch
import com.jev.overseas.core.engine.NextStep
import com.jev.overseas.core.engine.ScorePart
import com.jev.overseas.core.engine.Scoring
import com.jev.overseas.core.engine.SkipReason
import com.jev.overseas.core.engine.Verdict
import com.jev.overseas.core.scene.InputMode
import com.jev.overseas.core.scene.Level
import com.jev.overseas.core.scene.Position
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared
import com.jev.overseas.core.scene.StanceOption
import com.jev.overseas.core.session.AssistantSession
import com.jev.overseas.core.session.ErrorAction
import com.jev.overseas.core.session.PanelState
import com.jev.overseas.core.session.Phase
import com.jev.overseas.core.session.Stale
import java.util.Locale

/**
 * Draws [PanelState] and forwards taps to
 * the session. Replies and their scores always come first; the analysis sits
 * behind "expand". All flow rules live in [AssistantSession]; the only state
 * kept here is what is open on screen (the score breakdown, "More", the second
 * reply).
 */
@SuppressLint("ViewConstructor")
class PanelView(
    context: Context,
    private val session: AssistantSession,
    private val host: Host,
) : FrameLayout(context) {

    interface Host {
        fun closePanel()
        /** Fold the panel into the bubble and keep the round. */
        fun minimizePanel()
        fun openSettings()
        /** Builds a diagnostic report and opens the share sheet; [includeText] adds this round's goal and replies. */
        fun shareReport(includeText: Boolean)
        val jevModel: String
        val draftModel: String
    }

    private val ui = Ui(context)
    private val scroll = ScrollView(context).apply { isFillViewport = false; isVerticalScrollBarEnabled = false }
    private val content = ui.column(paddingDp = 10, gapDp = 8)
    /**
     * "Goal & analysis" sits here, pinned below the scrolling part, so long replies
     * can never push it out of sight.
     */
    private val footer = ui.column(gapDp = 0).apply { setPadding(ui.dp(10), 0, ui.dp(10), ui.dp(4)) }
    /** Where the goal and analysis start in [content]; scrolled to when the user opens them. */
    private var analysisAnchor: View? = null
    private var scrollToAnalysis = false

    private var last: PanelState? = null
    private var editingGoal = false
    private var moreOpen = false
    private var ownGoalOpen = false
    /** Reply texts whose card the user opened; the first card is always open. Reset for a new batch. */
    private val openCards = HashSet<String>()
    private var batchKey: List<String> = emptyList()
    private var breakdownIndex: Int? = null
    // Breakdown sections the user opened. Kept as fields so a redraw does not close them.
    private var passedOpen = false
    private val levelsOpen = HashSet<String>()
    private var calcOpen = false
    /** Scroll position of the replies, restored when the breakdown closes. */
    private var repliesScroll = 0
    private var readOpen = false
    private var includeText = false
    private var onFieldChange: (() -> Unit)? = null
    private val animated = HashSet<String>()

    private val detailsInput = input()
    private val goalInput = input()

    init {
        background = ui.rounded(ui.base, 14, ui.line)
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        isFocusableInTouchMode = true
    }

    // ------------------------------------------------------------------ size and keys

    /**
     * The panel is as tall as its content: up to 58% of the screen (deciding) or 66%
     * (results), and up to 92% with the analysis or a breakdown open. It is never
     * stretched to the limit.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screen = resources.displayMetrics.heightPixels
        val s = last
        val results = s != null && (s.phase == Phase.CANDIDATES || s.phase == Phase.CHECKING) && !editingGoal
        val cap = when {
            s?.expanded == true || breakdownIndex != null -> 0.92f
            results -> 0.66f
            else -> 0.58f
        }
        // Never taller than what is visible: the panel starts below the status bar and must end above the
        // navigation bar, or the pinned row at the bottom is cut off.
        // Where the panel really starts on screen: its window is placed below the status bar and then
        // offset again, so it begins below the chat's title bar (about 340 px down on the test phone).
        // Before the first layout that position is unknown; assume the same offset twice.
        val statusBar = maxOf(systemDimen("status_bar_height"), ui.dp(24))
        val onScreen = IntArray(2).also { getLocationOnScreen(it) }[1]
        val top = if (onScreen > 0) onScreen else statusBar * 2 + ui.dp(4)
        val bottom = maxOf(rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars())?.bottom ?: 0,
            systemDimen("navigation_bar_height"), ui.dp(24))
        val visible = screen - top - bottom - ui.dp(8)
        val maxHeight = minOf((screen * cap).toInt(), visible)
        // The pinned footer is measured first; the scrolling part gets what is left above it.
        val footerHeight = if (footer.visibility == View.VISIBLE && footer.childCount > 0) {
            footer.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            footer.measuredHeight
        } else 0
        (scroll.layoutParams as LayoutParams).bottomMargin = footerHeight
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
    }

    /**
     * A touch outside the card folds it into the bubble (FLAG_WATCH_OUTSIDE_TOUCH),
     * except while a text field has focus: the keyboard is outside the card too.
     */
    fun onOutsideTouch() {
        if (!detailsInput.hasFocus() && !goalInput.hasFocus()) host.minimizePanel()
    }

    /** Back closes the breakdown, then the expanded view, then the panel. */
    fun handleBack() {
        when {
            breakdownIndex != null -> closeBreakdown()
            last?.expanded == true -> session.setExpanded(false)
            else -> host.minimizePanel()
        }
    }

    // ------------------------------------------------------------------ render

    fun render(s: PanelState) {
        if (s.phase == Phase.READING && last?.phase != Phase.READING) {
            editingGoal = false; moreOpen = false; ownGoalOpen = false; openCards.clear(); breakdownIndex = null
            // Including text in a report is a choice for one round, never a standing setting.
            includeText = false
            animated.clear()
            detailsInput.setText(""); goalInput.setText("")
        }
        if (s.phase == Phase.DRAFTING) editingGoal = false
        if (s.candidates == null) breakdownIndex = null
        val key = s.candidates?.verdicts?.map { it.draft.text }.orEmpty()
        if (key != batchKey) { openCards.clear(); batchKey = key }
        last = s
        redraw()
    }

    private fun redraw() {
        val s = last ?: return
        (detailsInput.parent as? ViewGroup)?.removeView(detailsInput)
        (goalInput.parent as? ViewGroup)?.removeView(goalInput)
        content.removeAllViews()
        footer.removeAllViews()
        analysisAnchor = null
        content.addView(header(s))
        if (s.choosingScene) {
            scenePicker(s)
        } else if (breakdownIndex != null && s.candidates?.verdicts?.getOrNull(breakdownIndex!!) != null) {
            breakdown(s, breakdownIndex!!)
        } else {
            if (s.noticeIsWarning) s.notice?.let { content.addView(warningBanner(it)) }
            s.chatChange?.let { content.addView(changeBanner(it.message, it.otherChat)) }
            s.error?.let { content.addView(errorCard(s)) }
            when {
                s.busy && s.phase != Phase.CHECKING -> progress(s)
                (s.phase == Phase.CANDIDATES || s.phase == Phase.CHECKING) && !editingGoal -> results(s)
                else -> decide(s)
            }
            if (!s.noticeIsWarning) s.notice?.let { content.addView(ui.label(it, 12f, ui.secondary)) }
            // One full-width row, pinned at the bottom, opens the goal and the analysis; the reply cards never change size.
            if (!(s.busy && s.phase != Phase.CHECKING) && (s.goal != null || s.analysis != null || s.report != null)) {
                // Closed: pinned at the bottom. Open: no bottom row; "Hide goal & analysis" sits at the top of what opened.
                if (!s.expanded) footer.addView(goalAnalysisRow(s), fullWidth())
                if (s.expanded) {
                    val anchor = View(context)
                    content.addView(anchor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
                    analysisAnchor = anchor
                    expandedSections(s)
                }
            }
        }
        requestLayout()
        if (scrollToAnalysis) {
            scrollToAnalysis = false
            analysisAnchor?.let { a -> scroll.post { scroll.smoothScrollTo(0, a.top) } }
        }
    }

    // ------------------------------------------------------------------ header

    private fun header(s: PanelState): View {
        val row = ui.row(6)
        val dotColor = when {
            s.error != null -> ui.warn
            s.busy -> ui.accent
            else -> ui.ok
        }
        row.addView(ui.label("●", 11f, dotColor))
        row.addView(ui.label("JEV", 13f, ui.text, bold = true).apply { letterSpacing = 0.12f })
        val results = (s.phase == Phase.CANDIDATES || s.phase == Phase.CHECKING) && !editingGoal && s.goal != null
        val context = if (results) {
            val name = if (s.ownGoal) ui.str(R.string.own_goal) else s.stance?.label ?: ui.str(R.string.reply)
            ui.label(name, 13f, ui.secondary, lines = 1)
        } else {
            clickable(ui.label(selectionText(s.selection), 13f, ui.secondary, lines = 1)) { session.showScenePicker() }
        }
        row.addView(context, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ui.icon(R.drawable.ic_minimize, ui.secondary, 18, { host.minimizePanel() }, ui.str(R.string.minimize)))
        row.addView(ui.icon(R.drawable.ic_close, ui.secondary, 18, { host.closePanel() }, ui.str(R.string.close)))
        // Double-tap on the header toggles the expanded view.
        var lastTap = 0L
        row.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTap < 350) session.setExpanded(!s.expanded)
            lastTap = now
        }
        return row
    }

    private fun selectionText(sel: Selection?): String {
        sel ?: return ui.str(R.string.choose_scene)
        val rel = sel.relationship?.label
        return listOfNotNull(sel.scene.label, rel).joinToString(" › ") + if (sel.conflict) " · " + (sel.scene.conflictLabel ?: "") else ""
    }

    // ------------------------------------------------------------------ scene picker

    private fun scenePicker(s: PanelState) {
        val sel = s.selection
        if (s.newChat) {
            content.addView(ui.label(ui.str(R.string.new_chat), 13f, ui.text, bold = true))
            content.addView(ui.label(ui.str(R.string.new_chat_detail), 12f, ui.muted))
        }
        content.addView(ui.label(ui.str(R.string.who_is_this), 13f))
        content.addView(ui.flow().apply {
            for (scene in com.jev.overseas.assistant.Settings.SCENES) addView(ui.chip(scene.label, sel?.scene == scene) { session.selectScene(scene) })
        })
        if (sel != null) {
            val spec = SceneCatalog.spec(sel.scene)
            content.addView(ui.label(ui.str(R.string.relationship), 12f, ui.muted))
            content.addView(ui.flow().apply {
                for (rel in spec.relationships) addView(ui.chip(rel.label, sel.relationship == rel) { session.selectRelationship(rel) })
                if (sel.scene != com.jev.overseas.core.scene.Scene.GENERAL) {
                    addView(ui.chip(ui.str(R.string.not_sure), sel.relationship == null) { session.selectRelationship(null) })
                }
            })
            sel.scene.conflictLabel?.let { label ->
                content.addView(ui.flow().apply { addView(ui.chip(label, sel.conflict) { session.toggleConflict() }) })
            }
            content.addView(ui.matchWidth(ui.primaryButton(ui.str(R.string.analyse)) { session.analyse() }))
        }
        // Opened from a round: go back to it without paying for a new analysis.
        if (session.hasRound && !s.busy) {
            content.addView(ui.ghostButton(ui.str(R.string.back), ui.secondary) { session.closeScenePicker() })
        }
    }

    // ------------------------------------------------------------------ phase A

    private fun progress(s: PanelState) {
        val step = when (s.phase) {
            Phase.READING -> R.string.step_reading
            Phase.ANALYSING -> R.string.step_analysing
            else -> R.string.step_drafting
        }
        content.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ui.accent)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(4))
        })
        content.addView(ui.row(6).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.label(ui.str(step), 13f, ui.secondary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.ghostButton(ui.str(R.string.cancel), ui.secondary) { session.cancel() })
        }, fullWidth())
        s.report?.let { content.addView(ui.label(reportText(it), 12f, ui.muted)) }
    }

    // ------------------------------------------------------------------ phase B: decide

    private fun decide(s: PanelState) {
        val a = s.analysis
        s.skip?.let { content.addView(ui.label(it.message, 13f, ui.secondary)) }
        if (a != null) summaryLine(a)?.let { content.addView(it) }
        if (a != null) signalLines(a).forEach { content.addView(it) }
        if (a != null && AnalysisEngine.HISTORY_NOTICE in a.notices && a.next != NextStep.SAFETY_HOLD) content.addView(historyLine(s))
        if (a?.suggestScene == true) content.addView(sceneBanner())
        if (s.nothingToSend) {
            content.addView(ui.card(gapDp = 4).apply {
                addView(ui.label(ui.str(R.string.nothing_to_send), 13f, ui.text, bold = true))
                addView(ui.label(ui.str(R.string.nothing_to_send_detail), 12f, ui.secondary))
            })
        }
        if (a == null && !s.ownGoalAllowed) return
        if (a != null && a.next != NextStep.SAFETY_HOLD || a == null) {
            if (a != null) {
                val first = s.groups.firstOrNull()
                // Say what they are doing before asking what to do about it, so the options read as answers to it.
                first?.let { content.addView(ui.label(it.title, 13f, ui.text, bold = true)) }
                content.addView(ui.label(ui.str(R.string.what_to_do), 13f, if (first != null) ui.secondary else ui.text))
                content.addView(ui.flow().apply {
                    // Compact: the first few options of the main group; the rest sit behind "More".
                    val all = first?.options ?: s.moreOptions
                    val shown = if (moreOpen) all else all.take(COMPACT_OPTIONS) + all.drop(COMPACT_OPTIONS).filter { it == s.stance }
                    for (o in shown) addView(stanceChip(s, o))
                    addView(ui.chip(ui.str(if (moreOpen) R.string.less else R.string.more), moreOpen) { moreOpen = !moreOpen; redraw() })
                })
                if (moreOpen) moreOptions(s, skipFirst = first != null)
            }
        }
        // The chosen option in a sentence: "Keep it light" alone does not say what the reply will do.
        s.stance?.takeIf { !s.ownGoal }?.let { content.addView(ui.label(it.summary, 12f, ui.secondary)) }
        if (a?.next == NextStep.SAFETY_HOLD) {
            content.addView(ui.flow().apply {
                for (o in s.moreOptions) addView(stanceChip(s, o))
                addView(ui.chip(ui.str(R.string.write_own_goal), ownGoalOpen || s.ownGoal) { ownGoalOpen = !ownGoalOpen; redraw() })
            })
        }

        val stance = s.stance
        val followUp = s.skip?.reason == SkipReason.USER_WROTE_LAST
        val ownMode = (ownGoalOpen || s.ownGoal || (a == null && s.ownGoalAllowed)) && s.ownGoalAllowed
        if (!ownMode && stance != null && !stance.noReply && stance.input != InputMode.NONE) {
            detailsInput.hint = stance.inputHint + if (stance.input == InputMode.REQUIRED) "" else ""
            if (detailsInput.text.toString() != s.details && !detailsInput.hasFocus()) detailsInput.setText(s.details)
            content.addView(detailsInput, fullWidth())
        }
        if (ownMode) {
            goalInput.hint = ui.str(if (followUp) R.string.follow_up_hint else R.string.own_goal_hint)
            if (s.ownGoal && goalInput.text.isNullOrBlank()) goalInput.setText(s.goal?.summary)
            content.addView(goalInput, fullWidth())
        }
        if (ownMode || (stance != null && !stance.noReply)) {
            val label = if (ownMode && followUp) R.string.draft_follow_up else R.string.draft_replies
            val field = if (ownMode) goalInput else detailsInput
            val needsText = ownMode || stance?.input == InputMode.REQUIRED
            val button = ui.primaryButton(ui.str(label), enabled = !s.busy) { draftTapped(ownMode) }
            fun refreshButton() {
                val ready = !s.busy && (!needsText || field.text.toString().isNotBlank())
                button.isEnabled = ready
                button.setTextColor(if (ready) ui.onAccent else ui.muted)
                ui.pressable(button, if (ready) ui.accent else ui.raised, 12)
            }
            refreshButton()
            onFieldChange = { refreshButton() }
            content.addView(button, fullWidth())
        }
        val bottom = ui.row(4)
        if (editingGoal && s.candidates != null) {
            bottom.addView(action(R.drawable.ic_back, R.string.back_to_replies) { editingGoal = false; ownGoalOpen = false; redraw() })
        }
        if (bottom.childCount > 0) content.addView(bottom, fullWidth())
    }

    private fun draftTapped(ownMode: Boolean) {
        if (ownMode) {
            val text = goalInput.text.toString().trim()
            if (text.isEmpty()) return
            session.editSummary(text)
        } else {
            val stance = last?.stance ?: return
            val text = detailsInput.text.toString().trim()
            if (stance.input == InputMode.REQUIRED && text.isEmpty()) return
            if (stance.input != InputMode.NONE) session.setDetails(text)
        }
        editingGoal = false
        ownGoalOpen = false
        hideKeyboard()
        session.draft()
    }

    private fun stanceChip(s: PanelState, o: StanceOption): View =
        ui.chip(o.label, !s.ownGoal && s.stance == o) {
            ownGoalOpen = false
            detailsInput.setText("")
            session.chooseStance(o)
        }

    private fun moreOptions(s: PanelState, skipFirst: Boolean) {
        val groups = if (skipFirst) s.groups.drop(1) else s.groups
        for (g in groups) {
            content.addView(ui.label(g.title, 12f, ui.muted))
            content.addView(ui.flow().apply { for (o in g.options) addView(stanceChip(s, o)) })
        }
        if (skipFirst) {
            content.addView(ui.label(ui.str(R.string.other_options), 12f, ui.muted))
            content.addView(ui.flow().apply { for (o in s.moreOptions) addView(stanceChip(s, o)) })
        }
        content.addView(ui.flow().apply {
            addView(ui.chip(ui.str(R.string.write_own_goal), ownGoalOpen || s.ownGoal) { ownGoalOpen = !ownGoalOpen; redraw() })
        })
    }

    /** One line: what they're doing, against the usual, friction. Or the notice that matters most. */
    private fun summaryLine(a: Analysis): View? {
        val major = when (a.next) {
            NextStep.SAFETY_HOLD, NextStep.BOUNDARY -> ui.bad
            NextStep.UNCLEAR, NextStep.NO_REPLY_NEEDED -> ui.info
            else -> null
        }
        if (major != null) {
            return banner(a.notices.first(), major, if (major == ui.bad) R.drawable.ic_warning else R.drawable.ic_info)
        }
        val parts = ArrayList<String>()
        a.labels.firstOrNull()?.let { first ->
            parts.add(first.behavior.label)
            positionWords(first.reading?.position)?.let { parts.add(it) }
        }
        a.labels.drop(1).forEach { parts.add(it.behavior.label) }
        a.friction?.let { parts.add(String.format(Locale.US, "friction %.1f", it.level)) }
        if (parts.isEmpty()) return null
        val row = ui.row(4)
        row.addView(ui.label(parts.joinToString(" · "), 12f, ui.secondary, lines = 2), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ui.icon(R.drawable.ic_chevron, ui.muted, 16))
        return clickable(row) { session.setExpanded(true) }
    }

    private fun positionWords(p: Position?): String? = when (p) {
        Position.ABOVE -> "above usual"
        Position.AT -> "as usual"
        Position.BELOW -> "below usual"
        Position.RARE -> "unusual for them"
        Position.DEPENDS -> "depends on how you two usually talk"
        else -> null
    }

    /**
     * Cues and the tone signal, at most two short lines, in the compact view
     * (matrix legend: shown as their own card, worded as "may"). The full
     * wording stays in the analysis section.
     */
    private fun signalLines(a: Analysis): List<View> {
        if (a.next == NextStep.SAFETY_HOLD || a.next == NextStep.BOUNDARY) return emptyList()
        val lines = ArrayList<String>()
        if (com.jev.overseas.core.scene.WorkScene.CLIENT_COMPLAINT in a.notices) lines.add(com.jev.overseas.core.scene.WorkScene.CLIENT_COMPLAINT)
        a.cues.forEach { c -> lines.add("Cue: " + (c.reading?.note ?: c.behavior.label)) }
        a.tone?.let { lines.add("Tone: " + it.title.replaceFirstChar { ch -> ch.lowercase() }) }
        // Their turn asked for more than one thing: say which one the options answer.
        a.notices.firstOrNull { it.startsWith("They said ") }?.let { lines.add(0, it) }
        if (AnalysisEngine.FILE_NOTICE in a.notices) lines.add(0, AnalysisEngine.FILE_NOTICE)
        return lines.take(3).map { ui.label(it, 12f, ui.info, lines = 2) }
    }

    /** They refer to something earlier that was not read: say so, and offer one wider read. */
    private fun historyLine(s: PanelState): View = ui.row(6).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ui.label(AnalysisEngine.HISTORY_NOTICE, 12f, ui.info, lines = 2), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!s.readFurther) addView(ui.ghostButton(ui.str(R.string.read_further), ui.accent) { session.readFurther() })
    }

    /** General scene, personal words: no relationship reading here, so offer the scene picker. */
    private fun sceneBanner(): View = ui.card(stroke = ui.info, gapDp = 6).apply {
        addView(ui.label(AnalysisEngine.PERSONAL_NOTICE, 13f, ui.text))
        addView(ui.ghostButton(ui.str(R.string.pick_scene), ui.accent) { session.showScenePicker() })
    }

    // ------------------------------------------------------------------ phase C: results

    private fun results(s: PanelState) {
        val set = s.candidates
        if (s.stale == Stale.GOAL) {
            content.addView(ui.card(stroke = ui.warn, gapDp = 6).apply {
                addView(ui.label(ui.str(R.string.goal_changed), 13f, ui.warn))
                addView(ui.primaryButton(ui.str(R.string.draft_again)) { session.draft() }, fullWidth())
            })
        }
        if (s.switches.isNotEmpty() && s.stale != Stale.GOAL) {
            // Fine-tuning is visible where the replies are, and one tap undoes it.
            content.addView(ui.row(6).apply {
                addView(ui.label(ui.str(R.string.fine_tuned, s.switches.joinToString(", ") { it.label }), 12f, ui.accent),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ui.icon(R.drawable.ic_close, ui.accent, 14, {
                    s.switches.forEach { session.toggleSwitch(it) }
                    session.draft()
                }, ui.str(R.string.undo_fine_tune)))
            }, fullWidth())
        }
        if (set != null && set.close) content.addView(ui.label(ui.str(R.string.too_close), 12f, ui.secondary))
        val items: List<Pair<Draft, Verdict?>> = set?.verdicts?.map { it.draft to it } ?: s.drafts.map { it to null }
        items.forEachIndexed { i, (draft, verdict) ->
            val full = i == 0 || draft.text in openCards
            content.addView(if (full) fullCard(s, i, draft, verdict) else compactCard(s, i, draft, verdict))
        }
        s.replacePending?.let { content.addView(replaceCard()) }
        if (set != null) {
            val row = ui.row(0)
            row.addView(action(R.drawable.ic_refresh, R.string.another_batch) { session.anotherBatch() })
            row.addView(action(R.drawable.ic_edit, R.string.change_goal) { editingGoal = true; redraw() })
            content.addView(row, fullWidth())
        }
    }

    /** "Goal & analysis ⌄" / "Hide ⌃": a 40 dp full-width row with a divider above it. */
    private fun goalAnalysisRow(s: PanelState): View = ui.column(gapDp = 0).apply {
        addView(View(context).apply { setBackgroundColor(ui.line) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1)))
        addView(clickable(ui.row(6).apply {
            minimumHeight = ui.dp(40)
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.label(ui.str(if (s.expanded) R.string.hide_analysis else R.string.goal_and_analysis), 13f, ui.text),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.icon(if (s.expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more, ui.secondary, 18))
        }) {
            // Opening jumps to the goal and analysis, which may start below the replies.
            if (s.expanded) hideAnalysis() else {
                scrollToAnalysis = true
                session.setExpanded(true)
            }
        }, fullWidth())
    }

    /** "Why 4.5 ›": opens this reply's breakdown. Every card has one, open or folded. */
    private fun whyLink(index: Int, v: Verdict): View? {
        if (v.hardChecks.isEmpty() && v.total == null) return null
        val text = v.total?.takeIf { v.scored }?.let { ui.str(R.string.why_score, String.format(Locale.US, "%.1f", it)) } ?: ui.str(R.string.see_checks)
        return clickable(ui.row(2).apply {
            minimumHeight = ui.dp(32)
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.label(text, 12f, ui.accent))
            addView(ui.icon(R.drawable.ic_chevron, ui.accent, 14))
        }) { openBreakdown(index) }
    }

    private fun systemDimen(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /** Closes the goal and analysis and goes back to the top of the panel. */
    private fun hideAnalysis() {
        session.setExpanded(false)
        scroll.post { scroll.smoothScrollTo(0, 0) }
    }

    private fun openBreakdown(index: Int) {
        repliesScroll = scroll.scrollY
        breakdownIndex = index
        passedOpen = false; levelsOpen.clear(); calcOpen = false
        redraw()
        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun closeBreakdown() {
        breakdownIndex = null
        redraw()
        val y = repliesScroll
        scroll.post { scroll.scrollTo(0, y) }
    }

    /** A small text action with an optional icon, sized for a 40 dp touch target. */
    private fun action(icon: Int?, label: Int, trailing: Int? = null, onClick: () -> Unit): View = clickable(ui.row(3).apply {
        minimumHeight = ui.dp(40)
        setPadding(ui.dp(3), 0, ui.dp(3), 0)
        icon?.let { addView(ui.icon(it, ui.secondary, 14)) }
        addView(ui.label(ui.str(label), 12f, ui.secondary))
        trailing?.let { addView(ui.icon(it, ui.secondary, 14)) }
    }, onClick)

    private fun fullCard(s: PanelState, index: Int, draft: Draft, v: Verdict?): View {
        val stroke = when {
            v == null -> ui.line
            v.state == CandidateState.NEEDS_REWRITE -> ui.bad
            v.state == CandidateState.AWAITING_CONFIRMATION || v.state == CandidateState.NEEDS_EDIT -> ui.warn
            s.candidates?.topPick == index -> ui.ok
            else -> ui.line
        }
        val card = ui.card(stroke = stroke, gapDp = 6)
        if (s.stale == Stale.GOAL) card.alpha = 0.5f

        // Score ring and the two parts.
        val top = ui.row(10).apply { gravity = Gravity.TOP }
        val ring = ScoreRing(context, ui, RING_DP)
        when {
            v == null -> ring.setScoring()
            v.state == CandidateState.NEEDS_REWRITE -> ring.setEmpty(ui.str(R.string.blocked))
            v.state == CandidateState.FAILED -> ring.setEmpty(ui.str(R.string.not_checked))
            v.total == null -> ring.setEmpty(ui.str(R.string.not_scored))
            else -> {
                val key = v.draft.text
                ring.setScore(v.total!!, ui.scoreColor(v.total!!), animate = animated.add(key))
            }
        }
        if (v?.scored == true) clickable(ring) { openBreakdown(index) }
        top.addView(ring)
        val parts = ui.column(gapDp = 2)
        val badgeRow = ui.row(6)
        val badge = when {
            v == null -> draft.label.replaceFirstChar { it.uppercase() }
            s.candidates?.topPick == index -> ui.str(R.string.top_pick, draft.label.replaceFirstChar { it.uppercase() })
            else -> draft.label.replaceFirstChar { it.uppercase() }
        }
        badgeRow.addView(ui.tag(badge, if (s.candidates?.topPick == index) ui.ok else ui.secondary))
        if (v?.unsure == true && v.scored) badgeRow.addView(ui.tag(ui.str(R.string.jev_unsure), ui.warn))
        badgeRow.addView(ui.spacer())
        if (index > 0) {
            badgeRow.addView(ui.icon(R.drawable.ic_expand_less, ui.secondary, 18, { openCards.remove(draft.text); redraw() }, ui.str(R.string.fold_reply)))
        } else {
            badgeRow.addView(ui.label(ui.str(R.string.scored_by_jev), 11f, ui.muted))
        }
        parts.addView(badgeRow, fullWidth())
        if (v != null && v.state != CandidateState.NEEDS_REWRITE && v.state != CandidateState.FAILED) {
            v.g?.let { parts.addView(partRow(ui.str(R.string.goal), it, ui.ok), fullWidth()) }
            v.e?.let { parts.addView(partRow(ui.str(R.string.delivery), it, ui.accent), fullWidth()) }
            if (v.g == null && v.e != null) parts.addView(ui.label(ui.str(R.string.delivery_only), 11f, ui.muted))
        } else if (v == null) {
            parts.addView(ui.label(ui.str(R.string.checking), 12f, ui.muted))
        }
        top.addView(parts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(top, fullWidth())

        // The reply itself: the thing the user will send.
        card.addView(ui.label(draft.text, 14f, ui.text).apply { setLineSpacing(0f, 1.4f); setTextIsSelectable(false) })

        if (v != null) {
            checkLine(v, s)?.let { card.addView(it) }
            whyLink(index, v)?.let { card.addView(it) }
            buttons(s, index, v)?.let { card.addView(it, fullWidth()) }
        }
        return card
    }

    private fun partRow(name: String, part: ScorePart, color: Int): View {
        val col = ui.column(gapDp = 2)
        val row = ui.row(4)
        row.addView(ui.label("$name · ${part.label.lowercase()}", 12f, ui.secondary, lines = 1), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ui.label(String.format(Locale.US, "%.1f", part.value), 12f, ui.text, monospace = true))
        col.addView(row, fullWidth())
        col.addView(ui.bar(part.value / 5.0, color))
        return col
    }

    private fun checkLine(v: Verdict, s: PanelState): View? = when (v.state) {
        CandidateState.ELIGIBLE -> {
            val (passed, total) = v.checkCounts
            val items = v.checklist.filter { it.id.startsWith("include.") }.map { forYou(it.label) }
            val text = ui.str(R.string.checks_passed, passed, total) + items.joinToString("") { " · $it" }
            ui.row(4).apply {
                addView(ui.icon(R.drawable.ic_shield, ui.ok, 14))
                addView(ui.label(text, 12f, ui.secondary, lines = 2), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        CandidateState.AWAITING_CONFIRMATION -> ui.column(gapDp = 3).apply {
            val confirmed = v.draft.text in s.confirmed
            addView(ui.tag(if (confirmed) ui.str(R.string.checked_by_you) else ui.str(R.string.one_to_check), ui.warn))
            v.reasons.forEach { addView(ui.label(it, 12f, ui.warn)) }
        }
        CandidateState.NEEDS_EDIT -> ui.column(gapDp = 3).apply { v.reasons.forEach { addView(ui.label(it, 12f, ui.warn)) } }
        CandidateState.NEEDS_REWRITE -> ui.column(gapDp = 3).apply {
            addView(ui.tag(ui.str(R.string.blocked), ui.bad))
            v.reasons.forEach { addView(ui.label(it, 12f, ui.bad)) }
        }
        CandidateState.UNSCORED -> ui.label(v.reasons.joinToString(" "), 12f, ui.muted)
        CandidateState.FAILED -> ui.label(ui.str(R.string.could_not_check), 12f, ui.warn)
    }

    private fun buttons(s: PanelState, index: Int, v: Verdict): View? {
        if (s.stale == Stale.GOAL) return null
        if (v.state == CandidateState.FAILED) return ui.row(8).apply {
            // Check the same reply again; nothing is redrafted.
            addView(ui.ghostButton(ui.str(R.string.retry)) { session.recheck() })
        }
        if (!v.copyable) return null
        if (v.state == CandidateState.AWAITING_CONFIRMATION && v.draft.text !in s.confirmed) {
            return ui.row(8).apply { addView(ui.weighted(ui.ghostButton(ui.str(R.string.i_checked), ui.warn) { session.confirmChecked(index) })) }
        }
        val usable = s.usable(index)
        return ui.row(8).apply {
            addView(ui.weighted(ui.primaryButton(ui.str(R.string.fill), enabled = usable) { session.fill(index) }))
            addView(ui.ghostButton(ui.str(R.string.copy), if (usable) ui.text else ui.muted) { if (usable) session.copy(index) })
        }
    }

    private fun compactCard(s: PanelState, index: Int, draft: Draft, v: Verdict?): View {
        val card = ui.card(gapDp = 4)
        if (s.stale == Stale.GOAL) card.alpha = 0.5f
        val row = ui.row(6)
        val total = v?.total
        if (v != null && v.scored && total != null) {
            row.addView(ui.label(String.format(Locale.US, "%.1f", total), 16f, ui.scoreColor(total), monospace = true))
            row.addView(ui.label("/ 5", 11f, ui.muted))
        }
        row.addView(ui.label(draft.label.replaceFirstChar { it.uppercase() }, 13f, ui.text))
        row.addView(ui.spacer())
        when (v?.state) {
            null -> row.addView(ui.label(ui.str(R.string.checking), 11f, ui.muted))
            CandidateState.AWAITING_CONFIRMATION -> row.addView(ui.tag(ui.str(R.string.one_to_check), ui.warn))
            CandidateState.NEEDS_EDIT -> row.addView(ui.tag(ui.str(R.string.missing_tag), ui.warn))
            CandidateState.NEEDS_REWRITE -> row.addView(ui.tag(ui.str(R.string.blocked), ui.bad))
            CandidateState.FAILED -> row.addView(ui.tag(ui.str(R.string.not_checked), ui.warn))
            CandidateState.UNSCORED -> row.addView(ui.tag(ui.str(R.string.not_scored), ui.secondary))
            CandidateState.ELIGIBLE -> row.addView(ui.tag(ui.str(R.string.passed), ui.ok))
        }
        row.addView(ui.icon(R.drawable.ic_expand_more, ui.secondary, 18))
        card.addView(row, fullWidth())
        card.addView(ui.label(draft.text, 13f, ui.text, lines = 3))
        v?.reasons?.firstOrNull()?.let { card.addView(ui.label(it, 12f, if (v.state == CandidateState.NEEDS_REWRITE) ui.bad else ui.warn, lines = 2)) }
        if (v != null) whyLink(index, v)?.let { card.addView(it) }
        return clickable(card) { openCards.add(draft.text); redraw() }
    }

    private fun replaceCard(): View = ui.card(stroke = ui.warn, gapDp = 6).apply {
        addView(ui.label(ui.str(R.string.replace_question), 13f))
        addView(ui.row(8).apply {
            addView(ui.weighted(ui.primaryButton(ui.str(R.string.replace)) { session.confirmReplace() }))
            addView(ui.weighted(ui.ghostButton(ui.str(R.string.keep_mine)) { session.cancelReplace() }))
        }, fullWidth())
    }

    // ------------------------------------------------------------------ banners and errors

    private fun changeBanner(message: String, otherChat: Boolean): View = ui.card(stroke = ui.info, gapDp = 6).apply {
        addView(ui.row(6).apply {
            addView(ui.icon(R.drawable.ic_info, ui.info, 16))
            addView(ui.label(message, 13f, ui.text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, fullWidth())
        if (otherChat) {
            // Replies written for someone else can never be "used anyway" here.
            addView(ui.matchWidth(ui.primaryButton(ui.str(R.string.analyse_this_chat)) { session.reopen() }))
        } else {
            addView(ui.row(8).apply {
                addView(ui.weighted(ui.primaryButton(ui.str(R.string.read_again)) { session.readAgain() }))
                addView(ui.weighted(ui.ghostButton(ui.str(R.string.use_anyway)) { session.useAnyway() }))
            }, fullWidth())
        }
    }

    /** Something the user asked for did not happen: say so where it cannot be missed. */
    private fun warningBanner(message: String): View = ui.card(fill = ui.surface, stroke = ui.warn, gapDp = 6).apply {
        addView(ui.row(6).apply {
            gravity = Gravity.TOP
            addView(ui.icon(R.drawable.ic_warning, ui.warn, 18))
            addView(ui.label(message, 14f, ui.warn, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, fullWidth())
        if (message == AssistantSession.NOT_SAME_CHAT) {
            addView(ui.ghostButton(ui.str(R.string.analyse_this_chat), ui.accent) { session.reopen() }, fullWidth())
        }
    }

    private fun banner(message: String, color: Int, icon: Int): View = ui.row(6).apply {
        setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6))
        background = ui.rounded(ui.surface, 10, color)
        gravity = Gravity.TOP
        addView(ui.icon(icon, color, 16))
        addView(ui.label(message, 12f, ui.text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun errorCard(s: PanelState): View {
        val e = s.error!!
        return ui.card(stroke = ui.warn, gapDp = 6).apply {
            addView(ui.label(e.message, 13f, ui.text))
            val (label, action) = when (e.kind.action) {
                ErrorAction.SETTINGS -> ui.str(R.string.open_settings) to { host.openSettings() }
                ErrorAction.RETRY -> ui.str(R.string.retry) to { session.retry() }
                ErrorAction.START_AGAIN -> ui.str(R.string.start_again) to { session.analyse() }
                ErrorAction.OPEN_CHAT -> ui.str(R.string.try_again) to { session.reopen() }
            }
            addView(ui.ghostButton(label, ui.accent, action))
        }
    }

    // ------------------------------------------------------------------ score breakdown (7.3)

    /**
     * The score breakdown in tiers: the conclusion, the checks (problems
     * first, passes folded), the chosen Goal and Delivery levels (all six behind
     * "See all levels"), and how the number is calculated.
     */
    private fun breakdown(s: PanelState, index: Int) {
        val set = s.candidates!!
        val v = set.verdicts[index]
        val sel = s.selection ?: return
        content.addView(ui.row(6).apply {
            addView(ui.icon(R.drawable.ic_back, ui.secondary, 18, { closeBreakdown() }, ui.str(R.string.back)))
            addView(ui.label(ui.str(R.string.score_breakdown), 13f, ui.text, bold = true))
        }, fullWidth())
        if (set.verdicts.size > 1) {
            content.addView(ui.flow().apply {
                set.verdicts.forEachIndexed { i, other ->
                    addView(ui.chip(ui.str(R.string.reply_n, i + 1) + " · " + other.draft.label, i == index) {
                        if (i != index) { breakdownIndex = i; passedOpen = false; levelsOpen.clear(); calcOpen = false; redraw() }
                    })
                }
            })
        }
        content.addView(ui.label(v.draft.text, 13f, ui.secondary, lines = 3))

        // 1. The conclusion.
        val g = v.g
        val e = v.e
        content.addView(ui.card(gapDp = 4).apply {
            val total = v.total
            addView(ui.row(4).apply {
                gravity = Gravity.BOTTOM
                if (total != null && v.scored) {
                    addView(ui.label(String.format(Locale.US, "%.1f", total), 28f, ui.scoreColor(total), monospace = true))
                    addView(ui.label("/ 5", 14f, ui.muted))
                } else {
                    addView(ui.label(stateWords(v), 16f, ui.text, bold = true))
                }
            })
            val parts = listOfNotNull(
                g?.let { ui.str(R.string.goal) + ": " + it.label.lowercase() },
                e?.let { ui.str(R.string.delivery) + ": " + it.label.lowercase() },
            )
            if (parts.isNotEmpty()) addView(ui.label(parts.joinToString(" · "), 13f, ui.secondary))
            if (g == null && e != null) addView(ui.label(ui.str(R.string.delivery_only), 12f, ui.muted))
            if (g?.capped == true) addView(ui.label(ui.str(R.string.goal_capped), 12f, ui.warn))
            if (v.unsure) addView(ui.label(ui.str(R.string.split), 12f, ui.warn))
            v.reasons.forEach { addView(ui.label(it, 12f, if (v.state == CandidateState.NEEDS_REWRITE) ui.bad else ui.warn)) }
        })

        // 2. Checks: what needs attention first, then the passes in one folded line.
        content.addView(checksBlock(v))

        // 3. Goal and Delivery: the level Jev chose, with all six behind a toggle.
        if (g != null) content.addView(levelBlock("g", ui.str(R.string.goal), ui.str(R.string.goal_question), g, Shared.G_LEVELS, ui.ok))
        if (e != null) {
            val who = sel.relationship?.label?.lowercase() ?: ui.str(R.string.them)
            content.addView(levelBlock("e", ui.str(R.string.delivery), ui.str(R.string.delivery_question, who), e,
                SceneCatalog.spec(sel.scene).eLevels(sel), ui.accent))
        }

        // 4. How the number is made.
        content.addView(ui.card(gapDp = 4).apply {
            addView(toggleRow(ui.str(R.string.how_calculated), calcOpen) { calcOpen = !calcOpen; redraw() }, fullWidth())
            if (calcOpen) {
                val formula = when {
                    v.total == null -> ui.str(R.string.not_scored)
                    g != null && e != null -> String.format(Locale.US, "0.7 × %.1f + 0.3 × %.1f = %.1f / 5", g.value, e.value, v.total)
                    else -> String.format(Locale.US, "Delivery only = %.1f / 5", v.total)
                }
                addView(ui.label(formula, 13f, ui.text, monospace = true))
                addView(ui.label(ui.str(R.string.goal_share) + " · " + ui.str(R.string.delivery_share), 12f, ui.secondary))
                addView(ui.label(ui.str(R.string.checks_explained), 12f, ui.secondary))
                addView(ui.label(ui.str(R.string.required_explained), 12f, ui.secondary))
            }
        })
        content.addView(ui.label(ui.str(R.string.scored_footer, host.jevModel), 11f, ui.muted).apply { gravity = Gravity.CENTER }, fullWidth())
    }

    private fun stateWords(v: Verdict): String = when (v.state) {
        CandidateState.NEEDS_REWRITE -> ui.str(R.string.blocked)
        CandidateState.FAILED -> ui.str(R.string.not_checked)
        else -> ui.str(R.string.not_scored)
    }

    private fun toggleRow(text: String, open: Boolean, onClick: () -> Unit): View = clickable(ui.row(6).apply {
        minimumHeight = ui.dp(32)
        gravity = Gravity.CENTER_VERTICAL
        addView(ui.label(text, 13f, ui.text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ui.icon(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, ui.secondary, 16))
    }, onClick)

    private fun levelBlock(key: String, name: String, question: String, part: ScorePart, levels: List<Level>, color: Int): View {
        val card = ui.card(gapDp = 3)
        val chosen = Scoring.nearestLevel(part.value, levels.size)
        card.addView(ui.row(6).apply {
            addView(ui.label(name, 13f, ui.text))
            addView(ui.spacer())
            addView(ui.label(String.format(Locale.US, "%.1f", part.value), 13f, color, monospace = true))
        }, fullWidth())
        card.addView(ui.label(question, 12f, ui.muted))
        card.addView(ui.label(levels[chosen].short + ": " + levels[chosen].what, 12f, ui.text))
        val open = key in levelsOpen
        card.addView(toggleRow(ui.str(R.string.see_all_levels), open) {
            if (open) levelsOpen.remove(key) else levelsOpen.add(key); redraw()
        }, fullWidth())
        if (open) {
            val probs = part.probabilities
            val top = probs.indices.maxByOrNull { probs[it] } ?: -1
            for (i in levels.indices.reversed()) {
                card.addView(levelRow(i.toString(), levels[i].short, probs.getOrElse(i) { 0.0 }, i == top, color), fullWidth())
            }
            card.addView(ui.label(ui.str(R.string.level_numbers), 11f, ui.muted))
        }
        return card
    }

    private fun levelRow(level: String, text: String, p: Double, highlight: Boolean, color: Int): View = ui.row(8).apply {
        setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(2))
        if (highlight) background = ui.rounded(ui.raised, 5)
        val tone = if (highlight) ui.text else if (p >= 0.05) ui.secondary else ui.muted
        addView(ui.label(level, 12f, if (highlight) color else tone, monospace = true).apply { minWidth = ui.dp(22) })
        addView(ui.label(text, 12f, tone), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ui.label(prob(p), 12f, tone, monospace = true))
    }

    /** "Must not" (hard checks) and "Must include" (required items); the count matches the card's. */
    private fun checksBlock(v: Verdict): View {
        val card = ui.card(gapDp = 3)
        val (passed, total) = v.checkCounts
        card.addView(ui.row(6).apply {
            addView(ui.label(ui.str(R.string.checks), 13f))
            addView(ui.spacer())
            addView(ui.tag(ui.str(R.string.n_of_m_passed, passed, total), if (passed == total) ui.ok else ui.warn))
        }, fullWidth())
        val include = v.checklist.filter { it.id.startsWith("include.") }
        val groups = listOf(
            Triple(ui.str(R.string.must_not), v.hardChecks, true),
            Triple(ui.str(R.string.must_include), include, false),
        )
        val passes = ArrayList<Pair<CheckResult, Boolean>>()
        for ((title, checks, hard) in groups) {
            val problems = checks.filter { it.outcome != CheckOutcome.PASS }
            passes.addAll(checks.filter { it.outcome == CheckOutcome.PASS }.map { it to hard })
            if (problems.isEmpty()) continue
            card.addView(ui.label(title, 12f, ui.muted).apply { setPadding(0, ui.dp(4), 0, 0) })
            problems.forEach { card.addView(checkRow(it, hard), fullWidth()) }
        }
        if (passes.isNotEmpty()) {
            card.addView(toggleRow(ui.str(R.string.n_passed, passes.size), passedOpen) { passedOpen = !passedOpen; redraw() }, fullWidth())
            if (passedOpen) passes.forEach { (c, hard) -> card.addView(checkRow(c, hard), fullWidth()) }
        }
        // "Picks up what they said" is a quality signal, shown but not counted.
        v.checklist.firstOrNull { it.id == "acknowledges" }?.let { card.addView(checkRow(it, hard = false), fullWidth()) }
        return card
    }

    private fun checkRow(c: CheckResult, hard: Boolean): View = ui.row(6).apply {
        val (icon, color) = when (c.outcome) {
            CheckOutcome.PASS -> R.drawable.ic_check to ui.ok
            CheckOutcome.UNSURE -> R.drawable.ic_warning to ui.warn
            CheckOutcome.FAIL -> (if (hard) R.drawable.ic_block else R.drawable.ic_warning) to (if (hard) ui.bad else ui.warn)
            CheckOutcome.UNKNOWN -> R.drawable.ic_info to ui.muted
        }
        addView(ui.icon(icon, color, 14))
        val text = if (hard) hardLabel(c) else forYou(c.label).replaceFirstChar { it.uppercase() }
        addView(ui.label(text, 12f, ui.secondary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ui.label(c.probability?.let(::prob) ?: "–", 12f, ui.secondary, monospace = true))
    }

    /** Hard checks read as what the reply does right: "States nothing you didn't say". */
    private fun hardLabel(c: CheckResult): String = when (c.id) {
        "unsupported_fact" -> ui.str(R.string.hc_unsupported_fact)
        "new_commitment" -> ui.str(R.string.hc_new_commitment)
        "commits_others" -> ui.str(R.string.hc_commits_others)
        "opposite_stance" -> ui.str(R.string.hc_opposite_stance)
        "crosses_boundary" -> ui.str(R.string.hc_crosses_boundary)
        "admits_fault" -> ui.str(R.string.hc_admits_fault)
        "unstated_declaration" -> ui.str(R.string.hc_unstated_declaration)
        "beyond_clarification" -> ui.str(R.string.hc_beyond_clarification)
        else -> ui.str(R.string.hc_avoid, forYou(c.label.removePrefix("Does what you ruled out: ")))
    }

    // ------------------------------------------------------------------ expanded

    private fun expandedSections(s: PanelState) {
        // Opening scrolls here, so the way to close it is right at the top of what opened.
        content.addView(clickable(ui.row(6).apply {
            minimumHeight = ui.dp(40)
            gravity = Gravity.CENTER_VERTICAL
            addView(ui.label(ui.str(R.string.hide_goal_analysis), 13f, ui.accent), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ui.icon(R.drawable.ic_expand_less, ui.accent, 18))
        }) { hideAnalysis() }, fullWidth())
        s.goal?.let { goal ->
            content.addView(ui.card(gapDp = 4).apply {
                addView(ui.row(6).apply {
                    addView(ui.label(ui.str(R.string.goal), 13f))
                    addView(ui.spacer())
                    addView(action(R.drawable.ic_edit, R.string.change_goal) { editingGoal = true; ownGoalOpen = s.ownGoal; redraw() })
                }, fullWidth())
                addView(ui.label(goal.summary, 13f, ui.text))
                if (goal.mustInclude.isNotEmpty()) addView(ui.label(ui.str(R.string.will_say) + " " + goal.mustInclude.joinToString(" · ") { forYou(it) }, 12f, ui.secondary))
                if (goal.mustAvoid.isNotEmpty()) addView(ui.label(ui.str(R.string.wont) + " " + goal.mustAvoid.joinToString(" · ") { forYou(it) }, 12f, ui.secondary))
                // The generic "what the summary says" line explains nothing; show promises only when a stance lists them.
                if (!s.ownGoal && goal.commitments.isNotEmpty()) addView(ui.label(ui.str(R.string.may_promise) + " " + goal.commitments.joinToString(" · ") { forYou(it) }, 12f, ui.secondary))
                addView(ui.label(ui.str(R.string.fine_tune), 12f, ui.muted).apply { setPadding(0, ui.dp(4), 0, 0) })
                addView(ui.flow().apply {
                    for (sw in GoalSwitch.applicable(s.stance.takeIf { !s.ownGoal })) addView(ui.chip(sw.label, sw in s.switches) {
                        // A switch is a deliberate small change to the same goal: redraft straight away, no extra "Draft again" tap.
                        // While replies are being drafted, the old drafts are dropped and drafting starts again.
                        val redraft = s.candidates != null || s.phase == Phase.DRAFTING || s.phase == Phase.CHECKING
                        session.toggleSwitch(sw)
                        if (redraft) session.draft()
                    })
                })
            })
        }
        s.analysis?.let { analysisCard(s, it) }
        val info = ui.card(gapDp = 3)
        info.addView(ui.label(ui.str(R.string.this_round), 13f))
        s.report?.let { info.addView(ui.label(reportText(it), 12f, ui.secondary)) }
        if (s.readLines.isNotEmpty()) {
            info.addView(clickable(ui.label(ui.str(if (readOpen) R.string.hide_read else R.string.show_read), 12f, ui.accent)) { readOpen = !readOpen; redraw() })
            if (readOpen) s.readLines.forEach { info.addView(ui.label(it, 12f, if (it.startsWith("You")) ui.secondary else ui.text)) }
        }
        val t = s.timing
        val total = listOfNotNull(t.read, t.analyse, t.draft, t.check).sum()
        if (total > 0) info.addView(ui.label(ui.str(R.string.round_cost, secs(total), String.format(Locale.US, "%.4f", s.cost), s.budgetUsed), 12f, ui.secondary))
        info.addView(ui.label(ui.str(R.string.round_id, session.roundId), 11f, ui.muted, monospace = true))
        info.addView(ui.row(6).apply {
            addView(ui.chip(ui.str(R.string.include_text), includeText) { includeText = !includeText; redraw() })
            addView(ui.spacer())
            addView(ui.ghostButton(ui.str(R.string.report_problem), ui.accent) { host.shareReport(includeText) })
        }, fullWidth())
        if (includeText) info.addView(ui.label(ui.str(R.string.include_text_note), 11f, ui.warn))
        content.addView(info)
    }

    private fun analysisCard(s: PanelState, a: Analysis) {
        val sel = s.selection ?: return
        val card = ui.card(gapDp = 4)
        card.addView(ui.row(6).apply {
            addView(ui.label(ui.str(R.string.analysis), 13f))
            addView(ui.spacer())
            addView(ui.tag(ui.str(R.string.experimental), ui.info))
        }, fullWidth())
        for (n in a.notices) card.addView(ui.label(n, 12f, ui.secondary))
        for (d in a.detected + a.cues) {
            card.addView(ui.label(d.behavior.label + (d.reading?.note?.let { " — $it" } ?: ""), 12f, ui.text))
        }
        a.tone?.let { card.addView(ui.label("${it.title}. ${it.detail}", 12f, ui.warn)) }
        a.friction?.let { f ->
            val them = f.other?.score ?: 0.0
            val you = f.self?.score ?: 0.0
            card.addView(ui.label(String.format(Locale.US, "Friction: them %.1f, you %.1f · %s %s", them, you, f.label.lowercase(), f.source.label).trim(), 12f, ui.secondary))
            f.other?.probabilities?.let { probs ->
                card.addView(ui.row(2).apply {
                    probs.forEachIndexed { i, p ->
                        val cell = View(context).apply {
                            background = ui.rounded(if (p >= 0.25) (if (i >= 3) ui.bad else ui.ok) else ui.raised, 1)
                            alpha = (0.35 + p).coerceAtMost(1.0).toFloat()
                        }
                        addView(cell, LinearLayout.LayoutParams(0, ui.dp(5), 1f))
                    }
                }, fullWidth())
            }
        }
        // All signals, two columns, densely packed.
        val spec = SceneCatalog.spec(sel.scene)
        val asked = spec.behaviors(sel).filter { it.id in a.probabilities }.sortedByDescending { a.probabilities[it.id] }
        if (asked.isNotEmpty()) {
            card.addView(ui.label(ui.str(R.string.all_signals, asked.size), 12f, ui.muted).apply { setPadding(0, ui.dp(4), 0, 0) })
            asked.chunked(2).forEach { pair ->
                card.addView(ui.row(10).apply {
                    pair.forEach { b ->
                        val p = a.probabilities.getValue(b.id)
                        addView(ui.column(gapDp = 1).apply {
                            addView(ui.row(4).apply {
                                addView(ui.label(b.label, 11f, if (p >= 0.7) ui.text else ui.muted, lines = 1), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                                addView(ui.label(prob(p), 11f, ui.secondary, monospace = true))
                            }, fullWidth())
                            addView(ui.bar(p, if (p >= 0.7) ui.accent else ui.muted))
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                }, fullWidth())
            }
        }
        if (a.missing.isNotEmpty()) card.addView(ui.label(ui.str(R.string.signals_missing, a.missing.size), 11f, ui.warn))
        content.addView(card)
    }

    // ------------------------------------------------------------------ helpers

    /** Goal items are written for the models ("states the user's condition"); the panel speaks to the user. */
    private fun forYou(text: String): String = text
        .replace("the user's", "your").replace("the user is", "you are").replace("the user cannot", "you can't")
        .replace("the user", "you")

    private companion object {
        const val COMPACT_OPTIONS = 4
        /** One ring size everywhere: opening anything never makes a card grow. */
        const val RING_DP = 58
    }

    private fun reportText(r: com.jev.overseas.core.session.ReadReport): String =
        (if (r.cached) ui.str(R.string.read_report_cached, r.messages, r.latestTurn) else ui.str(R.string.read_report, r.messages, r.screens, r.latestTurn)) +
            (if (r.stopReason == "start of the chat") " · " + ui.str(R.string.whole_chat) else "") +
            (if (!r.continuous) " · " + ui.str(R.string.gap_possible) else "") +
            (if (!r.returnedToBottom) " · " + ui.str(R.string.not_back_at_bottom) else "") + if (r.unreadable > 0) " · " + ui.str(R.string.unreadable_rows, r.unreadable) else ""

    private fun prob(p: Double): String = String.format(Locale.US, "%.2f", p).removePrefix("0").let { if (it.startsWith("1")) "1.0" else it }

    private fun secs(ms: Long): String = String.format(Locale.US, "%.1fs", ms / 1000.0)

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun <T : View> clickable(view: T, onClick: () -> Unit): T = view.apply {
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun input(): EditText = EditText(context).apply {
        setTextColor(ui.text)
        setHintTextColor(ui.muted)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        background = ui.rounded(ui.raised, 9, ui.accent)
        setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        maxLines = 4
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onFieldChange?.invoke() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    /** Opens the score breakdown for reply [index] (also used by the debug preview). */
    fun showBreakdown(index: Int) = openBreakdown(index)
}
