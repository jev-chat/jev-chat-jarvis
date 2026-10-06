package com.jev.overseas.core.scene

import com.jev.overseas.core.scene.Shared.behavior
import com.jev.overseas.core.scene.Shared.question

/**
 * Friends: new, regular, close.
 *
 * Declining an invitation is not a worse friendship; venting is not a request
 * for advice; jokes need a basis in the chat. Whether the user is free or wants
 * to go is the user's fact and decision.
 */
object FriendsScene : SceneSpec {

    override val scene = Scene.FRIENDS

    val NEW = RelationshipType("new", Scene.FRIENDS, "New friend",
        "This is a chat between two people who have recently become friends. Ordinary between them: friendly, polite talk; no teasing, nothing very personal, no big favours.")
    val REGULAR = RelationshipType("regular", Scene.FRIENDS, "Friend",
        "This is a chat between friends. Ordinary between them: relaxed talk, some joking, meeting up, small favours.")
    val CLOSE = RelationshipType("close", Scene.FRIENDS, "Close friend",
        "This is a chat between close friends. Ordinary between them: very casual talk; teasing and swearing are normal and friendly; personal topics; big favours get asked.")

    override val relationships = listOf(NEW, REGULAR, CLOSE)

    const val UNSPECIFIED_NOTE = "This is a chat between friends. How close they are is not known."
    const val CONFLICT_NOTE = " They have fallen out or are in a disagreement, possibly offline."

    val F01 = behavior("F01", "Invites you", "invite the user to an event or to hang out",
        "Such as \"pub Friday?\", \"you coming Saturday?\" or \"fancy a film tonight\".",
        "They only talk about an event (\"how was the pub?\").")
    val F02 = behavior("F02", "Shares good news", "share good news about themselves",
        "Such as \"I GOT THE JOB\" or \"we're engaged!!\".",
        "No good news about themselves (\"how's the job hunt going?\").")
    val F03 = behavior("F03", "Vents", "share bad news or vent about a problem, without asking what to do",
        "Such as \"worst day ever, my landlord is being a nightmare\" or \"ugh I failed the test\".",
        "They ask for advice (\"what should I do about my landlord?\"), or share nothing bad.")
    val F04 = behavior("F04", "Asks your opinion", "ask the user for advice or an opinion",
        "Such as \"what should I do?\", \"which one looks better?\" or \"be honest, was I out of order?\".",
        "They announce a decision without asking (\"I've decided to quit.\").")
    val F05 = behavior("F05", "Teases you", "tease the user or joke at the user's expense",
        "Such as \"you absolute muppet\", \"classic you lol\" or \"youre out of your mind\".",
        "A joke that is not about the user (\"that's hilarious\").")
    val F06 = behavior("F06", "Asks a big favour",
        "ask the user for money, a place to stay, a lift, or another favour that costs real time or money",
        "Such as \"could you lend me £200 till payday?\" or \"can I crash at yours for a week?\".",
        "A small favour (\"can you send me that link?\"), or no favour.")
    val F07 = behavior("F07", "Cancels a plan", "cancel or pull out of a plan they had with the user",
        "Such as \"can't make it tonight, sorry\" or \"gonna have to bail\".",
        "They are only late (\"running 10 mins late\").")
    val F08 = behavior("F08", "Says they miss you", "say they miss the user or care about the user, as a friend",
        "Such as \"miss you mate\" or \"love you, you know that\".",
        "An ordinary sign-off (\"see you later\").")
    val F09 = behavior("F09", "Says they like you romantically", "say they have romantic feelings for the user",
        "Such as \"I think I like you as more than a friend\".",
        "Friendly affection (\"love you mate\").")

    /** Matrix row "wtf, you idiot" from the approved friends matrix. */
    val F10 = behavior("F10", "Swears at you", "use a swear word or an insult about the user or about what the user said",
        "Such as \"wtf\", \"wtf is wrong with you\", \"you idiot\" or \"are you serious, you muppet\".",
        "Swearing about something else (\"wtf is this weather\"), or no swearing or insult.")

    /** Matrix: close friends cancelling "shown as a cue when it happens repeatedly". Asked only for close friends. */
    val CANCELLED_BEFORE = behavior("cancelled_before", "Cancelled before",
        "cancel a plan with the user after already cancelling an earlier plan with the user in earlier_messages",
        "earlier_messages show them cancelling a plan with the user, and latest_messages cancel again, such as \"sorry, can't make it again\".",
        "This is the first cancellation visible in the chat.", Role.CUE)

