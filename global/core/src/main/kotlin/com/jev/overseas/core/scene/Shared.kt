package com.jev.overseas.core.scene

/**
 * Questions, levels and options used by every scene.
 *
 * Wording follows the TypeSafe guidance for Jev: one condition per question,
 * phrased so that "yes" is the high value, literal rather than inferential, with
 * criteria that carry realistic examples. The analysis target is the other
 * person's latest turn (`latest_messages`); `earlier_messages` are context only.
 */
object Shared {

    /** "In latest_messages, does the other person <verb phrase>?" */
    fun behavior(
        id: String,
        label: String,
        verbPhrase: String,
        yes: String,
        no: String,
        role: Role = Role.ACTION,
    ) = Behavior(id, label, "In latest_messages, does the other person $verbPhrase?", yes, "$no $EARLIER_ONLY", role)

    /** A question that is not of the "does the other person…" form. */
    fun question(id: String, label: String, question: String, yes: String, no: String, role: Role = Role.ACTION) =
        Behavior(id, label, question, yes, no, role)

    private const val EARLIER_ONLY = "Something that appears only in earlier_messages does not count."

    // ------------------------------------------------------------------ shared behaviours

    val K01 = behavior("K01", "Everyday chat", "share everyday news or ask how the user is",
        "Ordinary small talk, such as \"Guess what happened at work\" or \"How was your day?\".",
        "A serious topic (\"We need to talk.\"), a request, or a complaint.")
    val K02 = behavior("K02", "Asks you a question", "ask a question that the user is expected to answer",
        "A real question to the user, such as \"What time does it start?\" or \"Did you speak to her?\".",
        "No question, or only a rhetorical one.")
    val K03 = behavior("K03", "Shares a feeling", "say how they feel, without blaming the user for it",
        "They describe their own feeling, such as \"ugh today was awful\" or \"I felt really alone last night.\".",
        "They state no feeling, or they blame the user (\"You left me alone all night.\").")
    val K04 = behavior("K04", "Plans or timing", "propose a plan or a time, or ask about an arrangement",
        "Such as \"Dinner Friday?\" or \"What time shall I come over?\".",
        "No plan or time is proposed or asked about.")
    val K05 = behavior("K05", "Asks a favour", "ask the user to do something for them",
        "Such as \"Can you send me that link?\" or \"Could you pick me up at 6?\".",
        "They ask for nothing, or ask a question that only needs an answer.")
    val K06 = behavior("K06", "Has a complaint", "complain about something the user did or did not do",
        "Such as \"You said you'd call and you didn't.\" or \"You never replied to my message.\".",
        "They describe their own feeling without blaming the user (\"I felt really alone last night.\"), or complain about someone else.")
    val K07 = behavior("K07", "Wants an explanation", "ask the user to explain something the user did",
        "Such as \"Why didn't you tell me?\" or \"Where were you last night?\".",
        "An ordinary question (\"What time are you home?\").")
    val K08 = behavior("K08", "Apologises", "apologise or try to make up",
        "Such as \"I'm sorry about earlier.\" or \"Can we start over?\".",
        "A passing courtesy (\"Sorry, running 10 mins late.\").")
    val K09 = behavior("K09", "Sets a boundary",
        "say they need space, or ask the user to stop contacting them or to stop raising a topic",
        "Such as \"I need some space tonight.\", \"Please stop texting me.\" or \"I don't want to talk about this.\".",
        "They end the chat for an ordinary reason (\"I'm going to bed, night.\").")
    val K10 = question("K10", "Just an update",
        "Do latest_messages only give information or an update, without asking the user for anything?",
        "Such as \"The train's delayed by 20 minutes.\" or \"Dad's operation went fine.\".",
        "They ask or request something, or they are only an acknowledgement such as \"ok\" or \"k\".", Role.PASSIVE)
    val K11 = question("K11", "Thanks or acknowledgement",
        "Are latest_messages only thanks, an acknowledgement, a greeting, or small talk?",
        "Such as \"Thanks!\", \"ok\", \"k\", \"got it\", \"sure\" or \"Morning!\".",
        "They also ask or say something of substance (\"Thanks. Also, where's the invoice?\").", Role.PASSIVE)
    val K12 = behavior("K12", "Ending the chat", "signal that they are ending the chat for now",
        "Such as \"anyway gotta go\", \"night!\" or \"ttyl\".",
        "A short pause (\"brb\"), or the chat simply continues.", Role.PASSIVE)
    val K13 = behavior("K13", "Turns you down",
        "turn down a suggestion from the user without offering another time or option",
        "Such as \"I'm pretty busy this week\" or \"can't sorry\", after the user suggested something.",
        "They decline but offer an alternative (\"can't Friday, how about Sunday?\"), or the user suggested nothing.")

