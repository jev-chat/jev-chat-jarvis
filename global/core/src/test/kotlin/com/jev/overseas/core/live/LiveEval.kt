package com.jev.overseas.core.live

import com.jev.overseas.core.engine.AnalysisEngine
import com.jev.overseas.core.engine.AnalysisOutcome
import com.jev.overseas.core.engine.Assistant
import com.jev.overseas.core.engine.CandidateChecks
import com.jev.overseas.core.engine.ChatMessage
import com.jev.overseas.core.engine.CheckOutcome
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.Draft
import com.jev.overseas.core.engine.Goal
import com.jev.overseas.core.engine.NextStep
import com.jev.overseas.core.engine.RunBudget
import com.jev.overseas.core.engine.Sender
import com.jev.overseas.core.engine.Thresholds
import com.jev.overseas.core.json.MiniJson
import com.jev.overseas.core.net.Answer
import com.jev.overseas.core.net.ChatResult
import com.jev.overseas.core.net.DecisionsResult
import com.jev.overseas.core.net.ModelConfig
import com.jev.overseas.core.net.ModelGateway
import com.jev.overseas.core.net.OpenRouterGateway
import com.jev.overseas.core.net.Usage
import com.jev.overseas.core.scene.Direction
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Runs the pilot corpus against the live models and prints how the answers
 * compare with the author's expected labels. A development tool, not a unit
 * test: it needs a key and spends a little money.
 *
 * Usage (from the repository root):
 *   sh ./gradlew :core:liveEval -PevalArgs="analysis"
 *   sh ./gradlew :core:liveEval -PevalArgs="candidates"
 *   sh ./gradlew :core:liveEval -PevalArgs="ladders"
 *   sh ./gradlew :core:liveEval -PevalArgs="draft"
 *   sh ./gradlew :core:liveEval -PevalArgs="revision"   (see RevisionEval)
 *   add "case=<id prefix>" to run a subset, "raw" to print every probability.
 *
 * Results are "agreement with the author's expected labels", not accuracy: the
 * labels have not been checked by independent annotators.
 */
fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: "analysis"
    val filter = args.firstOrNull { it.startsWith("case=") }?.removePrefix("case=")
    val raw = "raw" in args
    if (mode == "key") {
        // Free: what OpenRouter reports for this key (all usage of the key, not only this project).
        val info = OpenRouterGateway(ModelConfig(apiKey = LiveKey.load())).keyInfo()
        println("key usage: $" + "%.4f".format(info.usage) + ", limit: " + (info.limit?.let { "$" + "%.2f".format(it) } ?: "none") +
            ", remaining: " + (info.limitRemaining?.let { "$" + "%.4f".format(it) } ?: "n/a"))
        return
    }
    val corpus = File(System.getProperty("jev.fixtures"), "corpus/batch-0-r2")
    val gateway = LedgerGateway(OpenRouterGateway(ModelConfig(apiKey = LiveKey.load())))
    val eval = LiveEval(corpus, gateway, filter, raw)
    when (mode) {
        "analysis" -> eval.analysis()
        "candidates" -> eval.candidates()
        "ladders" -> eval.ladders()
        "draft" -> eval.draft()
        "edge" -> EdgeEval(File(System.getProperty("jev.fixtures"), "corpus/edge-v1"), gateway, filter).run()
        "revision" -> RevisionEval(gateway, filter).run(File(System.getProperty("jev.fixtures")).parentFile.resolve("_reports/revision-eval.txt"))
        else -> error("Unknown mode $mode")
    }
    println("\nrequests: ${gateway.calls}, cost this run: $" + "%.5f".format(gateway.cost))
}