    val DISPLEASURE = question("tone.displeasure", "Possible displeasure",
        "Is the latest message from the other person a short reply of a kind that often signals displeasure after a disagreement?",
        "Such as \"k\", \"k.\", \"Fine.\", \"Whatever.\", \"Sure.\", \"ok.\", \"Do what you want.\" or \"forget it\".",
        "A short reply that is clearly warm or neutral (\"ok!\", \"sounds good\", \"kk see you then\").", Role.CUE)

    private val ALL = Shared.SHARED_BEHAVIORS + listOf(
        F01, F02, F03, F04, F05, F10, F06, F07, F08, F09, Shared.AGREEMENT_CLAIMED, Shared.REPEATED, Shared.HISTORY_NEEDED,
    )

    override fun behaviors(selection: Selection) = if (selection.relationship == CLOSE) ALL + CANCELLED_BEFORE else ALL
    override fun toneQuestions(selection: Selection) = listOf(DISPLEASURE)

    override val priority = listOf(
        "K09", "F09", "K06", "K07", "F06", "F04", "F01", "K04", "K05", "F07", "K13", "K08", "K02", "F03", "F02", "F10", "F05", "F08", "K03", "K01",
    )

    override fun reading(behaviorId: String, selection: Selection, hits: Set<String>): Reading? {
        val rel = selection.relationship ?: return null
        return when (behaviorId) {
            "F05" -> when (rel) {
                NEW -> Reading(Position.ABOVE, "Teasing from someone you don't know well yet. It may not be a joke.")
                CLOSE -> if (selection.conflict) Reading(Position.ABOVE, "You've fallen out, so this may not be a joke.")
                else Reading(Position.AT, "Teasing is normal between close friends.")
                else -> Reading(Position.DEPENDS, "Whether this is a joke depends on whether you two usually tease each other.")
            }
            "F10" -> when (rel) {
                NEW -> Reading(Position.ABOVE, "Strong words from someone you don't know well yet. Take them at face value.")
                CLOSE -> if (selection.conflict) Reading(Position.ABOVE, "You've fallen out, so this is probably not a joke.")
                else Reading(Position.AT, "Close friends swear at each other. Most likely a joke.")
                else -> Reading(Position.DEPENDS, "Whether this is a joke depends on what came before.")
            }
            "cancelled_before" -> Reading(Position.CUE, "They've cancelled before. A pattern, not a conclusion.")
            "F06" -> when (rel) {
                CLOSE -> Reading(Position.AT, "Close friends do ask big favours. It is still your decision.")
                else -> Reading(Position.ABOVE, "A big favour for this level of friendship.")
            }
            "F03" -> when (rel) {
                NEW -> Reading(Position.ABOVE, "Sharing something personal: they are bringing you closer.")
                else -> Reading(Position.AT, "Friends vent to each other.")
            }
            "F08" -> when (rel) {
                NEW -> Reading(Position.ABOVE, "Warm for a new friendship.")
                else -> Reading(Position.AT, "Ordinary between friends.")
            }
            "F09" -> Reading(Position.ABOVE, "This is about romantic feelings. The Romance scene may suit this better.")
            "F01" -> Reading(Position.AT, if (rel == NEW) "An invitation: a step toward a closer friendship." else "An ordinary invitation.")
            "F07" -> Reading(Position.AT, "Plans get cancelled. Not a signal on its own.")
            else -> null
        }
    }

    private val ALWAYS_CHOOSE = setOf("F01", "F04", "F06", "F09", "K02", "K04", "K05", "K06", "K07", "K09", "K13")

    override fun mustChoose(behaviorId: String, selection: Selection, reading: Reading?): Boolean =
        behaviorId in ALWAYS_CHOOSE || reading?.position == Position.ABOVE || reading?.position == Position.DEPENDS

    private val SUPPORT = StanceOption("support", "Be supportive", "Be on their side; no advice",
        mustInclude = listOf("responds to the feeling they described"),
        mustAvoid = listOf("gives advice they did not ask for"), hasDecision = false)

