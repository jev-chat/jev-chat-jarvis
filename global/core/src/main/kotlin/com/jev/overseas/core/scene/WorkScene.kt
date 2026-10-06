package com.jev.overseas.core.scene

import com.jev.overseas.core.scene.Shared.behavior
import com.jev.overseas.core.scene.Shared.question

/**
 * Work: managers, colleagues, reports, clients.
 *
 * Whenever the other person asks for something, the reply involves a commitment,
 * so the user always chooses a stance first. The baseline changes how the panel
 * describes the message, which option comes first, and how formal the reply is.
 */
object WorkScene : SceneSpec {

    override val scene = Scene.WORK

    val SENIOR = RelationshipType("senior", Scene.WORK, "Manager or senior",
        "This is a work chat. The other person is the user's manager or someone the user answers to. " +
            "Assigning tasks, chasing progress and pointing out problems are part of their role. Replies to them are concise and polite.")
    val PEER = RelationshipType("peer", Scene.WORK, "Colleague",
        "This is a work chat between colleagues at the same level. Asking for information, collaborating and arranging times are ordinary; " +
            "handing the user extra work is not. Replies can be fairly casual.")
    val REPORT = RelationshipType("report", Scene.WORK, "Someone who reports to me",
        "This is a work chat. The other person reports to the user. Asking the user for decisions and information and reporting progress are ordinary; " +
            "assigning the user work is not. Replies are clear and give direction without talking down.")
    val CLIENT = RelationshipType("client_or_external", Scene.WORK, "Client or external",
        "This is a work chat. The other person is a client or someone outside the user's organisation. Requests, chasing and feedback are ordinary; " +
            "requests beyond the agreed scope are not. Replies are complete and careful, and commitments are conservative.")

    override val relationships = listOf(SENIOR, PEER, REPORT, CLIENT)

    const val UNSPECIFIED_NOTE = "This is a work chat. The other person's role is not known."
    const val CONFLICT_NOTE = " The two are currently in a dispute, or the user is being held to account for something."

    // ------------------------------------------------------------------ behaviours

    val W01 = behavior("W01", "Asks for work that's yours",
        "ask the user to send, finish, or do a piece of work that is already the user's task",
        "They ask for a deliverable or an action that belongs to the user's existing work, such as \"Can you send the report by Friday?\", \"Pls get the deck over to me today\" or \"I need it by end of day\".",
        "They ask for nothing, or only ask about progress (\"How's the report going?\"), or ask the user to take on something new as a favour.")
    val W02 = behavior("W02", "Asks about progress",
        "ask how far along something is, or when it will be ready",
        "A question about progress or timing, such as \"Any update on the invoice?\" or \"Where are we with the Q3 numbers?\".",
        "No question about progress. A demand for the work itself (\"Send me the Q3 numbers.\", \"I need it today.\") is not a progress question.")
    val W03 = behavior("W03", "Wants your decision",
        "ask the user to approve, choose, confirm, or agree to something",
        "Such as \"Are you ok with option B?\" or \"Can you sign off on this?\".",
        "They only inform the user of a decision already made (\"FYI we went with option B.\"), or ask for work rather than a decision.")
    val W04 = behavior("W04", "Asks you to take on something extra",
        "ask the user to take on a task that is not already the user's, or to do them a favour",
        "They hand over or offer a new piece of work, such as \"Could you cover the client call tomorrow?\", \"Any chance you can pick this up? I'm swamped\" or \"Could you pull together the cost analysis?\".",
        "They only chase or remind the user about work the chat shows is already the user's (\"Reminder: your slides are due Thursday.\", \"I need the report I asked you for.\"), or ask for nothing.")
    val W05 = behavior("W05", "Asks for information",
        "ask a factual question that the user is expected to answer",
        "Such as \"What's the login for the staging site?\" or \"Who's the contact at Acme?\".",
        "A request to do something (\"Can you take this on?\"), a question about progress, or no question at all.")
    val W06 = behavior("W06", "Points out a problem",
        "point out a mistake, a delay, or a problem in the user's work or conduct",
        "They say something of the user's is wrong, late, or not good enough, such as \"The figures in section 2 don't add up.\" or \"This was due yesterday.\".",
        "A neutral request to check something (\"Can you double-check section 2 when you get a sec?\"), a deadline for future work, or a warning about what might happen later.")
    val W07 = behavior("W07", "Proposes a time",
        "propose or ask for a time for a meeting or a call",
        "Such as \"Free for a quick call at 3?\" or \"Can we do Tuesday instead?\".",
        "No meeting or call time is proposed (\"Let's catch up at some point.\"). A deadline for a deliverable is not a meeting time.")
    val W08 = question("W08", "Just an update",
        "Do latest_messages only give information or an update, without asking the user for anything?",
        "Such as \"FYI the client moved the launch to May.\" or \"Heads up, I'm OOO Friday.\".",
        "They ask for something, point out a problem in the user's work, or are only an acknowledgement such as \"OK\" or \"Noted.\".",
        Role.PASSIVE)
    val W09 = question("W09", "Thanks or acknowledgement",
        "Are latest_messages only thanks, an acknowledgement, a greeting, or small talk?",
        "Such as \"Thanks, appreciate it!\", \"OK\", \"k\", \"Noted.\", \"Got it\" or \"Morning!\".",
        "They also ask or say something of substance (\"Thanks. Also, where's the invoice?\"), or the greeting asks the user something (\"Morning! Good weekend?\").",
        Role.PASSIVE)