object LiveKey {
    /** From OPENROUTER_API_KEY, or ~/.config/jev/openrouter.env (KEY=value or a bare key). Never printed. */
    fun load(): String {
        System.getenv("OPENROUTER_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val file = File(System.getProperty("user.home"), ".config/jev/openrouter.env")
        val line = file.readLines().map { it.trim() }.first { it.isNotEmpty() && !it.startsWith("#") }
        return line.substringAfter('=', line).trim().trim('"', '\'')
    }
}

/** Counts calls and cost, and appends each call to _reports/ledger.jsonl. */
class LedgerGateway(private val inner: ModelGateway) : ModelGateway {
    @Volatile var calls = 0
    @Volatile var cost = 0.0
    private val ledger = File(System.getProperty("jev.fixtures")).parentFile.resolve("_reports/ledger.jsonl")

    @Synchronized private fun log(route: String, usage: Usage?, ok: Boolean, seconds: Double) {
        calls++
        cost += usage?.cost ?: 0.0
        ledger.parentFile.mkdirs()
        ledger.appendText(MiniJson.encode(linkedMapOf(
            "at" to java.time.LocalDateTime.now().toString().take(19), "route" to route, "model" to "kotlin-live", "ok" to ok,
            "seconds" to seconds, "usage" to mapOf("cost" to (usage?.cost ?: 0.0)),
        )) + "\n")
    }

    override fun decisions(state: Map<String, Any?>, questions: Map<String, Any?>): DecisionsResult {
        val t = System.nanoTime()
        try {
            return inner.decisions(state, questions).also { log("decisions", it.usage, true, (System.nanoTime() - t) / 1e9) }
        } catch (e: Exception) {
            log("decisions", null, false, (System.nanoTime() - t) / 1e9); throw e
        }
    }

    override fun chat(system: String, user: String, temperature: Double, maxTokens: Int, json: Boolean): ChatResult {
        val t = System.nanoTime()
        try {
            return inner.chat(system, user, temperature, maxTokens, json).also {
                log("chat", it.usage, true, (System.nanoTime() - t) / 1e9)
                if (System.getenv("JEV_DEBUG_CHAT") == "1") println("   [chat raw] " + it.content.replace("\n", " ").take(600))
            }
        } catch (e: Exception) {
            log("chat", null, false, (System.nanoTime() - t) / 1e9); throw e
        }
    }
}

@Suppress("UNCHECKED_CAST")
class LiveEval(private val corpus: File, private val gateway: ModelGateway, private val filter: String?, private val raw: Boolean) {

    private val overrides: Map<String, Any?> = File(corpus, "overrides.json").takeIf { it.exists() }
        ?.let { MiniJson.parseObject(it.readText()) } ?: emptyMap()

    private fun lines(name: String): List<Map<String, Any?>> =
        File(corpus, name).readLines().filter { it.isNotBlank() }.map { MiniJson.parseObject(it) }

    private fun cases() = lines("cases.jsonl").filter { filter == null || (it["case_id"] as String).startsWith(filter) }

    private fun conversation(messages: List<*>): Conversation = Conversation(messages.map {
        val m = it as Map<String, Any?>
        ChatMessage(if (m["speaker"] == "self") Sender.USER else Sender.OTHER, m["text"] as String)
    })

    private fun selection(item: Map<String, Any?>): Selection {
        val scene = SceneCatalog.scene(item["scene"] as String)!!
        return Selection(scene, SceneCatalog.relationship(scene, item["relationship_type"] as String), item["conflict"] == true)
    }

    private fun strings(value: Any?): List<String> = (value as? List<*>)?.map { it.toString() } ?: emptyList()
    private fun ints(value: Any?): List<Int> = (value as? List<*>)?.map { (it as Number).toInt() } ?: emptyList()

    // ------------------------------------------------------------------ analysis