    override fun defaultStance(behaviorId: String, selection: Selection): StanceOption? = when (behaviorId) {
        "F02" -> StanceOption("congratulate", "Congratulate", "Congratulate them and share their excitement",
            mustInclude = listOf("congratulates them on their news"),
            mustAvoid = listOf("turns the conversation to the user's own news"), hasDecision = false)
        "F03" -> SUPPORT
        "K03" -> SUPPORT
        "F05" -> REPLY_NORMALLY
        "F10" -> SWEAR_NORMALLY
        "F07" -> StanceOption("no_worries", "No worries", "Say it's no problem",
            mustInclude = listOf("says it is no problem"), mustAvoid = listOf("promises a new date"), hasDecision = false)
        "F08" -> StanceOption("in_kind", "Say it back", "Say something equally warm, as a friend",
            mustInclude = listOf("responds warmly as a friend"), hasDecision = false)
        "K01" -> StanceOption("chat", "Chat back", "Reply naturally to what they shared",
            mustInclude = listOf("responds to what they shared"),
            mustAvoid = listOf("states a fact about the user's day or life that the user did not give"), hasDecision = false)
        "K08" -> StanceOption("accept", "Accept", "Accept the apology warmly",
            mustInclude = listOf("accepts their apology"), hasDecision = false)
        else -> null
    }

    /** Teasing: replying normally comes first; joking back only when the user picks it. Shared with Family A10. */
    val REPLY_NORMALLY = StanceOption("normal", "Reply normally", "Reply good-naturedly without teasing back",
        mustInclude = listOf("replies good-naturedly"), hasDecision = false)
    val TEASE_BACK = StanceOption("banter", "Tease back", "Tease back in the same spirit",
        mustInclude = listOf("jokes back in a friendly way"), hasDecision = false)
    val NOT_FUNNY = StanceOption("not_funny", "I don't like that joke", "Say you don't find that funny",
        mustInclude = listOf("says the user does not like that joke"), apology = Apology.AVOID)
    private val TEASING = listOf(REPLY_NORMALLY, TEASE_BACK, NOT_FUNNY)

    private val SWEAR_NORMALLY = StanceOption("normal_swear", "Reply normally", "Reply in the same easy spirit, without swearing back",
        mustInclude = listOf("replies in an easy-going way"), mustAvoid = listOf("insults them back"), hasDecision = false)

    private val FAVOUR = listOf(
        StanceOption("yes", "Yes", "Say yes", mustInclude = listOf("agrees to what they ask"),
            commitments = listOf("will do what they ask")),
        StanceOption("no", "No", "Say you can't, without inventing a reason",
            mustInclude = listOf("says the user cannot do it"), mustAvoid = listOf("agrees to what they ask"), apology = Apology.EXPECTED, apologyFor = "not being able to help"),
        StanceOption("yes_if", "Yes, with a condition", "Say yes on a condition",
            mustInclude = listOf("agrees on the user's condition"),
            input = InputMode.REQUIRED, inputHint = "Your condition"),
        Shared.CLARIFY,
    )

