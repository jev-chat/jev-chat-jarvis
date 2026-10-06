package com.jev.overseas.core.diagnostics

import com.jev.overseas.core.engine.Analysis
import com.jev.overseas.core.engine.AnalysisOutcome
import com.jev.overseas.core.engine.CandidateSet
import com.jev.overseas.core.engine.CheckResult
import com.jev.overseas.core.engine.Goal
import com.jev.overseas.core.engine.GoalBuilder
import com.jev.overseas.core.engine.GoalSwitch
import com.jev.overseas.core.engine.RunBudget
import com.jev.overseas.core.engine.ScorePart
import com.jev.overseas.core.net.ModelException
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.session.ChatChange
import com.jev.overseas.core.session.ReadResult

/**
 * Receives diagnostic events, one map per step of a round. The app writes them
 * to a local file that the user can export into a bug report; nothing is sent
 * anywhere by itself.
 */
fun interface Journal {
    fun record(event: Map<String, Any?>)

    companion object {
        val NONE = Journal { }
    }
}

/**
 * Builds the events. The rule for every event: identifiers, counts, scores,
 * probabilities, timings and error kinds only. No message text, no contact
 * name, no reply text, no goal wording the user typed, no key. Tested in
 * DiagnosticsTest.
 */
object Diagnostics {

    const val SCHEMA = "jev.diagnostics/1"

    fun roundStart(round: String, selection: Selection, reason: String) = event("round", round,
        "reason" to reason,
        "scene" to selection.scene.id,
        "relationship" to selection.relationship?.id,
        "conflict" to selection.conflict,
    )

    fun read(round: String, result: ReadResult, ms: Long) = when (result) {
        is ReadResult.Ok -> event("read", round,
            "ok" to true,
            "messages" to result.report.messages,
            "latestTurn" to result.report.latestTurn,
            "unreadable" to result.report.unreadable,
            "scrolledToLatest" to result.report.scrolledToLatest,
            "screens" to result.report.screens,
            "cached" to result.report.cached,
            "continuous" to result.report.continuous,
            "stop" to result.report.stopReason,
            "returned" to result.report.returnedToBottom,
            "senders" to result.conversation.messages.joinToString("") { it.sender.wire.take(1) },
            "ms" to ms,
        )
        ReadResult.NotConversation -> event("read", round, "ok" to false, "why" to "not_conversation", "ms" to ms)
        ReadResult.Unstable -> event("read", round, "ok" to false, "why" to "unstable", "ms" to ms)
        ReadResult.Unavailable -> event("read", round, "ok" to false, "why" to "unavailable", "ms" to ms)
        ReadResult.NotAtLatest -> event("read", round, "ok" to false, "why" to "not_at_latest", "ms" to ms)
        ReadResult.ChatChanged -> event("read", round, "ok" to false, "why" to "chat_changed", "ms" to ms)
        ReadResult.GroupChat -> event("read", round, "ok" to false, "why" to "group", "ms" to ms)
    }

    fun analysis(round: String, outcome: AnalysisOutcome, ms: Long, budget: RunBudget?) = when (outcome) {
        is AnalysisOutcome.Ready -> analysisReady(round, outcome.analysis, ms, budget)
        is AnalysisOutcome.Skipped -> event("analysis", round, "skipped" to outcome.reason.name, "ms" to ms)
        is AnalysisOutcome.Failed -> event("analysis", round, "failed" to outcome.error.kind.name, "ms" to ms)
    }

    private fun analysisReady(round: String, a: Analysis, ms: Long, budget: RunBudget?) = event("analysis", round,
        "next" to a.next.name,
        "detected" to a.detected.associate { it.behavior.id to r2(it.probability) },
        "unsure" to a.unsure.associate { it.behavior.id to r2(it.probability) },
        "cues" to a.cues.map { it.behavior.id },
        "missing" to a.missing,
        "frictionOther" to a.friction?.other?.let(::scoreMap),
        "frictionSelf" to a.friction?.self?.let(::scoreMap),
        "tone" to a.tone?.let { if (it.unclear) "unclear" else it.title },
        "groups" to a.groups.map { it.behaviorId },
        "defaultStance" to a.defaultStance?.id,
        "notices" to a.notices.size,
        "ms" to ms,
        "requests" to budget?.used,
        "cost" to budget?.cost,
    )

