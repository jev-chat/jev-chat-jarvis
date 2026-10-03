package com.jev.overseas.core

import com.jev.overseas.core.engine.AnalysisEngine
import com.jev.overseas.core.json.MiniJson
import com.jev.overseas.core.scene.Behavior
import com.jev.overseas.core.scene.FamilyScene
import com.jev.overseas.core.scene.InputMode
import com.jev.overseas.core.scene.Role
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.SceneSpec
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared
import com.jev.overseas.core.scene.StanceGroup
import com.jev.overseas.core.scene.StanceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consistency of the scene tables, for every scene, every relationship type (and
 * none), with the conflict switch on and off. These catch editing slips such as
 * a priority entry with a typo or an option that can never be drafted.
 */
class SceneDataTest {

    private fun selections(): List<Pair<SceneSpec, Selection>> = SceneCatalog.specs.values.flatMap { spec ->
        (spec.relationships + listOf(null)).flatMap { rel ->
            listOf(false, true).map { spec to Selection(spec.scene, rel, it) }
        }
    }

    private fun name(sel: Selection) = "${sel.scene.id}/${sel.relationship?.id ?: "none"}/${if (sel.conflict) "conflict" else "calm"}"

    private fun groups(spec: SceneSpec, sel: Selection): List<StanceGroup> =
        (spec.behaviors(sel)).flatMap { b -> listOf(emptySet(), setOf(b.id)).mapNotNull { spec.stances(b.id, sel, it) } }

    private fun options(spec: SceneSpec, sel: Selection): List<StanceOption> =
        groups(spec, sel).flatMap { it.options } +
            spec.behaviors(sel).mapNotNull { spec.defaultStance(it.id, sel) } +
            spec.conflictStances + FamilyScene.PRESSURE_STANCES +
            listOf(Shared.BRIEF_ACK, Shared.NO_REPLY, Shared.POLITE_CLOSE, Shared.CLARIFY, Shared.ANSWER)

    @Test fun questionIdsAreUniquePerRequest() {
        for ((spec, sel) in selections()) {
            val ids = (spec.behaviors(sel) + spec.toneQuestions(sel)).map { it.id } + listOf(Shared.FRICTION_OTHER_ID, Shared.FRICTION_SELF_ID)
            assertEquals("duplicate question id in ${name(sel)}", ids.size, ids.toSet().size)
            assertEquals(ids.toSet(), AnalysisEngine.questions(sel).keys)
        }
    }

    @Test fun requestSizeAndEncoding() {
        for ((_, sel) in selections()) {
            val qs = AnalysisEngine.questions(sel)
            assertTrue("${name(sel)} asks ${qs.size} questions", qs.size <= 60)
            val encoded = MiniJson.encode(qs)
            assertEquals(qs.keys, MiniJson.parseObject(encoded).keys)
        }
    }

    @Test fun priorityIdsExist() {
        for (spec in SceneCatalog.specs.values) {
            val known = selections().filter { it.first == spec }.flatMap { spec.behaviors(it.second) }.map { it.id }.toSet()
            for (id in spec.priority) assertTrue("${spec.scene.id} priority lists unknown $id", id in known)
        }
    }

    @Test fun everyActionCanBeAnswered() {
        for ((spec, sel) in selections()) {
            for (b in spec.behaviors(sel).filter { it.role == Role.ACTION }) {
                val answerable = spec.stances(b.id, sel, setOf(b.id)) != null || spec.defaultStance(b.id, sel) != null
                assertTrue("${name(sel)}: ${b.id} has neither a stance group nor a default stance", answerable)
            }
        }
    }

    @Test fun levelsHaveSixSteps() {
        for ((spec, sel) in selections()) {
            assertEquals(name(sel), 6, spec.eLevels(sel).size)
            assertEquals(name(sel), 6, spec.frictionExamples.size)
        }
        assertEquals(6, Shared.G_LEVELS.size)
    }

    @Test fun optionIdsAreUniqueWithinAGroup() {
        for ((spec, sel) in selections()) for (g in groups(spec, sel)) {
            val ids = g.options.map { it.id }
            assertEquals("${name(sel)} group '${g.title}' repeats an option id", ids.size, ids.toSet().size)
            assertTrue(g.options.isNotEmpty())
        }
    }

    @Test fun optionRules() {
        for ((spec, sel) in selections()) for (o in options(spec, sel)) {
            val where = "${name(sel)} option ${o.id}"
            if (o.input == InputMode.REQUIRED) assertTrue("$where needs an input hint", o.inputHint.isNotBlank())
            if (o.noReply) assertTrue("$where sends nothing but has must-include items", o.mustInclude.isEmpty())
            assertTrue("$where: avoidUnlessDetail must be part of mustAvoid", o.mustAvoid.containsAll(o.avoidUnlessDetail))
            assertTrue("$where has no label", o.label.isNotBlank() && o.summary.isNotBlank())
        }
    }

    @Test fun variantsDiffer() {
        for ((spec, sel) in selections()) {
            val (a, b) = spec.variants(sel)
            assertTrue(name(sel), a.isNotBlank() && b.isNotBlank() && a != b)
        }
    }

    @Test fun toneReadingPointsToAnAskedQuestion() {
        for ((spec, sel) in selections()) {
            val asked = spec.toneQuestions(sel).map { it.id }.toSet()
            for (short in listOf(false, true)) for (tense in listOf(false, true)) {
                val rule = spec.toneReading(sel, short, tense) ?: continue
                assertTrue("${name(sel)} tone rule uses ${rule.questionId}, which is not asked", rule.questionId in asked)
            }
        }
    }

    @Test fun questionTextHasNoEmojiOrLongDash() {
        val texts = selections().flatMap { (spec, sel) ->
            (spec.behaviors(sel) + spec.toneQuestions(sel)).flatMap { b: Behavior -> listOf(b.question, b.yes, b.no) } +
                spec.eLevels(sel).flatMap { listOf(it.what) + it.examples }
        } + Shared.G_LEVELS.flatMap { listOf(it.what) + it.examples } +
            listOf(Shared.G_QUESTION, Shared.E_QUESTION, Shared.FRICTION_OTHER_QUESTION, Shared.FRICTION_SELF_QUESTION) +
            listOf(Shared.UNSUPPORTED_FACT, Shared.NEW_COMMITMENT, Shared.COMMITS_OTHERS, Shared.OPPOSITE_STANCE, Shared.CROSSES_BOUNDARY,
                Shared.ADMITS_FAULT, Shared.UNSTATED_DECLARATION, Shared.BEYOND_CLARIFICATION, Shared.ACKNOWLEDGES)
                .flatMap { listOf(it.question, it.yes, it.no) }
        for (t in texts.distinct()) {
            assertTrue("long dash in: $t", '—' !in t && '–' !in t)
            assertTrue("emoji in: $t", t.codePoints().noneMatch { it >= 0x1F000 || it in 0x2600..0x27BF })
        }
    }
}