    val SHARED_BEHAVIORS = listOf(K01, K02, K03, K04, K05, K06, K07, K08, K09, K10, K11, K12, K13)

    // ------------------------------------------------------------------ shared cues

    val AGREEMENT_CLAIMED = behavior("agreement_claimed", "Says you agreed or promised",
        "say that the user made a promise, or that the two of them had agreed on something",
        "Such as \"You said you'd call.\" or \"We agreed no phones at dinner.\".",
        "A wish with no claim of a promise (\"I wish you'd call more.\").", Role.CUE)
    val REPEATED = behavior("repeated", "Raised before",
        "raise again a request or an issue that they already raised in earlier_messages",
        "earlier_messages already contain the same request or issue from them, and latest_messages bring it up again, such as \"Any update?\" or \"As I said, I need that form.\".",
        "This is the first time it comes up.", Role.CUE)
    val HISTORY_NEEDED = behavior("history_needed", "Refers to something earlier",
        "refer to an earlier agreement, message, or file that does not appear anywhere in earlier_messages",
        "Such as \"As we agreed last week…\" or \"per the doc I sent\", when no such agreement or document is visible.",
        "Everything they refer to is visible in earlier_messages or latest_messages.", Role.CUE)

    // ------------------------------------------------------------------ friction

    /** The same six situations for every scene; examples change per scene. */
    private val FRICTION_WHAT = listOf(
        "None" to "The speaker's messages contain no dissatisfaction, blame, insult, or threat directed at the other participant. Neutral requests, deadlines, refusals, calm boundaries, pointing out an error without displeasure, complaints about third parties or outside events, formal or procedural steps, and teasing that is plainly friendly all belong here.",
        "Own disappointment" to "The speaker expresses their own disappointment, frustration, or unease about the other participant or about how things stand between the two of them, without blaming a specific action of the other participant.",
        "Specific complaint" to "The speaker shows displeasure with the other participant about a specific action, delay, or result: they complain about it or blame the other participant for it. Simply pointing out an error or asking for a correction, with no displeasure, does not belong here.",
        "General blame" to "The speaker blames the other participant in general terms: a repeated pattern, their attitude, or their motives.",
        "Insult" to "The speaker seriously insults, ridicules, or shows contempt for the other participant as a person. Playful teasing between people who are close does not belong here.",
        "Threat" to "The speaker threatens to harm the other participant, to punish them out of spite, or to expose or humiliate them. A formal or procedural step, such as raising the matter with HR or a manager, and a consequence that simply follows from a deadline or a rule, do not belong here.",
    )

    fun frictionLevels(examples: List<List<String>>): List<Level> =
        FRICTION_WHAT.mapIndexed { i, (short, what) -> Level(short, what, examples.getOrElse(i) { emptyList() }) }

    const val FRICTION_OTHER_ID = "friction.other"
    const val FRICTION_SELF_ID = "friction.self"

    /**
     * Asked in a request of its own that carries only the latest turn and the few
     * messages before it (AnalysisEngine.frictionState), so "harshest" means
     * harshest now.
     */
    const val FRICTION_OTHER_QUESTION =
        "Rate only what the other person says in earlier_messages and latest_messages (messages with from = other). " +
            "The user's messages are context only. Which situation describes the other person's messages at their harshest?"
    const val FRICTION_SELF_QUESTION =
        "Rate only what the user says in earlier_messages (messages with from = user). " +
            "The other person's messages are context only. Which situation describes the user's messages at their harshest?"

    // ------------------------------------------------------------------ G

    /**
     * How fully a reply achieves what the user wants: the goal-completion
     * ladder. Clarity is folded into levels 2 and 4, so a hedged
     * or undercut decision still scores low. One ladder for every scene.
     */
    val G_LEVELS = listOf(
        Level("Opposite or absent", "candidate_reply says the opposite of the user's decision or request, or does not address it at all.",
            listOf("Sure, Friday works.", "Thanks for checking in! Hope the week is going well.")),
        Level("Off target", "candidate_reply responds to something else; the reader would not learn the user's decision or request from it.",
            listOf("The figures are in the shared folder if you need them.", "That means a lot.")),
        Level("Only hinted", "The decision or request can only be inferred, or is so hedged that the reader cannot tell whether it is firm.",
            listOf("Friday is looking pretty busy on my side.", "I might possibly struggle with Friday, maybe we could think about timing?")),
        Level("Key point missing", "The decision or request is clear, but an item in goal.must_include or a detail in goal.user_details is missing or stated wrongly.",
            listOf("I can't do Friday, sorry.", "It's going to be a bit late, sorry.")),
        Level("Minor gap", "The decision, every item in goal.must_include and every detail in goal.user_details are there, but one is vague enough that the reader would have to ask, or another sentence weakens it.",
            listOf("I can't send it by Friday. Can we sort something out?", "I won't make Friday, it'll be Thursday. Though if it really matters I could probably push for Friday.")),
        Level("Complete", "The decision, every item in goal.must_include and every detail in goal.user_details are stated clearly; the reader has nothing left to ask.",
            listOf("I won't be able to send it by Friday. Could I have until Thursday?", "I don't want to get back together, but I'd really like us to stay friends.")),
    )

