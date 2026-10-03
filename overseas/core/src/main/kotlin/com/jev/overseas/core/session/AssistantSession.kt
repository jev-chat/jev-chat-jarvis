package com.jev.overseas.core.session

import com.jev.overseas.core.engine.Analysis
import com.jev.overseas.core.engine.AnalysisOutcome
import com.jev.overseas.core.engine.Assistant
import com.jev.overseas.core.engine.CandidateSet
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.Goal
import com.jev.overseas.core.engine.GoalBuilder
import com.jev.overseas.core.engine.GoalSwitch
import com.jev.overseas.core.engine.NextStep
import com.jev.overseas.core.engine.RunBudget
import com.jev.overseas.core.engine.Sender
import com.jev.overseas.core.engine.SkipReason
import com.jev.overseas.core.net.ModelException
import com.jev.overseas.core.scene.RelationshipType
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.StanceOption
import com.jev.overseas.core.diagnostics.Diagnostics
import com.jev.overseas.core.diagnostics.Journal
import java.util.concurrent.Executor

/** One-off effects for the window, outside the drawn state. */
sealed class Effect {
    /** Fill or copy is done: fold the panel back into the bubble. */
    object Collapse : Effect()
}

/**
 * The panel's flow rules (S1–S15). The UI draws
 * [PanelState] and forwards taps to the intent methods; everything that decides
 * what may happen lives here and is tested offline.
 *
 * Intent methods are called on the UI thread and return at once; reading and
 * model calls run on [background]. Every piece of background work carries the
 * generation it started in, and its result is dropped when the generation has
 * moved on (S7): after a new analysis, a goal change, cancel or close.
 */
