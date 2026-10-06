package com.jev.overseas.core

import com.jev.overseas.core.engine.AnalysisEngine
import com.jev.overseas.core.engine.NextStep
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared
import com.jev.overseas.core.testing.Answers
import com.jev.overseas.core.testing.chat
import com.jev.overseas.core.testing.me
import com.jev.overseas.core.testing.them
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the code against the matrices the user approved, cell by cell
 * (`src/test/resources/approved/matrix.tsv`, typed by hand from the
 * approved scene matrices). Unlike ReadingTableGoldenTest, which
 * only pins whatever the code does today, a failure here means the code
 * disagrees with an approved decision.
 *
 * Each behaviour row runs the real analysis on a synthetic answer: the row's
 * behaviour (and any extra hits) at 0.9, everything else at 0.05, no friction.
 */
class ApprovedMatrixTest {

    private data class Row(
        val line: Int, val kind: String, val scene: String, val relationship: String, val conflict: Boolean,
        val behavior: String, val extra: List<String>, val position: String, val next: String,
        val doc: String, val source: String, val status: String,
    ) {
        override fun toString() = "line $line: $scene/$relationship${if (conflict) "/conflict" else ""} $behavior ($doc, $source)"
    }

    private val rows: List<Row> = File("src/test/resources/approved/matrix.tsv").readLines()
        .withIndex()
        .filter { (_, l) -> l.isNotBlank() && !l.startsWith("#") && !l.startsWith("kind\t") }
        .map { (i, l) ->
            val c = l.split('\t')
            Row(i + 1, c[0], c[1], c[2], c[3] == "yes", c[4], c[5].split(',').filter { it.isNotBlank() },
                c[6], c[7], c[8], c[9], c[10])
        }

    private fun selection(r: Row): Selection {
        val scene = SceneCatalog.scene(r.scene)!!
        val rel = if (r.relationship == "none") null else SceneCatalog.relationship(scene, r.relationship)
            ?: error("unknown relationship in $r")
        return Selection(scene, rel, r.conflict)
    }

    private fun nextName(n: NextStep) = when (n) {
        NextStep.CHOOSE_STANCE -> "CHOOSE"
        NextStep.DIRECT_DRAFT -> "DIRECT"
        NextStep.NO_REPLY_NEEDED -> "NO_REPLY"
        else -> n.name
    }

    @Test fun tableCoversEveryScene() {
        val approved = rows.filter { it.status == "approved" }
        assertTrue("expected the full table, got ${approved.size}", approved.size >= 140)
        for (scene in Scene.values()) assertTrue("no rows for $scene", approved.any { it.scene == scene.id })
    }

    @Test fun behaviourCellsMatchTheApprovedMatrix() {
        val failures = ArrayList<String>()
        for (r in rows.filter { it.kind == "behavior" && it.status == "approved" }) {
            val sel = selection(r)
            val spec = SceneCatalog.spec(sel.scene)
            if (spec.behaviors(sel).none { it.id == r.behavior }) { failures.add("$r: not asked"); continue }
            val hits = (r.extra + r.behavior).toSet()
            val result = Answers.all(AnalysisEngine.questions(sel), noul = { if (it in hits) 0.9 else 0.05 })
            val analysis = AnalysisEngine.interpret(chat(me("hi"), them("ok so here's the thing about this")), sel, result)
            val found = (analysis.detected + analysis.cues).firstOrNull { it.behavior.id == r.behavior }
            val position = found?.reading?.position?.name ?: "-"
            if (position != r.position) failures.add("$r: position $position, approved ${r.position}")
            val next = nextName(analysis.next)
            if (next != r.next) failures.add("$r: next $next, approved ${r.next}")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun shortReplyToneFollowsTheApprovedMatrix() {
        val failures = ArrayList<String>()
        for (r in rows.filter { it.kind == "tone" }) {
            val sel = selection(r).copy(conflict = false)
            val rule = SceneCatalog.spec(sel.scene).toneReading(sel, shortReply = true, tense = r.conflict)
            val got = rule?.questionId ?: "-"
            if (got != r.behavior) failures.add("$r: tone $got, approved ${r.behavior}")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun questionsNotAskedWhereTheMatrixSaysSo() {
        for (r in rows.filter { it.kind == "absent" }) {
            val sel = selection(r)
            assertTrue("$r is asked", SceneCatalog.spec(sel.scene).behaviors(sel).none { it.id == r.behavior })
        }
    }

    /** General: "miss you" gets no relationship reading; the user is asked to pick a scene, never drafted for directly. */
    @Test fun generalPersonalWordsAskForAScene() {
        for (r in rows.filter { it.kind == "notice" }) {
            val sel = selection(r)
            val result = Answers.all(AnalysisEngine.questions(sel), noul = { if (it == r.behavior) 0.9 else 0.05 })
            val a = AnalysisEngine.interpret(chat(me("hey"), them("miss you!")), sel, result)
            assertEquals("$r", r.next, nextName(a.next))
            assertTrue("$r", a.suggestScene && AnalysisEngine.PERSONAL_NOTICE in a.notices)
            assertTrue("$r: nothing to choose", a.groups.isNotEmpty())
        }
    }

    /** Pending rows are listed, not asserted: they wait for the user's decision. */
    @Test fun pendingRowsAreKnown() {
        val pending = rows.filter { it.status == "pending" }.map { it.behavior }.toSet()
        assertEquals(setOf("R08"), pending)
        assertTrue(Shared.SHARED_BEHAVIORS.none { it.id in pending })
    }
}
