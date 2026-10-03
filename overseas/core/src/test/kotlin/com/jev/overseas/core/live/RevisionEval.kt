package com.jev.overseas.core.live

import com.jev.overseas.core.engine.AnalysisEngine
import com.jev.overseas.core.engine.AnalysisOutcome
import com.jev.overseas.core.engine.Assistant
import com.jev.overseas.core.engine.CandidateChecks
import com.jev.overseas.core.engine.CandidateState
import com.jev.overseas.core.engine.ChatMessage
import com.jev.overseas.core.engine.CheckOutcome
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.Draft
import com.jev.overseas.core.engine.Goal
import com.jev.overseas.core.engine.GoalBuilder
import com.jev.overseas.core.engine.GoalSwitch
import com.jev.overseas.core.engine.RunBudget
import com.jev.overseas.core.engine.Sender
import com.jev.overseas.core.net.Answer
import com.jev.overseas.core.net.ModelGateway
import com.jev.overseas.core.scene.Apology
import com.jev.overseas.core.scene.FamilyScene
import com.jev.overseas.core.scene.FriendsScene
import com.jev.overseas.core.scene.GeneralScene
import com.jev.overseas.core.scene.RomanceScene
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.SceneCatalog
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared
import com.jev.overseas.core.scene.StanceOption
import com.jev.overseas.core.scene.WorkScene
import java.io.File

/**
 * The live checks for the revised scoring and analysis. Debug sets written for
 * these changes, not an independent validation set.
 *
 *   sh ./gradlew :core:liveEval -PevalArgs="revision"            all parts
 *   sh ./gradlew :core:liveEval -PevalArgs="revision case=g"     one part: g, apology, matrix, checks, friction
 */
class RevisionEval(private val gateway: ModelGateway, private val part: String?) {

    private val out = StringBuilder()
    private fun say(line: String) { println(line); out.append(line).append('\n') }

    private fun me(t: String) = ChatMessage(Sender.USER, t)
    private fun them(t: String) = ChatMessage(Sender.OTHER, t)
    private fun conv(vararg m: ChatMessage) = Conversation(m.toList())

    private fun stance(sel: Selection, behavior: String, id: String): StanceOption =
        SceneCatalog.spec(sel.scene).stances(behavior, sel, setOf(behavior))!!.options.first { it.id == id }

    fun run(reportFile: File) {
        if (part == null || part == "g") gLadder()
        if (part == null || part == "apology") apology()
        if (part == null || part == "matrix") matrix()
        if (part == null || part == "checks") checks()
        if (part == null || part == "friction") friction()
        if (part == "file") fileTurn()
        reportFile.parentFile.mkdirs()
        reportFile.appendText(out.toString())
        say("report appended to ${reportFile.name}")
    }

    // ------------------------------------------------------------------ G completion ladder

    private class Ladder(val name: String, val sel: Selection, val conv: Conversation, val goal: Goal, val replies: List<String>)