class AssistantSession(
    private val reader: ChatReader,
    private val assistantFor: () -> Assistant?,
    private val background: Executor,
    private val emit: (PanelState) -> Unit,
    private val target: ReplyTarget? = null,
    /** False when the user has not allowed writing into the message box: Fill becomes Copy. */
    private val fillEnabled: Boolean = true,
    private val effects: (Effect) -> Unit = {},
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    initialSelection: Selection? = null,
    /** Diagnostic events for the local log (no chat text; see Diagnostics). */
    private val journal: Journal = Journal.NONE,
    /** Which scene and relationship the user chose for each chat. Null: one selection for every chat. */
    private val memory: SelectionMemory? = null,
) {
    /** Short id that ties together the events of one round in a bug report. */
    private var round = "-"

    private var state = PanelState(selection = initialSelection)
    private var generation = 0
    private var goalVersion = 0

    // Kept in memory only; cleared on close (S11).
    private var conversation: Conversation? = null
    private var chatId: String? = null
    /**
     * With per-chat memory: the chat this panel is about (identified when the bubble
     * was tapped, or the chat of the current round). A read that lands in another
     * chat is not analysed with this chat's selection and is never remembered for it.
     */
    private var expectedChat: String? = null
    private var autoAnalyse = true
    /** Fingerprint the candidates are measured against: the read, or what the user accepted with "Use anyway". */
    private var baseline: String? = null
    private var changedTo: String? = null
    private var budget: RunBudget? = null
    private var assistant: Assistant? = null
    private val seen = ArrayList<String>()
    private var pending: (() -> Unit)? = null
    private var keptBatch: CandidateSet? = null
    private var retry: (() -> Unit)? = null

    val current: PanelState @Synchronized get() = state

    // ------------------------------------------------------------------ opening and selection

    /**
     * The bubble was tapped (S15). With a remembered scene and [autoAnalyse] on,
     * reading and analysis start at once; otherwise the scene picker shows first.
     */
    @Synchronized fun open(autoAnalyse: Boolean) {
        this.autoAnalyse = autoAnalyse
        if (assistantFor() == null) return noKey()
        if (hasRound) return resume(autoAnalyse)
        if (memory != null) return identifyChat(autoAnalyse)
        if (state.selection == null || !autoAnalyse) {
            set(state.copy(choosingScene = true))
            return
        }
        analyse()
    }

    /**
     * With per-chat memory: a quick read of the bottom screen (no model call) says
     * which chat this is. A chat the user has set up before is analysed at once
     * with its own scene and relationship; a new chat shows the picker first.
     */
    private fun identifyChat(autoAnalyse: Boolean) {
        dropWork()
        val gen = generation
        set(PanelState(selection = state.selection, phase = Phase.READING, expanded = state.expanded))
        work(gen) {
            val read = reader.readLatest()
            synchronized(this) {
                if (gen != generation) return@synchronized
                if (read !is ReadResult.Ok) return@synchronized failRead(read) { open(autoAnalyse) }
                log(Diagnostics.action(round, "identify", "known" to (memory?.get(read.chatId) != null)))
                startFor(read.chatId, autoAnalyse)
            }
        }
    }

    /** Analyse with the chat's remembered selection, or ask for one. */
    private fun startFor(chat: String, autoAnalyse: Boolean) {
        val remembered = memory?.get(chat)
        expectedChat = chat
        if (remembered != null && autoAnalyse) {
            set(state.copy(selection = remembered, choosingScene = false, newChat = false))
            startRun(null)
        } else {
            dropWork()
            conversation = null; this.chatId = null; baseline = null; changedTo = null
            set(PanelState(selection = remembered, choosingScene = true, newChat = remembered == null, expanded = state.expanded))
        }
    }

    /** True while there is a round to come back to after the panel was folded into the bubble. */
    val hasRound: Boolean
        @Synchronized get() = state.busy || state.analysis != null || state.skip != null || state.candidates != null

    /**
     * Reopening a folded panel. A quick local read (no model call) decides: the same
     * chat, unchanged, gets the last round back as it was; another chat, or new
     * messages in this one (including what the user just sent), start a fresh round.
     * A screen that cannot be read leaves the last round showing.
     */
    private fun resume(autoAnalyse: Boolean) {
        if (state.busy) return
        val gen = generation
        work(gen) {
            val read = reader.readLatest()
            synchronized(this) {
                if (gen != generation) return@synchronized
                if (read !is ReadResult.Ok) {
                    // Keep the round, but say why nothing can be used here, in words that fit what happened.
                    log(Diagnostics.action(round, "resume", "result" to "kept_unreadable"))
                    set(state.copy(notice = unreadableNotice(read), noticeIsWarning = true))
                    return@synchronized
                }
                if (read.chatId == chatId && read.fingerprint == baseline) {
                    log(Diagnostics.action(round, "resume", "result" to "kept"))
                    // Back in the chat: the "not in a chat" warning no longer applies.
                    if (state.notice == NOT_IN_A_CHAT) set(state.copy(notice = null, noticeIsWarning = false))
                    return@synchronized
                }
                log(Diagnostics.action(round, "resume", "result" to if (read.chatId == chatId) "chat_moved_on" else "other_chat"))
                if (memory != null && read.chatId != chatId) return@synchronized startFor(read.chatId, autoAnalyse)
                if (autoAnalyse && state.selection != null) {
                    startRun(null)
                } else {
                    resetAnalysis(state.selection ?: return@synchronized)
                }
            }
        }
    }

    /**
     * "Analyse this chat", "Try again" after a failed read: start from whatever chat
     * is on screen now, with that chat's own scene, exactly as a bubble tap would.
     */
    @Synchronized fun reopen() {
        if (state.busy) return
        dropWork()
        conversation = null; chatId = null; baseline = null; changedTo = null
        set(PanelState(selection = state.selection, expanded = state.expanded))
        open(autoAnalyse)
    }

    @Synchronized fun showScenePicker() = set(state.copy(choosingScene = true))

    /** Back from the scene picker to the round it was opened from, without a new (paid) analysis. */
    @Synchronized fun closeScenePicker() {
        if (!state.choosingScene || !hasRound) return
        set(state.copy(choosingScene = false))
    }

    @Synchronized fun selectScene(scene: Scene) {
        val old = state.selection
        if (old?.scene == scene) return
        resetAnalysis(Selection(scene, null, false))
    }

    @Synchronized fun selectRelationship(relationship: RelationshipType?) {
        val sel = state.selection ?: return
        if (sel.relationship == relationship) return
        resetAnalysis(sel.copy(relationship = relationship))
    }

    @Synchronized fun toggleConflict() {
        val sel = state.selection ?: return
        resetAnalysis(sel.copy(conflict = !sel.conflict))
    }

    /** S6: a different scene, relationship or conflict setting needs a new analysis. */
    private fun resetAnalysis(selection: Selection) {
        dropWork()
        conversation = null; chatId = null; baseline = null; changedTo = null
        set(PanelState(selection = selection, choosingScene = true, expanded = state.expanded))
    }

    // ------------------------------------------------------------------ analysis

    /** Read the chat and analyse it, with a fresh run budget (S8). Also "Start again" and "Retry" after a failed read. */
    @Synchronized fun analyse() = startRun(null)

    /**
     * S13b: read again after the chat changed. The chosen stance, details and
     * switches carry over when the new analysis still offers the same option; a
     * goal the user wrote is always kept.
     */
    @Synchronized fun readAgain() {
        if (state.chatChange?.otherChat == true) return open(autoAnalyse)
        log(Diagnostics.action(round, "read_again"))
        // Fine-tune switches belong to the replies they produced; a fresh read starts without them.
        val reuse = Reuse(state.stance, state.details, emptySet(), state.goal?.summary?.takeIf { state.ownGoal })
        startRun(reuse)
    }

    /** They refer to something Jev did not read. Read about twice as far back and analyse again. */
    @Synchronized fun readFurther() {
        if (state.busy || state.readFurther) return
        log(Diagnostics.action(round, "read_further"))
        startRun(Reuse(state.stance, state.details, emptySet(), state.goal?.summary?.takeIf { state.ownGoal }, wide = true))
    }

    /** What carries over to the next read. The stance is matched by its whole content, never by id alone. */
    private data class Reuse(val stance: StanceOption?, val details: String, val switches: Set<GoalSwitch>, val ownSummary: String?,
                             val wide: Boolean = false)

    private fun startRun(reuse: Reuse?) {
        val assistant = assistantFor() ?: return noKey()
        val selection = state.selection ?: return set(state.copy(choosingScene = true))
        dropWork()
        val gen = generation
        round = newRoundId()
        log(Diagnostics.roundStart(round, selection, if (reuse != null) "read_again" else "analyse"))
        val b = RunBudget(clock = clock)
        budget = b
        this.assistant = assistant
        seen.clear()
        set(PanelState(selection = selection, phase = Phase.READING, expanded = state.expanded, budgetMax = b.maxRequests,
            readFurther = reuse?.wide == true))
        val expected = expectedChat
        work(gen) { runAnalysis(gen, assistant, b, selection, reuse, expected) }
    }

    private fun runAnalysis(gen: Int, assistant: Assistant, b: RunBudget, selection: Selection, reuse: Reuse?, expected: String?) {
        val t0 = clock()
        val read = if (reuse?.wide == true) reader.readMore() else reader.readCurrent()
        val readMs = clock() - t0
        val conv = synchronized(this) {
            if (gen != generation) return
            log(Diagnostics.read(round, read, readMs))
            if (read !is ReadResult.Ok) return failRead(read) { startRun(reuse) }
            if (memory != null && expected != null && read.chatId != expected) {
                // This selection was chosen for another chat. Never analyse or remember it here.
                log(Diagnostics.action(round, "read", "result" to "other_chat"))
                return startFor(read.chatId, autoAnalyse)
            }
            conversation = read.conversation; chatId = read.chatId; baseline = read.fingerprint; changedTo = null
            if (memory != null && expected != null) {
                memory.put(read.chatId, selection)
                log(Diagnostics.action(round, "remember_scene"))
            }
            expectedChat = read.chatId
            set(state.copy(phase = Phase.ANALYSING, report = read.report, readLines = lines(read.conversation), timing = Timing(read = readMs)))
            read.conversation
        }
        val t1 = clock()
        val outcome = assistant.analyze(conv, selection, b)
        val analyseMs = clock() - t1
        synchronized(this) {
            if (gen != generation) return
            log(Diagnostics.analysis(round, outcome, analyseMs, b))
            val timing = state.timing.copy(analyse = analyseMs)
            when (outcome) {
                is AnalysisOutcome.Failed -> modelError(outcome.error, timing) { startRun(reuse) }
                is AnalysisOutcome.Skipped -> {
                    if (outcome.reason == SkipReason.BUDGET) {
                        set(withBudget(state.copy(phase = Phase.IDLE, timing = timing, error = PanelError(ErrorKind.BUDGET, outcome.message))))
                        return
                    }
                    set(withBudget(state.copy(phase = Phase.ANALYSED, skip = outcome, timing = timing)))
                    if (reuse?.ownSummary != null && state.ownGoalAllowed) applyOwnGoal(reuse.ownSummary, reuse.switches, draftNow = true)
                }
                is AnalysisOutcome.Ready -> {
                    set(withBudget(state.copy(phase = Phase.ANALYSED, analysis = outcome.analysis, timing = timing)))
                    afterAnalysis(outcome.analysis, reuse)
                }
            }
        }
    }

    private fun afterAnalysis(analysis: Analysis, reuse: Reuse?) {
        if (reuse?.ownSummary != null) return applyOwnGoal(reuse.ownSummary, reuse.switches, draftNow = true)
        var notice: String? = null
        if (reuse?.stance != null) {
            val same = Offer.all(analysis).firstOrNull { it == reuse.stance }
            if (same != null && !same.noReply) {
                applyStance(same, reuse.details, reuse.switches)
                return beginDraft(emptyList())
            }
            notice = CHANGED_QUESTION
        }
        // S2: nothing to decide, so draft with the default goal straight away; the goal stays visible and editable.
        if (analysis.next == NextStep.DIRECT_DRAFT && analysis.defaultStance != null) {
            applyStance(analysis.defaultStance, "", emptySet())
            if (notice != null) set(state.copy(notice = notice))
            beginDraft(emptyList())
        } else if (notice != null) {
            set(state.copy(notice = notice))
        }
    }

    // ------------------------------------------------------------------ goal

    @Synchronized fun chooseStance(option: StanceOption) {
        val analysis = state.analysis ?: return
        if (Offer.all(analysis).none { it == option }) return
        if (option.noReply) {
            // S4: nothing to send, so nothing is asked of a model.
            dropGoalWork()
            set(state.copy(stance = option, details = "", ownGoal = false, goal = null, candidates = null, drafts = emptyList(),
                stale = Stale.NONE, chatChange = null, nothingToSend = true, notice = null, phase = Phase.ANALYSED))
            return
        }
        val details = if (option == state.stance) state.details else ""
        dropGoalWork()
        // A new stance is a new goal: switches from the last replies do not carry over.
        applyStance(option, details, emptySet())
    }

    @Synchronized fun setDetails(text: String) {
        val stance = state.stance ?: return
        if (text == state.details || state.ownGoal) return
        dropGoalWork()
        applyStance(stance, text, emptySet())
    }

    @Synchronized fun toggleSwitch(switch: GoalSwitch) {
        val switches = if (switch in state.switches) state.switches - switch else state.switches + switch
        dropGoalWork()
        when {
            state.ownGoal && state.goal != null -> applyOwnGoal(state.goal!!.summary, switches, draftNow = false)
            state.stance != null && !state.stance!!.noReply -> applyStance(state.stance!!, state.details, switches)
            else -> set(state.copy(switches = switches))
        }
    }

    /** "Write my own goal", or editing the summary line. Allowed after any analysis and after S10 skips. */
    @Synchronized fun editSummary(summary: String) {
        if (!state.ownGoalAllowed || summary.isBlank()) return
        dropGoalWork()
        applyOwnGoal(summary, emptySet(), draftNow = false)
    }

    private fun applyStance(option: StanceOption, details: String, switches: Set<GoalSwitch>) {
        goalVersion++
        val goal = GoalBuilder.fromStance(option, details, switches, goalVersion, state.selection?.relationship?.id)
        set(state.copy(stance = option, details = details, switches = switches, ownGoal = false, goal = goal,
            nothingToSend = false, stale = staleAfterGoalChange(), notice = null, error = null))
    }

    private fun applyOwnGoal(summary: String, switches: Set<GoalSwitch>, draftNow: Boolean) {
        val goal = GoalBuilder.fromEditedSummary(state.goal, summary, switches).copy(version = ++goalVersion)
        set(state.copy(stance = null, details = "", switches = switches, ownGoal = true, goal = goal,
            nothingToSend = false, stale = staleAfterGoalChange(), error = null))
        if (draftNow) beginDraft(emptyList())
    }

    private fun staleAfterGoalChange() = if (state.candidates != null) Stale.GOAL else Stale.NONE

    // ------------------------------------------------------------------ drafting

    /** "Draft replies". */
    @Synchronized fun draft() {
        if (!state.canDraft) return
        guarded { beginDraft(emptyList()) }
    }

    /** "Another batch": new replies for the same goal, told not to repeat the earlier ones (S8). */
    @Synchronized fun anotherBatch() {
        if (!state.canDraft || state.candidates == null || state.stale == Stale.GOAL) return
        guarded { beginDraft(seen.toList()) }
    }

    private fun beginDraft(previous: List<String>) {
        val conv = conversation ?: return
        val goal = state.goal ?: return
        val assistant = assistant ?: return
        val b = budget ?: return
        val selection = state.selection ?: return
        if (b.stop != null) {
            set(withBudget(state.copy(error = PanelError(ErrorKind.BUDGET, Assistant.budgetMessage(b)))))
            return
        }
        generation++
        val gen = generation
        val boundary = state.analysis?.boundaryActive == true
        if (previous.isEmpty()) seen.clear()
        // "Another batch" that fails must not lose the replies already paid for.
        keptBatch = if (previous.isNotEmpty()) state.candidates else null
        log(Diagnostics.goal(round, goal, state.ownGoal, state.switches))
        set(state.copy(phase = Phase.DRAFTING, drafts = emptyList(), candidates = null, stale = Stale.NONE,
            confirmed = emptySet(), replacePending = null, error = null, nothingToSend = false,
            timing = state.timing.copy(draft = null, check = null)))
        val situation = state.analysis?.situation()
        work(gen) { runDraft(gen, assistant, conv, selection, goal, boundary, b, previous, situation) }
    }

    private fun runDraft(gen: Int, assistant: Assistant, conv: Conversation, selection: Selection, goal: Goal,
                         boundary: Boolean, b: RunBudget, previous: List<String>, situation: Map<String, Any?>?) {
        val t0 = clock()
        var draftMs: Long? = null
        val result = assistant.draftAndCheck(conv, selection, goal, boundary, b, previous, situation = situation, onDrafts = { drafts ->
            synchronized(this) {
                if (gen != generation) return@synchronized
                draftMs = clock() - t0
                set(withBudget(state.copy(phase = Phase.CHECKING, drafts = drafts, timing = state.timing.copy(draft = draftMs))))
            }
        })
        val total = clock() - t0
        synchronized(this) {
            if (gen != generation) return
            val timing = state.timing.copy(draft = draftMs ?: total, check = draftMs?.let { total - it })
            log(Diagnostics.candidates(round, result, timing.draft, timing.check, previous.size, b))
            if (!applyCandidates(result, timing)) return
        }
        // S13: the first check, as soon as the replies are there. A failed read is skipped silently here.
        val read = reader.readLatest()
        synchronized(this) {
            if (gen != generation || read !is ReadResult.Ok) return
            noteChange(read)
        }
    }

    /** Returns false when there is nothing to show as candidates. */
    private fun applyCandidates(result: CandidateSet, timing: Timing): Boolean {
        val kept = keptBatch
        keptBatch = null
        if (kept != null && (result.error != null || result.verdicts.isEmpty())) {
            if (result.error?.kind == ModelException.Kind.CANCELLED) return false
            set(state.copy(candidates = kept))
            result.error?.let { e -> modelError(e, timing) { anotherBatch() } } ?: run {
                retry = { anotherBatch() }
                set(withBudget(state.copy(phase = Phase.CANDIDATES, drafts = emptyList(), timing = timing,
                    error = PanelError(ErrorKind.FAILED, result.message ?: "No new replies this time."))))
            }
            return false
        }
        result.error?.let { e ->
            modelError(e, timing) { draft() }
            return false
        }
        if (result.verdicts.isEmpty()) {
            val b = budget
            val kind = if (b != null && b.stop != null && b.stop != RunBudget.Stop.CANCELLED) ErrorKind.BUDGET else ErrorKind.FAILED
            retry = { draft() }
            set(withBudget(state.copy(phase = Phase.ANALYSED, drafts = emptyList(), timing = timing,
                error = PanelError(kind, result.message ?: "No replies this time."))))
            return false
        }
        seen.addAll(result.verdicts.map { it.draft.text })
        // A plain message, never the warning style left over from an earlier refusal.
        set(withBudget(state.copy(phase = Phase.CANDIDATES, candidates = result, drafts = emptyList(), stale = Stale.NONE,
            timing = timing, notice = result.message, noticeIsWarning = false)))
        return true
    }

    /** "Retry" on a reply whose check did not complete: check the same text again. */
    @Synchronized fun recheck() {
        val set = state.candidates ?: return
        val conv = conversation ?: return
        val goal = state.goal ?: return
        val assistant = assistant ?: return
        val b = budget ?: return
        val selection = state.selection ?: return
        if (state.busy || set.verdicts.none { it.state == com.jev.overseas.core.engine.CandidateState.FAILED }) return
        if (b.stop != null) return set(withBudget(state.copy(error = PanelError(ErrorKind.BUDGET, Assistant.budgetMessage(b)))))
        log(Diagnostics.action(round, "recheck"))
        generation++
        val gen = generation
        val boundary = state.analysis?.boundaryActive == true
        set(state.copy(phase = Phase.CHECKING, error = null))
        work(gen) {
            val result = assistant.recheck(conv, selection, goal, boundary, b, set)
            synchronized(this) {
                if (gen != generation) return@synchronized
                log(Diagnostics.candidates(round, result, null, null, 0, b))
                if (result.error != null) {
                    set(state.copy(phase = Phase.CANDIDATES))
                    return@synchronized
                }
                set(withBudget(state.copy(phase = Phase.CANDIDATES, candidates = result, notice = result.message, noticeIsWarning = false)))
            }
        }
    }

    @Synchronized fun confirmChecked(index: Int) {
        val v = state.candidates?.verdicts?.getOrNull(index) ?: return
        set(state.copy(confirmed = state.confirmed + v.draft.text))
    }

    // ------------------------------------------------------------------ chat changes (S13)

    /**
     * Re-reads the chat (locally, no model call) and runs [action] only if it has
     * not changed since it was read. If it has, the banner shows and [action] waits
     * for "Use anyway". An unreadable screen does not block the action.
     */
    private fun guarded(action: () -> Unit) {
        val gen = generation
        work(gen) {
            val read = reader.readLatest()
            synchronized(this) {
                if (gen != generation) return@synchronized
                if (read is ReadResult.Ok && noteChange(read)) {
                    pending = action
                    return@synchronized
                }
                if (read is ReadResult.Ok && read.report.scrolledToLatest > 0) set(state.copy(notice = JUMPED, noticeIsWarning = false))
                action()
            }
        }
    }

    /** Shows the banner when [read] differs from what the candidates are based on. Returns true when it does. */
    private fun noteChange(read: ReadResult.Ok): Boolean {
        val base = baseline ?: return false
        val readChat = chatId
        if (readChat != null && read.chatId != readChat) {
            // Another chat is on screen: these replies were written for someone else. Only a fresh analysis helps.
            changedTo = null
            log(Diagnostics.action(round, "chat_change", "result" to "other_chat"))
            set(state.copy(chatChange = ChatChange(OTHER_CHAT, null, otherChat = true),
                stale = if (state.stale == Stale.GOAL) Stale.GOAL else if (state.candidates != null) Stale.CHAT else Stale.NONE))
            return true
        }
        if (read.fingerprint == base) return false
        changedTo = read.fingerprint
        val old = conversation ?: return false
        val change = ChatDiff.describe(old, read.conversation)
        log(Diagnostics.chatChange(round, change))
        set(state.copy(chatChange = change, stale = if (state.stale == Stale.GOAL) Stale.GOAL else if (state.candidates != null) Stale.CHAT else Stale.NONE))
        return true
    }

    /** S13a: keep the current replies; a later change shows the banner again. */
    @Synchronized fun useAnyway() {
        if (state.chatChange == null || state.chatChange!!.otherChat) return
        log(Diagnostics.action(round, "use_anyway"))
        baseline = changedTo ?: baseline
        changedTo = null
        set(state.copy(chatChange = null, stale = if (state.stale == Stale.CHAT) Stale.NONE else state.stale))
        val action = pending ?: return
        pending = null
        // The waiting action may touch the message box or the clipboard: never on the UI thread.
        val gen = generation
        work(gen) { synchronized(this) { if (gen == generation) action() } }
    }

    // ------------------------------------------------------------------ copy and fill

    @Synchronized fun copy(index: Int) {
        if (!state.usable(index)) return
        val text = state.candidates!!.verdicts[index].draft.text
        guarded { doCopy(text, notice = null) }
    }

    /** S14: same chat, unchanged, the user's own text kept unless they agree to replace it. */
    @Synchronized fun fill(index: Int) = fillAt(index, replaceConfirmed = false)

    @Synchronized fun confirmReplace() {
        val index = state.replacePending ?: return
        set(state.copy(replacePending = null))
        fillAt(index, replaceConfirmed = true)
    }

    @Synchronized fun cancelReplace() = set(state.copy(replacePending = null))

    private fun fillAt(index: Int, replaceConfirmed: Boolean) {
        if (!state.usable(index)) return
        val text = state.candidates!!.verdicts[index].draft.text
        if (!fillEnabled) return copy(index)
        val gen = generation
        work(gen) {
            val read = reader.readLatest()
            synchronized(this) {
                if (gen != generation) return@synchronized
                if (read !is ReadResult.Ok) {
                    log(Diagnostics.action(round, "fill", "result" to "refused_unreadable"))
                    set(state.copy(notice = if (read == ReadResult.NotConversation || read == ReadResult.Unavailable) OPEN_THE_CHAT
                        else unreadableNotice(read), noticeIsWarning = true))
                    return@synchronized
                }
                if (read.report.scrolledToLatest > 0) log(Diagnostics.action(round, "fill", "jumped_to_latest" to true))
                if (read.chatId != chatId) {
                    log(Diagnostics.action(round, "fill", "result" to "refused_other_chat"))
                    set(state.copy(notice = NOT_SAME_CHAT, noticeIsWarning = true))
                    return@synchronized
                }
                if (noteChange(read)) {
                    pending = { fillAt(index, replaceConfirmed) }
                    return@synchronized
                }
                writeBox(index, text, replaceConfirmed)
            }
        }
    }

    private fun writeBox(index: Int, text: String, replaceConfirmed: Boolean) {
        val target = target ?: return
        when (target.boxState()) {
            BoxState.HAS_TEXT -> if (!replaceConfirmed) {
                log(Diagnostics.action(round, "fill", "result" to "asked_replace"))
                set(state.copy(replacePending = index))
                return
            }
            BoxState.MISSING -> return doCopy(text, COPIED_INSTEAD)
            BoxState.EMPTY -> {}
        }
        if (target.fill(text)) {
            log(Diagnostics.action(round, "fill", "result" to "filled", "index" to index, "chars" to text.length, "replaced" to replaceConfirmed))
            set(state.copy(notice = null, noticeIsWarning = false))
            effects(Effect.Collapse)
        } else {
            doCopy(text, COPIED_INSTEAD)
        }
    }

    private fun doCopy(text: String, notice: String?) {
        target?.copy(text)
        log(Diagnostics.action(round, "copy", "fallback" to (notice != null), "chars" to text.length))
        set(state.copy(notice = notice, noticeIsWarning = notice != null))
        // A copy that replaced a fill stays on screen so "Copied instead" can be read.
        if (notice == null) effects(Effect.Collapse)
    }

    // ------------------------------------------------------------------ panel and lifecycle

    @Synchronized fun setExpanded(expanded: Boolean) = set(state.copy(expanded = expanded))

    @Synchronized fun dismissNotice() = set(state.copy(notice = null, noticeIsWarning = false))

    /** Runs the step that failed again ("Retry"). */
    @Synchronized fun retry() {
        log(Diagnostics.action(round, "retry"))
        val r = retry ?: return analyse()
        retry = null
        r()
    }

    /** Stops whatever is running. Late results are dropped. While drafting, the run and its budget stay. */
    @Synchronized fun cancel() {
        log(Diagnostics.action(round, "cancel", "phase" to state.phase.name))
        if (state.phase == Phase.DRAFTING || state.phase == Phase.CHECKING) {
            keptBatch?.let { set(state.copy(candidates = it)) }
            keptBatch = null
            dropGoalWork()
            set(state.copy(phase = if (state.candidates != null) Phase.CANDIDATES else Phase.ANALYSED, drafts = emptyList()))
            return
        }
        dropWork()
        val phase = when {
            state.candidates != null -> Phase.CANDIDATES
            state.analysis != null || state.skip != null -> Phase.ANALYSED
            else -> Phase.IDLE
        }
        set(state.copy(phase = phase, drafts = emptyList()))
    }

    /** S11: cancel and forget the chat. Only the selection and panel size survive. */
    @Synchronized fun close() {
        if (hasRound) log(Diagnostics.action(round, "close"))
        dropWork()
        conversation = null; chatId = null; baseline = null; changedTo = null; expectedChat = null
        assistant = null; budget = null; seen.clear()
        reader.forget()
        set(PanelState(selection = state.selection))
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Runs [block] on the background executor. An unexpected exception becomes an
     * error card and a diagnostic event instead of taking down the service;
     * the accessibility service would otherwise be switched off by the system.
     */
    private fun work(gen: Int, block: () -> Unit) {
        background.execute {
            try {
                block()
            } catch (e: Throwable) {
                if (e is InterruptedException) Thread.currentThread().interrupt()
                synchronized(this) {
                    log(Diagnostics.crash(round, e))
                    if (gen != generation) return@synchronized
                    val phase = when {
                        state.candidates != null -> Phase.CANDIDATES
                        state.analysis != null || state.skip != null -> Phase.ANALYSED
                        else -> Phase.IDLE
                    }
                    retry = null
                    set(state.copy(phase = phase, drafts = emptyList(),
                        error = PanelError(ErrorKind.FAILED, "Something went wrong inside Jev. Tap Retry.")))
                }
            }
        }
    }

    private fun dropWork() {
        generation++
        budget?.cancel()
        pending = null
        retry = null
    }

    /** A goal change drops replies in flight for the old goal, but keeps the run and its budget. */
    private fun dropGoalWork() {
        generation++
        pending = null
        if (state.phase == Phase.DRAFTING || state.phase == Phase.CHECKING) {
            set(state.copy(phase = if (state.candidates != null) Phase.CANDIDATES else Phase.ANALYSED, drafts = emptyList()))
        }
    }

    private fun unreadableNotice(read: ReadResult): String = when (read) {
        ReadResult.Unstable -> STILL_MOVING
        ReadResult.NotAtLatest -> NOT_AT_LATEST
        ReadResult.GroupChat -> GROUP_CHAT
        else -> NOT_IN_A_CHAT
    }

    private fun noKey() = set(state.copy(error = PanelError(ErrorKind.NO_KEY, "Add your OpenRouter key in Settings."), choosingScene = false))

    private fun failRead(read: ReadResult, again: () -> Unit) {
        retry = again
        val error = when (read) {
            ReadResult.NotConversation -> PanelError(ErrorKind.NOT_CONVERSATION, "Open a one-to-one chat")
            ReadResult.Unstable -> PanelError(ErrorKind.UNSTABLE, "The chat was still moving. Try again.")
            ReadResult.NotAtLatest -> PanelError(ErrorKind.UNSTABLE, "Couldn't reach the newest message. Scroll to the bottom of the chat and try again.")
            ReadResult.ChatChanged -> PanelError(ErrorKind.UNSTABLE, "The chat changed while Jev was reading. Try again in the chat you want.")
            ReadResult.GroupChat -> PanelError(ErrorKind.NOT_CONVERSATION, GROUP_CHAT)
            else -> PanelError(ErrorKind.UNAVAILABLE, "Open a one-to-one chat in WhatsApp")
        }
        set(state.copy(phase = Phase.IDLE, error = error))
    }

    private fun modelError(e: ModelException, timing: Timing, again: () -> Unit) {
        if (e.kind == ModelException.Kind.CANCELLED) return
        log(Diagnostics.error(round, if (state.goal != null) "drafting" else "analysis", e, budget))
        retry = again
        val error = when {
            e.status == 402 -> PanelError(ErrorKind.NETWORK, "Your OpenRouter credit has run out. Top it up, then tap Retry.")
            else -> null
        } ?: when (e.kind) {
            ModelException.Kind.AUTH -> PanelError(ErrorKind.AUTH, "OpenRouter refused the key. Check it in Settings.")
            ModelException.Kind.NETWORK -> PanelError(ErrorKind.NETWORK, "No internet connection. Check your connection and tap Retry.")
            ModelException.Kind.TIMEOUT -> PanelError(ErrorKind.NETWORK, "The model took too long to answer. Tap Retry.")
            ModelException.Kind.SERVER -> PanelError(ErrorKind.NETWORK, "OpenRouter had a problem on its side. Tap Retry in a moment.")
            ModelException.Kind.RATE_LIMITED -> PanelError(ErrorKind.NETWORK, "Too many requests right now. Wait a few seconds and tap Retry.")
            else -> PanelError(ErrorKind.FAILED, "The model's answer could not be used. Tap Retry.")
        }
        val phase = when {
            state.candidates != null -> Phase.CANDIDATES
            state.analysis != null || state.skip != null -> Phase.ANALYSED
            else -> Phase.IDLE
        }
        set(withBudget(state.copy(phase = phase, drafts = emptyList(), timing = timing, error = error)))
    }

    private fun lines(c: Conversation): List<String> = c.messages.map { m ->
        val who = when (m.sender) { Sender.USER -> "You"; Sender.OTHER -> "Them"; Sender.UNKNOWN -> "?" }
        val quote = m.replyTo?.let { q -> " [replying to ${if (q.sender == Sender.USER) "you" else "them"}: ${q.text.take(60)}]" } ?: ""
        "$who: ${m.text}$quote"
    }

    private fun log(event: Map<String, Any?>) = runCatching { journal.record(event) }

    private fun newRoundId(): String = java.lang.Long.toHexString(java.util.Random().nextLong() and 0xFFFFFF).padStart(6, '0')

    /**
     * Text of the current round for a bug report, only when the user ticks
     * "include the goal and replies": the goal line and the drafted replies. Never
     * the chat messages.
     */
    @Synchronized fun roundText(): Map<String, Any?>? {
        val goal = state.goal ?: return null
        return linkedMapOf(
            "round" to round,
            "goal" to goal.summary,
            "replies" to (state.candidates?.verdicts?.map { it.draft.text } ?: state.drafts.map { it.text }),
        )
    }

    /** The id shown on the panel, so a report can point at one round. */
    val roundId: String @Synchronized get() = round

    private fun withBudget(s: PanelState): PanelState {
        val b = budget ?: return s
        return s.copy(budgetUsed = b.used, budgetMax = b.maxRequests, cost = b.cost)
    }

    private fun set(s: PanelState) {
        state = s
        emit(s)
    }

    companion object {
        const val CHANGED_QUESTION = "Their new message changes what's being asked."
        const val OTHER_CHAT = "This is a different chat. These replies were written for the chat that was read."
        const val COPIED_INSTEAD = "Couldn't fill. Copied instead."
        const val NOT_SAME_CHAT = "This isn't the chat that was read. Nothing was filled."
        const val NOT_IN_A_CHAT = "You're not in a chat right now. Open the chat to use this round, or open another chat for a new one."
        const val OPEN_THE_CHAT = "Open the chat that was read, then try again."
        const val STILL_MOVING = "The chat was still moving, so it couldn't be checked. Tap again in a moment."
        const val NOT_AT_LATEST = "Couldn't reach the newest message to check it. Scroll to the bottom and try again."
        const val JUMPED = "Jumped to the latest message to check for new ones."
        const val GROUP_CHAT = "Jev works in one-to-one chats only. This is a group."
    }
}