    fun analysis() {
        val assistant = Assistant(gateway)
        val perId = HashMap<String, IntArray>() // [trueHit, trueUnsure, trueMiss, falseOk, falseUnsure, falseHit]
        var frictionOk = 0; var frictionN = 0
        var toneOk = 0; var toneN = 0
        var chooseOk = 0; var chooseN = 0
        var positionOk = 0; var positionN = 0
        for (case in cases()) {
            val id = case["case_id"] as String
            val expected = case["expected"] as Map<String, Any?>
            val conv = conversation(case["messages"] as List<*>)
            val sel = selection(case)
            val outcome = assistant.analyze(conv, sel, RunBudget())
            if (outcome !is AnalysisOutcome.Ready) { println("== $id  NOT ANALYSED: $outcome"); continue }
            val a = outcome.analysis
            val over = (overrides["cases"] as? Map<String, Any?>)?.get(id) as? Map<String, Any?>
            val moves = (over?.get("move_behavior") as? Map<String, Any?>) ?: emptyMap()
            val trueIds = (strings(expected["behaviors_true"]).filter { moves[it] == null } + moves.filterValues { it == "true" }.keys).distinct()
            val falseIds = strings(expected["behaviors_false"]).filter { moves[it] == null }
            val problems = ArrayList<String>()
            for (b in trueIds) {
                val p = a.probabilities[b]
                val slot = perId.getOrPut(b) { IntArray(6) }
                when {
                    p == null -> problems.add("$b not asked")
                    p >= Thresholds.HIT -> slot[0]++
                    p > Thresholds.MISS -> { slot[1]++; problems.add("$b expected true, unsure ${fmt(p)}") }
                    else -> { slot[2]++; problems.add("$b expected true, MISS ${fmt(p)}") }
                }
            }
            for (b in falseIds) {
                val p = a.probabilities[b] ?: continue
                val slot = perId.getOrPut(b) { IntArray(6) }
                when {
                    p <= Thresholds.MISS -> slot[3]++
                    p < Thresholds.HIT -> { slot[4]++; problems.add("$b expected false, unsure ${fmt(p)}") }
                    else -> { slot[5]++; problems.add("$b expected false, HIT ${fmt(p)}") }
                }
            }
            // friction
            for ((key, part) in listOf("friction_other" to a.friction?.other, "friction_self" to a.friction?.self)) {
                val exp = expected[key] as Map<String, Any?>
                val acceptable = (over?.get(key + "_acceptable") as? List<*>)?.map { (it as Number).toInt() } ?: ints(exp["acceptable"])
                val got = part?.score
                frictionN++
                if (got != null && acceptable.any { abs(got - it) <= 0.5 }) frictionOk++
                else problems.add("$key expected $acceptable got ${got?.let(::fmt)} ${part?.probabilities?.map { (it * 100).roundToInt() }}")
            }
            // tone
            val expectedTone = expected["tone_signal"] as String
            val acceptableTone = strings(expected["tone_acceptable"]).ifEmpty { listOf(expectedTone) }
            val gotTone = when {
                a.tone == null -> "none"
                a.tone!!.unclear -> "unclear"
                a.tone!!.title.contains("impatience", true) -> "impatience"
                a.tone!!.title.contains("Low", true) -> "low_engagement"
                else -> "displeasure"
            }
            toneN++
            if (gotTone in acceptableTone) toneOk++ else problems.add("tone expected $acceptableTone got $gotTone")
            // must choose
            val expectChoose = expected["must_choose_first"] == true
            val gotChoose = a.next == NextStep.CHOOSE_STANCE || a.next == NextStep.SAFETY_HOLD || a.next == NextStep.BOUNDARY
            chooseN++
            if (expectChoose == gotChoose) chooseOk++ else problems.add("must_choose expected $expectChoose, next=${a.next}")
            // baseline position
            for ((bid, pos) in (expected["baseline_position"] as Map<String, Any?>)) {
                if (bid == "very_short_reply") continue
                positionN++
                val got = (a.detected + a.cues + a.unsure).firstOrNull { it.behavior.id == bid }?.reading?.position?.name?.lowercase()
                val want = pos.toString()
                if (got == want || (want == "at" && got == "cue")) positionOk++ else problems.add("position $bid expected $want got $got")
            }
            val labels = a.labels.joinToString { "${it.behavior.id}:${fmt(it.probability)}" }
            println("== $id  next=${a.next}  labels=[$labels]  cues=${a.cues.map { it.behavior.id }}  friction=${a.friction?.let { fmt(it.level) + " " + it.source }}  tone=$gotTone")
            problems.forEach { println("     ! $it") }
            if (raw) println("     " + a.probabilities.entries.joinToString(" ") { "${it.key}=${fmt(it.value)}" })
        }
        println("\n-- behaviour agreement per ID (true: hit/unsure/miss | false: ok/unsure/hit)")
        var th = 0; var tu = 0; var tm = 0; var fo = 0; var fu = 0; var fh = 0
        for ((id, s) in perId.toSortedMap()) {
            println("   %-22s true %d/%d/%d   false %d/%d/%d".format(id, s[0], s[1], s[2], s[3], s[4], s[5]))
            th += s[0]; tu += s[1]; tm += s[2]; fo += s[3]; fu += s[4]; fh += s[5]
        }
        println("-- totals: expected-true hit $th, unsure $tu, miss $tm | expected-false ok $fo, unsure $fu, hit $fh")
        println("-- friction within acceptable: $frictionOk/$frictionN | tone: $toneOk/$toneN | must-choose: $chooseOk/$chooseN | baseline position: $positionOk/$positionN")
    }