    private fun gLadder() {
        say("\n=== G completion ladder: each group is written from level 0 to 5; G should rise with it")
        val senior = Selection(Scene.WORK, WorkScene.SENIOR)
        val friend = Selection(Scene.FRIENDS, FriendsScene.REGULAR)
        val parent = Selection(Scene.FAMILY, FamilyScene.PARENT)
        val errands = Selection(Scene.GENERAL, GeneralScene.ERRANDS)
        val ladders = listOf(
            Ladder("work late + Thursday", senior, conv(me("hi Sarah"), them("Can you send the deck by Friday?")),
                GoalBuilder.fromStance(stance(senior, "W01", "more_time"), "Thursday next week", relationshipId = "senior"), listOf(
                    "Sure, Friday's fine.",
                    "The figures are in the shared folder.",
                    "Friday's looking a bit tight on my side.",
                    "Sorry, I can't do Friday.",
                    "Sorry, Friday won't work, I'd need more time, maybe Thursday-ish next week? Though I might still manage Friday.",
                    "Sorry, I can't do Friday. Could I have until Thursday next week?")),
            Ladder("friends can't make it", friend, conv(me("hey"), them("pub friday?")),
                GoalBuilder.fromStance(stance(friend, "F01", "not_going"), relationshipId = "regular"), listOf(
                    "Yes! See you there.",
                    "Haha that pub is great.",
                    "Friday's a bit busy for me tbh.",
                    "Can't make Friday.",
                    "Sorry, can't make Friday. Maybe? I'll see.",
                    "Sorry, can't make it Friday. Have a good one!")),
            Ladder("parent not coming home", parent, conv(me("Hi Mum"), them("Are you coming home for Christmas?")),
                GoalBuilder.fromStance(stance(parent, "A04", "no"), relationshipId = "parent_or_elder"), listOf(
                    "Yes, I'll be home for Christmas!",
                    "How's the dog doing?",
                    "Christmas is looking complicated this year.",
                    "I won't be coming home for Christmas.",
                    "Sorry Mum, I don't think I'll make it home for Christmas, maybe.",
                    "Sorry Mum, I can't come home for Christmas this year.")),
            Ladder("viewing other time + Saturday", errands, conv(me("Hi, is the flat still available?"), them("Yes. Can you do a viewing Thursday at 5pm?")),
                GoalBuilder.fromStance(stance(errands, "K04", "other_time"), "Saturday morning", relationshipId = "errands"), listOf(
                    "Thursday at 5pm works.",
                    "Is the flat still available?",
                    "Thursday might be tricky.",
                    "Thursday doesn't work for me, sorry.",
                    "Sorry, Thursday doesn't work. Maybe the weekend sometime?",
                    "Sorry, Thursday doesn't work for me. Could we do Saturday morning?")),
        )
        var inversions = 0; var pairs = 0
        for (l in ladders) {
            val qs = CandidateChecks.questions(l.sel, l.goal, false, true)
            val gs = ArrayList<Double>()
            say("-- ${l.name}  (goal: ${l.goal.summary}; apology ${l.goal.apology.wire})")
            l.replies.forEachIndexed { level, text ->
                val r = gateway.decisions(com.jev.overseas.core.engine.CandidateChecks.state(l.conv, l.sel, l.goal, text), qs)
                val v = CandidateChecks.verdict(Draft("x", text), l.sel, l.goal, false, true, r)
                val raw = (r.answers["g"] as? Answer.Score)?.score
                gs.add(raw ?: -1.0)
                val fault = v.hardChecks.firstOrNull { it.id == "admits_fault" }
                say("   L$level G=${raw?.let(::f2) ?: "-"} shown=${v.g?.value?.let(::f2) ?: "-"} E=${v.e?.value?.let(::f2) ?: "-"} " +
                    "${v.state} admits_fault=${fault?.probability?.let(::f2) ?: "-"}  \"$text\"")
            }
            for (i in gs.indices) for (j in i + 1 until gs.size) { pairs++; if (gs[i] > gs[j] + 0.25) inversions++ }
        }
        say("G ladder: $inversions inversions (> 0.25) in $pairs ordered pairs")
    }

    // ------------------------------------------------------------------ apology

    private fun apology() {
        say("\n=== Apology: real drafts. EXPECTED should carry one short sorry; AVOID none; admits_fault should pass")
        val assistant = Assistant(gateway, "UK")
        val senior = Selection(Scene.WORK, WorkScene.SENIOR)
        val report = Selection(Scene.WORK, WorkScene.REPORT)
        val c = conv(me("Morning"), them("Any update on the Q3 report? Need it for Friday's meeting."))
        val cases = listOf(
            Triple("manager, late (expected)", senior, GoalBuilder.fromStance(stance(senior, "W02", "late"), "Monday", relationshipId = "senior")),
            Triple("manager, late, Don't apologise", senior, GoalBuilder.fromStance(stance(senior, "W02", "late"), "Monday", setOf(GoalSwitch.NO_APOLOGY), relationshipId = "senior")),
            Triple("report, late (optional)", report, GoalBuilder.fromStance(stance(report, "W02", "late"), "Monday", relationshipId = "report")),
            Triple("manager, disagree (avoid)", senior, GoalBuilder.fromStance(stance(senior, "W06", "disagree"), "the numbers match the finance export", relationshipId = "senior")),
        )
        val problemConv = conv(me("Here's the report"), them("The totals in section 2 are wrong."))
        val sorry = Regex("\\b(sorry|apolog\\w*)\\b", RegexOption.IGNORE_CASE)
        for ((name, sel, goal) in cases) {
            val chatConv = if (name.contains("disagree")) problemConv else c
            val set = assistant.draftAndCheck(chatConv, sel, goal, false, RunBudget())
            say("-- $name  (apology ${goal.apology.wire})")
            if (set.error != null) { say("   ERROR ${set.error!!.message}"); continue }
            for (v in set.verdicts) {
                val fault = v.hardChecks.firstOrNull { it.id == "admits_fault" }
                say("   sorry=${sorry.containsMatchIn(v.draft.text)} ${v.state} total=${v.total ?: "-"} admits_fault=${fault?.probability?.let(::f2) ?: "-"}  \"${v.draft.text}\"")
                v.reasons.forEach { say("      $it") }
            }
        }
    }