    const val G_QUESTION =
        "How fully does candidate_reply achieve what the user wants, as described in goal.summary, goal.must_include and goal.user_details?"

    const val E_QUESTION =
        "How well do the wording and tone of candidate_reply suit this chat and the relationship described in relationship_note? " +
            "Judge wording and tone only, not whether the decision itself is pleasant: a clear refusal or disagreement can be the best level."

    // ------------------------------------------------------------------ hard checks (A layer)

    data class Check(val id: String, val label: String, val question: String, val yes: String, val no: String)

    val UNSUPPORTED_FACT = Check("unsupported_fact", "States something you didn't say",
        "Does candidate_reply state, as a fact about the user or about events, something that is not found in earlier_messages, latest_messages, user_facts, or goal?",
        "It gives a reason, an event, a detail, a date, or an amount that appears nowhere in the chat or the user's facts, such as \"I've been off sick this week\" or \"my boss called an emergency meeting\".",
        "Everything factual in it is found in the chat, user_facts, or goal. Courtesy phrases, questions, suggestions, and statements that only repeat the goal are not factual claims.")
    val NEW_COMMITMENT = Check("new_commitment", "Promises something you didn't authorise",
        "Does candidate_reply commit the user to an action, a date, or an amount that is not listed in goal.authorized_commitments?",
        "It promises something not authorised, such as \"I'll have it to you by Monday.\", \"I'll call every Sunday.\" or \"I'll transfer the deposit this afternoon.\".",
        "It makes no promise, or only the ones in goal.authorized_commitments. A question, an offer phrased as a question (\"Would Tuesday work?\"), or a request is not a commitment.")
    val COMMITS_OTHERS = Check("commits_others", "Promises on someone else's behalf",
        "Does candidate_reply say that a third person will do something, when user_facts does not say that person has agreed?",
        "Such as \"Priya will take care of it.\" or \"I'll get Tom to send it over today.\".",
        "It mentions no third person's future action, or only suggests or asks (\"Could Priya take this one?\"), or user_facts says that person agreed.")
    val OPPOSITE_STANCE = Check("opposite_stance", "Goes against your decision",
        "Does candidate_reply take the opposite position to the decision described in goal.summary?",
        "The goal declines and the reply accepts, or the goal accepts and the reply declines, such as replying \"Sure, Friday works.\" when the goal is to say Friday is not possible.",
        "The reply keeps the goal's position, even if it is vague or soft.")
    val CROSSES_BOUNDARY = Check("crosses_boundary", "Pushes past their boundary",
        "Does candidate_reply keep up contact or raise the topic that the other person asked to stop in latest_messages?",
        "It argues, asks them to reconsider, asks questions, or continues the topic they asked to drop.",
        "It only acknowledges briefly and respects what they asked.")
    val ADMITS_FAULT = Check("admits_fault", "Admits fault for you",
        "Does candidate_reply say that the user made a mistake or was at fault?",
        "Such as \"That's my mistake, I dropped the ball.\", \"I should have flagged this earlier.\" or \"You're right, I've been terrible at this.\".",
        "It admits nothing. A courtesy such as \"Sorry for the delay.\" or \"Thanks for flagging, I'll take a look.\" is not an admission of fault.")
    val UNSTATED_DECLARATION = Check("unstated_declaration", "Declares feelings you didn't choose",
        "Does candidate_reply say that the user misses, loves, or has romantic feelings for the other person, or that the user wants to get back together?",
        "Such as \"Miss you too.\", \"I think I'm falling for you.\", \"I think about us all the time.\" or \"I'd like to try again.\".",
        "It expresses none of these. Friendly warmth such as \"That's kind of you to say.\" or \"Hope you're doing well.\" does not count.")
    val BEYOND_CLARIFICATION = Check("beyond_clarification", "Decides instead of asking",
        "Does candidate_reply give a decision or a commitment instead of only asking a question?",
        "It accepts, declines, or promises something, such as \"Sure, I'll take it.\" or \"I can't do that.\".",
        "It only asks for the missing information.")

    fun avoidCheck(index: Int, text: String) = Check("avoid.$index", "Does what you ruled out: $text",
        "Does candidate_reply do this: $text?",
        "The reply clearly does it.", "The reply does not do it.")