    /** "Morning! Good weekend?" asks something, so it gets a short polite reply rather than "no reply needed". */
    val W15 = behavior("W15", "Small talk", "ask how the user is or how their day or weekend went, or share a bit of everyday news",
        "Such as \"Morning! Good weekend?\", \"How was the holiday?\" or \"Survived Monday?\".",
        "A work question or request, or only a greeting or thanks with no question (\"Morning!\", \"Thanks!\").")
    val W10 = behavior("W10", "States a deadline",
        "state a time by which something is expected of the user",
        "Such as \"by Friday\", \"EOD today\", \"before the 3pm call\" or \"It needs to be ready by Thursday\".",
        "No time is given, or only a vague one (\"soon\", \"when you can\").", Role.CUE)
    val W11 = behavior("W11", "Says it's urgent",
        "say in words that the matter is urgent",
        "Urgency wording such as \"asap\", \"urgent\", \"need this now\", \"right away\" or \"top priority\".",
        "No urgency wording. A deadline alone (\"by Friday\", \"by end of day\") is not urgency wording.", Role.CUE)
    val W12 = behavior("W12", "Mentions escalating",
        "mention involving a manager, HR, the client, or another senior person because of a problem with the user",
        "Such as \"I'll need to flag this to David.\" or \"I'm going to have to raise this with HR.\".",
        "No one is being brought in over a problem. Copying someone for visibility, or mentioning a client or manager for another reason (\"the client presentation is tomorrow\"), does not count.", Role.CUE)
    val W13 = behavior("W13", "Chasing again",
        "repeat a request that they already made in earlier_messages",
        "earlier_messages already contain the same request from them, and latest_messages ask again, such as \"Any update?\" or \"Following up on the below\".",
        "The request is made for the first time.", Role.CUE)
    val W14 = behavior("W14", "Says who asked for it",
        "say who assigned or requested this work",
        "Such as \"David asked if you could take this.\", \"Client needs this by Thursday.\" or \"This came down from the leadership team.\".",
        "They do not say who wants it (\"Could you pick this up for me?\").", Role.CUE)

    val IMPATIENCE = question("tone.impatience", "Possible impatience",
        "Do latest_messages use wording that, in workplace chat, often signals impatience while staying formally polite?",
        "Such as \"Per my last email\", \"As I said before\", \"As previously discussed\", \"Just following up again\", \"Any update??\", or a repeated \"Friendly reminder\".",
        "Ordinary wording, including plain acknowledgements (\"Noted.\", \"OK\", \"k\", \"Will do\", \"Thanks\") and a first, polite follow-up (\"Just checking in on this\").",
        Role.CUE)

    private val ALL = listOf(W01, W02, W03, W04, W05, W06, W07, W08, W09, W10, W11, W12, W13, W14, W15, Shared.HISTORY_NEEDED)

    override fun behaviors(selection: Selection) = ALL
    override fun toneQuestions(selection: Selection) = listOf(IMPATIENCE)