    override fun stances(behaviorId: String, selection: Selection, hits: Set<String>): StanceGroup? = when (behaviorId) {
        "F01", "K04" -> StanceGroup(behaviorId, "They're inviting you", listOf(
            StanceOption("going", "I'm in", "Say you'll come", mustInclude = listOf("says the user will come"),
                commitments = listOf("will come to what they proposed")),
            StanceOption("not_going", "Can't make it", "Say you can't make it, without inventing a reason",
                mustInclude = listOf("says the user cannot make it"),
                mustAvoid = listOf("says the user will come", "promises to come another time"), apology = Apology.EXPECTED, apologyFor = "not being able to make it"),
            StanceOption("another_time", "Another time", "Say you'd like to but that time doesn't work",
                mustInclude = listOf("says the user would like to but that time does not work"),
                input = InputMode.OPTIONAL, inputHint = "When could you? (optional)", apology = Apology.EXPECTED, apologyFor = "that time not working"),
            StanceOption("not_sure", "Not sure yet", "Say you're not sure yet and will let them know",
                mustInclude = listOf("says the user is not sure yet"),
                mustAvoid = listOf("says the user will come", "says the user cannot come"),
                commitments = listOf("will let them know")),
        ))
        "F04" -> StanceGroup(behaviorId, "They want your opinion", listOf(
            StanceOption("give_opinion", "Give my view", "Give your view",
                mustInclude = listOf("gives the user's view using the user's own details"),
                input = InputMode.REQUIRED, inputHint = "Your view, in a few words"),
            StanceOption("ask_more", "Ask more first", "Ask about the situation before giving a view",
                mustInclude = listOf("asks about the situation"), mustAvoid = listOf("gives advice"), clarifyOnly = true),
            StanceOption("no_opinion", "Rather not say", "Say you'd rather not weigh in",
                mustInclude = listOf("says the user would rather not give a view"), mustAvoid = listOf("gives advice")),
        ))
        "F03", "K03" -> StanceGroup(behaviorId, "They're venting", listOf(
            SUPPORT,
            StanceOption("ask_what_happened", "Ask what happened", "Ask them what happened",
                mustInclude = listOf("asks what happened"), mustAvoid = listOf("gives advice"), hasDecision = false),
            StanceOption("give_advice", "Give advice", "Give your advice",
                mustInclude = listOf("gives the user's advice using the user's own details"),
                input = InputMode.REQUIRED, inputHint = "Your advice, in a few words"),
        ))
        // F10 answers first when both hit.
        "F05" -> if ("F10" in hits) null else StanceGroup(behaviorId, "They're teasing you", TEASING)
        "F10" -> StanceGroup(behaviorId, "They're swearing at you", listOf(
            SWEAR_NORMALLY,
            StanceOption("not_ok", "Say it's not OK", "Say calmly that you don't like being spoken to like that",
                mustInclude = listOf("says the user does not like being spoken to like that"),
                mustAvoid = listOf("insults them back"), apology = Apology.AVOID),
            StanceOption("ask_meaning", "Ask what they mean", "Ask what they mean before reacting",
                mustInclude = listOf("asks what they mean"), mustAvoid = listOf("insults them back"), hasDecision = false),
        ))
        "F06", "K05" -> StanceGroup(behaviorId, "They're asking a favour", FAVOUR)
        "F07" -> StanceGroup(behaviorId, "They're cancelling", listOf(
            StanceOption("no_worries", "No worries", "Say it's no problem",
                mustInclude = listOf("says it is no problem"), mustAvoid = listOf("promises a new date"), hasDecision = false),
            StanceOption("rearrange", "No worries, another day", "Say it's fine and suggest another day",
                mustInclude = listOf("says it is fine", "suggests doing it another day"), hasDecision = false),
            StanceOption("disappointed", "I'm disappointed", "Say you're disappointed",
                mustInclude = listOf("says the user is disappointed"), apology = Apology.AVOID),
        ))
        "F08" -> StanceGroup(behaviorId, "They say they miss you", listOf(
            StanceOption("in_kind", "Say it back", "Say something equally warm, as a friend",
                mustInclude = listOf("responds warmly as a friend"), hasDecision = false),
            StanceOption("gentle", "Reply gently", "Reply kindly without matching it",
                mustInclude = listOf("responds kindly to what they said"), hasDecision = false),
        ))
        "F09" -> StanceGroup(behaviorId, "They have feelings for you", listOf(
            StanceOption("me_too", "I feel the same", "Say you feel the same",
                mustInclude = listOf("says the user feels the same way"), allowsDeclaration = true),
            StanceOption("need_time", "I need time", "Say you need time to think",
                mustInclude = listOf("says the user needs time to think"),
                mustAvoid = listOf("says the user feels the same way", "says the user does not feel the same way")),
            StanceOption("just_friends", "Just friends", "Say kindly that you see them as a friend",
                mustInclude = listOf("says the user sees them as a friend"),
                mustAvoid = listOf("says the user has romantic feelings for them")),
        ))
        "K06", "K07" -> StanceGroup(behaviorId, "They have a complaint", Shared.complaintStances())
        "K02" -> StanceGroup(behaviorId, "They're asking you something", listOf(
            Shared.ANSWER,
            StanceOption("keep_light", "Keep it light", "Reply lightly without going into detail",
                mustInclude = listOf("replies to their question in a light way"),
                mustAvoid = listOf("states a fact about the user that the user did not give"), hasDecision = false),
        ))
        "K09" -> StanceGroup(behaviorId, "They've set a boundary", listOf(Shared.NO_REPLY, Shared.BRIEF_ACK))
        "K13" -> StanceGroup(behaviorId, "They turned you down", listOf(
            StanceOption("gracious", "Take it well", "Accept it gracefully",
                mustInclude = listOf("accepts what they said without pushing"),
                mustAvoid = listOf("pushes them to change their mind"), hasDecision = false),
            StanceOption("another_option", "Offer another option", "Offer another time or option",
                mustInclude = listOf("offers the user's alternative"),
                input = InputMode.REQUIRED, inputHint = "What else would work?"),
        ))
        "K08" -> StanceGroup(behaviorId, "They're apologising", listOf(
            StanceOption("accept", "Accept", "Accept the apology warmly", mustInclude = listOf("accepts their apology"), hasDecision = false),
            StanceOption("need_time", "I need a bit more time", "Say you need a bit more time",
                mustInclude = listOf("says the user needs more time"), mustAvoid = listOf("says everything is fine now")),
        ))
        else -> null
    }

