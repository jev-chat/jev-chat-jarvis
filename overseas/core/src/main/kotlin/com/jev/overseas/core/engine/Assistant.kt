package com.jev.overseas.core.engine

import com.jev.overseas.core.net.DecisionsResult
import com.jev.overseas.core.net.ModelException
import com.jev.overseas.core.net.ModelGateway
import com.jev.overseas.core.net.Usage
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Limits one run: the number of model requests, the total time spent waiting
 * for models, and whether the user has cancelled. Every request, including a
 * retry, a revision and "another batch", takes one unit. When any limit is
 * reached the run stops asking instead of quietly continuing.
 *
 * Waiting time counts only while at least one request is in flight, so the time
 * the user spends choosing a stance does not use it up, and the parallel checks
 * count once rather than twice.
 */
class RunBudget(
    val maxRequests: Int = DEFAULT_MAX,
    val maxWaitMs: Long = DEFAULT_MAX_WAIT_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    enum class Stop { REQUESTS, TIME, CANCELLED }

    @Volatile var used: Int = 0
        private set
    @Volatile var cost: Double = 0.0
        private set
    @Volatile var cancelled: Boolean = false
        private set

    private var inFlight = 0
    private var flightStartedAt = 0L
    private var waitedBefore = 0L

    /** Why the last refused [take] was refused, or null while the budget still allows requests. */
    val stop: Stop?
        @Synchronized get() = when {
            cancelled -> Stop.CANCELLED
            used >= maxRequests -> Stop.REQUESTS
            waitedMs >= maxWaitMs -> Stop.TIME
            else -> null
        }

    @Synchronized fun take(): Boolean = if (stop == null) { used++; true } else false

    @Synchronized fun add(usage: Usage?) { if (usage != null) cost += usage.cost }

    /** After this, [take] always returns false. Results that arrive later are the caller's to discard. */
    @Synchronized fun cancel() { cancelled = true }

    /** Milliseconds spent with at least one request in flight. */
    val waitedMs: Long
        @Synchronized get() = waitedBefore + if (inFlight > 0) clock() - flightStartedAt else 0L

    /** Runs one model call and counts the time spent waiting for it. */
    fun <T> timed(call: () -> T): T {
        begin()
        try {
            return call()
        } finally {
            end()
        }
    }

    @Synchronized private fun begin() {
        if (inFlight == 0) flightStartedAt = clock()
        inFlight++
    }

    @Synchronized private fun end() {
        inFlight--
        if (inFlight == 0) waitedBefore += clock() - flightStartedAt
    }

    val remaining: Int get() = maxRequests - used

    companion object {
        /**
         * One analysis (two requests: behaviours and friction), one draft and one
         * check per reply is five requests; a revision or another batch adds three.
         * Seventeen leaves room for five rounds of drafting, as before friction got
         * its own request.
         */
        const val DEFAULT_MAX = 17

        /** At most 90 seconds of model waiting per run. */
        const val DEFAULT_MAX_WAIT_MS = 90_000L
    }
}

sealed class AnalysisOutcome {
    data class Ready(val analysis: Analysis) : AnalysisOutcome()

    /** Nothing was sent to a model; [message] says why. */
    data class Skipped(val reason: SkipReason, val message: String) : AnalysisOutcome()

    /** The model call failed. Nothing is drafted after a failed analysis. */
    data class Failed(val error: ModelException) : AnalysisOutcome()
}

enum class SkipReason { EMPTY, USER_WROTE_LAST, SENDER_UNKNOWN, UNREADABLE, NOT_ENGLISH, BUDGET }

data class CandidateSet(
    /** Ranked for display. May hold fewer than two replies; never padded. */
    val verdicts: List<Verdict>,
    /** True when the best two eligible replies are too close to rank meaningfully. */
    val close: Boolean,
    /** True when the first drafts all failed and these are a second attempt. */
    val revised: Boolean,
    /** A sentence for the panel when there is nothing to recommend or something went wrong. */
    val message: String?,
    val error: ModelException?,
    val goalVersion: Int,
    /** Index of the reply shown as "Top pick", or null. */
    val topPick: Int? = null,
) {
    val eligibleCount: Int get() = verdicts.count { it.state == CandidateState.ELIGIBLE }
}