    override val priority = listOf("W06", "W01", "W04", "W03", "W07", "W02", "W05", "W15")

    // ------------------------------------------------------------------ baseline readings

    override fun reading(behaviorId: String, selection: Selection, hits: Set<String>): Reading? {
        val rel = selection.relationship ?: return null
        val sourceGiven = "W14" in hits
        return when (behaviorId) {
            "W01" -> when (rel) {
                REPORT -> Reading(Position.RARE, "Unusual from someone who reports to you.")
                else -> Reading(Position.AT, "An ordinary request in this working relationship.")
            }
            "W04" -> when (rel) {
                SENIOR -> Reading(Position.AT, "Assigning work is part of their role.")
                PEER -> Reading(Position.ABOVE,
                    if (sourceGiven) "Extra work from a colleague. They say who asked for it."
                    else "Extra work from a colleague. It may be their own task: they did not say who assigned it.")
                REPORT -> Reading(Position.ABOVE, "Unusual: someone who reports to you is handing you work.")
                else -> Reading(Position.ABOVE, "This may be outside the agreed scope. Check before committing.")
            }
            "W02" -> when (rel) {
                REPORT -> Reading(Position.RARE, "Unusual from someone who reports to you.")
                else -> Reading(Position.AT, "Chasing progress is ordinary here.")
            }
            "W06" -> when (rel) {
                REPORT -> Reading(Position.RARE, "A report raising a problem with your work is unusual. Worth taking seriously.")
                CLIENT -> Reading(Position.ABOVE, "A complaint from a client. Be careful with admissions and promises.")
                else -> Reading(Position.AT, "Pointing out problems is ordinary in this working relationship.")
            }
            "W03", "W05", "W07" -> Reading(Position.AT, "An ordinary request in this working relationship.")
            "W12" -> when (rel) {
                SENIOR -> Reading(Position.CUE, "They mention a formal step. That is a process, not necessarily a threat.")
                PEER -> Reading(Position.ABOVE, "A colleague mentioning escalation is putting pressure on you.")
                REPORT -> Reading(Position.RARE, "Unusual from someone who reports to you.")
                else -> Reading(Position.ABOVE, "The complaint is being escalated.")
            }
            "W13" -> when (rel) {
                REPORT -> Reading(Position.RARE, "Unusual: someone who reports to you is chasing you again.")
                else -> Reading(Position.CUE, "They have asked before. This is a pressure cue.")
            }
            else -> null
        }
    }

    override fun mustChoose(behaviorId: String, selection: Selection, reading: Reading?): Boolean =
        behaviorId in setOf("W01", "W02", "W03", "W04", "W05", "W06", "W07")

    override fun defaultStance(behaviorId: String, selection: Selection): StanceOption? = when (behaviorId) {
        "W15" -> SMALL_TALK
        else -> null
    }

    private val SMALL_TALK = StanceOption("small_talk", "Reply politely", "Reply briefly and politely to their small talk",
        mustInclude = listOf("responds politely to what they asked"),
        mustAvoid = listOf("states a specific fact about the user's weekend, day or plans that the user did not give"),
        hasDecision = false, input = InputMode.OPTIONAL, inputHint = "Anything true you want to mention (optional)")

    // ------------------------------------------------------------------ stances

    private val CHECK_INTERNALLY = StanceOption("check_internally", "Check internally first",
        "Say you need to check internally before answering; do not accept or decline yet",
        mustInclude = listOf("says the user needs to check before answering"),
        mustAvoid = listOf("accepts the request", "declines the request"),
        commitments = listOf("will come back with an answer"))