    // ------------------------------------------------------------------ matrix rows never run live before

    private class Row(val name: String, val sel: Selection, val conv: Conversation, val expect: String, val position: String?)

    private fun matrix() {
        say("\n=== Matrix rows not run live before: expected behaviour detected, and its position")
        val w = { r: com.jev.overseas.core.scene.RelationshipType -> Selection(Scene.WORK, r) }
        val f = { r: com.jev.overseas.core.scene.RelationshipType -> Selection(Scene.FRIENDS, r) }
        val fa = { r: com.jev.overseas.core.scene.RelationshipType -> Selection(Scene.FAMILY, r) }
        val g = { r: com.jev.overseas.core.scene.RelationshipType -> Selection(Scene.GENERAL, r) }
        val rows = listOf(
            Row("work peer escalation", w(WorkScene.PEER), conv(me("I'll get to it"), them("If this isn't sorted by tomorrow I'll have to flag it to Priya.")), "W12", "ABOVE"),
            Row("work client escalation", w(WorkScene.CLIENT), conv(me("We're looking into it"), them("This is the third delay. I'm escalating this to your director.")), "W12", "ABOVE"),
            Row("work report escalation", w(WorkScene.REPORT), conv(me("Let's discuss Monday"), them("If nothing changes I'll raise it with HR.")), "W12", "RARE"),
            Row("work report chasing again", w(WorkScene.REPORT), conv(them("Did you get a chance to approve my leave?"), me("Will check"), them("Any update on my leave request?")), "W13", "RARE"),
            Row("work peer chasing again", w(WorkScene.PEER), conv(them("Can you send me the slides?"), me("Yep"), them("Following up on the slides, need them today")), "W13", "CUE"),
            Row("work progress", w(WorkScene.SENIOR), conv(me("Morning"), them("Where are we with the Q3 numbers?")), "W02", "AT"),
            Row("work per my last email", w(WorkScene.CLIENT), conv(me("Thanks"), them("Per my last email, we still need the signed contract.")), "tone.impatience", null),
            Row("work small talk", w(WorkScene.SENIOR), conv(me("Have a good weekend"), them("Morning! Good weekend?")), "W15", null),
            Row("friends regular teasing", f(FriendsScene.REGULAR), conv(me("I locked myself out again"), them("classic you lol")), "F05", "DEPENDS"),
            Row("friends new swearing", f(FriendsScene.NEW), conv(me("I don't think that's right"), them("wtf are you on about")), "F10", "ABOVE"),
            Row("friends close swearing", f(FriendsScene.CLOSE), conv(me("I ate the last slice"), them("you absolute idiot lmao")), "F10", "AT"),
            Row("friends swearing about weather", f(FriendsScene.CLOSE), conv(me("you out?"), them("wtf is this weather, soaked")), "F10:no", null),
            Row("friends big favour", f(FriendsScene.REGULAR), conv(me("hey"), them("could you lend me £300 till payday?")), "F06", "ABOVE"),
            Row("friends close cancels again", f(FriendsScene.CLOSE), conv(them("can't do saturday sorry"), me("no worries, next week?"), me("friday?"), them("gonna have to bail on friday too, sorry")), "cancelled_before", "CUE"),
            Row("family sibling personal decision", fa(FamilyScene.SIBLING), conv(me("yeah all good"), them("are you still with that guy?")), "A02", "DEPENDS"),
            Row("family in-law money", fa(FamilyScene.IN_LAW), conv(me("Thanks for dinner"), them("Could you help with the cost of Mum's new boiler?")), "A05", "ABOVE"),
            Row("family parent guilt", fa(FamilyScene.PARENT), conv(me("Busy week"), them("I suppose we'll just have Christmas on our own then.")), "A08", "CUE"),
            Row("family parent teasing", fa(FamilyScene.PARENT), conv(me("burnt the rice again"), them("still can't cook then? 😂")), "A10", "DEPENDS"),
            Row("family in-law teasing", fa(FamilyScene.IN_LAW), conv(me("I'll bring dessert"), them("hopefully not shop-bought this time haha")), "A10", "ABOVE"),
            Row("general price", g(GeneralScene.ERRANDS), conv(me("Is the bike still for sale?"), them("Yes, it's £120, collection only.")), "B01", null),
            Row("general deposit", g(GeneralScene.ERRANDS), conv(me("I'd like the flat"), them("Great, can you send the deposit today to hold it?")), "B02", null),
            Row("general hurry", g(GeneralScene.ERRANDS), conv(me("Let me think about it"), them("I've got two other people viewing today.")), "B06", null),
            Row("general greeting", g(GeneralScene.UNSPECIFIED), conv(me("thanks for the help yesterday"), them("no problem! how's your week going?")), "K01", null),
            Row("romance ultimatum", Selection(Scene.ROMANCE, RomanceScene.ESTABLISHED), conv(me("I'm going out with the team tonight"), them("If you go out tonight, we're done.")), "ultimatum", null),
        )
        val assistant = Assistant(gateway)
        var ok = 0; var posOk = 0; var posN = 0
        for (r in rows) {
            val outcome = assistant.analyze(r.conv, r.sel, RunBudget())
            if (outcome !is AnalysisOutcome.Ready) { say("   ${r.name}: NOT ANALYSED $outcome"); continue }
            val a = outcome.analysis
            val negative = r.expect.endsWith(":no")
            val id = r.expect.removeSuffix(":no")
            val p = a.probabilities[id]
            val hit = if (id.startsWith("tone.")) a.tone != null && !a.tone!!.unclear else (p ?: 0.0) >= 0.7
            val good = hit != negative
            if (good) ok++
            val found = (a.detected + a.cues).firstOrNull { it.behavior.id == id }
            val pos = found?.reading?.position?.name
            if (r.position != null && !negative) { posN++; if (pos == r.position) posOk++ }
            say("   ${if (good) "ok " else "XX "} ${r.name}: $id p=${p?.let(::f2) ?: "-"} pos=${pos ?: "-"}${r.position?.let { " (want $it)" } ?: ""} next=${a.next} " +
                "top=${a.detected.take(3).joinToString(",") { it.behavior.id + ":" + f2(it.probability) }}")
        }
        say("matrix rows: $ok/${rows.size} detected as expected; position $posOk/$posN")
    }

