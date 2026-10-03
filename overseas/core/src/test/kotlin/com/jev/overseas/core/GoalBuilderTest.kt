package com.jev.overseas.core

import com.jev.overseas.core.engine.GoalBuilder
import com.jev.overseas.core.engine.GoalSwitch
import com.jev.overseas.core.scene.Apology
import com.jev.overseas.core.scene.Direction
import com.jev.overseas.core.scene.StanceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalBuilderTest {

    private val moreTime = StanceOption("more_time", "Ask for more time", "Say the time isn't possible and ask for more time",
        mustInclude = listOf("says the requested time is not possible", "asks for more time"),
        mustAvoid = listOf("promises a specific new delivery date", "apologises twice"),
        avoidUnlessDetail = listOf("promises a specific new delivery date"),
        commitments = listOf("will keep them posted"),
        direction = Direction.KEEP)

    @Test fun withoutDetailsKeepsEveryAvoid() {
        val g = GoalBuilder.fromStance(moreTime)
        assertEquals(moreTime.mustAvoid, g.mustAvoid)
        assertEquals(moreTime.summary, g.summary)
        assertNull(g.userDetails)
        assertTrue(g.userFacts.isEmpty())
        assertEquals(listOf("will keep them posted"), g.commitments)
        assertEquals("more_time", g.stanceId)
        assertEquals(1, g.version)
    }

    @Test fun detailsLiftAvoidUnlessDetail() {
        val g = GoalBuilder.fromStance(moreTime, details = "  next Tuesday ")
        assertEquals(listOf("apologises twice"), g.mustAvoid)
        assertEquals("${moreTime.summary} (next Tuesday)", g.summary)
        assertEquals(listOf("The user says: next Tuesday"), g.userFacts)
        assertTrue(g.commitments.last().contains("next Tuesday"))
        assertEquals(Direction.KEEP, g.direction)
    }

    @Test fun blankDetailsAreNoDetails() {
        assertEquals(moreTime.mustAvoid, GoalBuilder.fromStance(moreTime, details = "   ").mustAvoid)
    }

    @Test fun switchesAddAvoids() {
        val g = GoalBuilder.fromStance(moreTime, switches = GoalSwitch.values().toSet(), version = 3)
        // This stance promises something, so "No new promises" would block every draft; it is not applied.
        assertFalse(GoalSwitch.NO_NEW_PROMISES.avoid in g.mustAvoid)
        assertTrue(GoalSwitch.NO_REASONS.avoid in g.mustAvoid)
        assertTrue(GoalSwitch.NO_APOLOGY.avoid in g.mustAvoid)
        assertEquals(3, g.version)
        assertEquals(g.mustAvoid.distinct(), g.mustAvoid)
    }

    // ------------------------------------------------------------------ apology

    private val work = com.jev.overseas.core.scene.Selection(com.jev.overseas.core.scene.Scene.WORK, com.jev.overseas.core.scene.WorkScene.SENIOR)
    private fun workStance(behavior: String, id: String) =
        com.jev.overseas.core.scene.WorkScene.stances(behavior, work, emptySet())!!.options.first { it.id == id }

    @Test fun lateToAManagerExpectsAShortSorry() {
        val g = GoalBuilder.fromStance(workStance("W02", "late"), relationshipId = "senior")
        assertEquals(Apology.EXPECTED, g.apology)
        assertTrue("says sorry briefly for the delay" in g.mustInclude)
        assertEquals("expected", g.toState()["apology"])
        assertEquals("the delay", g.toState()["apology_for"])
        assertEquals(false, g.toState()["allows_fault_admission"])
    }

    @Test fun toSomeoneWhoReportsToYouItIsOptional() {
        val g = GoalBuilder.fromStance(workStance("W02", "late"), relationshipId = "report")
        assertEquals(Apology.OPTIONAL, g.apology)
        assertTrue(g.mustInclude.none { it.startsWith("says sorry") })
    }

    @Test fun dontApologiseOverridesAnExpectedApology() {
        val g = GoalBuilder.fromStance(workStance("W02", "late"), switches = setOf(GoalSwitch.NO_APOLOGY), relationshipId = "senior")
        assertEquals(Apology.AVOID, g.apology)
        assertTrue(g.mustInclude.none { it.startsWith("says sorry") })
        assertTrue("apologises" in g.mustAvoid)
    }

    @Test fun avoidStancesRuleOutAnApology() {
        val g = GoalBuilder.fromStance(workStance("W06", "disagree"), relationshipId = "senior")
        assertEquals(Apology.AVOID, g.apology)
        assertTrue("apologises" in g.mustAvoid)
    }

    @Test fun anApologyStanceAddsNothingAndHidesTheSwitch() {
        val admit = com.jev.overseas.core.scene.Shared.complaintStances().first { it.id == "admit_apologise" }
        assertTrue(admit.isApology)
        assertFalse(GoalSwitch.NO_APOLOGY in GoalSwitch.applicable(admit))
        val g = GoalBuilder.fromStance(admit, switches = setOf(GoalSwitch.NO_APOLOGY))
        assertEquals(admit.mustInclude, g.mustInclude)
        assertFalse("apologises" in g.mustAvoid)
    }

    @Test fun declineToAManagerOrClientButNotAColleague() {
        val decline = workStance("W04", "decline")
        assertEquals(Apology.EXPECTED, GoalBuilder.fromStance(decline, relationshipId = "senior").apology)
        assertEquals(Apology.EXPECTED, GoalBuilder.fromStance(decline, relationshipId = "client_or_external").apology)
        assertEquals(Apology.OPTIONAL, GoalBuilder.fromStance(decline, relationshipId = "peer").apology)
    }

    @Test fun editedSummaryReplacesTheTemplate() {
        val before = GoalBuilder.fromStance(moreTime, version = 2)
        val g = GoalBuilder.fromEditedSummary(before, "  Say I'm out until Monday  ", setOf(GoalSwitch.NO_APOLOGY))
        assertEquals("Say I'm out until Monday", g.summary)
        // One check that every point the user wrote is in the reply.
        assertEquals(listOf("covers every point in the goal summary"), g.mustInclude)
        assertEquals(listOf(GoalSwitch.NO_APOLOGY.avoid), g.mustAvoid)
        assertEquals(3, g.version)
        assertNull(g.stanceId)
        // Fault and feelings are asked about, but only so the user looks before sending.
        assertFalse(g.allowsFaultAdmission)
        assertFalse(g.allowsDeclaration)
        assertEquals(setOf("admits_fault", "unstated_declaration"), g.confirmChecks)
        assertTrue(g.hasDecision)
        assertFalse(g.clarifyOnly)
        // The previous stance's direction does not carry over to words the user wrote.
        assertNull(g.direction)
        assertEquals(listOf("The user says: Say I'm out until Monday"), g.userFacts)
        assertEquals(1, GoalBuilder.fromEditedSummary(null, "x").version)
    }
}