    private val NOT_RIGHT_PERSON = StanceOption("not_right_person", "I'm not the right person",
        "Say you're not the right person for this",
        mustInclude = listOf("says the user is not the right person for this"),
        mustAvoid = listOf("says another person will do it"),
        input = InputMode.OPTIONAL, inputHint = "Who should they ask? (optional)")
    private val SUGGEST_COLLEAGUE = StanceOption("suggest_colleague", "Suggest a colleague",
        "Say you can't take it now and suggest a colleague, without promising for them",
        mustInclude = listOf("says the user cannot take this on now", "suggests or asks whether a colleague could take it"),
        mustAvoid = listOf("says the colleague will do it"),
        input = InputMode.OPTIONAL, inputHint = "Which colleague? Have they agreed?")
    private val NEED_HELP = StanceOption("need_help", "I need help with this",
        "Say you can't complete this alone and need support",
        mustInclude = listOf("says the user cannot complete this alone", "asks for support"),
        mustAvoid = listOf("agrees to do it alone"),
        input = InputMode.OPTIONAL, inputHint = "Who or what do you need? (optional)")
    private val ASK_WHO_ASSIGNED = StanceOption("ask_who_assigned", "Ask who assigned this",
        "Ask who assigned this or how it should be prioritised; do not accept or decline yet",
        mustInclude = listOf("asks who assigned this work or how to prioritise it"),
        mustAvoid = listOf("accepts the request", "declines the request"),
        clarifyOnly = true)
    private val THIRD_PARTY = listOf(NOT_RIGHT_PERSON, SUGGEST_COLLEAGUE, NEED_HELP, ASK_WHO_ASSIGNED)

    private val DELIVERABLE = listOf(
        StanceOption("agree", "Agree", "Agree to do it as asked",
            mustInclude = listOf("agrees to do what was asked"),
            commitments = listOf("will do what was asked, by the time they stated")),
        StanceOption("agree_condition", "Agree with a condition", "Agree, on a condition",
            mustInclude = listOf("agrees to do what was asked", "states the user's condition"),
            commitments = listOf("will do what was asked if the condition is met"),
            input = InputMode.REQUIRED, inputHint = "Your condition"),
        StanceOption("more_time", "Ask for more time", "Say the requested time isn't possible and ask for more time",
            mustInclude = listOf("says the requested time is not possible", "asks for more time"),
            mustAvoid = listOf("promises a specific new delivery date"),
            avoidUnlessDetail = listOf("promises a specific new delivery date"),
            input = InputMode.OPTIONAL, inputHint = "A new date, if you have one (optional)", apology = Apology.EXPECTED, apologyFor = "not making the time they asked for", apologyByRelationship = mapOf("report" to Apology.OPTIONAL)),
        StanceOption("cannot", "Can't do it", "Say clearly that you can't do this",
            mustInclude = listOf("says the user cannot do this"),
            mustAvoid = listOf("agrees to do part of it"), apology = Apology.EXPECTED, apologyFor = "not being able to do it", apologyByRelationship = mapOf("report" to Apology.OPTIONAL)),
        Shared.CLARIFY,
    )

    private val NEW_WORK = listOf(
        StanceOption("take_on", "Take it on", "Agree to take it on",
            mustInclude = listOf("agrees to take this on"),
            commitments = listOf("will do what was asked, by the time they stated")),
        StanceOption("decline", "Decline", "Say clearly that you can't take this on",
            mustInclude = listOf("says the user cannot take this on"),
            mustAvoid = listOf("agrees to do part of it"), apologyFor = "not being able to take it on", apologyByRelationship = mapOf("senior" to Apology.EXPECTED, "client_or_external" to Apology.EXPECTED)),
        StanceOption("yes_but", "Yes, but something must move", "Agree only if other work can be moved",
            mustInclude = listOf("says the user can take it only if other work is moved or delayed"),
            mustAvoid = listOf("agrees without any condition"),
            input = InputMode.OPTIONAL, inputHint = "What would have to move? (optional)"),
        Shared.CLARIFY,
    )

    private val DECISION = listOf(
        StanceOption("approve", "Agree", "Say yes to what they propose",
            mustInclude = listOf("agrees to what they propose")),
        StanceOption("reject", "Disagree", "Say no to what they propose",
            mustInclude = listOf("says the user does not agree with what they propose"),
            input = InputMode.OPTIONAL, inputHint = "Your reason (optional)", apology = Apology.AVOID),
        StanceOption("need_info", "Need more information", "Ask for what you need before deciding",
            mustInclude = listOf("asks for more information"),
            mustAvoid = listOf("agrees to what they propose", "refuses what they propose"),
            clarifyOnly = true, input = InputMode.OPTIONAL, inputHint = "What do you need to know? (optional)"),
        StanceOption("answer_later", "Answer later", "Say you can't decide yet",
            mustInclude = listOf("says the user cannot decide yet"),
            mustAvoid = listOf("agrees to what they propose", "refuses what they propose"),
            commitments = listOf("will come back with an answer")),
    )