    // ------------------------------------------------------------------ new checks

    private fun checks() {
        say("\n=== beyond_goal (on) and contradicts_earlier (research, off in the app): yes cases should be >= 0.7, no cases <= 0.45")
        val senior = Selection(Scene.WORK, WorkScene.SENIOR)
        val two = conv(me("Morning"), them("Can you get the report to me by Friday? Also, are you OK with option B for the launch?"))
        val moreTime = GoalBuilder.fromStance(stance(senior, "W01", "more_time"), relationshipId = "senior")
        val beyond = listOf(
            true to "Sorry, Friday won't work, could I have until Tuesday? And yes, option B is fine.",
            true to "Sorry, I can't do Friday. Option B works for me though.",
            true to "Sorry, I'll need more time on the report, could I have till Tuesday? I'd rather not go with B.",
            false to "Sorry, Friday won't work for the report. Could I have until Tuesday?",
            false to "Sorry, I can't do Friday, could I have a few more days? I'll come back to you on option B separately.",
            false to "Friday's not possible for the report, sorry. Would early next week work?",
        )
        CandidateChecks.contradictionCheck = true
        try {
            val qsBeyond = CandidateChecks.questions(senior, moreTime, false, true)
            for ((yes, text) in beyond) {
                val r = gateway.decisions(CandidateChecks.state(two, senior, moreTime, text), qsBeyond)
                val p = (r.answers["beyond_goal"] as? Answer.Noul)?.yes
                val good = if (yes) (p ?: 0.0) >= 0.7 else (p ?: 1.0) <= 0.45
                say("   ${if (good) "ok " else "XX "} beyond_goal want=${if (yes) "yes" else "no "} p=${p?.let(::f2) ?: "-"}  \"$text\"")
            }
            val earlier = conv(them("When can you send the draft?"), me("I'll have it to you Thursday."), them("Great. Still on track?"))
            val onTrack = GoalBuilder.fromStance(stance(senior, "W02", "on_track"), relationshipId = "senior")
            val contra = listOf(
                true to "Yes, all on track, you'll have it Monday.",
                true to "On track, it'll be with you next Wednesday.",
                true to "Yep, on track for Friday.",
                false to "Yes, all on track for Thursday.",
                false to "Yep, on track, you'll have it Thursday as planned.",
                false to "Sorry, it'll be Monday now rather than Thursday, but otherwise on track.",
            )
            val qsContra = CandidateChecks.questions(senior, onTrack, false, true)
            for ((yes, text) in contra) {
                val r = gateway.decisions(CandidateChecks.state(earlier, senior, onTrack, text), qsContra)
                val p = (r.answers["contradicts_earlier"] as? Answer.Noul)?.yes
                val good = if (yes) (p ?: 0.0) >= 0.7 else (p ?: 1.0) <= 0.45
                say("   ${if (good) "ok " else "XX "} contradicts_earlier want=${if (yes) "yes" else "no "} p=${p?.let(::f2) ?: "-"}  \"$text\"")
            }
        } finally {
            CandidateChecks.contradictionCheck = false
        }
    }