    // ------------------------------------------------------------------ candidates

    private fun goal(raw: Map<String, Any?>, facts: List<String>, sel: Selection, hasDecision: Boolean): Goal {
        val direction = Direction.values().firstOrNull { it.id == raw["direction"] }
        val rel = sel.relationship?.id
        return Goal(
            stanceId = null,
            stanceLabel = raw["stance"] as String,
            summary = raw["summary"] as String,
            direction = direction,
            mustInclude = strings(raw["must_include"]),
            mustAvoid = strings(raw["must_avoid"]),
            commitments = strings(raw["authorized_commitments"]),
            userDetails = facts.joinToString(" ").ifBlank { null },
            hasDecision = hasDecision,
            clarifyOnly = false,
            allowsFaultAdmission = false,
            allowsDeclaration = direction == Direction.CLOSER || rel == "dating" || rel == "established",
            version = 1,
        )
    }

    fun candidates() {
        var hardOk = 0; var hardN = 0; var cleanOk = 0; var cleanN = 0
        var gOk = 0; var gN = 0; var eOk = 0; var eN = 0; var incOk = 0; var incN = 0
        for (case in cases().filter { it["case_type"] == "candidate" }) {
            val id = case["case_id"] as String
            val conv = conversation(case["messages"] as List<*>)
            val sel = selection(case)
            val goal = goal(case["goal"] as Map<String, Any?>, strings(case["user_facts"]), sel, case["g_applicable"] != false)
            val questions = CandidateChecks.questions(sel, goal, boundaryActive = false, hasLatestTurn = true)
            println("== $id  goal: ${goal.summary}")
            for (cand in case["candidates"] as List<*>) {
                val c = cand as Map<String, Any?>
                val exp = c["expected"] as Map<String, Any?>
                val text = c["text"] as String
                val result = gateway.decisions(CandidateChecks.state(conv, sel, goal, text), questions)
                val v = CandidateChecks.verdict(Draft(c["intended_role"] as String, text), sel, goal, false, true, result)
                val expectedViolations = strings(exp["violations"]).toSet()
                val gotViolations = v.hardChecks.filter { it.outcome == CheckOutcome.FAIL }
                    .map { if (it.id.startsWith("avoid.")) "must_avoid_violated" else it.id }.toSet()
                val unsure = v.hardChecks.filter { it.outcome == CheckOutcome.UNSURE }.map { it.id + ":" + fmt(it.probability!!) }
                val line = StringBuilder("   [${c["cand_id"]} ${c["intended_role"]}] state=${v.state} total=${v.total} G=${v.g?.let { fmt(it.value) }} E=${v.e?.let { fmt(it.value) }}")
                if (expectedViolations.isEmpty()) {
                    cleanN++
                    if (gotViolations.isEmpty()) cleanOk++ else line.append("  ! FALSE ALARM $gotViolations")
                    if (unsure.isNotEmpty()) line.append("  (unsure $unsure)")
                    val gLevel = (exp["g_level"] as? Number)?.toInt()
                    val gAcc = ints(exp["g_acceptable"])
                    if (gLevel != null && v.g != null) {
                        gN++
                        if (gAcc.any { abs(v.g!!.value - it) <= 0.6 }) gOk++ else line.append("  ! G expected $gAcc")
                    }
                    val eAcc = ints(exp["e_acceptable"])
                    if (v.e != null && eAcc.isNotEmpty()) {
                        eN++
                        if (eAcc.any { abs(v.e!!.value - it) <= 0.6 }) eOk++ else line.append("  ! E expected $eAcc")
                    }
                    val met = (exp["must_include_met"] as List<*>).map { it == true }
                    v.checklist.filter { it.id.startsWith("include.") }.forEachIndexed { i, r ->
                        incN++
                        val want = met.getOrNull(i) ?: return@forEachIndexed
                        val got = r.outcome == CheckOutcome.PASS
                        if (want == got) incOk++ else line.append("  ! include[$i] expected $want got ${r.outcome} ${fmt(r.probability ?: -1.0)}")
                    }
                } else {
                    hardN++
                    val main = exp["main_violation"] as? String
                    if (main != null && main in gotViolations) hardOk++
                    else line.append("  ! MISSED $main (got $gotViolations, unsure $unsure)")
                    val extra = gotViolations - expectedViolations
                    if (extra.isNotEmpty()) line.append("  (extra $extra)")
                }
                println(line)
                println("        " + text.take(150))
                if (raw) println("        " + result.answers.entries.joinToString(" ") { (k, a) ->
                    k + "=" + when (a) { is Answer.Noul -> fmt(a.yes); is Answer.Score -> fmt(a.score) + a.probabilities.map { (it * 100).roundToInt() } }
                })
            }
        }
        println("\n-- violation candidates caught (main violation): $hardOk/$hardN")
        println("-- clean candidates not blocked: $cleanOk/$cleanN")
        println("-- must_include agreement: $incOk/$incN | G within acceptable: $gOk/$gN | E within acceptable: $eOk/$eN")
    }