    private val PROBLEM = listOf(
        StanceOption("acknowledge_fix", "Acknowledge and fix it", "Acknowledge the problem and say you'll deal with it",
            mustInclude = listOf("acknowledges the problem they raised", "says the user will deal with it"),
            commitments = listOf("will look into the problem and fix it"),
            allowsFaultAdmission = true,
            input = InputMode.OPTIONAL, inputHint = "When, if you want to give a time (optional)", apology = Apology.EXPECTED, apologyFor = "the problem", apologyByRelationship = mapOf("report" to Apology.OPTIONAL)),
        StanceOption("explain", "Explain what happened", "Explain what happened on your side",
            mustInclude = listOf("explains what happened using only the user's own details"),
            input = InputMode.REQUIRED, inputHint = "What happened on your side"),
        StanceOption("disagree", "Disagree with their account", "Say calmly that you see it differently",
            mustInclude = listOf("says the user sees it differently"),
            mustAvoid = listOf("says the user made a mistake"),
            input = InputMode.OPTIONAL, inputHint = "Your version (optional)", apology = Apology.AVOID),
        StanceOption("ask_what", "Ask what exactly is wrong", "Ask which part they mean before saying anything else",
            mustInclude = listOf("asks which part or what exactly is wrong"),
            mustAvoid = listOf("says the user made a mistake", "says they are wrong"),
            clarifyOnly = true, apology = Apology.AVOID),
    )

    /**
     * A client complaint (question bank: "admitting fault and promises need the
     * user's confirmation"). Acknowledging is about having received the problem,
     * not about fault; an admission is flagged for the user to check, not blocked.
     */
    private val PROBLEM_CLIENT = listOf(
        StanceOption("acknowledge_fix", "Acknowledge and fix it", "Acknowledge the problem and say you'll deal with it",
            mustInclude = listOf("acknowledges receiving the problem they raised", "says the user will deal with it"),
            commitments = listOf("will look into the problem and fix it"),
            confirmChecks = setOf("admits_fault"),
            input = InputMode.OPTIONAL, inputHint = "When, if you want to give a time (optional)", apology = Apology.EXPECTED, apologyFor = "the problem"),
    ) + PROBLEM.drop(1)

    private val TIME = listOf(
        StanceOption("time_ok", "That works", "Confirm the time works",
            mustInclude = listOf("confirms the proposed time works"),
            commitments = listOf("will attend at the proposed time")),
        StanceOption("other_time", "Suggest another time", "Say that time doesn't work and suggest another",
            mustInclude = listOf("says the proposed time does not work", "suggests the user's alternative time"),
            input = InputMode.REQUIRED, inputHint = "When can you do?", apology = Apology.EXPECTED, apologyFor = "that time not working", apologyByRelationship = mapOf("report" to Apology.OPTIONAL)),
        StanceOption("cannot_attend", "Can't make it", "Say you can't make it",
            mustInclude = listOf("says the user cannot make it"),
            mustAvoid = listOf("proposes a specific new time"), apology = Apology.EXPECTED, apologyFor = "not being able to make it", apologyByRelationship = mapOf("report" to Apology.OPTIONAL)),
    )

    private val STATUS = listOf(
        StanceOption("on_track", "On track", "Say it's on track",
            mustInclude = listOf("says the work is on track"),
            mustAvoid = listOf("promises a specific delivery time that they did not state"),
            input = InputMode.OPTIONAL, inputHint = "Anything to add? (optional)"),
        StanceOption("late", "It will be late", "Say it will be late",
            mustInclude = listOf("says the work will be late"),
            mustAvoid = listOf("promises a specific new delivery date"),
            avoidUnlessDetail = listOf("promises a specific new delivery date"),
            input = InputMode.OPTIONAL, inputHint = "New timing, if you know it (optional)", apology = Apology.EXPECTED, apologyFor = "the delay", apologyByRelationship = mapOf("report" to Apology.OPTIONAL)),
        StanceOption("done", "Already done", "Say it's done",
            mustInclude = listOf("says the work is done"),
            input = InputMode.OPTIONAL, inputHint = "Where is it? (optional)"),
        StanceOption("not_started", "Not started yet", "Say you haven't started yet",
            mustInclude = listOf("says the user has not started yet"),
            mustAvoid = listOf("promises a specific delivery date"),
            avoidUnlessDetail = listOf("promises a specific delivery date"),
            input = InputMode.OPTIONAL, inputHint = "When will you start? (optional)", apology = Apology.EXPECTED, apologyFor = "not having started yet", apologyByRelationship = mapOf("report" to Apology.OPTIONAL)),
    )

