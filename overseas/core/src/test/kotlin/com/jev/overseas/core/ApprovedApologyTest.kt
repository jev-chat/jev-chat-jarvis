package com.jev.overseas.core

import com.jev.overseas.core.engine.GoalBuilder
import com.jev.overseas.core.scene.Apology
import com.jev.overseas.core.scene.FamilyScene
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The apology level of each stance against the table the user approved
 * (`approved/apology.tsv`, typed by hand from the approved apology table). The goal is built the way
 * the session builds it, with the relationship, so overrides count.
 */
class ApprovedApologyTest {

    @Test fun apologyFollowsTheApprovedTable() {
        val rows = File("src/test/resources/approved/apology.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("scene\t") }
        assertTrue(rows.size >= 60)
        val failures = ArrayList<String>()
        for (line in rows) {
            val (sceneId, behavior, stanceId, relId, level) = line.split('\t')
            val scene = SceneCatalog.scene(sceneId)!!
            val rel = SceneCatalog.relationship(scene, relId)!!
            val sel = Selection(scene, rel, behavior == "conflict")
            val spec = SceneCatalog.spec(scene)
            val options = when (behavior) {
                "conflict" -> spec.conflictStances
                "pressure" -> FamilyScene.PRESSURE_STANCES
                else -> spec.stances(behavior, sel, setOf(behavior))?.options.orEmpty()
            }
            val stance = options.firstOrNull { it.id == stanceId }
            if (stance == null) { failures.add("$line: stance not offered"); continue }
            val goal = GoalBuilder.fromStance(stance, relationshipId = rel.id)
            val expected = Apology.valueOf(level)
            if (goal.apology != expected) failures.add("$line: got ${goal.apology}")
            val hasItem = goal.mustInclude.any { it.startsWith("says sorry") || it.startsWith("apologises") }
            if ((expected == Apology.EXPECTED) != hasItem) failures.add("$line: required apology item present=$hasItem")
            if (expected == Apology.AVOID && "apologises" !in goal.mustAvoid) failures.add("$line: no 'apologises' in must_avoid")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
