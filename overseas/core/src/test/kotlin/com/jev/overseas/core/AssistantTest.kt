package com.jev.overseas.core

import com.jev.overseas.core.engine.AnalysisOutcome
import com.jev.overseas.core.engine.Assistant
import com.jev.overseas.core.engine.CandidateState
import com.jev.overseas.core.engine.ChatMessage
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.Goal
import com.jev.overseas.core.engine.GoalBuilder
import com.jev.overseas.core.engine.RunBudget
import com.jev.overseas.core.engine.Sender
import com.jev.overseas.core.engine.SkipReason
import com.jev.overseas.core.json.MiniJson
import com.jev.overseas.core.net.ChatResult
import com.jev.overseas.core.net.DecisionsResult
import com.jev.overseas.core.net.ModelException
import com.jev.overseas.core.net.ModelException.Kind
import com.jev.overseas.core.scene.RomanceScene
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.StanceOption
import com.jev.overseas.core.scene.WorkScene
import com.jev.overseas.core.testing.Answers
import com.jev.overseas.core.testing.FakeGateway
import com.jev.overseas.core.testing.chat
import com.jev.overseas.core.testing.me
import com.jev.overseas.core.testing.them
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class AssistantTest {

    private val work = Selection(Scene.WORK, WorkScene.SENIOR)
    private val conv = chat(me("hi"), them("can you send the deck by friday?"))
    private val goal: Goal = GoalBuilder.fromStance(StanceOption("cannot", "Can't do it", "Say clearly that you can't do this",
        mustInclude = listOf("says the user cannot do this"), mustAvoid = listOf("agrees to do part of it")))

    /** Checks pass everything and score high, unless [bad] marks a reply text as violating. */
    private fun checks(bad: (String) -> Boolean = { false }): (Map<String, Any?>, Map<String, Any?>) -> DecisionsResult = { state, qs ->
        val text = state["candidate_reply"] as String?
        Answers.all(qs,
            noul = { id -> when {
                text != null && bad(text) && id == "new_commitment" -> 0.95
                id.startsWith("include.") || id == "acknowledges" -> 0.9
                else -> 0.05
            } },
            score = { id, _ -> if (id == "g") Answers.level(5) else Answers.level(4) })
    }

    private fun gateway() = FakeGateway(onDecisions = checks())

    // ------------------------------------------------------------------ skips

    private fun skip(c: Conversation, sel: Selection = work): AnalysisOutcome.Skipped {
        val g = gateway()
        val out = Assistant(g).analyze(c, sel, RunBudget())
        assertTrue(g.calls.isEmpty())
        return out as AnalysisOutcome.Skipped
    }

    @Test fun skipsInOrder() {
        assertEquals(SkipReason.EMPTY, skip(chat()).reason)
        // Unknown sender is checked before "you wrote last" and before readability.
        assertEquals(SkipReason.SENDER_UNKNOWN, skip(chat(me("hi"), ChatMessage(Sender.UNKNOWN, "x"))).reason)
        assertEquals(SkipReason.USER_WROTE_LAST, skip(chat(them("hi"), me("hey"))).reason)
        assertEquals(SkipReason.UNREADABLE, skip(chat(me("hi"), ChatMessage(Sender.OTHER, "[voice message]", readable = false, media = "voice"))).reason)
        assertEquals(SkipReason.UNREADABLE, skip(chat(me("hi"), ChatMessage(Sender.OTHER, "[deleted message]", readable = false, media = "deleted"))).reason)
        assertEquals(SkipReason.NOT_ENGLISH, skip(chat(me("hi"), them("你明天有空吗"))).reason)
    }

    @Test fun waitAdviceOnlyForEarlyOrPastRomance() {
        val twoInARow = chat(them("hey"), me("how was it?"), me("you there?"))
        fun message(rel: String?) = skip(twoInARow, Selection(Scene.ROMANCE, rel?.let { id -> RomanceScene.relationships.first { it.id == id } })).message
        for (rel in listOf("just_connected", "talking", "ex_friends", "ex_distant", null)) {
            assertTrue("$rel", message(rel).contains("better to wait"))
        }
        for (rel in listOf("dating", "established")) {
            assertTrue("$rel", message(rel).startsWith("You wrote last"))
        }
        assertTrue(skip(chat(them("hey"), me("one")), Selection(Scene.ROMANCE)).message.startsWith("You wrote last"))
        assertTrue(skip(twoInARow, work).message.startsWith("You wrote last"))
    }

    @Test fun budgetSpentBeforeAnalysis() {
        val g = gateway()
        val out = Assistant(g).analyze(conv, work, RunBudget(0)) as AnalysisOutcome.Skipped
        assertEquals(SkipReason.BUDGET, out.reason)
        assertTrue(g.calls.isEmpty())
    }

    // ------------------------------------------------------------------ analysis errors

    @Test fun retryableErrorIsRetriedOnceWithTheSameRequest() {
        // The behaviour request fails once; the friction request (a separate request) does not.
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        val g = FakeGateway(onDecisions = { s, q ->
            if ("W01" in q && failed.compareAndSet(false, true)) throw ModelException(Kind.SERVER, 502, "bad gateway") else checks()(s, q)
        })
        val budget = RunBudget()
        assertTrue(Assistant(g).analyze(conv, work, budget) is AnalysisOutcome.Ready)
        assertEquals(3, g.decisions.size)
        val main = g.decisions.filter { "W01" in it.questions!! }
        assertEquals(2, main.size)
        assertEquals(MiniJson.encode(main[0].state), MiniJson.encode(main[1].state))
        assertEquals(MiniJson.encode(main[0].questions), MiniJson.encode(main[1].questions))
        assertEquals(3, budget.used)
    }

    @Test fun secondFailureIsReported() {
        val g = FakeGateway(onDecisions = { _, _ -> throw ModelException(Kind.TIMEOUT, null, "slow") })
        val out = Assistant(g).analyze(conv, work, RunBudget()) as AnalysisOutcome.Failed
        assertEquals(Kind.TIMEOUT, out.error.kind)
        assertEquals(4, g.decisions.size) // each of the two requests retried once
        assertTrue(g.chats.isEmpty())
    }

    @Test fun authIsNotRetried() {
        val g = FakeGateway(onDecisions = { _, _ -> throw ModelException(Kind.AUTH, 401, "no") })
        val out = Assistant(g).analyze(conv, work, RunBudget()) as AnalysisOutcome.Failed
        assertEquals(Kind.AUTH, out.error.kind)
        assertEquals(2, g.calls.size) // the two requests, neither retried
    }

    @Test fun incompleteAnswersFailTheAnalysis() {
        val g = FakeGateway(onDecisions = { _, qs -> Answers.all(qs, score = { _, _ -> null }) })
        val out = Assistant(g).analyze(conv, work, RunBudget()) as AnalysisOutcome.Failed
        assertEquals(Kind.BAD_RESPONSE, out.error.kind)
        assertTrue(g.chats.isEmpty())
    }

    // ------------------------------------------------------------------ drafting and checking

    @Test fun normalRoundIsOneChatAndTwoChecks() {
        val g = gateway()
        val budget = RunBudget()
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, budget)
        assertEquals(1, g.chats.size)
        assertEquals(2, g.decisions.size)
        assertTrue(g.decisions.all { it.isCheck })
        assertEquals(2, set.verdicts.size)
        assertTrue(set.verdicts.all { it.state == CandidateState.ELIGIBLE })
        assertFalse(set.revised)
        assertNull(set.error)
        assertEquals(3, budget.used)
        assertEquals(3 * FakeGateway.USAGE.cost, budget.cost, 1e-12)
    }

    @Test fun unparsableDraftIsAskedForOnceMore() {
        var n = 0
        val g = FakeGateway(onDecisions = checks(), onChat = { _, _ ->
            ChatResult(if (n++ == 0) "sorry, no" else Answers.replies("One.", "Two."), FakeGateway.USAGE)
        })
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget())
        assertEquals(2, g.chats.size)
        assertEquals(2, set.verdicts.size)
    }

    @Test fun twoUnparsableDraftsGiveAPlainMessage() {
        val g = FakeGateway(onDecisions = checks(), onChat = { _, _ -> ChatResult("nothing", FakeGateway.USAGE) })
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget())
        assertEquals(2, g.chats.size)
        assertTrue(set.verdicts.isEmpty())
        assertEquals("The drafting model returned nothing usable.", set.message)
        assertTrue(g.decisions.isEmpty())
    }

    @Test fun allBlockedTriggersOneRevisionWithFeedback() {
        var n = 0
        val g = FakeGateway(onDecisions = checks(bad = { it.startsWith("Bad") }), onChat = { _, _ ->
            ChatResult(if (n++ == 0) Answers.replies("Bad one.", "Bad two.") else Answers.replies("Good one.", "Good two."), FakeGateway.USAGE)
        })
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget())
        assertEquals(2, g.chats.size)
        val second = MiniJson.parseObject(g.chats[1].user!!)
        assertEquals(listOf("A reply promises something you didn't authorise. Do not do that."), second["problems_to_fix_from_the_last_attempt"])
        assertEquals(listOf("Bad one.", "Bad two."), second["do_not_repeat_these_earlier_drafts"])
        assertTrue(set.revised)
        assertEquals(listOf("Good one.", "Good two."), set.verdicts.map { it.draft.text })
        assertNull(set.message)
    }

    @Test fun revisionThatStillFailsSaysSo() {
        val g = FakeGateway(onDecisions = checks(bad = { true }))
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget())
        assertEquals(2, g.chats.size)
        assertTrue(set.revised)
        assertEquals(com.jev.overseas.core.engine.CandidateChecks.NONE_PASSED, set.message)
        assertNull(set.topPick)
    }

    @Test fun noRevisionWhenTooLittleBudgetLeft() {
        val g = FakeGateway(onDecisions = checks(bad = { true }))
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget(5))
        assertEquals(1, g.chats.size)
        assertFalse(set.revised)
        assertTrue(set.verdicts.all { it.state == CandidateState.NEEDS_REWRITE })
    }

    @Test fun oneFailedCheckDoesNotAffectTheOther() {
        val ok = checks()
        val g = FakeGateway(onDecisions = { s, q ->
            if (s["candidate_reply"] == "Sure, that works.") throw ModelException(Kind.BAD_REQUEST, 400, "no") else ok(s, q)
        })
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget())
        assertEquals(setOf(CandidateState.ELIGIBLE, CandidateState.FAILED), set.verdicts.map { it.state }.toSet())
        assertEquals("Yes, happy to.", set.verdicts.first().draft.text)
    }

    @Test fun unexpectedExceptionInACheckBecomesFailed() { // F2
        val ok = checks()
        val g = FakeGateway(onDecisions = { s, q ->
            if (s["candidate_reply"] == "Sure, that works.") throw IllegalStateException("boom") else ok(s, q)
        })
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget())
        val failed = set.verdicts.single { it.state == CandidateState.FAILED }
        assertEquals("Checking failed: IllegalStateException", failed.reasons.single())
        assertTrue(set.verdicts.any { it.state == CandidateState.ELIGIBLE })
    }

    @Test fun noRevisionWhenChecksDidNotComplete() { // F3
        val g = FakeGateway(onDecisions = { _, _ -> throw ModelException(Kind.BAD_REQUEST, 400, "no") })
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget())
        assertEquals(1, g.chats.size)
        assertFalse(set.revised)
        assertTrue(set.verdicts.all { it.state == CandidateState.FAILED })
    }

    @Test fun draftingFailureIsReported() {
        val g = FakeGateway(onDecisions = checks(), onChat = { _, _ -> throw ModelException(Kind.AUTH, 401, "no") })
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget())
        assertEquals(Kind.AUTH, set.error!!.kind)
        assertEquals(1, g.chats.size)
    }

    @Test fun previousDraftsAreSentForAnotherBatch() {
        val g = gateway()
        Assistant(g).draftAndCheck(conv, work, goal, false, RunBudget(), previous = listOf("Old."))
        assertEquals(listOf("Old."), MiniJson.parseObject(g.chats.single().user!!)["do_not_repeat_these_earlier_drafts"])
    }

    @Test fun requestsNeverExceedTheBudget() { // AC10
        val random = Random(7)
        repeat(200) {
            val max = 1 + random.nextInt(16)
            val g = FakeGateway(
                onDecisions = { s, q ->
                    when (random.nextInt(5)) {
                        0 -> throw ModelException(Kind.SERVER, 500, "x")
                        1 -> throw ModelException(Kind.RATE_LIMITED, 429, "x")
                        2 -> checks(bad = { true })(s, q)
                        else -> checks()(s, q)
                    }
                },
                onChat = { _, _ ->
                    when (random.nextInt(4)) {
                        0 -> throw ModelException(Kind.TIMEOUT, null, "x")
                        1 -> ChatResult("junk", null)
                        else -> ChatResult(Answers.replies("A ${random.nextInt()}.", "B."), null)
                    }
                },
            )
            val budget = RunBudget(max)
            val assistant = Assistant(g)
            if (assistant.analyze(conv, work, budget) is AnalysisOutcome.Ready || random.nextBoolean()) {
                assistant.draftAndCheck(conv, work, goal, false, budget)
                assistant.draftAndCheck(conv, work, goal, false, budget, previous = listOf("A."))
            }
            assertTrue("max $max but ${g.calls.size} requests", g.calls.size <= max)
            assertEquals(g.calls.size, budget.used)
        }
    }

    // ------------------------------------------------------------------ time cap and cancel

    @Test fun waitingTimeCapStopsTheRun() {
        var now = 0L
        val budget = RunBudget(maxWaitMs = 1_000, clock = { now })
        val ok = checks()
        // The friction request runs alongside the main one; only the main one takes time here.
        val g = FakeGateway(onDecisions = { s, q -> if (q.keys.none { it.startsWith("friction") }) now += 600; ok(s, q) }, onChat = { _, _ ->
            now += 600; ChatResult(Answers.replies("One.", "Two."), null)
        })
        assertTrue(Assistant(g).analyze(conv, work, budget) is AnalysisOutcome.Ready)
        assertEquals(600, budget.waitedMs)
        now += 60_000 // the user thinking does not count
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, budget)
        assertEquals(1, g.chats.size)
        assertTrue(set.verdicts.all { it.state == CandidateState.FAILED })
        assertEquals(Assistant.TIME_MESSAGE, set.verdicts.first().reasons.single())
        assertEquals(RunBudget.Stop.TIME, budget.stop)
        assertFalse(budget.take())
        val again = Assistant(g).analyze(conv, work, budget) as AnalysisOutcome.Skipped
        assertEquals(Assistant.TIME_MESSAGE, again.message)
    }

    @Test fun overlappingCallsCountOnce() {
        var now = 0L
        val budget = RunBudget(clock = { now })
        budget.timed {
            now += 100
            budget.timed { now += 300 }
            now += 100
        }
        assertEquals(500, budget.waitedMs)
    }

    @Test fun cancelStopsEverything() {
        val budget = RunBudget()
        budget.cancel()
        assertFalse(budget.take())
        val g = gateway()
        val out = Assistant(g).analyze(conv, work, budget) as AnalysisOutcome.Failed
        assertEquals(Kind.CANCELLED, out.error.kind)
        assertEquals(Kind.CANCELLED, Assistant(g).draftAndCheck(conv, work, goal, false, budget).error!!.kind)
        assertTrue(g.calls.isEmpty())
    }

    @Test fun cancelDuringDraftingDropsTheRound() {
        val budget = RunBudget()
        val g = FakeGateway(onDecisions = checks(), onChat = { _, _ ->
            budget.cancel(); ChatResult(Answers.replies("One.", "Two."), null)
        })
        val set = Assistant(g).draftAndCheck(conv, work, goal, false, budget)
        assertEquals(Kind.CANCELLED, set.error!!.kind)
        assertTrue(set.verdicts.isEmpty())
        assertTrue(g.decisions.isEmpty())
    }
}