    private val INFORMATION = listOf(
        Shared.ANSWER,
        StanceOption("find_out", "I'll find out", "Say you'll find out and come back",
            mustInclude = listOf("says the user will find out"),
            mustAvoid = listOf("states the answer"),
            commitments = listOf("will find out and come back with the answer")),
        StanceOption("someone_else", "Someone else knows better", "Say someone else is better placed to answer",
            mustInclude = listOf("says someone else is better placed to answer"),
            mustAvoid = listOf("says another person will answer"),
            input = InputMode.OPTIONAL, inputHint = "Who? (optional)"),
    )

    override fun stances(behaviorId: String, selection: Selection, hits: Set<String>): StanceGroup? {
        val rel = selection.relationship
        fun withExtras(base: List<StanceOption>): List<StanceOption> {
            val out = ArrayList<StanceOption>(base)
            out.addAll(THIRD_PARTY)
            out.add(CHECK_INTERNALLY)
            // What is most relevant for this relationship goes first.
            when {
                rel == CLIENT -> out.moveToFront(CHECK_INTERNALLY)
                rel == PEER && behaviorId == "W04" && "W14" !in hits -> out.moveToFront(ASK_WHO_ASSIGNED)
            }
            return out
        }
        return when (behaviorId) {
            "W01" -> StanceGroup("W01", "They're asking for a deliverable", withExtras(DELIVERABLE))
            "W04" -> StanceGroup("W04", "They're giving you extra work", withExtras(NEW_WORK))
            "W03" -> StanceGroup("W03", "They want your decision",
                if (rel == CLIENT) listOf(CHECK_INTERNALLY) + DECISION else DECISION + CHECK_INTERNALLY)
            "W06" -> StanceGroup("W06", "They're pointing out a problem", if (rel == CLIENT) PROBLEM_CLIENT else PROBLEM)
            "W07" -> StanceGroup("W07", "They're proposing a time", TIME)
            "W02" -> StanceGroup("W02", "They're asking about progress", STATUS)
            "W05" -> StanceGroup("W05", "They're asking for information", INFORMATION)
            "W15" -> StanceGroup("W15", "Small talk", listOf(SMALL_TALK, Shared.BRIEF_ACK))
            // A warning about escalation asks for nothing in so many words, but it does call for a position.
            "W12" -> StanceGroup("W12", "They mention escalating", (if (rel == CLIENT) PROBLEM_CLIENT else PROBLEM) + CHECK_INTERNALLY)
            else -> null
        }
    }

    private fun <T> ArrayList<T>.moveToFront(item: T) {
        if (remove(item)) add(0, item)
    }

    override val conflictStances = listOf(
        StanceOption("state_my_side", "State my side", "State your side of it, facts only",
            mustInclude = listOf("states the user's side using only the user's own details"),
            mustAvoid = listOf("says the user made a mistake"),
            input = InputMode.REQUIRED, inputHint = "The facts on your side", apology = Apology.AVOID),
        StanceOption("ask_specifics", "Ask them to be specific", "Ask them to say exactly what the issue is",
            mustInclude = listOf("asks them to say exactly what the issue is"),
            mustAvoid = listOf("says the user made a mistake", "says they are wrong"), clarifyOnly = true),
        StanceOption("suggest_call", "Suggest a call or meeting", "Suggest talking it through by phone or in person",
            mustInclude = listOf("suggests talking by phone or in person"),
            mustAvoid = listOf("argues the point in the message")),
    )

    // ------------------------------------------------------------------ scoring and drafting