    // ------------------------------------------------------------------ ladders

    fun ladders() {
        for (ladder in lines("ladders.jsonl").filter { filter == null || (it["ladder_id"] as String).startsWith(filter) }) {
            val id = ladder["ladder_id"] as String
            val kind = ladder["kind"] as String
            val sel = selection(ladder)
            val situation = ladder["situation"] as Map<String, Any?>
            val conv = conversation(situation["messages"] as List<*>)
            val goal = goal(situation["goal"] as Map<String, Any?>, strings(situation["user_facts"]), sel, true)
            val questions = CandidateChecks.questions(sel, goal, boundaryActive = false, hasLatestTurn = true)
            println("== $id ($kind, ${sel.scene.id}/${sel.relationship?.id})  goal: ${goal.summary}")
            val values = ArrayList<Double>()
            for (rung in ladder["rungs"] as List<*>) {
                val r = rung as Map<String, Any?>
                val text = r["text"] as String
                val result = gateway.decisions(CandidateChecks.state(conv, sel, goal, text), questions)
                val score = result.answers[if (kind == "G") "g" else "e"] as? Answer.Score
                val other = result.answers[if (kind == "G") "e" else "g"] as? Answer.Score
                values.add(score?.score ?: -1.0)
                println("   L${r["level"]}: ${kind}=${score?.let { fmt(it.score) }} ${score?.probabilities?.map { (it * 100).roundToInt() }}  (other=${other?.let { fmt(it.score) }})  ${text.take(110)}")
            }
            val inversions = values.zipWithNext().count { (a, b) -> b < a - 0.25 }
            println("   order inversions: $inversions; values ${values.map(::fmt)}")
        }
    }