    fun includeCheck(index: Int, text: String) = Check("include.$index", text,
        "Does candidate_reply do this: $text?",
        "The reply clearly does it.", "The reply does not do it, or only hints at it.")

    /** Their turn asked for two things and the goal covers one; the reply must not settle the other. */
    val BEYOND_GOAL = Check("beyond_goal", "Decides something you didn't",
        "Does candidate_reply agree to, refuse, or decide something the other person asked in latest_messages that goal.summary does not cover?",
        "They asked for two things and the reply settles the one the goal does not mention, such as \"Option B is fine too.\" when the goal only asks for more time on the report.",
        "The reply settles only what goal.summary covers. Saying the other point will be answered separately, or leaving it out, is not deciding it.")

    /** Confirm-only. A date, time or amount that differs from what the user said before is shown for checking. */
    val CONTRADICTS_EARLIER = Check("contradicts_earlier", "Differs from what you said before",
        "Does candidate_reply state a date, time, amount or commitment that conflicts with what the user said earlier in the conversation, without saying it has changed?",
        "The user said Thursday earlier and the reply says Monday as if nothing changed, or the user agreed to £200 and the reply says £150.",
        "Nothing in it conflicts with what the user said before, or it says plainly that the plan has changed (\"Sorry, it'll be Monday now, not Thursday.\").")

    val ACKNOWLEDGES = Check("acknowledges", "Picks up what they said",
        "Does candidate_reply refer to the specific thing the other person said in latest_messages?",
        "It mentions or clearly responds to the particular thing they said.",
        "It is generic and could be sent in reply to almost any message.")

    // ------------------------------------------------------------------ options every scene offers

    val NO_REPLY = StanceOption("no_reply", "Don't reply", "Send nothing for now", hasDecision = false, noReply = true)

    val BRIEF_ACK = StanceOption("brief_ack", "Brief acknowledgement",
        "Acknowledge briefly, nothing more",
        mustInclude = listOf("acknowledges what they said"),
        mustAvoid = listOf("makes a new promise"),
        hasDecision = false)

    val POLITE_CLOSE = StanceOption("polite_close", "Close politely",
        "Acknowledge briefly and politely, and leave it there",
        mustInclude = listOf("acknowledges what they said"),
        mustAvoid = listOf("asks them a question", "asks them to reconsider"),
        hasDecision = false)

    val CLARIFY = StanceOption("clarify", "Ask a clarifying question",
        "Ask what is needed before deciding; do not accept or decline yet",
        mustInclude = listOf("asks a question about what they need"),
        mustAvoid = listOf("accepts the request", "declines the request"),
        hasDecision = true, clarifyOnly = true)

    /** Complaint handling used by Romance, Friends, Family and General. */
    fun complaintStances(askLabel: String = "Ask how they feel first") = listOf(
        StanceOption("admit_apologise", "Admit and apologise", "Admit it and apologise for that specific thing",
            mustInclude = listOf("apologises for the specific thing they raised"),
            commitments = emptyList(), allowsFaultAdmission = true,
            input = InputMode.OPTIONAL, inputHint = "What happened on your side (optional)"),
        StanceOption("explain", "Explain what happened", "Explain what happened on your side",
            mustInclude = listOf("explains what happened using only the user's own details"),
            input = InputMode.REQUIRED, inputHint = "What happened on your side"),
        StanceOption("disagree", "Disagree with their account", "Say calmly that you see it differently",
            mustInclude = listOf("says the user sees it differently"),
            mustAvoid = listOf("apologises for the thing they raised"),
            input = InputMode.OPTIONAL, inputHint = "Your version (optional)", apology = Apology.AVOID),
        StanceOption("ask_first", askLabel, "Ask about it before saying anything else",
            mustInclude = listOf("asks them a question about what they raised"),
            mustAvoid = listOf("apologises for the thing they raised", "says they are wrong"),
            clarifyOnly = false, apology = Apology.AVOID),
        StanceOption("talk_later", "Talk about it later", "Say you'd rather talk about this later, not by text",
            mustInclude = listOf("says the user would rather talk about this later or in person"),
            mustAvoid = listOf("argues about the thing they raised")),
    )

    /** Their latest turn is only a file: say it arrived, never what is in it. */
    val ACK_FILE = StanceOption("ack_file", "Say you got it", "Say you've received what they sent",
        mustInclude = listOf("says the user received what they sent"),
        mustAvoid = listOf("says what is in the file or comments on its content"),
        hasDecision = false)

    val ANSWER = StanceOption("answer", "Answer", "Answer their question with your details",
        mustInclude = listOf("answers their question using the user's details"),
        input = InputMode.REQUIRED, inputHint = "Your answer, in a few words")
}