    override fun eLevels(selection: Selection): List<Level> {
        val best = when (selection.relationship) {
            PEER -> listOf("Can't do Friday, sorry. Could I get a few more days?")
            CLIENT -> listOf("I won't be able to send it by Friday. Would an extension to early next week be possible?")
            else -> listOf("I won't be able to send it by Friday. Would an extension be possible?")
        }
        return listOf(
            Level("Hostile", "The reply is insulting, sarcastic, or hostile toward the other person.",
                listOf("Maybe if the brief had been clear I wouldn't be late.", "Try reading the second page next time.")),
            Level("Out of place", "The tone is clearly out of place for a work conversation: flippant about a serious request, overly familiar, or curt to the point of rudeness.",
                listOf("lol no chance.", "Can't. Ask someone else.")),
            Level("Over-apologetic", "The tone is acceptable, but the reply is padded with apology or preamble, so the main point takes effort to find.",
                listOf("I'm so sorry, I really hate to ask and I totally understand if it's a problem, I know how busy you are, but I was wondering if maybe there's any way the Friday deadline could possibly move?")),
            Level("Reads like an email", "The reply is clear and polite, but reads like a formal email or a template rather than a message in this chat.",
                listOf("Dear Sarah, I hope this message finds you well. I am writing to inform you that I will be unable to deliver the report by Friday. Kind regards, Sam",
                    "I wish to inform you that the report cannot be completed by the close of business today.")),
            Level("One odd phrase", "The reply is clear, polite, and matches the register of this chat; one phrase is slightly unnatural and a fluent speaker would likely reword it.",
                listOf("I won't be able to send it by Friday. Would it be possible to obtain an extension?")),
            Level("Ready to send", "The reply reads like something a fluent professional would send in this chat as it is: clear, the right length, and in the same register as the other person.",
                best),
        )
    }

    override val frictionExamples = listOf(
        listOf("Can you send the report by Friday?", "There are two errors in the figures you sent.", "If we miss Friday we lose the slot.", "If this doesn't improve I'll need to raise it with HR."),
        listOf("I was hoping to have this by now.", "That's a bit disappointing."),
        listOf("You said Wednesday and it's now Friday. This has held up the client call."),
        listOf("This keeps happening with your reports.", "You never flag delays until the last minute."),
        listOf("This is amateur work. Do you even know what you're doing?"),
        listOf("Miss this again and I'll make sure everyone knows how useless you are."),
    )

    override fun variants(selection: Selection) = "more direct" to "softer"

    override fun styleRules(selection: Selection): String = buildString {
        append("Work chat rules:\n")
        append("- The first sentence answers the question or states the decision.\n")
        append("- Mirror formality: casual chat gets natural full words, formal chat gets formal wording.\n")
        append("- Use workplace abbreviations such as FYI, ASAP or EOD only if the other person used them first.\n")
        append("- When declining or asking for more time: at most one \"sorry\", no stacked apology, no long preamble.\n")
        append("- Never admit fault, give a reason, or promise a time that the user did not supply.\n")
        append("- Never accept or assign work on behalf of a colleague. Without confirmed agreement, phrase it as a suggestion or a question.\n")
        append("- This is a chat message, not an email: no greeting line, no sign-off.\n")
        when (selection.relationship) {
            SENIOR -> append("- To a manager: concise and polite.\n")
            PEER -> append("- To a colleague: may be more casual.\n")
            REPORT -> append("- To someone who reports to the user: clear, gives direction, never talks down.\n")
            CLIENT -> append("- To a client or external contact: more complete and careful; keep commitments conservative.\n")
            else -> {}
        }
        if (selection.conflict) {
            append("- There is a dispute: state only what user_facts contains, no emotional wording, no sarcasm, no point-by-point rebuttal, at most three sentences.\n")
        }
    }

    override fun toneReading(selection: Selection, shortReply: Boolean, tense: Boolean): ToneRule =
        ToneRule(IMPATIENCE.id, "Possible impatience", "Wording that often signals impatience at work while staying polite.")

    const val CLIENT_COMPLAINT = "A client complaint: admitting fault and making promises need your confirmation."

    override fun notices(selection: Selection, hits: Set<String>): List<String> =
        if (selection.relationship == CLIENT && "W06" in hits) listOf(CLIENT_COMPLAINT) else emptyList()
}