/**
 * Runs the three model steps: analyse the conversation, draft replies inside a
 * fixed goal, then check and score each reply. Blocking; call it off the main
 * thread. A transient failure is retried once with the identical request.
 */
class Assistant(
    private val gateway: ModelGateway,
    /** Used when the user's own messages do not show British or American spelling. */
    private val defaultSpelling: String = "US",
    /** "US" or "UK" when the user chose one in Settings; null for Auto. */
    private val fixedSpelling: String? = null,
) {

    /**
     * Two requests in parallel: the behaviour questions on the read window, and the
     * friction questions on the recent messages only. Both must succeed.
     */
    fun analyze(conversation: Conversation, selection: Selection, budget: RunBudget): AnalysisOutcome {
        skipReason(conversation, selection)?.let { return it }
        // Both requests or neither: never spend one unit on half an analysis.
        if (budget.stop != null || budget.remaining < 2) return refused(budget)
        if (!budget.take() || !budget.take()) return refused(budget)
        val pool = Executors.newFixedThreadPool(2)
        return try {
            val friction = pool.submit(Callable {
                withRetry(budget) { gateway.decisions(AnalysisEngine.frictionState(conversation, selection), AnalysisEngine.frictionQuestions(selection)) }
            })
            // Wait for both, even when one fails, so every request taken from the budget was really made.
            val main = runCatching {
                withRetry(budget) { gateway.decisions(AnalysisEngine.state(conversation, selection), AnalysisEngine.behaviourQuestions(selection)) }
            }
            val other = runCatching {
                try {
                    friction.get()
                } catch (e: ExecutionException) {
                    throw (e.cause as? ModelException) ?: ModelException(ModelException.Kind.BAD_RESPONSE, null, "Friction request failed")
                }
            }
            main.exceptionOrNull()?.let { throw it }
            other.exceptionOrNull()?.let { throw it }
            val mainResult = main.getOrThrow()
            val otherResult = other.getOrThrow()
            budget.add(mainResult.usage)
            budget.add(otherResult.usage)
            val result = DecisionsResult(mainResult.answers + otherResult.answers, mainResult.usage, mainResult.model)
            AnalysisEngine.incomplete(selection, result)?.let { reason ->
                return AnalysisOutcome.Failed(ModelException(ModelException.Kind.BAD_RESPONSE, 200, reason))
            }
            AnalysisOutcome.Ready(AnalysisEngine.interpret(conversation, selection, result))
        } catch (e: ModelException) {
            AnalysisOutcome.Failed(e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            AnalysisOutcome.Failed(cancelled())
        } finally {
            pool.shutdownNow()
        }
    }

    private fun refused(budget: RunBudget): AnalysisOutcome = when (budget.stop) {
        RunBudget.Stop.CANCELLED -> AnalysisOutcome.Failed(cancelled())
        else -> AnalysisOutcome.Skipped(SkipReason.BUDGET, budgetMessage(budget))
    }

    private fun skipReason(conversation: Conversation, selection: Selection): AnalysisOutcome.Skipped? = when {
        conversation.isEmpty ->
            AnalysisOutcome.Skipped(SkipReason.EMPTY, "No messages could be read on this screen.")
        conversation.senderUncertain ->
            AnalysisOutcome.Skipped(SkipReason.SENDER_UNKNOWN, "Couldn't tell who sent the newest message. Scroll a little and try again.")
        conversation.latestTurn.isEmpty() -> {
            val n = conversation.userTrailing
            // Waiting is advice only where a run of unanswered messages reads as pressure.
            val waitRelationship = selection.relationship == null || selection.relationship.id in WAIT_RELATIONSHIPS
            val wait = selection.scene == Scene.ROMANCE && waitRelationship && n >= 2
            AnalysisOutcome.Skipped(
                SkipReason.USER_WROTE_LAST,
                if (wait) "You've sent $n messages in a row and they haven't replied. It's usually better to wait."
                else "You wrote last, so there's nothing new from them. Want to send a follow-up? Say what it should add.",
            )
        }
        // A file is analysed with the messages around it; only a voice note or a deleted message stops here.
        !conversation.latestTurnReadable && !conversation.latestTurnFilesOnly ->
            AnalysisOutcome.Skipped(SkipReason.UNREADABLE, "Their latest message is a voice note or a deleted message, which Jev can't read. You can write your own goal.")
        !conversation.looksEnglish ->
            AnalysisOutcome.Skipped(SkipReason.NOT_ENGLISH, "This version only supports chats in English.")
        else -> null
    }

    /**
     * Drafts two replies for [goal], checks and scores each, and returns them
     * ranked. When every reply fails a check, one revision is attempted with the
     * failures spelled out; if that fails too, the result says so plainly.
     */
    fun draftAndCheck(
        conversation: Conversation,
        selection: Selection,
        goal: Goal,
        boundaryActive: Boolean,
        budget: RunBudget,
        previous: List<String> = emptyList(),
        /** Called with the drafts as soon as they are parsed, before they are checked. */
        onDrafts: (List<Draft>) -> Unit = {},
        /** What the analysis found, for the drafting model (Analysis.situation). */
        situation: Map<String, Any?>? = null,
    ): CandidateSet {
        val first = attempt(conversation, selection, goal, boundaryActive, budget, previous, emptyList(), onDrafts, situation)
        if (first.error != null || first.verdicts.isEmpty()) return first
        val anyUsable = first.verdicts.any { it.state != CandidateState.NEEDS_REWRITE && it.state != CandidateState.NEEDS_EDIT && it.state != CandidateState.FAILED }
        // A revision needs something to fix; checks that did not complete give the drafting model nothing to go on.
        val fixable = first.verdicts.any { it.state == CandidateState.NEEDS_REWRITE || it.state == CandidateState.NEEDS_EDIT }
        if (anyUsable || !fixable || budget.remaining < 3) return first

        val feedback = CandidateChecks.feedback(first.verdicts, goal)
        val second = attempt(conversation, selection, goal, boundaryActive, budget,
            previous + first.verdicts.map { it.draft.text }, feedback, onDrafts, situation)
        if (second.error?.kind == ModelException.Kind.CANCELLED) return second
        // A failed or empty revision keeps the first batch, which already says why nothing passed.
        if (second.error != null || second.verdicts.isEmpty()) return first
        // Keep the better batch: more eligible replies first, then the higher best score.
        fun best(set: CandidateSet) = set.verdicts.mapNotNull { if (it.copyable) it.total else null }.maxOrNull() ?: -1.0
        val better = compareValuesBy(second, first, { it.eligibleCount }, { best(it) }) >= 0
        return if (better) second.copy(revised = true) else first
    }

    private fun attempt(
        conversation: Conversation,
        selection: Selection,
        goal: Goal,
        boundaryActive: Boolean,
        budget: RunBudget,
        previous: List<String>,
        feedback: List<String>,
        onDrafts: (List<Draft>) -> Unit,
        situation: Map<String, Any?>?,
    ): CandidateSet {
        fun failed(error: ModelException?, message: String) =
            CandidateSet(emptyList(), close = false, revised = false, message = message, error = error, goalVersion = goal.version)

        val variants = SceneCatalog.spec(selection.scene).variants(selection)
        val spelling = fixedSpelling ?: conversation.userSpelling ?: defaultSpelling
        val system = Drafting.systemPrompt(selection, variants)
        val user = Drafting.userPrompt(conversation, selection, goal, spelling, previous, feedback, situation)

        fun refused() = when (budget.stop) {
            RunBudget.Stop.CANCELLED -> failed(cancelled(), "Cancelled.")
            else -> failed(null, budgetMessage(budget))
        }

        var drafts: List<Draft> = emptyList()
        // A reply that cannot be parsed is asked for once more; an empty result is never padded.
        for (round in 0 until 2) {
            if (!budget.take()) return refused()
            try {
                val chat = withRetry(budget) { gateway.chat(system, user, Drafting.TEMPERATURE, Drafting.MAX_TOKENS, json = true) }
                budget.add(chat.usage)
                drafts = Drafting.parse(chat.content, variants)
            } catch (e: ModelException) {
                return failed(e, "Drafting failed: ${e.message}")
            }
            if (drafts.isNotEmpty()) break
        }
        if (drafts.isEmpty()) return failed(null, "The drafting model returned nothing usable.")
        if (budget.cancelled) return failed(cancelled(), "Cancelled.")
        onDrafts(drafts)

        // "Picks up what they said" needs something readable from them (S10: own goal after a photo or after the user wrote last).
        val hasTurn = conversation.latestTurnReadable
        val questions = CandidateChecks.questions(selection, goal, boundaryActive, hasTurn)
        val pool = Executors.newFixedThreadPool(drafts.size)
        val verdicts = try {
            drafts.map { draft ->
                draft to pool.submit(Callable {
                    check(conversation, selection, goal, boundaryActive, hasTurn, draft, questions, budget)
                })
            }.map { (draft, future) ->
                // Whatever goes wrong while checking one reply stays with that reply.
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    failedVerdict(draft, "Checking failed: ${e.cause?.javaClass?.simpleName ?: "error"}")
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return failed(cancelled(), "Cancelled.")
        } finally {
            pool.shutdownNow()
        }
        if (budget.cancelled) return failed(cancelled(), "Cancelled.")
        val ranked = CandidateChecks.rank(verdicts)
        val noneEligible = ranked.none { it.state == CandidateState.ELIGIBLE }
        return CandidateSet(ranked, CandidateChecks.isClose(ranked), revised = false,
            message = if (noneEligible) CandidateChecks.NONE_PASSED else null, error = null, goalVersion = goal.version,
            topPick = CandidateChecks.topPick(ranked))
    }

    /**
     * Checks the replies whose check did not complete again, as they are.
     * Nothing is drafted; replies that were already checked keep their verdicts.
     */
    fun recheck(
        conversation: Conversation,
        selection: Selection,
        goal: Goal,
        boundaryActive: Boolean,
        budget: RunBudget,
        set: CandidateSet,
    ): CandidateSet {
        val hasTurn = conversation.latestTurnReadable
        val questions = CandidateChecks.questions(selection, goal, boundaryActive, hasTurn)
        val verdicts = set.verdicts.map { v ->
            if (v.state != CandidateState.FAILED || budget.cancelled) v
            else check(conversation, selection, goal, boundaryActive, hasTurn, v.draft, questions, budget)
        }
        if (budget.cancelled) return set.copy(error = cancelled())
        val ranked = CandidateChecks.rank(verdicts)
        val noneEligible = ranked.none { it.state == CandidateState.ELIGIBLE }
        return set.copy(verdicts = ranked, close = CandidateChecks.isClose(ranked),
            message = if (noneEligible) CandidateChecks.NONE_PASSED else null, topPick = CandidateChecks.topPick(ranked))
    }

    private fun failedVerdict(draft: Draft, reason: String) =
        Verdict(draft, CandidateState.FAILED, emptyList(), emptyList(), null, null, null, listOf(reason))

    private fun check(
        conversation: Conversation,
        selection: Selection,
        goal: Goal,
        boundaryActive: Boolean,
        hasTurn: Boolean,
        draft: Draft,
        questions: Map<String, Any?>,
        budget: RunBudget,
    ): Verdict {
        if (!budget.take()) return failedVerdict(draft, budgetMessage(budget))
        return try {
            val result = withRetry(budget) {
                gateway.decisions(CandidateChecks.state(conversation, selection, goal, draft.text), questions)
            }
            budget.add(result.usage)
            CandidateChecks.verdict(draft, selection, goal, boundaryActive, hasTurn, result)
        } catch (e: ModelException) {
            failedVerdict(draft, "Checking failed: ${e.message}")
        }
    }

    /** One retry of the identical request, and only for failures that may pass a second time. */
    private fun <T> withRetry(budget: RunBudget, call: () -> T): T =
        try {
            budget.timed(call)
        } catch (e: ModelException) {
            if (!e.kind.retryable || !budget.take()) throw e
            budget.timed(call)
        }

    companion object {
        const val BUDGET_MESSAGE = "This run has used all of its model requests. Start again to continue."
        const val TIME_MESSAGE = "This run has waited too long for the models. Start again to continue."
        const val CANCELLED_MESSAGE = "Cancelled"

        /** Romance relationships where several unanswered messages in a row mean "wait". */
        val WAIT_RELATIONSHIPS = setOf("just_connected", "talking", "ex_friends", "ex_distant")

        fun budgetMessage(budget: RunBudget): String = when (budget.stop) {
            RunBudget.Stop.TIME -> TIME_MESSAGE
            RunBudget.Stop.CANCELLED -> CANCELLED_MESSAGE
            else -> BUDGET_MESSAGE
        }

        private fun cancelled() = ModelException(ModelException.Kind.CANCELLED, null, CANCELLED_MESSAGE)
    }
}
