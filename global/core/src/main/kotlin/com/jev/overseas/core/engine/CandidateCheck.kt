package com.jev.overseas.core.engine

import com.jev.overseas.core.net.Answer
import com.jev.overseas.core.net.DecisionsResult
import com.jev.overseas.core.scene.Level
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared

enum class CandidateState {
    /** Passed every hard check and every required item. */
    ELIGIBLE,
    /** A hard violation was found. No score is shown and the reply cannot be copied. */
    NEEDS_REWRITE,
    /** A required item is missing. */
    NEEDS_EDIT,
    /** A hard check landed in the unsure band; the user is asked to look at it. */
    AWAITING_CONFIRMATION,
    /** Checks passed but scoring did not come back. Copyable, not ranked. */
    UNSCORED,
    /** The checks themselves could not be completed. Not copyable. */
    FAILED,
}

enum class CheckOutcome { PASS, FAIL, UNSURE, UNKNOWN }

data class CheckResult(val id: String, val label: String, val probability: Double?, val outcome: CheckOutcome)

data class ScorePart(
    /** Probability-weighted level, 0–5, unrounded. */
    val value: Double,
    /** The short text of the nearest level. */
    val label: String,
    /** True when the probability sits on two non-adjacent levels. */
    val inconsistent: Boolean,
    val probabilities: List<Double>,
    /** True when [value] was lowered to 3.0 because a required item or a user detail is missing. */
    val capped: Boolean = false,
)

data class Verdict(
    val draft: Draft,
    val state: CandidateState,
    /** Every hard check that was asked, with its outcome. */
    val hardChecks: List<CheckResult>,
    /** Required items and the "picks up what they said" check. */
    val checklist: List<CheckResult>,
    val g: ScorePart?,
    val e: ScorePart?,
    /** S = 0.7 G + 0.3 E, one decimal; E alone when the goal has no decision; null when not scored. */
    val total: Double?,
    /** Plain sentences for the panel: what is wrong, or what to look at. */
    val reasons: List<String>,
) {
    val copyable: Boolean
        get() = state != CandidateState.NEEDS_REWRITE && state != CandidateState.FAILED

    val scored: Boolean get() = total != null && state != CandidateState.NEEDS_REWRITE && state != CandidateState.FAILED

    /** Jev's judgement was split on G or E: the score is shown with a marker and never gets Top pick. */
    val unsure: Boolean get() = g?.inconsistent == true || e?.inconsistent == true

    /**
     * "N of M checks passed", the same count on the card and in the breakdown:
     * every hard check and every required item; "picks up what they said" is a
     * quality signal shown separately and not counted.
     */
    val checkCounts: Pair<Int, Int>
        get() {
            val counted = hardChecks + checklist.filter { it.id.startsWith("include.") }
            return counted.count { it.outcome == CheckOutcome.PASS } to counted.size
        }
}

/** Builds the check-and-score request for one candidate and reads the result. */
object CandidateChecks {

    private const val G_ID = "g"
    private const val G_CAP = 3.0
    private const val E_ID = "e"

    private val PERSONAL_SCENES = setOf(Scene.ROMANCE, Scene.FRIENDS, Scene.FAMILY)

    fun state(conversation: Conversation, selection: Selection, goal: Goal, candidate: String): Map<String, Any?> {
        val state = linkedMapOf<String, Any?>(
            "relationship_note" to SceneCatalog.relationshipNote(selection, goal.direction),
            // The same earlier messages the drafting model saw, so a fact it used is never "unsupported".
            "earlier_messages" to conversation.earlier.map { it.toState() },
            "latest_messages" to conversation.latestTurn.map { it.toState() },
            "user_facts" to goal.userFacts,
            "goal" to goal.toState(),
            "candidate_reply" to candidate,
        )
        if (conversation.glossary.isNotEmpty()) state["glossary"] = conversation.glossary
        return state
    }

    /**
     * The contradiction check. On because a live check separated 6 of 6 cases
     * (yes >= 0.90, no <= 0.07); it only asks the user to look, never blocks.
     */
    @Volatile var contradictionCheck = true

    /** The hard checks that apply to this goal and situation. */
    fun hardChecks(selection: Selection, goal: Goal, boundaryActive: Boolean): List<Shared.Check> = buildList {
        add(Shared.UNSUPPORTED_FACT)
        add(Shared.NEW_COMMITMENT)
        add(Shared.COMMITS_OTHERS)
        if (goal.hasDecision && !goal.clarifyOnly) add(Shared.OPPOSITE_STANCE)
        goal.mustAvoid.forEachIndexed { i, text -> add(Shared.avoidCheck(i, text)) }
        if (boundaryActive) add(Shared.CROSSES_BOUNDARY)
        if (!goal.allowsFaultAdmission) add(Shared.ADMITS_FAULT)
        if (!goal.allowsDeclaration && selection.scene in setOf(Scene.ROMANCE, Scene.FRIENDS)) add(Shared.UNSTATED_DECLARATION)
        if (goal.clarifyOnly) add(Shared.BEYOND_CLARIFICATION)
        // A goal written by the user covers what they wrote; a stance covers one request.
        if (goal.hasDecision && !goal.clarifyOnly && goal.stanceId != null) add(Shared.BEYOND_GOAL)
        if (contradictionCheck) add(Shared.CONTRADICTS_EARLIER)
    }

