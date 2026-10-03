package com.jev.overseas.core

import com.jev.overseas.core.engine.Assistant
import com.jev.overseas.core.engine.CandidateState
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.NextStep
import com.jev.overseas.core.engine.SkipReason
import com.jev.overseas.core.net.ChatResult
import com.jev.overseas.core.net.DecisionsResult
import com.jev.overseas.core.net.ModelException
import com.jev.overseas.core.scene.FriendsScene
import com.jev.overseas.core.scene.InputMode
import com.jev.overseas.core.scene.Scene
import com.jev.overseas.core.scene.Selection
import com.jev.overseas.core.scene.Shared
import com.jev.overseas.core.scene.WorkScene
import com.jev.overseas.core.session.AssistantSession
import com.jev.overseas.core.session.BoxState
import com.jev.overseas.core.session.ChatDiff
import com.jev.overseas.core.session.ChatReader
import com.jev.overseas.core.session.Effect
import com.jev.overseas.core.session.ErrorAction
import com.jev.overseas.core.session.ErrorKind
import com.jev.overseas.core.session.PanelState
import com.jev.overseas.core.session.Phase
import com.jev.overseas.core.session.ReadReport
import com.jev.overseas.core.session.ReadResult
import com.jev.overseas.core.session.ReplyTarget
import com.jev.overseas.core.session.Stale
import com.jev.overseas.core.testing.Answers
import com.jev.overseas.core.testing.FakeGateway
import com.jev.overseas.core.testing.chat
import com.jev.overseas.core.testing.me
import com.jev.overseas.core.testing.them
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class AssistantSessionTest {

    // ------------------------------------------------------------------ fakes

    private class FakeReader(var result: ReadResult) : ChatReader {
        /** Results to return before falling back to [result]. */
        val queue = ArrayDeque<ReadResult>()
        var reads = 0
        override fun readCurrent(): ReadResult { reads++; return queue.removeFirstOrNull() ?: result }
    }

    private class FakeTarget(var box: BoxState = BoxState.EMPTY, var fillWorks: Boolean = true) : ReplyTarget {
        val filled = ArrayList<String>()
        val copied = ArrayList<String>()
        override fun boxState() = box
        override fun fill(text: String): Boolean { if (fillWorks) filled.add(text); return fillWorks }
        override fun copy(text: String) { copied.add(text) }
    }

    /** Runs tasks only when asked, to test results that arrive late. */
    private class ManualExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    private fun ok(conversation: Conversation, chatId: String = "chat-a") = ReadResult.Ok(
        conversation, ReadReport(conversation.messages.size, conversation.latestTurn.size), chatId,
        conversation.messages.joinToString("|") { "${it.sender}:${it.text}" }.hashCode().toString(),
    )

    private val work = Selection(Scene.WORK, WorkScene.SENIOR)
    private val friends = Selection(Scene.FRIENDS, FriendsScene.REGULAR)
    private val ask = chat(me("hi"), them("can you send the deck by friday?"))
    private val askAgain = chat(me("hi"), them("can you send the deck by friday?"), them("actually thursday would be better"))

    /** Analysis hits come from [hits]; checks pass unless the reply starts with "Bad". */
    private var hits: Map<String, Double> = mapOf("W01" to 0.92)
    private var threat = false
    private var replies = Answers.replies("Can't do Friday, sorry.", "Friday won't work for me.")

    private val gateway = FakeGateway(
        onDecisions = { state, qs -> decide(state, qs) },
        onChat = { _, _ -> ChatResult(replies, FakeGateway.USAGE) },
    )

    private fun decide(state: Map<String, Any?>, qs: Map<String, Any?>): DecisionsResult {
        val text = state["candidate_reply"] as String?
        if (text == null) {
            return Answers.all(qs, noul = { hits[it] ?: 0.05 }, score = { id, _ ->
                if (id == Shared.FRICTION_OTHER_ID && threat) Answers.level(5) else Answers.level(0)
            })
        }
        return Answers.all(qs,
            noul = { id -> when {
                text.startsWith("Bad") && id == "new_commitment" -> 0.95
                text.startsWith("Check") && id == "unsupported_fact" -> 0.55
                id.startsWith("include.") || id == "acknowledges" -> 0.9
                else -> 0.05
            } },
            score = { id, _ -> if (id == "g") Answers.level(5) else Answers.level(4) })
    }

    private val reader = FakeReader(ok(ask))
    private val target = FakeTarget()
    private val states = ArrayList<PanelState>()
    private val effects = ArrayList<Effect>()
    private var hasKey = true

    private val events = ArrayList<Map<String, Any?>>()

    private fun session(
        selection: Selection? = work,
        executor: Executor = Executor { it.run() },
        fillEnabled: Boolean = true,
    ) = AssistantSession(reader, { if (hasKey) Assistant(gateway) else null }, executor, { states.add(it) }, target,
        fillEnabled, { effects.add(it) }, initialSelection = selection, journal = { events.add(it) })

    private fun AssistantSession.option(id: String) =
        (current.groups.flatMap { it.options } + current.moreOptions).first { it.id == id }

    /** Analyse, choose "cannot" and draft. */
    private fun AssistantSession.toCandidates(): AssistantSession {
        analyse()
        chooseStance(option("cannot"))
        draft()
        assertEquals(Phase.CANDIDATES, current.phase)
        return this
    }

    // ------------------------------------------------------------------ S1, S2

    @Test fun s1_noDraftAfterAFailedAnalysis() {
        gateway.onDecisions = { _, _ -> throw ModelException(ModelException.Kind.AUTH, 401, "no") }
        val s = session()
        s.analyse()
        assertEquals(ErrorKind.AUTH, s.current.error!!.kind)
        assertEquals(ErrorAction.SETTINGS, s.current.error!!.kind.action)
        assertFalse(s.current.canDraft)
        s.draft()
        assertTrue(gateway.chats.isEmpty())
    }

    @Test fun s2_chooseStanceBeforeAnyReply() {
        val s = session()
        s.analyse()
        assertEquals(Phase.ANALYSED, s.current.phase)
        assertEquals(NextStep.CHOOSE_STANCE, s.current.analysis!!.next)
        assertNull(s.current.candidates)
        assertTrue(gateway.chats.isEmpty())
        assertFalse(s.current.canDraft)
        s.chooseStance(s.option("cannot"))
        s.draft()
        assertEquals(2, s.current.candidates!!.verdicts.size)
        assertEquals(1, gateway.chats.size)
    }

    @Test fun s2_directDraftUsesTheDefaultGoal() {
        hits = mapOf("F02" to 0.9)
        reader.result = ok(chat(me("hey"), them("I got the job!!")))
        val s = session(friends)
        s.analyse()
        assertEquals(NextStep.DIRECT_DRAFT, s.current.analysis!!.next)
        assertEquals("congratulate", s.current.stance!!.id)
        assertNotNull(s.current.goal)
        assertEquals(Phase.CANDIDATES, s.current.phase)
        // The goal stays editable.
        s.editSummary("Congratulate them and ask when they start")
        assertTrue(s.current.ownGoal)
        assertEquals(Stale.GOAL, s.current.stale)
    }

    @Test fun phasesInOrder() {
        session().toCandidates()
        val phases = states.map { it.phase }.distinct()
        assertEquals(listOf(Phase.READING, Phase.ANALYSING, Phase.ANALYSED, Phase.DRAFTING, Phase.CHECKING, Phase.CANDIDATES), phases)
        val checking = states.first { it.phase == Phase.CHECKING }
        assertEquals(2, checking.drafts.size)
        assertNull(checking.candidates)
    }

    // ------------------------------------------------------------------ S3, S4, S5

    @Test fun s3_threatOffersNothingButOwnGoal() {
        threat = true
        val s = session()
        s.analyse()
        assertEquals(NextStep.SAFETY_HOLD, s.current.analysis!!.next)
        assertTrue(s.current.groups.isEmpty())
        assertEquals(listOf("no_reply"), s.current.moreOptions.map { it.id })
        s.chooseStance(Shared.BRIEF_ACK)
        assertNull(s.current.goal)
        s.draft()
        assertTrue(gateway.chats.isEmpty())
        s.editSummary("Say I won't discuss this further")
        assertTrue(s.current.canDraft)
        s.draft()
        assertEquals(1, gateway.chats.size)
    }

    @Test fun s3_boundaryChecksForPushingPast() {
        hits = mapOf("K09" to 0.95, "K05" to 0.9)
        reader.result = ok(chat(me("did I do something wrong?"), them("please stop texting me")))
        val s = session(friends)
        s.analyse()
        assertEquals(NextStep.BOUNDARY, s.current.analysis!!.next)
        assertEquals(setOf("brief_ack", "no_reply"), s.current.moreOptions.map { it.id }.toSet())
        assertEquals(listOf("K09"), s.current.groups.map { it.behaviorId })
        s.chooseStance(s.option("brief_ack"))
        s.draft()
        assertTrue(gateway.decisions.filter { it.isCheck }.all { "crosses_boundary" in it.questions!! })
    }

    @Test fun s4_noReplyCallsNoModel() {
        val s = session()
        s.analyse()
        s.chooseStance(s.option("no_reply"))
        assertTrue(s.current.nothingToSend)
        assertFalse(s.current.canDraft)
        s.draft()
        assertTrue(gateway.chats.isEmpty())
    }

    @Test fun s5_requiredDetailsBeforeDrafting() {
        val s = session()
        s.analyse()
        val conditional = s.option("agree_condition")
        assertEquals(InputMode.REQUIRED, conditional.input)
        s.chooseStance(conditional)
        assertFalse(s.current.canDraft)
        s.draft()
        assertTrue(gateway.chats.isEmpty())
        s.setDetails("Thursday works, Wednesday doesn't")
        assertTrue(s.current.canDraft)
        assertEquals(listOf("The user says: Thursday works, Wednesday doesn't"), s.current.goal!!.userFacts)
    }

    // ------------------------------------------------------------------ S6, S7, S8

    @Test fun s6_selectionChangeNeedsANewAnalysis() {
        val s = session().toCandidates()
        s.selectRelationship(WorkScene.PEER)
        assertNull(s.current.analysis)
        assertNull(s.current.candidates)
        assertTrue(s.current.choosingScene)
        assertEquals(WorkScene.PEER, s.current.selection!!.relationship)
        s.toggleConflict()
        assertTrue(s.current.selection!!.conflict)
        s.selectScene(Scene.ROMANCE)
        assertNull(s.current.selection!!.relationship)
    }

    @Test fun s6_goalChangeMarksRepliesStale() {
        val s = session().toCandidates()
        val v1 = s.current.goal!!.version
        s.chooseStance(s.option("more_time"))
        assertEquals(Stale.GOAL, s.current.stale)
        assertTrue(s.current.goal!!.version > v1)
        assertFalse(s.current.usable(0))
        s.copy(0)
        assertTrue(target.copied.isEmpty())
        s.draft()
        assertEquals(Stale.NONE, s.current.stale)
        assertTrue(s.current.usable(0))
        s.toggleSwitch(com.jev.overseas.core.engine.GoalSwitch.NO_APOLOGY)
        assertEquals(Stale.GOAL, s.current.stale)
    }

    @Test fun s7_lateRepliesForAnOldGoalAreDropped() {
        val ex = ManualExecutor()
        val s = session(executor = ex)
        s.analyse(); ex.runAll()
        s.chooseStance(s.option("cannot"))
        s.draft(); ex.tasks.removeFirst().run() // the chat check; drafting is now queued
        assertEquals(Phase.DRAFTING, s.current.phase)
        s.chooseStance(s.option("more_time"))
        ex.runAll()
        assertNull(s.current.candidates)
        assertEquals("more_time", s.current.stance!!.id)
        assertEquals(Phase.ANALYSED, s.current.phase)
    }

    @Test fun s8_anotherBatchAndBudget() {
        val s = session().toCandidates()
        s.anotherBatch()
        val prompt = com.jev.overseas.core.json.MiniJson.parseObject(gateway.chats.last().user!!)
        assertEquals(listOf("Can't do Friday, sorry.", "Friday won't work for me."), prompt["do_not_repeat_these_earlier_drafts"])
        repeat(3) { s.anotherBatch() } // 2 (analysis) + 5 rounds x 3 = 17 requests
        assertEquals(17, s.current.budgetUsed)
        s.anotherBatch()
        assertEquals(ErrorKind.BUDGET, s.current.error!!.kind)
        assertEquals(ErrorAction.START_AGAIN, s.current.error!!.kind.action)
        assertEquals(17, gateway.calls.size)
        s.analyse() // "Start again": a fresh budget
        assertEquals(2, s.current.budgetUsed)
    }

    // ------------------------------------------------------------------ S9

    @Test fun s9_onlyUsableRepliesAndOnlyTheirText() {
        replies = Answers.replies("Bad: I'll send it Monday.", "Check this one.")
        val s = session().toCandidates()
        val states = s.current.candidates!!.verdicts.map { it.state }
        // A revision was attempted; both rounds produce the same pair here.
        assertTrue(CandidateState.NEEDS_REWRITE in states)
        val blocked = states.indexOf(CandidateState.NEEDS_REWRITE)
        val awaiting = states.indexOf(CandidateState.AWAITING_CONFIRMATION)
        s.copy(blocked)
        assertTrue(target.copied.isEmpty())
        s.copy(awaiting)
        assertTrue(target.copied.isEmpty())
        s.confirmChecked(awaiting)
        s.copy(awaiting)
        assertEquals(listOf("Check this one."), target.copied)
        assertEquals(listOf(Effect.Collapse), effects)
    }

    // ------------------------------------------------------------------ S10, S11, S12

    @Test fun s10_ownGoalAfterTheUserWroteLast() {
        reader.result = ok(chat(them("hi"), me("are we still on for friday?")))
        val s = session()
        s.analyse()
        assertEquals(SkipReason.USER_WROTE_LAST, s.current.skip!!.reason)
        assertTrue(gateway.calls.isEmpty())
        assertFalse(s.current.canDraft)
        s.editSummary("Check they got my message about Friday")
        s.draft()
        assertEquals(Phase.CANDIDATES, s.current.phase)
        assertFalse(gateway.decisions.any { "acknowledges" in it.questions!! })
    }

    @Test fun s10_notForOtherSkips() {
        reader.result = ok(chat(me("hi"), them("你明天有空吗")))
        val s = session()
        s.analyse()
        assertEquals(SkipReason.NOT_ENGLISH, s.current.skip!!.reason)
        s.editSummary("anything")
        assertNull(s.current.goal)
    }

    @Test fun s11_closeForgetsTheChat() {
        val ex = ManualExecutor()
        val s = session(executor = ex)
        s.analyse(); ex.runAll()
        s.chooseStance(s.option("cannot"))
        s.draft(); ex.tasks.removeFirst().run()
        s.close()
        ex.runAll() // a late result after close
        val st = s.current
        assertEquals(PanelState(selection = work), st)
        assertFalse(st.toString().contains("deck"))
    }

    @Test fun s12_noKeyNoRead() {
        hasKey = false
        val s = session()
        s.open(autoAnalyse = true)
        assertEquals(ErrorKind.NO_KEY, s.current.error!!.kind)
        s.analyse()
        assertEquals(0, reader.reads)
        assertTrue(gateway.calls.isEmpty())
    }

    // ------------------------------------------------------------------ S13

    @Test fun s13_changeNoticedWhenRepliesArrive() {
        reader.queue.addAll(listOf(ok(ask), ok(ask))) // analysis read, check before drafting
        reader.result = ok(askAgain) // the read right after the replies arrive
        val s = session()
        s.analyse(); s.chooseStance(s.option("cannot")); s.draft()
        assertEquals("1 new message from them", s.current.chatChange!!.message)
        assertEquals(1, s.current.chatChange!!.newFromThem)
        assertEquals(Stale.CHAT, s.current.stale)
        assertFalse(s.current.usable(0))
        assertEquals(2, s.current.candidates!!.verdicts.size) // still readable
    }

    @Test fun s13_unreadableAtArrivalIsSkipped() {
        reader.queue.addAll(listOf(ok(ask), ok(ask)))
        reader.result = ReadResult.Unstable
        val s = session()
        s.analyse(); s.chooseStance(s.option("cannot")); s.draft()
        assertNull(s.current.chatChange)
        assertTrue(s.current.usable(0))
    }

    @Test fun s13_checkBeforeCopyThenUseAnyway() { // S13a
        val s = session().toCandidates()
        reader.result = ok(askAgain)
        s.copy(0)
        assertTrue(target.copied.isEmpty())
        assertEquals("1 new message from them", s.current.chatChange!!.message)
        s.useAnyway()
        assertEquals(listOf("Can't do Friday, sorry."), target.copied) // the copy waited and then ran
        assertNull(s.current.chatChange)
        assertEquals(Stale.NONE, s.current.stale)
        // A further change shows the banner again.
        reader.result = ok(chat(me("hi"), them("can you send the deck by friday?"), them("actually thursday would be better"), them("??")))
        s.copy(1)
        assertEquals(1, target.copied.size)
        assertNotNull(s.current.chatChange)
    }

    @Test fun s13_generalWordingWhenNotCountable() {
        val s = session().toCandidates()
        reader.result = ok(chat(me("hi"), them("can you send the deck by friday?"), me("on it")))
        s.copy(0)
        assertEquals(ChatDiff.GENERAL, s.current.chatChange!!.message)
        assertNull(s.current.chatChange!!.newFromThem)
    }

    @Test fun s13_checkBeforeDraftAndAnotherBatch() {
        val s = session()
        s.analyse()
        s.chooseStance(s.option("cannot"))
        reader.result = ok(askAgain)
        s.draft()
        assertTrue(gateway.chats.isEmpty())
        assertNotNull(s.current.chatChange)
        s.useAnyway()
        assertEquals(1, gateway.chats.size)
        assertEquals(Phase.CANDIDATES, s.current.phase)
    }

    @Test fun s13b_readAgainKeepsTheStanceWhenStillOffered() {
        val s = session()
        s.analyse()
        s.chooseStance(s.option("more_time"))
        s.setDetails("next Tuesday")
        s.draft()
        reader.result = ok(askAgain)
        s.copy(0)
        assertNotNull(s.current.chatChange)
        s.readAgain()
        assertEquals(Phase.CANDIDATES, s.current.phase)
        assertEquals("more_time", s.current.stance!!.id)
        assertEquals("next Tuesday", s.current.details)
        assertNull(s.current.chatChange)
        val lastDraft = com.jev.overseas.core.json.MiniJson.parseObject(gateway.chats.last().user!!)
        assertEquals(2, (lastDraft["latest_messages_to_reply_to"] as List<*>).size)
        assertEquals(5, s.current.budgetUsed) // new budget: analysis (2) + one round (3)
    }

    @Test fun s13b_readAgainReturnsToTheChoiceWhenTheQuestionChanged() {
        val s = session().toCandidates()
        reader.result = ok(chat(me("hi"), them("can you send the deck by friday?"), me("sure"), them("great. free for a call at 3?")))
        hits = mapOf("W07" to 0.9)
        s.copy(0)
        val chats = gateway.chats.size
        s.readAgain()
        assertEquals(Phase.ANALYSED, s.current.phase)
        assertEquals(AssistantSession.CHANGED_QUESTION, s.current.notice)
        assertNull(s.current.candidates)
        assertEquals(chats, gateway.chats.size)
    }

    @Test fun s13b_ownGoalIsAlwaysKept() {
        val s = session()
        s.analyse()
        s.editSummary("Say I'll check with Sam first")
        s.draft()
        hits = mapOf("W07" to 0.9)
        reader.result = ok(askAgain)
        s.readAgain()
        assertTrue(s.current.ownGoal)
        assertEquals("Say I'll check with Sam first", s.current.goal!!.summary)
        assertEquals(Phase.CANDIDATES, s.current.phase)
    }

    // ------------------------------------------------------------------ S14

    @Test fun s14_fillWritesTheTextAndCollapses() {
        val s = session().toCandidates()
        s.fill(0)
        assertEquals(listOf("Can't do Friday, sorry."), target.filled)
        assertEquals(listOf(Effect.Collapse), effects)
    }

    @Test fun s14_refusesAnotherChat() {
        val s = session().toCandidates()
        reader.result = ok(ask, chatId = "chat-b")
        s.fill(0)
        assertTrue(target.filled.isEmpty())
        assertEquals(AssistantSession.NOT_SAME_CHAT, s.current.notice)
        assertTrue(s.current.noticeIsWarning)
        reader.result = ReadResult.NotConversation
        s.fill(0)
        assertTrue(target.filled.isEmpty())
        assertTrue(target.copied.isEmpty())
    }

    @Test fun s14_asksBeforeReplacingTheUsersText() {
        val s = session().toCandidates()
        target.box = BoxState.HAS_TEXT
        s.fill(1)
        assertTrue(target.filled.isEmpty())
        assertEquals(1, s.current.replacePending)
        s.cancelReplace()
        assertNull(s.current.replacePending)
        s.fill(1)
        s.confirmReplace()
        assertEquals(listOf("Friday won't work for me."), target.filled)
    }

    @Test fun s14_failedFillFallsBackToCopy() {
        val s = session().toCandidates()
        target.fillWorks = false
        s.fill(0)
        assertEquals(listOf("Can't do Friday, sorry."), target.copied)
        assertEquals(AssistantSession.COPIED_INSTEAD, s.current.notice)
        // The panel stays open so "Copied instead" can be read.
        assertTrue(effects.isEmpty())
        target.box = BoxState.MISSING
        s.fill(1)
        assertEquals(2, target.copied.size)
    }

    @Test fun s14_withoutFillPermissionFillCopies() {
        val s = session(fillEnabled = false).toCandidates()
        s.fill(0)
        assertTrue(target.filled.isEmpty())
        assertEquals(listOf("Can't do Friday, sorry."), target.copied)
    }

    // ------------------------------------------------------------------ S15 and lifecycle

    @Test fun s15_bubbleTapAnalysesWithARememberedScene() {
        val s = session()
        s.open(autoAnalyse = true)
        assertEquals(1, reader.reads)
        assertEquals(Phase.ANALYSED, s.current.phase)
    }

    @Test fun s15_firstUseShowsTheScenePicker() {
        val s = session(selection = null)
        s.open(autoAnalyse = true)
        assertTrue(s.current.choosingScene)
        assertEquals(0, reader.reads)
        val off = session()
        off.open(autoAnalyse = false)
        assertTrue(off.current.choosingScene)
        assertEquals(0, reader.reads)
    }

    @Test fun fineTuneAppliesToItsOwnRepliesOnly() {
        val s = session().toCandidates()
        s.toggleSwitch(com.jev.overseas.core.engine.GoalSwitch.NO_APOLOGY)
        s.draft()
        assertTrue("apologises" in s.current.goal!!.mustAvoid)
        s.anotherBatch() // the same request again keeps it
        assertTrue("apologises" in s.current.goal!!.mustAvoid)
        s.chooseStance(s.option("more_time")) // a new goal does not
        assertTrue(s.current.switches.isEmpty())
        assertFalse("apologises" in s.current.goal!!.mustAvoid)
        s.toggleSwitch(com.jev.overseas.core.engine.GoalSwitch.NO_APOLOGY)
        s.editSummary("Say Monday, with a short sorry")
        assertTrue(s.current.switches.isEmpty())
        s.toggleSwitch(com.jev.overseas.core.engine.GoalSwitch.NO_REASONS)
        reader.result = ok(askAgain)
        s.readAgain()
        assertTrue(s.current.switches.isEmpty())
    }

    @Test fun diagnosticsCarryNoChatOrReplyText() {
        val s = session()
        s.analyse()
        s.chooseStance(s.option("more_time"))
        s.setDetails("next Tuesday, I'm swamped")
        s.draft()
        s.fill(0)
        reader.result = ok(askAgain)
        s.copy(1)
        s.useAnyway()
        s.toggleSwitch(com.jev.overseas.core.engine.GoalSwitch.NO_APOLOGY)
        s.draft()
        s.close()
        val types = events.map { it["type"] }.toSet()
        assertEquals(setOf("round", "read", "analysis", "goal", "candidates", "action", "chat_change"), types)
        assertTrue(events.all { it["round"] == events.first()["round"] })
        val dump = com.jev.overseas.core.json.MiniJson.encode(events)
        for (secret in listOf("deck", "friday", "thursday", "Can't do Friday", "Friday won't work", "next Tuesday", "swamped", "chat-a")) {
            assertFalse("diagnostics contain '$secret'", dump.contains(secret, ignoreCase = true))
        }
        // The text the user may choose to attach is available separately.
        assertNull(s.roundText()) // cleared by close
    }

    @Test fun roundTextIsGoalAndRepliesOnly() {
        val s = session().toCandidates()
        val text = s.roundText()!!
        assertEquals(setOf("round", "goal", "replies"), text.keys)
        assertEquals(listOf("Can't do Friday, sorry.", "Friday won't work for me."), text["replies"])
    }

    private class MapMemory : com.jev.overseas.core.session.SelectionMemory {
        val map = HashMap<String, Selection>()
        override fun get(chatId: String) = map[chatId]
        override fun put(chatId: String, selection: Selection) { map[chatId] = selection }
    }

    @Test fun aNewChatAsksFirstAKnownChatIsAnalysedAtOnce() {
        val memory = MapMemory()
        val s = AssistantSession(reader, { Assistant(gateway) }, Executor { it.run() }, { states.add(it) }, target,
            initialSelection = work, memory = memory)
        s.open(autoAnalyse = true)
        assertTrue(s.current.choosingScene)
        assertTrue(s.current.newChat)
        assertNull(s.current.selection)
        assertTrue(gateway.calls.isEmpty())
        s.selectScene(Scene.WORK)
        s.selectRelationship(WorkScene.SENIOR)
        s.analyse()
        assertEquals(work, memory.map["chat-a"])
        s.close()
        s.open(autoAnalyse = true)
        assertEquals(Phase.ANALYSED, s.current.phase) // known chat: straight to analysis
        assertEquals(work, s.current.selection)
        // Moving to a chat that was never set up asks again.
        reader.result = ok(chat(me("hey"), them("dinner friday?")), chatId = "chat-b")
        s.open(autoAnalyse = true)
        assertTrue(s.current.choosingScene)
        assertNull(s.current.analysis)
        // A chat set up with another scene keeps its own.
        memory.map["chat-c"] = friends
        reader.result = ok(chat(me("hey"), them("dinner friday?")), chatId = "chat-c")
        s.close()
        s.open(autoAnalyse = true)
        assertEquals(friends, s.current.selection)
    }

    @Test fun reopeningTheSameUnchangedChatKeepsTheRound() {
        val s = session().toCandidates()
        val calls = gateway.calls.size
        s.open(autoAnalyse = true)
        assertEquals(Phase.CANDIDATES, s.current.phase)
        assertEquals(2, s.current.candidates!!.verdicts.size)
        assertEquals(calls, gateway.calls.size)
    }

    @Test fun reopeningAfterTheChatMovedOnStartsFresh() {
        val s = session().toCandidates()
        val decisions = gateway.decisions.size
        reader.result = ok(chat(me("hi"), them("can you send the deck by friday?"), me("Sorry, Friday won't work.")))
        s.open(autoAnalyse = true)
        assertNull(s.current.candidates)
        assertEquals(SkipReason.USER_WROTE_LAST, s.current.skip!!.reason)
        assertTrue(s.current.skip!!.message.contains("follow-up"))
        assertEquals(decisions, gateway.decisions.size) // skipped: nothing sent to a model
    }

    @Test fun reopeningInAnotherChatStartsFresh() {
        val s = session().toCandidates()
        reader.result = ok(ask, chatId = "chat-b")
        s.open(autoAnalyse = true)
        assertNull(s.current.candidates)
        assertEquals(Phase.ANALYSED, s.current.phase)
    }

    @Test fun reopeningWhenTheScreenCannotBeReadKeepsTheRound() {
        val s = session().toCandidates()
        reader.result = ReadResult.NotConversation
        s.open(autoAnalyse = true)
        assertEquals(Phase.CANDIDATES, s.current.phase)
        assertEquals(AssistantSession.NOT_IN_A_CHAT, s.current.notice)
        assertTrue(s.current.noticeIsWarning)
    }

    @Test fun readFailuresSayWhatToDo() {
        reader.result = ReadResult.NotConversation
        val s = session()
        s.analyse()
        assertEquals(ErrorKind.NOT_CONVERSATION, s.current.error!!.kind)
        assertEquals("Open a one-to-one chat", s.current.error!!.message)
        assertTrue(gateway.calls.isEmpty())
        reader.result = ReadResult.Unstable
        s.retry()
        assertEquals(ErrorKind.UNSTABLE, s.current.error!!.kind)
        reader.result = ok(ask)
        s.retry()
        assertEquals(Phase.ANALYSED, s.current.phase)
    }

    @Test fun networkErrorRetriesTheDraft() {
        val s = session()
        s.analyse()
        s.chooseStance(s.option("cannot"))
        gateway.onChat = { _, _ -> throw ModelException(ModelException.Kind.NETWORK, null, "Network error: UnknownHostException") }
        s.draft()
        assertEquals(ErrorKind.NETWORK, s.current.error!!.kind)
        assertEquals(ErrorAction.RETRY, s.current.error!!.kind.action)
        gateway.onChat = { _, _ -> ChatResult(replies, null) }
        s.retry()
        assertEquals(Phase.CANDIDATES, s.current.phase)
        assertNull(s.current.error)
    }

    @Test fun cancelDropsTheRun() {
        val ex = ManualExecutor()
        val s = session(executor = ex)
        s.analyse()
        s.cancel()
        ex.runAll()
        assertEquals(Phase.IDLE, s.current.phase)
        assertNull(s.current.analysis)
        assertTrue(gateway.calls.isEmpty())
    }

    @Test fun timingIsRecorded() {
        val s = session().toCandidates()
        val t = s.current.timing
        assertNotNull(t.read); assertNotNull(t.analyse); assertNotNull(t.draft); assertNotNull(t.check)
    }

    // ------------------------------------------------------------------ chats, stances and errors

    private fun memorySession(memory: MapMemory) = AssistantSession(reader, { Assistant(gateway) }, Executor { it.run() },
        { states.add(it) }, target, memory = memory, journal = { events.add(it) })

    /** A scene picked while chat A was open is never used for, or remembered against, chat B. */
    @Test fun aSelectionMadeForOneChatIsNotUsedInAnother() {
        val memory = MapMemory()
        val s = memorySession(memory)
        s.open(autoAnalyse = true)
        assertTrue(s.current.newChat)
        s.selectScene(Scene.WORK)
        s.selectRelationship(WorkScene.SENIOR)
        // The user moves to another chat before tapping "Read and analyse".
        reader.result = ok(chat(me("hey"), them("dinner friday?")), chatId = "chat-b")
        s.analyse()
        assertNull(s.current.analysis)
        assertTrue(s.current.choosingScene)
        assertTrue(s.current.newChat)
        assertTrue(memory.map.isEmpty())
        assertTrue(gateway.decisions.isEmpty())
    }

    @Test fun analyseThisChatInsteadUsesThatChatsOwnScene() {
        val memory = MapMemory()
        memory.map["chat-a"] = work
        val s = memorySession(memory)
        s.open(autoAnalyse = true)
        s.chooseStance(s.option("cannot"))
        s.draft()
        assertEquals(Phase.CANDIDATES, s.current.phase)
        reader.result = ok(chat(me("hey"), them("dinner friday?")), chatId = "chat-b")
        s.fill(0)
        assertEquals(AssistantSession.NOT_SAME_CHAT, s.current.notice)
        s.reopen()
        assertTrue(s.current.choosingScene)
        assertTrue(s.current.newChat)
        assertNull(memory.map["chat-b"])
        assertEquals(work, memory.map["chat-a"])
    }

    @Test fun anotherChatOnScreenBlocksCopyWithoutUseAnyway() {
        val s = session().toCandidates()
        reader.result = ok(ask, chatId = "chat-b")
        s.copy(0)
        assertTrue(s.current.chatChange!!.otherChat)
        assertTrue(target.copied.isEmpty())
        s.useAnyway()
        assertTrue(target.copied.isEmpty())
        assertNotNull(s.current.chatChange)
        assertFalse(s.current.usable(0))
    }

    /** R06 and R07 both offer a "dir_closer" option; Read again must bring back the one the user chose. */
    @Test fun readAgainBringsBackTheSameStanceNotOneWithTheSameId() {
        hits = mapOf("R06" to 0.9, "R07" to 0.9)
        val romance = Selection(Scene.ROMANCE, com.jev.overseas.core.scene.RomanceScene.TALKING)
        reader.result = ok(chat(me("hey"), them("miss you, free thursday?")))
        val s = session(selection = romance)
        s.analyse()
        val meet = s.current.groups.first { it.behaviorId == "R07" }.options.first { it.id == "dir_closer" }
        val miss = s.current.groups.first { it.behaviorId == "R06" }.options.first { it.id == "dir_closer" }
        assertFalse(meet == miss)
        s.chooseStance(meet)
        reader.result = ok(chat(me("hey"), them("miss you, free thursday?"), them("or friday")))
        s.readAgain()
        assertEquals(meet, s.current.stance)
        assertEquals("Say yes and fix a time", s.current.goal!!.summary)
    }

    @Test fun anUnexpectedExceptionBecomesAnErrorCard() {
        gateway.onDecisions = { _, _ -> throw IllegalStateException("boom") }
        val s = session()
        s.analyse()
        assertEquals(ErrorKind.FAILED, s.current.error!!.kind)
        assertTrue(events.any { it["type"] == "crash" && it["exception"] == "IllegalStateException" })
    }

    // ------------------------------------------------------------------ state fixes

    @Test fun anotherBatchThatFailsKeepsTheLastReplies() {
        val s = session().toCandidates()
        val before = s.current.candidates!!.verdicts.map { it.draft.text }
        gateway.onChat = { _, _ -> throw ModelException(ModelException.Kind.NETWORK, null, "Network error") }
        s.anotherBatch()
        assertEquals(before, s.current.candidates!!.verdicts.map { it.draft.text })
        assertEquals(Phase.CANDIDATES, s.current.phase)
        assertEquals(ErrorKind.NETWORK, s.current.error!!.kind)
    }

    @Test fun cancellingADraftKeepsTheRun() {
        val ex = ManualExecutor()
        val s = session(executor = ex)
        s.analyse(); ex.runAll()
        s.chooseStance(s.option("cannot"))
        s.draft(); ex.tasks.removeFirst().run() // only the chat check before drafting; the draft stays queued
        assertEquals(Phase.DRAFTING, s.current.phase)
        s.cancel()
        ex.runAll()
        assertEquals(Phase.ANALYSED, s.current.phase)
        assertNull(s.current.candidates)
        // The budget is still there: drafting again works.
        s.draft(); ex.runAll()
        assertEquals(Phase.CANDIDATES, s.current.phase)
    }

    @Test fun backFromTheScenePickerKeepsTheRound() {
        val s = session().toCandidates()
        val calls = gateway.calls.size
        s.showScenePicker()
        assertTrue(s.current.choosingScene)
        s.closeScenePicker()
        assertFalse(s.current.choosingScene)
        assertEquals(Phase.CANDIDATES, s.current.phase)
        assertEquals(calls, gateway.calls.size)
    }

    @Test fun retryAfterAFailedReadAgainKeepsTheGoal() {
        val s = session()
        s.analyse()
        s.chooseStance(s.option("cannot"))
        reader.result = ReadResult.Unstable
        s.readAgain()
        assertEquals(ErrorKind.UNSTABLE, s.current.error!!.kind)
        reader.result = ok(askAgain)
        s.retry()
        assertEquals("cannot", s.current.stance?.id)
        assertEquals(Phase.CANDIDATES, s.current.phase)
    }

    /** Retry after a check that did not come back checks the same reply again; nothing is redrafted. */
    @Test fun retryRechecksTheSameReply() {
        var failChecks = true
        gateway.onDecisions = { state, qs ->
            if (state["candidate_reply"] != null && failChecks) throw ModelException(ModelException.Kind.SERVER, 500, "x")
            decide(state, qs)
        }
        val s = session().toCandidates()
        assertTrue(s.current.candidates!!.verdicts.all { it.state == CandidateState.FAILED })
        val chats = gateway.chats.size
        failChecks = false
        s.recheck()
        assertEquals(chats, gateway.chats.size)
        assertTrue(s.current.candidates!!.verdicts.all { it.state == CandidateState.ELIGIBLE })
        assertEquals(listOf("Can't do Friday, sorry.", "Friday won't work for me."), s.current.candidates!!.verdicts.map { it.draft.text })
    }
}