    /** The goal as structure: which stance, how long the user's own words were, which switches. Never the words. */
    fun goal(round: String, goal: Goal, ownGoal: Boolean, switches: Set<GoalSwitch>) = event("goal", round,
        "stance" to goal.stanceId,
        "ownGoal" to ownGoal,
        "version" to goal.version,
        "detailsChars" to (goal.userDetails?.length ?: 0),
        "switches" to switches.map { it.name },
        // Template items only: the item for the user's own detail quotes what they typed.
        "mustInclude" to if (ownGoal) goal.mustInclude.size else goal.mustInclude.filterNot { it.startsWith(GoalBuilder.DETAIL_PREFIX) },
        "detailChecked" to goal.mustInclude.any { it.startsWith(GoalBuilder.DETAIL_PREFIX) },
        "mustAvoid" to goal.mustAvoid.size,
        "hasDecision" to goal.hasDecision,
    )

    fun candidates(round: String, set: CandidateSet, draftMs: Long?, checkMs: Long?, previous: Int, budget: RunBudget?) =
        event("candidates", round,
            "goalVersion" to set.goalVersion,
            "previous" to previous,
            "revised" to set.revised,
            "close" to set.close,
            "message" to set.message,
            "replies" to set.verdicts.map { v ->
                linkedMapOf<String, Any?>(
                    "label" to v.draft.label,
                    "chars" to v.draft.text.length,
                    "words" to v.draft.text.split(Regex("\\s+")).count { it.isNotEmpty() },
                    "state" to v.state.name,
                    "total" to v.total,
                    "g" to v.g?.let(::partMap),
                    "e" to v.e?.let(::partMap),
                    "hard" to v.hardChecks.map(::checkMap),
                    "checklist" to v.checklist.map(::checkMap),
                )
            },
            "draftMs" to draftMs,
            "checkMs" to checkMs,
            "requests" to budget?.used,
            "cost" to budget?.cost,
        )

    fun error(round: String, step: String, e: ModelException, budget: RunBudget?) = event("error", round,
        "step" to step,
        "kind" to e.kind.name,
        "status" to e.status,
        // Only Jev's own wording (no HTTP status, or a 200 that could not be used). A provider's
        // error text may quote the request, so it is never logged.
        "detail" to e.message?.take(160)?.takeIf { e.status == null || e.status == 200 },
        "requests" to budget?.used,
    )

    fun chatChange(round: String, change: ChatChange) =
        event("chat_change", round, "newFromThem" to change.newFromThem, "otherChat" to change.otherChat)

    /** An unexpected exception in background work: its class and where it was thrown in Jev's code, nothing else. */
    fun crash(round: String, e: Throwable) = event("crash", round,
        "exception" to e.javaClass.simpleName,
        "at" to e.stackTrace.firstOrNull { it.className.startsWith("com.jev") }?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" },
    )

    /** Fill, copy, a refused fill, the replace question, the banner choices, resume decisions, close. */
    fun action(round: String, name: String, vararg extra: Pair<String, Any?>) = event("action", round, "name" to name, *extra)

    private fun event(type: String, round: String, vararg fields: Pair<String, Any?>): Map<String, Any?> =
        linkedMapOf<String, Any?>("type" to type, "round" to round).apply { fields.forEach { (k, v) -> put(k, v) } }

    private fun scoreMap(s: com.jev.overseas.core.net.Answer.Score) =
        linkedMapOf("score" to r2(s.score), "p" to s.probabilities.map(::r2))

    private fun partMap(p: ScorePart) =
        linkedMapOf("value" to r2(p.value), "label" to p.label, "split" to p.inconsistent, "p" to p.probabilities.map(::r2))

    private fun checkMap(c: CheckResult) =
        linkedMapOf("id" to c.id, "p" to c.probability?.let(::r2), "outcome" to c.outcome.name)

    private fun r2(v: Double) = Math.round(v * 100) / 100.0
}