    override val conflictStances = listOf(
        StanceOption("make_up", "Make up", "Say you want to put it behind you",
            mustInclude = listOf("says the user wants to make up"), mustAvoid = listOf("argues the point again")),
        StanceOption("say_upset", "Say what upset me", "Say what upset you",
            mustInclude = listOf("says what upset the user, using the user's own details"),
            input = InputMode.REQUIRED, inputHint = "What upset you", apology = Apology.AVOID),
        StanceOption("stand_firm", "Stand by my view", "Say calmly that you still see it your way",
            mustInclude = listOf("says the user still sees it their way"), mustAvoid = listOf("apologises"), apology = Apology.AVOID),
        StanceOption("cool_off", "Cool off, talk later", "Say you'd rather leave it for now and talk later",
            mustInclude = listOf("says the user wants to leave it for now and talk later"),
            mustAvoid = listOf("argues the point again")),
        StanceOption("meet_up", "Talk face to face", "Suggest meeting to talk it through",
            mustInclude = listOf("suggests meeting to talk it through"), mustAvoid = listOf("argues the point again")),
    )

    override fun eLevels(selection: Selection): List<Level> {
        val mismatch = when (selection.relationship) {
            CLOSE -> listOf("Dear Tom, congratulations on your new position. Best wishes.")
            NEW -> listOf("lol about time you loser")
            else -> listOf("Dear Tom, congratulations on your new position. Best wishes.", "lol about time you loser")
        }
        return listOf(
            Level("Mocking", "The reply mocks, insults, or shows contempt for the other person in a way that is not an obvious joke between them.",
                listOf("Wow, they must be desperate.")),
            Level("Lecturing", "The reply lectures, judges, makes the other person feel guilty, or turns the moment into being about the user.",
                listOf("Finally. Told you to apply ages ago.", "Nice, I got promoted last week too actually.")),
            Level("Closeness mismatch", "The closeness of the reply does not match the friendship described in relationship_note: it is noticeably stiffer and more formal, or noticeably more familiar, than fits.",
                mismatch),
            Level("Flat", "The reply does not engage with what the other person actually said: it is generic, flat, or gives them nothing to respond to.",
                listOf("Congrats.", "That's great news.")),
            Level("One odd phrase", "The reply picks up on what the other person said and its closeness fits the friendship; one phrase is slightly unnatural and a fluent speaker would likely reword it.",
                listOf("So happy for you, you have worked so much for this. When do you start?")),
            Level("Fits", "The reply picks up on what the other person said, its closeness fits the friendship, and it matches their energy and length.",
                listOf("YES!! So happy for you, you've worked so hard for this. When do you start?")),
        )
    }

    override val frictionExamples = listOf(
        listOf("can't make it tonight, sorry", "you absolute muppet, see you at 8", "youre out of your mind lol", "I'd rather not lend money, sorry."),
        listOf("bit gutted about last night tbh", "I was really looking forward to it."),
        listOf("You bailed on me again last night and didn't even text."),
        listOf("You always do this.", "You only ever call when you need something."),
        listOf("You're a terrible friend and everyone knows it."),
        listOf("Keep this up and I'll tell everyone what you did."),
    )

    override fun variants(selection: Selection) = "shorter" to "more enthusiastic"

    override fun styleRules(selection: Selection): String = buildString {
        append("Friends chat rules:\n")
        append("- Match their energy: excited when they are excited, calm when they are low.\n")
        append("- Use contractions and everyday words; this is not a work message.\n")
        append("- Banter and swearing only if the user's own messages in this chat already do it and there is no falling-out.\n")
        append("- When they vent, respond to the feeling first. No advice unless they asked or the goal says so.\n")
        append("- When declining an invitation, give no invented reason. At most one \"sorry\".\n")
        append("- Do not turn their news into something about the user.\n")
        when (selection.relationship) {
            NEW -> append("- A new friend: friendly and polite, no teasing.\n")
            CLOSE -> append("- A close friend: very casual.\n")
            else -> {}
        }
        if (selection.conflict) append("- They have fallen out: no jokes, no \"always\" or \"never\", two or three sentences at most.\n")
    }

    /** Matrix: a short reply is judged only between close friends who have fallen out; never for new or regular friends. */
    override fun toneReading(selection: Selection, shortReply: Boolean, tense: Boolean): ToneRule? =
        if (shortReply && tense && selection.relationship == CLOSE) ToneRule(DISPLEASURE.id, "Possible displeasure",
            "A very short reply right after a disagreement.") else null
}
