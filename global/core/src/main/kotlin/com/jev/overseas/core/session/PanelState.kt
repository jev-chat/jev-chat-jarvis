package com.jev.overseas.core.session

import com.jev.overseas.core.engine.Analysis
import com.jev.overseas.core.engine.AnalysisOutcome
import com.jev.overseas.core.engine.CandidateSet
import com.jev.overseas.core.engine.CandidateState
import com.jev.overseas.core.engine.Draft
import com.jev.overseas.core.engine.Goal
import com.jev.overseas.core.engine.GoalSwitch
import com.jev.overseas.core.engine.NextStep
import com.jev.overseas.core.engine.RunBudget
import com.jev.overseas.core.engine.SkipReason
import com.jev.overseas.core.scene.InputMode
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared
import com.jev.overseas.core.scene.StanceGroup
import com.jev.overseas.core.scene.StanceOption

enum class Phase { IDLE, READING, ANALYSING, ANALYSED, DRAFTING, CHECKING, CANDIDATES }

/** Why the shown candidates may no longer fit. */
enum class Stale {
    NONE,
    /** The user changed the goal: the replies must be drafted again before use. */
    GOAL,
    /** The chat changed after it was read: usable once the user says "Use anyway". */
    CHAT,
}

enum class ErrorAction { SETTINGS, RETRY, START_AGAIN, OPEN_CHAT }

enum class ErrorKind(val action: ErrorAction) {
    NO_KEY(ErrorAction.SETTINGS),
    AUTH(ErrorAction.SETTINGS),
    NETWORK(ErrorAction.RETRY),
    FAILED(ErrorAction.RETRY),
    BUDGET(ErrorAction.START_AGAIN),
    NOT_CONVERSATION(ErrorAction.OPEN_CHAT),
    UNSTABLE(ErrorAction.RETRY),
    UNAVAILABLE(ErrorAction.OPEN_CHAT),
}

data class PanelError(val kind: ErrorKind, val message: String)

/** Milliseconds per step of the last run, null when the step has not run. */
data class Timing(val read: Long? = null, val analyse: Long? = null, val draft: Long? = null, val check: Long? = null)

/**
 * Everything the panel draws. Immutable; the session emits a new one on every
 * change. Chat text appears only in [readLines]; close() resets the state.
 */
data class PanelState(
    val phase: Phase = Phase.IDLE,
    val selection: Selection? = null,
    /** True when the scene picker should be shown (first use, or the user opened it). */
    val choosingScene: Boolean = false,
    /** The picker is showing because this chat has not been set up yet. */
    val newChat: Boolean = false,
    val report: ReadReport? = null,
    /**
     * What was read, one line per message, for "Show what was read" in the expanded
     * view. In memory only, and gone after close (S11).
     */
    val readLines: List<String> = emptyList(),
    val analysis: Analysis? = null,
    val skip: AnalysisOutcome.Skipped? = null,
    val error: PanelError? = null,
    val stance: StanceOption? = null,
    val details: String = "",
    val switches: Set<GoalSwitch> = emptySet(),
    /** True when the goal is one the user wrote themselves. */
    val ownGoal: Boolean = false,
    val goal: Goal? = null,
    /** Drafts shown while they are being checked. */
    val drafts: List<Draft> = emptyList(),
    val candidates: CandidateSet? = null,
    val stale: Stale = Stale.NONE,
    /** Set while the chat-change banner is showing. */
    val chatChange: ChatChange? = null,
    /** Texts of replies the user marked "I've checked this". */
    val confirmed: Set<String> = emptySet(),
    /** Index of the reply waiting for "Replace what's in the box?". */
    val replacePending: Int? = null,
    /** A one-line message for the panel, such as why a fill was refused. */
    val notice: String? = null,
    /** True when [notice] reports something that did not happen (a refused fill, a copy instead). */
    val noticeIsWarning: Boolean = false,
    /** The user chose to send nothing. */
    val nothingToSend: Boolean = false,
    val expanded: Boolean = false,
    /** This round was read further back already; the button is not offered again. */
    val readFurther: Boolean = false,
    val budgetUsed: Int = 0,
    val budgetMax: Int = RunBudget.DEFAULT_MAX,
    val cost: Double = 0.0,
    val timing: Timing = Timing(),
) {
    val busy: Boolean get() = phase in setOf(Phase.READING, Phase.ANALYSING, Phase.DRAFTING, Phase.CHECKING)

    /** Stance groups the user may pick from now. */
    val groups: List<StanceGroup> get() = analysis?.let { Offer.groups(it) } ?: emptyList()

    /** The options behind "More". */
    val moreOptions: List<StanceOption> get() = analysis?.let { Offer.extras(it) } ?: emptyList()

    /** True when the user may write their own goal here (S3, S10). */
    val ownGoalAllowed: Boolean
        get() = analysis != null || skip?.reason in setOf(SkipReason.USER_WROTE_LAST, SkipReason.UNREADABLE)

    /** True when "Draft replies" can be pressed. */
    val canDraft: Boolean
        get() {
            val g = goal ?: return false
            if (busy || stance?.noReply == true) return false
            if (analysis == null && !(ownGoal && ownGoalAllowed)) return false
            if (analysis?.next == NextStep.SAFETY_HOLD && !ownGoal) return false
            if (stance?.input == InputMode.REQUIRED && details.isBlank()) return false
            return g.summary.isNotBlank()
        }

    /** True when reply [index] may be filled or copied now (S9). */
    fun usable(index: Int): Boolean {
        val v = candidates?.verdicts?.getOrNull(index) ?: return false
        if (!v.copyable || stale == Stale.GOAL || chatChange != null) return false
        if (v.state == CandidateState.AWAITING_CONFIRMATION && v.draft.text !in confirmed) return false
        return true
    }
}

/** Which options the panel offers for an analysis (S3). */
object Offer {
    fun groups(a: Analysis): List<StanceGroup> = if (a.next == NextStep.SAFETY_HOLD) emptyList() else a.groups

    fun extras(a: Analysis): List<StanceOption> = when (a.next) {
        // A threat: nothing is drafted unless the user writes the goal themselves.
        NextStep.SAFETY_HOLD -> listOf(Shared.NO_REPLY)
        NextStep.BOUNDARY -> a.extraOptions.filter { it.id == Shared.BRIEF_ACK.id || it.id == Shared.NO_REPLY.id }
        else -> a.extraOptions
    }

    fun all(a: Analysis): List<StanceOption> =
        (groups(a).flatMap { it.options } + extras(a) + listOfNotNull(a.defaultStance)).distinct()
}