    // ------------------------------------------------------------------ friction scope

    private fun friction() {
        say("\n=== Friction scope: an old row should no longer make a calm present tense")
        val sel = Selection(Scene.FRIENDS, FriendsScene.CLOSE)
        val cases = listOf(
            "old row, calm now" to conv(them("you're a terrible friend and everyone knows it"), me("that's not fair"), them("sorry, I was angry"), me("it's ok"),
                me("we're good"), them("cool"), me("so pub friday?"), them("yeah sounds good, 8?")) to 0..1,
            "old threat, calm now" to conv(them("keep this up and I'll tell everyone what you did"), me("please don't"), them("fine. forget it"), me("thanks"),
                me("how's work"), me("still busy?"), them("haha yeah mad week, you?")) to 0..1,
            "old blame, calm now" to conv(them("you always do this"), me("sorry"), them("whatever"), me("lunch tomorrow?"), me("my treat"),
                them("go on then"), me("1pm?"), them("perfect see you then")) to 0..1,
            "old complaint, neutral now" to conv(them("you bailed on me again last night and didn't even text"), me("I know, sorry"), me("won't happen again"),
                them("ok"), me("film sat?"), me("I'll book"), them("sure")) to 0..1,
            "harsh now" to conv(me("pub friday?"), them("you're a terrible friend and everyone knows it")) to 4..4,
            "blame now" to conv(me("can't make it, sorry"), them("you always do this")) to 3..3,
            "complaint now" to conv(me("hey"), them("you bailed on me again last night and didn't even text")) to 2..3,
            "calm" to conv(me("pub friday?"), them("yeah go on")) to 0..0,
        )
        var ok = 0
        for ((pair, want) in cases) {
            val (name, c) = pair
            val r = gateway.decisions(AnalysisEngine.frictionState(c, sel), AnalysisEngine.frictionQuestions(sel))
            val other = (r.answers[Shared.FRICTION_OTHER_ID] as? Answer.Score)?.score
            val good = other != null && Math.round(other).toInt() in want
            if (good) ok++
            say("   ${if (good) "ok " else "XX "} $name: other=${other?.let(::f2) ?: "-"} want $want")
        }
        say("friction scope: $ok/${cases.size}")
    }

    private fun f2(v: Double) = "%.2f".format(v)

    // ------------------------------------------------------------------ a file on its own

    private fun fileTurn() {
        say("\n=== A PDF on its own: analysed with context; replies must not guess what is in it")
        val sel = Selection(Scene.WORK, WorkScene.CLIENT)
        val pdf = ChatMessage(Sender.OTHER, "[document]", readable = false, media = "document")
        val c = conv(them("I'll send the signed contract over this afternoon"), me("Great, thanks. Can you send it when ready?"), pdf)
        val assistant = Assistant(gateway, "UK")
        val budget = RunBudget()
        val outcome = assistant.analyze(c, sel, budget)
        if (outcome !is AnalysisOutcome.Ready) { say("   NOT ANALYSED $outcome"); return }
        val a = outcome.analysis
        say("   next=${a.next} first=${a.groups.firstOrNull()?.options?.firstOrNull()?.id} notices=${a.notices}")
        val goal = GoalBuilder.fromStance(a.groups.first().options.first(), relationshipId = sel.relationship!!.id)
        val set = assistant.draftAndCheck(c, sel, goal, false, budget, situation = a.situation())
        for (v in set.verdicts) {
            val fact = v.hardChecks.firstOrNull { it.id == "unsupported_fact" }
            say("   ${v.state} total=${v.total ?: "-"} unsupported_fact=${fact?.probability?.let(::f2) ?: "-"}  \"${v.draft.text}\"")
            v.reasons.forEach { say("      $it") }
        }
    }
}