    // ------------------------------------------------------------------ drafting

    fun draft() {
        val assistant = Assistant(gateway)
        val items = cases().filter { it["case_type"] == "candidate" }.map { Triple(it["case_id"] as String, it, it["goal"] as Map<String, Any?>) } +
            lines("ladders.jsonl").map { val s = it["situation"] as Map<String, Any?>; Triple(it["ladder_id"] as String, it + s, s["goal"] as Map<String, Any?>) }
        for ((id, item, rawGoal) in items) {
            if (filter != null && !id.startsWith(filter)) continue
            val sel = selection(item)
            val conv = conversation(item["messages"] as List<*>)
            val goal = goal(rawGoal, strings(item["user_facts"]), sel, item["g_applicable"] != false)
            val budget = RunBudget()
            val set = assistant.draftAndCheck(conv, sel, goal, boundaryActive = false, budget = budget)
            println("== $id  goal: ${goal.summary}   (requests ${budget.used}, revised=${set.revised}, close=${set.close}) ${set.message ?: ""}")
            for (v in set.verdicts) {
                println("   [${v.draft.label}] ${v.state} total=${v.total} G=${v.g?.let { fmt(it.value) + " " + it.label }} E=${v.e?.let { fmt(it.value) + " " + it.label }}")
                println("        ${v.draft.text}")
                v.reasons.forEach { println("        - $it") }
                val notPassed = (v.hardChecks + v.checklist).filter { it.outcome != CheckOutcome.PASS }
                if (notPassed.isNotEmpty()) println("        checks: " + notPassed.joinToString { "${it.id}=${it.outcome}:${it.probability?.let(::fmt)}" })
            }
        }
    }

    private fun fmt(v: Double) = "%.2f".format(v)
}

/**
 * Edge cases (corpus/edge-v1): unusual inputs that the pilot corpus does not
 * cover. Each case states the expected next step and key hits; some also draft
 * one stance and check the replies. Mismatches are reported as known weaknesses,
 * not fixed by moving thresholds.
 */
@Suppress("UNCHECKED_CAST")
class EdgeEval(private val corpus: File, private val gateway: ModelGateway, private val filter: String?) {

    private fun strings(value: Any?): List<String> = (value as? List<*>)?.map { it.toString() } ?: emptyList()

    private fun sender(s: Any?) = if (s == "self") Sender.USER else Sender.OTHER

    private fun conversation(messages: List<*>): Conversation = Conversation(messages.map {
        val m = it as Map<String, Any?>
        val text = m["text"] as String
        val quote = (m["quote"] as? Map<String, Any?>)?.let { q -> ChatMessage(sender(q["speaker"]), q["text"] as String) }
        when (m["kind"]) {
            "photo" -> ChatMessage(sender(m["speaker"]), "[photo] $text", readable = true, replyTo = quote)
            "voice" -> ChatMessage(sender(m["speaker"]), "[voice message]", readable = false, replyTo = quote)
            else -> ChatMessage(sender(m["speaker"]), text, replyTo = quote)
        }
    })

