package com.jev.overseas.core

import com.jev.overseas.core.engine.CandidateChecks
import com.jev.overseas.core.engine.CandidateState
import com.jev.overseas.core.engine.CheckOutcome
import com.jev.overseas.core.engine.Draft
import com.jev.overseas.core.engine.Goal
import com.jev.overseas.core.engine.Scoring
import com.jev.overseas.core.engine.Verdict
import com.jev.overseas.core.net.Answer
import com.jev.overseas.core.scene.FriendsScene
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.WorkScene
import com.jev.overseas.core.testing.Answers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateChecksTest {

    private val work = Selection(Scene.WORK, WorkScene.SENIOR)
    private val friends = Selection(Scene.FRIENDS, FriendsScene.REGULAR)

    private fun goal(
        hasDecision: Boolean = true,
        clarifyOnly: Boolean = false,
        fault: Boolean = false,
        declaration: Boolean = false,
        include: List<String> = listOf("says no"),
        avoid: List<String> = listOf("agrees to part"),
    ) = Goal("cannot", "Can't", "Say you can't", null, include, avoid, emptyList(), null, hasDecision, clarifyOnly, fault, declaration, 1)

    private fun ids(sel: Selection, g: Goal, boundary: Boolean = false) = CandidateChecks.hardChecks(sel, g, boundary).map { it.id }

    @Test fun hardChecksFollowTheGoal() {
        // beyond_goal: a stance's goal covers one request, so deciding another one is checked.
        assertEquals(listOf("unsupported_fact", "new_commitment", "commits_others", "opposite_stance", "avoid.0", "admits_fault",
            "beyond_goal", "contradicts_earlier"), ids(work, goal()))
        assertFalse("beyond_goal" in ids(work, goal(hasDecision = false)))
        assertFalse("opposite_stance" in ids(work, goal(hasDecision = false)))
        val clarify = ids(work, goal(clarifyOnly = true))
        assertFalse("opposite_stance" in clarify)
        assertTrue("beyond_clarification" in clarify)
        assertFalse("admits_fault" in ids(work, goal(fault = true)))
        assertTrue("crosses_boundary" in ids(work, goal(), boundary = true))
        assertTrue("unstated_declaration" in ids(friends, goal()))
        assertFalse("unstated_declaration" in ids(friends, goal(declaration = true)))
        assertFalse("unstated_declaration" in ids(work, goal()))
        assertEquals(listOf("avoid.0", "avoid.1"), ids(work, goal(avoid = listOf("a", "b"))).filter { it.startsWith("avoid.") })
    }

    @Test fun questionsAskGOnlyWithADecision() {
        val withG = CandidateChecks.questions(work, goal(), false, true)
        assertTrue("g" in withG && "e" in withG)
        assertFalse("acknowledges" in withG) // work is not a personal scene
        val noG = CandidateChecks.questions(friends, goal(hasDecision = false), false, true)
        assertFalse("g" in noG)
        assertTrue("acknowledges" in noG)
        assertFalse("acknowledges" in CandidateChecks.questions(friends, goal(), false, hasLatestTurn = false))
    }

    // ------------------------------------------------------------------ verdicts

    private fun verdict(
        g: Goal = goal(),
        yes: Map<String, Double> = emptyMap(),
        include: Double = 0.9,
        gScore: Answer.Score? = Answers.level(5),
        eScore: Answer.Score? = Answers.level(4),
        drop: Set<String> = emptySet(),
        sel: Selection = work,
    ): Verdict {
        val qs = CandidateChecks.questions(sel, g, false, true)
        val result = Answers.all(qs,
            noul = { id -> if (id in drop) null else yes[id] ?: if (id.startsWith("include.") || id == "acknowledges") include else 0.05 },
            score = { id, _ -> if (id == "g") gScore else eScore })
        return CandidateChecks.verdict(Draft("more direct", "No, sorry."), sel, g, false, true, result)
    }

    @Test fun eligible() {
        val v = verdict()
        assertEquals(CandidateState.ELIGIBLE, v.state)
        assertEquals(0.7 * 5 + 0.3 * 4, v.total!!, 1e-9)
        assertTrue(v.copyable && v.scored)
        assertEquals("Complete", v.g!!.label)
    }

    @Test fun statePriority() {
        // violation beats everything
        assertEquals(CandidateState.NEEDS_REWRITE, verdict(yes = mapOf("new_commitment" to 0.9, "unsupported_fact" to 0.5), include = 0.1, drop = setOf("commits_others")).state)
        // a hard check that did not come back
        assertEquals(CandidateState.FAILED, verdict(yes = mapOf("unsupported_fact" to 0.5), drop = setOf("commits_others")).state)
        // A missing required item is worse than an unsure check, so it wins
        assertEquals(CandidateState.NEEDS_EDIT, verdict(yes = mapOf("unsupported_fact" to 0.5), include = 0.1).state)
        // an unsure required item asks the user to look; it is no longer counted as passed
        assertEquals(CandidateState.AWAITING_CONFIRMATION, verdict(include = 0.5).state)
        // a required item that did not come back cannot be cleared
        assertEquals(CandidateState.FAILED, verdict(drop = setOf("include.0")).state)
        assertEquals(CandidateState.NEEDS_EDIT, verdict(include = 0.1, gScore = null).state)
        assertEquals(CandidateState.UNSCORED, verdict(gScore = null).state)
        assertEquals(CandidateState.ELIGIBLE, verdict().state)
    }

    @Test fun blockedAndFailedAreNotCopyable() {
        val blocked = verdict(yes = mapOf("opposite_stance" to 0.95))
        assertFalse(blocked.copyable)
        assertFalse(blocked.scored)
        assertTrue(blocked.reasons.single().contains("goes against your decision"))
        val avoid = verdict(yes = mapOf("avoid.0" to 0.95))
        assertTrue(avoid.reasons.single().contains("agrees to part, which you ruled out"))
        assertFalse(verdict(drop = setOf("new_commitment")).copyable)
        assertTrue(verdict(gScore = null).copyable)
        assertTrue(verdict(include = 0.1).copyable)
    }

    @Test fun thresholdEdges() {
        fun outcome(p: Double) = verdict(yes = mapOf("unsupported_fact" to p)).hardChecks.first { it.id == "unsupported_fact" }.outcome
        assertEquals(CheckOutcome.FAIL, outcome(0.7))
        assertEquals(CheckOutcome.UNSURE, outcome(0.6999))
        assertEquals(CheckOutcome.UNSURE, outcome(0.4501))
        assertEquals(CheckOutcome.PASS, outcome(0.45))
        fun included(p: Double) = verdict(include = p).checklist.first().outcome
        assertEquals(CheckOutcome.PASS, included(0.7))
        assertEquals(CheckOutcome.UNSURE, included(0.5))
        assertEquals(CheckOutcome.FAIL, included(0.3))
    }

    @Test fun totalArithmetic() {
        val g = Answers.spread(0.0, 0.0, 0.0, 0.0, 0.8, 0.2) // 4.2
        val e = Answers.spread(0.0, 0.0, 0.0, 0.4, 0.6, 0.0) // 3.6
        assertEquals(4.0, verdict(gScore = g, eScore = e).total!!, 1e-9)
        assertEquals(4.0, Scoring.shown(Scoring.total(4.2, 3.6)), 1e-9)
        assertEquals(4.3, Scoring.shown(Scoring.total(4.5, 3.9)), 1e-9)
    }

    @Test fun deliveryOnlyWhenNothingToDecide() {
        val v = verdict(g = goal(hasDecision = false), eScore = Answers.spread(0.0, 0.0, 0.0, 0.0, 0.7, 0.3), sel = friends)
        assertNull(v.g)
        assertEquals(4.3, v.total!!, 1e-9)
        assertEquals(CandidateState.ELIGIBLE, v.state)
    }

    @Test fun wrongLevelCountIsUnscored() {
        val v = verdict(gScore = Answer.Score(4.0, listOf(0.0, 0.0, 0.0, 1.0, 0.0), 0.9))
        assertEquals(CandidateState.UNSCORED, v.state)
        assertNull(v.total)
        assertNull(v.g)
    }

    @Test fun splitProbabilitiesAreFlagged() {
        assertTrue(verdict(gScore = Answers.spread(0.0, 0.0, 0.5, 0.0, 0.5, 0.0)).g!!.inconsistent)
        assertFalse(verdict(gScore = Answers.spread(0.0, 0.0, 0.0, 0.5, 0.5, 0.0)).g!!.inconsistent)
    }

    // ------------------------------------------------------------------ ranking

    private fun scored(total: Double, text: String) = Verdict(Draft("x", text), CandidateState.ELIGIBLE, emptyList(), emptyList(), null, null, total, emptyList())
    private fun state(s: CandidateState, text: String) = Verdict(Draft("x", text), s, emptyList(), emptyList(), null, null, null, emptyList())

    @Test fun rankOrder() {
        val ranked = CandidateChecks.rank(listOf(
            state(CandidateState.NEEDS_REWRITE, "blocked"), state(CandidateState.UNSCORED, "unscored"),
            scored(3.9, "low"), scored(4.4, "high"), scored(3.9, "low2"),
        ))
        assertEquals(listOf("high", "low", "low2", "unscored", "blocked"), ranked.map { it.draft.text })
    }

    @Test fun closeness() {
        assertTrue(CandidateChecks.isClose(listOf(scored(4.3, "a"), scored(4.1, "b"))))
        assertFalse(CandidateChecks.isClose(listOf(scored(4.3, "a"), scored(4.0, "b"))))
        assertFalse(CandidateChecks.isClose(listOf(scored(4.3, "a"), state(CandidateState.UNSCORED, "b"))))
    }

    @Test fun feedbackText() {
        val a = verdict(yes = mapOf("new_commitment" to 0.9))
        val b = verdict(include = 0.1)
        assertEquals(listOf(
            "A reply promises something you didn't authorise. Do not do that.",
            "A reply did not do this required thing: says no.",
        ), CandidateChecks.feedback(listOf(a, b, a)))
    }

    /** An own goal still asks about fault and feelings, but a "yes" only asks the user to look. */
    @Test fun ownGoalFaultAndFeelingsAreConfirmOnly() {
        val own = com.jev.overseas.core.engine.GoalBuilder.fromEditedSummary(null, "Tell her I can't do Friday")
        assertTrue("admits_fault" in ids(friends, own))
        assertTrue("unstated_declaration" in ids(friends, own))
        val v = verdict(g = own, sel = friends, yes = mapOf("unstated_declaration" to 0.9))
        assertEquals(CandidateState.AWAITING_CONFIRMATION, v.state)
        assertTrue(v.copyable)
        assertTrue(CandidateChecks.feedback(listOf(v), own).isEmpty())
    }

    // ------------------------------------------------------------------ completion, ranking, top pick

    private fun scored(state: CandidateState, total: Double, split: Boolean = false) = Verdict(
        Draft("x", "reply $state $total"), state, emptyList(), emptyList(),
        com.jev.overseas.core.engine.ScorePart(total, "", split, List(6) { 1.0 / 6 }), null, total, emptyList())

    /** A reply that misses something never outranks one that passed everything, whatever its score. */
    @Test fun eligibleRepliesComeFirstWhateverTheScore() {
        val missing = scored(CandidateState.NEEDS_EDIT, 4.6)
        val good = scored(CandidateState.ELIGIBLE, 4.2)
        val toCheck = scored(CandidateState.AWAITING_CONFIRMATION, 4.8)
        val ranked = CandidateChecks.rank(listOf(missing, toCheck, good))
        assertEquals(listOf(good, toCheck, missing), ranked)
        assertEquals(0, CandidateChecks.topPick(ranked))
    }

    @Test fun noTopPickWithoutAnEligibleReplyOrWhenClose() {
        assertNull(CandidateChecks.topPick(CandidateChecks.rank(listOf(scored(CandidateState.AWAITING_CONFIRMATION, 4.8)))))
        val close = CandidateChecks.rank(listOf(scored(CandidateState.ELIGIBLE, 4.3), scored(CandidateState.ELIGIBLE, 4.2)))
        assertTrue(CandidateChecks.isClose(close))
        assertNull(CandidateChecks.topPick(close))
        // An eligible reply far ahead of a non-eligible one is not "close".
        val apart = CandidateChecks.rank(listOf(scored(CandidateState.ELIGIBLE, 4.1), scored(CandidateState.NEEDS_EDIT, 4.2)))
        assertFalse(CandidateChecks.isClose(apart))
        assertEquals(0, CandidateChecks.topPick(apart))
        // Jev was split on the best one: no Top pick.
        assertNull(CandidateChecks.topPick(listOf(scored(CandidateState.ELIGIBLE, 4.6, split = true))))
    }

    /** What the user typed is a required item, and missing it caps G at "Key point missing". */
    @Test fun aMissingDetailIsNeedsEditAndCapsG() {
        val late = WorkScene.stances("W02", work, emptySet())!!.options.first { it.id == "late" }
        val g = com.jev.overseas.core.engine.GoalBuilder.fromStance(late, "Thursday")
        val detail = g.mustInclude.indexOfFirst { it.contains("\"Thursday\"") }
        assertTrue(detail >= 0)
        assertEquals(listOf("Thursday"), (g.toState()["user_details"] as List<*>))
        val v = verdict(g = g, yes = mapOf("include.$detail" to 0.1), gScore = Answers.level(5))
        assertEquals(CandidateState.NEEDS_EDIT, v.state)
        assertEquals(3.0, v.g!!.value, 1e-9)
        assertTrue(v.g!!.capped)
        assertEquals("Key point missing", v.g!!.label)
        assertEquals(Scoring.shown(0.7 * 3 + 0.3 * 4), v.total!!, 1e-9)
    }

    @Test fun checkCountsLeaveOutAcknowledges() {
        val v = verdict(sel = friends)
        val (passed, total) = v.checkCounts
        assertEquals(total, v.hardChecks.size + v.checklist.count { it.id.startsWith("include.") })
        assertEquals(passed, total)
    }

    /** To a client, "Acknowledge and fix it" may not admit fault unchecked; an admission asks the user to look. */
    @Test fun clientAcknowledgementFlagsAnAdmissionForTheUser() {
        val client = Selection(Scene.WORK, WorkScene.CLIENT)
        val ack = WorkScene.stances("W06", client, emptySet())!!.options.first()
        assertEquals("acknowledge_fix", ack.id)
        assertFalse(ack.allowsFaultAdmission)
        val g = com.jev.overseas.core.engine.GoalBuilder.fromStance(ack)
        assertTrue("admits_fault" in ids(client, g))
        val v = verdict(g = g, sel = client, yes = mapOf("admits_fault" to 0.9))
        assertEquals(CandidateState.AWAITING_CONFIRMATION, v.state)
        // A manager still gets the plain stance, where admitting fault is the point.
        val senior = WorkScene.stances("W06", work, emptySet())!!.options.first()
        assertTrue(senior.allowsFaultAdmission)
        assertEquals(listOf(WorkScene.CLIENT_COMPLAINT), WorkScene.notices(client, setOf("W06")))
    }

    /** "Thursday" earlier and "Monday" now asks the user to check; it never blocks the reply. */
    @Test fun contradictionAsksTheUserToCheck() {
        val v = verdict(yes = mapOf("contradicts_earlier" to 0.95))
        assertEquals(CandidateState.AWAITING_CONFIRMATION, v.state)
        assertTrue(v.reasons.any { it.contains("differs from what you said earlier") })
        assertTrue(CandidateChecks.feedback(listOf(v), goal()).isEmpty())
    }
}