    fun checklist(selection: Selection, goal: Goal, hasLatestTurn: Boolean): List<Shared.Check> = buildList {
        goal.mustInclude.forEachIndexed { i, text -> add(Shared.includeCheck(i, text)) }
        if (hasLatestTurn && selection.scene in PERSONAL_SCENES) add(Shared.ACKNOWLEDGES)
    }

    fun questions(
        selection: Selection,
        goal: Goal,
        boundaryActive: Boolean,
        hasLatestTurn: Boolean,
    ): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (c in hardChecks(selection, goal, boundaryActive) + checklist(selection, goal, hasLatestTurn)) {
            out[c.id] = AnalysisEngine.noul(c.question, c.yes, c.no)
        }
        if (goal.hasDecision) out[G_ID] = AnalysisEngine.score(Shared.G_QUESTION, Shared.G_LEVELS)
        out[E_ID] = AnalysisEngine.score(Shared.E_QUESTION, SceneCatalog.spec(selection.scene).eLevels(selection))
        return out
    }

    fun verdict(
        draft: Draft,
        selection: Selection,
        goal: Goal,
        boundaryActive: Boolean,
        hasLatestTurn: Boolean,
        result: DecisionsResult,
    ): Verdict {
        val hard = hardChecks(selection, goal, boundaryActive).map { check ->
            val p = (result.answers[check.id] as? Answer.Noul)?.yes
            val outcome = when {
                p == null -> CheckOutcome.UNKNOWN
                p >= Thresholds.VIOLATION -> CheckOutcome.FAIL
                p <= Thresholds.CLEAR -> CheckOutcome.PASS
                else -> CheckOutcome.UNSURE
            }
            CheckResult(check.id, check.label, p, outcome)
        }
        val list = checklist(selection, goal, hasLatestTurn).map { check ->
            val p = (result.answers[check.id] as? Answer.Noul)?.yes
            val outcome = when {
                p == null -> CheckOutcome.UNKNOWN
                p >= Thresholds.INCLUDED -> CheckOutcome.PASS
                p <= Thresholds.NOT_INCLUDED -> CheckOutcome.FAIL
                else -> CheckOutcome.UNSURE
            }
            CheckResult(check.id, check.label, p, outcome)
        }
        val eLevels = SceneCatalog.spec(selection.scene).eLevels(selection)
        val rawG = if (goal.hasDecision) part(result.answers[G_ID], Shared.G_LEVELS) else null
        val e = part(result.answers[E_ID], eLevels)
        // A reply that leaves out something required cannot be more than "Key point missing", whatever Jev said.
        val anyMissing = list.any { it.id.startsWith("include.") && it.outcome == CheckOutcome.FAIL }
        val g = if (rawG != null && anyMissing && rawG.value > G_CAP) {
            rawG.copy(value = G_CAP, label = Shared.G_LEVELS[G_CAP.toInt()].short, capped = true)
        } else rawG

        // Checks that only ask the user to look (an own goal's fault or feelings): a "yes" is not a violation.
        val confirmOnly = confirmOnly(goal)
        val violations = hard.filter { it.outcome == CheckOutcome.FAIL && it.id !in confirmOnly }
        val unknownHard = hard.filter { it.outcome == CheckOutcome.UNKNOWN }
        val unsureHard = hard.filter { it.outcome == CheckOutcome.UNSURE || (it.outcome == CheckOutcome.FAIL && it.id in confirmOnly) }
        val included = list.filter { it.id.startsWith("include.") }
        val missing = included.filter { it.outcome == CheckOutcome.FAIL }
        // A required item that did not come back is as unknown as a hard check that did not.
        val unknownIncluded = included.filter { it.outcome == CheckOutcome.UNKNOWN }
        val unsureIncluded = included.filter { it.outcome == CheckOutcome.UNSURE }
        val scoresMissing = e == null || (goal.hasDecision && g == null)

        val reasons = ArrayList<String>()
        val state = when {
            violations.isNotEmpty() -> {
                violations.forEach { reasons.add("Needs rewriting: ${describe(it)}.") }
                CandidateState.NEEDS_REWRITE
            }
            unknownHard.isNotEmpty() || unknownIncluded.isNotEmpty() -> {
                reasons.add("Some checks did not come back, so this reply could not be cleared.")
                CandidateState.FAILED
            }
            missing.isNotEmpty() -> {
                missing.forEach { reasons.add("Missing: ${it.label}.") }
                unsureHard.forEach { reasons.add("Please check: it may be that it ${describeLower(it)}.") }
                CandidateState.NEEDS_EDIT
            }
            unsureHard.isNotEmpty() || unsureIncluded.isNotEmpty() -> {
                unsureHard.forEach { reasons.add("Please check: it may be that it ${describeLower(it)}.") }
                unsureIncluded.forEach { reasons.add("Please check that it does this: ${it.label}.") }
                CandidateState.AWAITING_CONFIRMATION
            }
            scoresMissing -> {
                reasons.add("Checks passed, but scoring did not come back.")
                CandidateState.UNSCORED
            }
            else -> CandidateState.ELIGIBLE
        }
        val total = when {
            scoresMissing || e == null -> null
            g != null -> Scoring.shown(Scoring.total(g.value, e.value))
            else -> Scoring.shown(e.value)
        }
        return Verdict(draft, state, hard, list, g, e, total, reasons)
    }

    private fun confirmOnly(goal: Goal): Set<String> = goal.confirmChecks + Shared.CONTRADICTS_EARLIER.id

    private fun part(answer: Answer?, levels: List<Level>): ScorePart? {
        val score = answer as? Answer.Score ?: return null
        if (score.probabilities.size != levels.size) return null
        return ScorePart(
            value = score.score.coerceIn(0.0, (levels.size - 1).toDouble()),
            label = levels[Scoring.nearestLevel(score.score, levels.size)].short,
            inconsistent = Scoring.isSplit(score.probabilities),
            probabilities = score.probabilities,
        )
    }

    private fun describe(result: CheckResult): String = when {
        result.id.startsWith("avoid.") -> "it ${result.label.removePrefix("Does what you ruled out: ")}, which you ruled out"
        else -> describeLower(result)
    }

    private fun describeLower(result: CheckResult): String = when (result.id) {
        "unsupported_fact" -> "states something you didn't say"
        "new_commitment" -> "promises something you didn't authorise"
        "commits_others" -> "promises something on someone else's behalf"
        "opposite_stance" -> "goes against your decision"
        "crosses_boundary" -> "pushes past the boundary they set"
        "admits_fault" -> "admits fault on your behalf"
        "unstated_declaration" -> "declares feelings you didn't choose to express"
        "beyond_clarification" -> "gives a decision when you only wanted to ask"
        "beyond_goal" -> "decides something your goal doesn't cover"
        "contradicts_earlier" -> "differs from what you said earlier (check the date or amount)"
        else -> result.label.removePrefix("Does what you ruled out: ").let { "does this: $it" }
    }

    /** Panel order of the states: only a reply that passed everything can come first. */
    private val STATE_ORDER = listOf(
        CandidateState.ELIGIBLE, CandidateState.AWAITING_CONFIRMATION, CandidateState.NEEDS_EDIT,
        CandidateState.UNSCORED, CandidateState.FAILED, CandidateState.NEEDS_REWRITE,
    )

    /**
     * Order for the panel: by state (eligible, to check, missing something,
     * unscored, failed, blocked), and within a state by shown total, highest
     * first, ties in their original order.
     */
    fun rank(verdicts: List<Verdict>): List<Verdict> =
        verdicts.withIndex()
            .sortedWith(compareBy<IndexedValue<Verdict>> { STATE_ORDER.indexOf(it.value.state) }
                .thenByDescending { it.value.total ?: -1.0 }
                .thenBy { it.index })
            .map { it.value }

    /** True when the two best eligible replies are within [Thresholds.CLOSE] of each other. */
    fun isClose(ranked: List<Verdict>): Boolean {
        val eligible = ranked.filter { it.state == CandidateState.ELIGIBLE && it.total != null }
        return eligible.size >= 2 && eligible[0].total!! - eligible[1].total!! <= Thresholds.CLOSE + 1e-9
    }

    /**
     * Index of the reply that gets "Top pick", or null: the first reply, only when
     * it passed every check, Jev was not split on it, and no other eligible reply
     * is about as good.
     */
    fun topPick(ranked: List<Verdict>): Int? {
        val first = ranked.firstOrNull() ?: return null
        if (first.state != CandidateState.ELIGIBLE || first.total == null || first.unsure) return null
        return if (isClose(ranked)) null else 0
    }

    const val NONE_PASSED = "No reply passed every check. See what's flagged, try another batch, or change the goal."

    /** What to tell the drafting model when every reply failed. */
    fun feedback(verdicts: List<Verdict>, goal: Goal? = null): List<String> =
        verdicts.flatMap { v ->
            v.hardChecks.filter { it.outcome == CheckOutcome.FAIL && (goal == null || it.id !in confirmOnly(goal)) }.map { "A reply ${describeLower(it)}. Do not do that." } +
                v.checklist.filter { it.id.startsWith("include.") && it.outcome == CheckOutcome.FAIL }
                    .map { "A reply did not do this required thing: ${it.label}." }
        }.distinct()
}