    fun run() {
        val assistant = Assistant(gateway)
        var pass = 0; var n = 0
        for (line in File(corpus, "cases.jsonl").readLines().filter { it.isNotBlank() }) {
            val case = MiniJson.parseObject(line)
            val id = case["case_id"] as String
            if (filter != null && !id.startsWith(filter)) continue
            n++
            val scene = SceneCatalog.scene(case["scene"] as String)!!
            val sel = Selection(scene, SceneCatalog.relationship(scene, case["relationship_type"] as String?), case["conflict"] == true)
            val conv = conversation(case["messages"] as List<*>)
            val exp = case["expected"] as Map<String, Any?>
            val problems = ArrayList<String>()
            val budget = RunBudget()
            val outcome = assistant.analyze(conv, sel, budget)
            if (outcome !is AnalysisOutcome.Ready) {
                println("== $id  NOT ANALYSED: $outcome")
                problems.add("not analysed")
            } else {
                val a = outcome.analysis
                fun hit(b: String) = a.hit(b)
                strings(exp["next"]).takeIf { it.isNotEmpty() }?.let { if (a.next.name !in it) problems.add("next ${a.next}, expected $it") }
                strings(exp["hits"]).filterNot(::hit).forEach { problems.add("$it expected hit, got ${a.probabilities[it]?.let(::fmt)}") }
                strings(exp["hits_any"]).takeIf { it.isNotEmpty() }?.let { ids ->
                    if (ids.none(::hit)) problems.add("expected one of $ids, got ${ids.map { "$it=${a.probabilities[it]?.let(::fmt)}" }}")
                }
                strings(exp["not_hits"]).filter(::hit).forEach { problems.add("$it expected not hit, got ${fmt(a.probabilities[it]!!)}") }
                val tone = when { a.tone == null -> "none"; a.tone!!.unclear -> "unclear"; else -> "shown" }
                strings(exp["tone"]).takeIf { it.isNotEmpty() }?.let { if (tone !in it) problems.add("tone $tone, expected $it") }
                (exp["glossary_min"] as? Number)?.toInt()?.let { min ->
                    if (conv.glossary.size < min) problems.add("glossary ${conv.glossary.keys}, expected at least $min")
                }
                val labels = a.labels.joinToString { "${it.behavior.id}:${fmt(it.probability)}" }
                println("== $id  next=${a.next}  labels=[$labels]  cues=${a.cues.map { it.behavior.id }}  friction=${a.friction?.let { fmt(it.level) }}  tone=$tone  missing=${a.missing.size}")
                (case["draft"] as? Map<String, Any?>)?.let { d -> draft(assistant, conv, sel, a, d, budget, problems) }
            }
            if (problems.isEmpty()) pass++
            problems.forEach { println("     ! $it") }
        }
        println("\n-- edge cases as expected: $pass/$n")
    }

    private fun draft(
        assistant: Assistant, conv: Conversation, sel: Selection, a: com.jev.overseas.core.engine.Analysis,
        d: Map<String, Any?>, budget: RunBudget, problems: MutableList<String>,
    ) {
        val stanceId = d["stance"] as String
        val option = (a.groups.flatMap { it.options } + a.extraOptions).firstOrNull { it.id == stanceId }
        if (option == null) { problems.add("stance $stanceId not offered"); return }
        val boundary = d["boundary"] == true
        val set = assistant.draftAndCheck(conv, sel, com.jev.overseas.core.engine.GoalBuilder.fromStance(option), boundary, budget)
        println("   draft '$stanceId' (requests ${budget.used}, revised=${set.revised}) ${set.message ?: ""}")
        for (v in set.verdicts) {
            println("     [${v.draft.label}] ${v.state} total=${v.total}  ${v.draft.text}")
            val notPassed = (v.hardChecks + v.checklist).filter { it.outcome != CheckOutcome.PASS }
            if (notPassed.isNotEmpty()) println("        checks: " + notPassed.joinToString { "${it.id}=${it.outcome}:${it.probability?.let(::fmt)}" })
            if (!v.copyable) continue
            val lower = v.draft.text.lowercase()
            strings(d["forbid"]).filter { lower.contains(it) }.forEach { problems.add("usable reply contains '$it': ${v.draft.text}") }
            if (d["no_question"] == true && '?' in v.draft.text) problems.add("usable reply asks a question: ${v.draft.text}")
        }
        if (set.verdicts.isEmpty()) problems.add("no replies drafted")
    }

    private fun fmt(v: Double) = "%.2f".format(v)
}
