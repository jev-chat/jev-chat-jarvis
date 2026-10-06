package com.jev.overseas.core.scene

import com.jev.overseas.core.scene.Shared.behavior
import com.jev.overseas.core.scene.Shared.question

/**
 * Family: parents and elders, siblings, younger relatives, a partner's family.
 *
 * Being family does not mean the user must agree or explain everything, and it
 * does not mean a confrontation either. Care and pressure can come together: care
 * is shown as a behaviour, guilt wording as a cue. Visits, calls and money are
 * only ever promised by the user.
 */
object FamilyScene : SceneSpec {

    override val scene = Scene.FAMILY

    val PARENT = RelationshipType("parent_or_elder", Scene.FAMILY, "Parent or elder",
        "This is a family chat. The other person is the user's parent or an older relative. Ordinary between them: asking about food, health and safety; asking about work, relationships and money; giving advice directly; very short messages.")
    val SIBLING = RelationshipType("sibling_or_peer", Scene.FAMILY, "Sibling or cousin",
        "This is a family chat. The other person is the user's sibling or a relative of the same generation. Ordinary between them: casual talk, teasing, helping each other.")
    val YOUNGER = RelationshipType("child_or_younger", Scene.FAMILY, "Child or younger relative",
        "This is a family chat. The other person is the user's child or a younger relative. Ordinary between them: asking the user for help or permission, checking in. Asking about the user's private matters is unusual.")
    val IN_LAW = RelationshipType("partner_family", Scene.FAMILY, "Partner's family",
        "This is a chat with a member of the user's partner's family. Ordinary between them: polite, measured talk. Asking about the user's private matters and decisions is unusual.")

    override val relationships = listOf(PARENT, SIBLING, YOUNGER, IN_LAW)

    const val UNSPECIFIED_NOTE = "This is a family chat. How the two are related is not known."
    const val CONFLICT_NOTE = " They are currently in a disagreement, possibly offline."

    val A01 = behavior("A01", "Checks you're okay", "ask whether the user is eating, sleeping, safe, or well",
        "Such as \"Have you eaten?\", \"Did you get home ok?\" or \"Are you sleeping enough?\".",
        "An ordinary question about something else (\"What did you have for dinner? Send me the recipe\").")
    val A02 = behavior("A02", "Asks about a personal decision",
        "ask about or comment on a personal decision of the user, such as work, relationships, marriage, children, money, or health",
        "Such as \"Have you thought about moving back home?\", \"When are you two getting married?\" or \"Are you still at that job?\".",
        "Everyday small talk (\"How was work today?\").")
    val A03 = behavior("A03", "Gives advice you didn't ask for", "tell the user what they should do, when the user did not ask",
        "Such as \"You should really call your aunt.\" or \"You need to start saving.\".",
        "They offer but do not push (\"Do you want my advice?\"), or give no advice.")
    val A04 = behavior("A04", "Wants you to visit or call", "ask the user to visit, come home, or call",
        "Such as \"Are you coming home for Christmas?\" or \"Call me when you can.\".",
        "They only mention a plan of their own (\"We're going to Gran's on Sunday.\").")
    val A05 = behavior("A05", "Asks for help", "ask the user for practical help, such as errands, help with technology, care, or money",
        "Such as \"Can you help me with this form?\" or \"Could you send some money for the boiler?\".",
        "They ask for nothing (\"Thanks for sorting the form.\").")
    val A06 = behavior("A06", "Family news", "share news about the family",
        "Such as \"Your cousin had the baby!\" or \"Dad's operation went fine.\".",
        "No family news (\"How's your week?\").")
    val A07 = behavior("A07", "Says they're worried", "say that they are worried about the user",
        "Such as \"I'm worried about you.\" or \"I just want to know you're ok.\".",
        "A plain question (\"Are you ok?\").")
    val A08 = behavior("A08", "Guilt wording", "say or imply that the user is failing a duty to the family",
        "Such as \"You never call.\", \"After everything we've done for you.\" or \"I suppose we'll just have Christmas on our own then.\".",
        "A wish with no reproach (\"We'd love to see you at Christmas.\").", Role.CUE)
    val A09 = behavior("A09", "Compares you with someone", "compare the user unfavourably with another person",
        "Such as \"Your cousin has already bought a house.\" or \"Your sister calls every day.\".",
        "News about someone else with no comparison (\"Your cousin bought a house!\").", Role.CUE)

    /** Matrix row "teasing you" from the approved family matrix. */
    val A10 = behavior("A10", "Teases you", "tease the user or joke at the user's expense",
        "Such as \"you absolute muppet\", \"classic you lol\" or \"still can't cook then?\".",
        "A joke that is not about the user (\"that's hilarious\").")

    val DISPLEASURE = question("tone.displeasure", "Possible displeasure",
        "Is the latest message from the other person a short reply of a kind that often signals displeasure after a disagreement?",
        "Such as \"Fine.\", \"Whatever.\", \"Do what you want.\" or \"forget it\".",
        "A short reply that is neutral (\"Ok.\", \"K\", \"sounds good\").", Role.CUE)

    private val ALL = Shared.SHARED_BEHAVIORS + listOf(
        A01, A02, A03, A04, A05, A06, A07, A08, A09, A10, Shared.AGREEMENT_CLAIMED, Shared.REPEATED, Shared.HISTORY_NEEDED,
    )

    override fun behaviors(selection: Selection) = ALL
    override fun toneQuestions(selection: Selection) = listOf(DISPLEASURE)

    override val priority = listOf(
        "K09", "K06", "K07", "A02", "A05", "A04", "A03", "A07", "K04", "K05", "K13", "K08", "K02", "A01", "A06", "A10", "K03", "K01",
    )

    override fun reading(behaviorId: String, selection: Selection, hits: Set<String>): Reading? {
        val rel = selection.relationship ?: return null
        // The shared plan and favour questions overlap A04 and A05. When only the shared one came
        // back, read it as the family row it stands for.
        if (behaviorId == "K04" && "A04" !in hits) return reading("A04", selection, hits)
        if (behaviorId == "K05" && "A05" !in hits) return reading("A05", selection, hits)
        return when (behaviorId) {
            "A01" -> when (rel) {
                YOUNGER -> Reading(Position.RARE, "Unusual from a younger relative.")
                IN_LAW -> Reading(Position.AT, "A polite check-in.")
                else -> Reading(Position.AT, "Ordinary care.")
            }
            "A02" -> when (rel) {
                PARENT -> Reading(Position.AT, "Parents often ask about this. Whether to discuss it is up to you.")
                SIBLING -> Reading(Position.DEPENDS, "Whether this is ordinary depends on how close you two are.")
                YOUNGER -> Reading(Position.ABOVE, "Unusual from a younger relative.")
                else -> Reading(Position.ABOVE, "A sensitive question from your partner's family.")
            }
            "A03" -> when (rel) {
                YOUNGER -> Reading(Position.RARE, "Unusual from a younger relative.")
                IN_LAW -> Reading(Position.ABOVE, "Advice from your partner's family goes beyond what is ordinary.")
                else -> Reading(Position.AT, "Direct advice is ordinary here.")
            }
            "A04" -> Reading(Position.AT, "An ordinary request. It is still your decision.")
            "A05" -> when (rel) {
                IN_LAW -> Reading(Position.ABOVE, "A request for help from your partner's family. Decide before replying.")
                else -> Reading(Position.AT, "An ordinary request. It is still your decision.")
            }
            "A10" -> when (rel) {
                PARENT -> Reading(Position.DEPENDS, "Whether this is ordinary depends on your family's habits.")
                IN_LAW -> Reading(Position.ABOVE, "Teasing from your partner's family goes beyond what is ordinary.")
                else -> Reading(Position.AT, "Teasing is ordinary here.")
            }
            "A08" -> Reading(Position.CUE, "Guilt wording. That is a pressure cue, whatever the request.")
            "A09" -> Reading(Position.CUE, "A comparison with someone else. That is a pressure cue.")
            else -> null
        }
    }

    private val ALWAYS_CHOOSE = setOf("A02", "A03", "A04", "A05", "A07", "K02", "K04", "K05", "K06", "K07", "K09", "K13")

    override fun mustChoose(behaviorId: String, selection: Selection, reading: Reading?): Boolean =
        behaviorId in ALWAYS_CHOOSE || reading?.position == Position.ABOVE || reading?.position == Position.DEPENDS

    private val REASSURE = StanceOption("reassure", "Reassure them", "Reassure them without specifics you haven't given",
        mustInclude = listOf("reassures them"),
        mustAvoid = listOf("states a specific fact about the user's meals, sleep, health or whereabouts that the user did not give"),
        hasDecision = false,
        input = InputMode.OPTIONAL, inputHint = "Anything true you want to mention (optional)")

    override fun defaultStance(behaviorId: String, selection: Selection): StanceOption? = when (behaviorId) {
        "A01" -> REASSURE
        "A06" -> StanceOption("respond_news", "Respond to the news", "Respond warmly to the news",
            mustInclude = listOf("responds to the news they shared"), hasDecision = false)
        "K01" -> StanceOption("chat", "Chat back", "Reply naturally to what they shared",
            mustInclude = listOf("responds to what they shared"),
            mustAvoid = listOf("states a fact about the user's day or life that the user did not give"), hasDecision = false)
        "K03" -> StanceOption("support", "Be supportive", "Show you understand",
            mustInclude = listOf("responds to the feeling they described"), hasDecision = false)
        "K08" -> StanceOption("accept", "Accept", "Accept the apology warmly",
            mustInclude = listOf("accepts their apology"), hasDecision = false)
        "A10" -> FriendsScene.REPLY_NORMALLY
        else -> null
    }

    private val YES_NO = listOf(
        StanceOption("yes", "Yes", "Say yes", mustInclude = listOf("agrees to what they ask"),
            commitments = listOf("will do what they ask")),
        StanceOption("no", "No", "Say you can't, kindly and clearly",
            mustInclude = listOf("says the user cannot do it"), mustAvoid = listOf("agrees to what they ask"), apologyFor = "not being able to", apologyByRelationship = mapOf("parent_or_elder" to Apology.EXPECTED, "partner_family" to Apology.EXPECTED)),
        StanceOption("other", "Another time", "Suggest another time",
            mustInclude = listOf("suggests the user's alternative time"),
            input = InputMode.REQUIRED, inputHint = "When could you?"),
        StanceOption("later", "Confirm later", "Say you'll confirm later",
            mustInclude = listOf("says the user will confirm later"),
            mustAvoid = listOf("agrees to what they ask", "refuses what they ask"),
            commitments = listOf("will confirm later")),
    )

    override fun stances(behaviorId: String, selection: Selection, hits: Set<String>): StanceGroup? = when (behaviorId) {
        "A01" -> StanceGroup(behaviorId, "They're checking you're okay", listOf(
            REASSURE,
            StanceOption("brief", "Keep it brief", "Answer briefly", mustInclude = listOf("answers briefly"),
                mustAvoid = listOf("states a specific fact the user did not give"), hasDecision = false),
        ))
        "A02" -> StanceGroup(behaviorId, "They're asking about a personal decision", listOf(
            StanceOption("answer", "Answer", "Answer with your details",
                mustInclude = listOf("answers their question using the user's details"),
                input = InputMode.REQUIRED, inputHint = "Your answer, in a few words"),
            StanceOption("brush_off", "Keep it short", "Answer lightly without details",
                mustInclude = listOf("responds without giving details"),
                mustAvoid = listOf("states a decision or plan the user did not give"), hasDecision = false),
            StanceOption("not_discuss", "I'd rather not discuss it", "Say kindly that you'd rather not talk about this",
                mustInclude = listOf("says the user would rather not discuss this"),
                mustAvoid = listOf("states a decision or plan the user did not give"), apology = Apology.AVOID),
            StanceOption("my_decision", "Tell them my decision", "Tell them what you've decided",
                mustInclude = listOf("states the user's decision using the user's own details"),
                input = InputMode.REQUIRED, inputHint = "Your decision", apology = Apology.AVOID),
        ))
        "A03" -> StanceGroup(behaviorId, "They're giving advice", listOf(
            StanceOption("thanks_consider", "Thanks, I'll think about it", "Thank them and say you'll think about it",
                mustInclude = listOf("thanks them", "says the user will think about it"),
                mustAvoid = listOf("promises to follow the advice"), hasDecision = false),
            StanceOption("own_plan", "I have my own plan", "Say kindly that you have your own plan",
                mustInclude = listOf("says the user has their own plan"),
                mustAvoid = listOf("promises to follow the advice")),
            StanceOption("not_that", "Not this topic", "Say you'd rather not get into this topic",
                mustInclude = listOf("says the user would rather not get into this")),
        ))
        "A10" -> StanceGroup(behaviorId, "They're teasing you",
            // No teasing back with a partner's family (style rule: polite, no teasing).
            if (selection.relationship == IN_LAW) listOf(FriendsScene.REPLY_NORMALLY, FriendsScene.NOT_FUNNY)
            else listOf(FriendsScene.REPLY_NORMALLY, FriendsScene.TEASE_BACK, FriendsScene.NOT_FUNNY))
        "A04", "K04" -> StanceGroup(behaviorId, "They want you to visit or call", YES_NO)
        "A05", "K05" -> StanceGroup(behaviorId, "They're asking for help", listOf(
            YES_NO[0], YES_NO[1],
            StanceOption("yes_if", "Yes, with a condition", "Say yes on a condition",
                mustInclude = listOf("agrees on the user's condition"),
                input = InputMode.REQUIRED, inputHint = "Your condition"),
            Shared.CLARIFY,
        ))
        "A07" -> StanceGroup(behaviorId, "They're worried about you", listOf(
            REASSURE,
            StanceOption("honest", "Tell them honestly", "Tell them honestly how things are",
                mustInclude = listOf("says how things are, using the user's own details"),
                input = InputMode.REQUIRED, inputHint = "How things really are"),
            StanceOption("thanks_private", "Thanks, but I'd rather not go into it", "Thank them and say you'd rather not go into detail",
                mustInclude = listOf("thanks them for caring", "says the user would rather not go into detail")),
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
        ))
        "K08" -> StanceGroup(behaviorId, "They're apologising", listOf(
            StanceOption("accept", "Accept", "Accept the apology warmly", mustInclude = listOf("accepts their apology"), hasDecision = false),
            StanceOption("need_time", "I need a bit more time", "Say you need a bit more time",
                mustInclude = listOf("says the user needs more time"), mustAvoid = listOf("says everything is fine now")),
        ))
        else -> null
    }

    /** Offered alongside the usual options when guilt wording or a comparison is detected. */
    val PRESSURE_STANCES = listOf(
        StanceOption("feel_no_yield", "Acknowledge, don't give way", "Acknowledge how they feel without giving way",
            mustInclude = listOf("acknowledges how they feel"),
            mustAvoid = listOf("agrees to what they are pressing for", "apologises for failing the family"), apology = Apology.AVOID),
        StanceOption("give_way", "Give way", "Give them what they're asking for",
            mustInclude = listOf("agrees to what they are asking for"),
            commitments = listOf("will do what they are asking for")),
        StanceOption("uncomfortable", "Say it makes me uncomfortable", "Say calmly that being spoken to this way is uncomfortable",
            mustInclude = listOf("says that this way of putting it makes the user uncomfortable"),
            mustAvoid = listOf("throws blame back at them"), apology = Apology.AVOID),
        StanceOption("only_matter", "Stick to the request", "Respond only to the actual request, not the reproach",
            mustInclude = listOf("responds to the practical request"),
            mustAvoid = listOf("responds to the reproach", "apologises for failing the family"), apology = Apology.AVOID),
    )

    override val conflictStances = listOf(
        StanceOption("make_up", "Make up", "Say you want to put it behind you",
            mustInclude = listOf("says the user wants to make up"), mustAvoid = listOf("argues the point again")),
        StanceOption("say_upset", "Say what upset me", "Say what upset you",
            mustInclude = listOf("says what upset the user, using the user's own details"),
            input = InputMode.REQUIRED, inputHint = "What upset you"),
        StanceOption("stand_firm", "Stand by my decision", "Say calmly that your decision stands",
            mustInclude = listOf("says the user's decision stands"), mustAvoid = listOf("apologises for the decision"), apology = Apology.AVOID),
        StanceOption("cool_off", "Cool off, talk later", "Say you'd rather leave it for now and talk later",
            mustInclude = listOf("says the user wants to leave it for now and talk later"),
            mustAvoid = listOf("argues the point again")),
        StanceOption("call", "Talk on the phone", "Suggest talking on the phone instead",
            mustInclude = listOf("suggests talking on the phone"), mustAvoid = listOf("argues the point again")),
    )

    override fun eLevels(selection: Selection): List<Level> {
        val mismatch = when (selection.relationship) {
            IN_LAW -> listOf("lol no chance")
            else -> listOf("As previously stated, my decision is unchanged.", "lol no chance")
        }
        return listOf(
            Level("Contempt", "The reply mocks, insults, or shows contempt for the other person.",
                listOf("God, you're so needy.")),
            Level("Snaps back", "The reply snaps at the other person, dismisses their concern, or throws blame or guilt back at them.",
                listOf("Stop going on about it, it's my life.", "You always do this.")),
            Level("Closeness mismatch", "The closeness of the reply does not match the family relationship described in relationship_note: it is noticeably colder and more formal, or noticeably more flippant, than fits.",
                mismatch),
            Level("Scripted", "The reply does not engage with what the other person actually said or the care behind it: it is generic or scripted.",
                listOf("I appreciate your concern and will consider all options.")),
            Level("One odd phrase", "The reply answers the other person, its warmth fits the relationship, and it keeps the user's position; one phrase is slightly unnatural and a fluent speaker would likely reword it.",
                listOf("I have thought, Mum, and I'm staying here for now. I know you are missing me.")),
            Level("Fits", "The reply answers the other person, its warmth fits the relationship, and it keeps the user's position clearly without lecturing.",
                listOf("I have, Mum, and I'm staying here for now. I know you miss me.")),
        )
    }

    override val frictionExamples = listOf(
        listOf("Have you eaten?", "Are you coming home for Christmas?", "Ok.", "You should really call your aunt.", "The trains are never on time."),
        listOf("I worry about you so far away.", "It's been a hard week without hearing from you."),
        listOf("You said you'd call on Sunday and you didn't."),
        listOf("You never call.", "You only think about yourself.", "After everything we've done for you."),
        listOf("You're a disgrace to this family."),
        listOf("If you don't come home, don't expect to be in the will. I'll tell everyone why."),
    )

    override fun variants(selection: Selection) = "shorter" to "gentler"

    override fun styleRules(selection: Selection): String = buildString {
        append("Family chat rules:\n")
        append("- Use the address term the user already uses in this chat (Mum, Mom, Dad, a name). If none is visible, use none.\n")
        append("- When stating the user's own decision: acknowledge the care first, then state the decision. No lecturing, no old grievances, no point-by-point rebuttal.\n")
        append("- Never invent reassuring facts (ate, got home, feeling fine). Without the fact, reassure without specifics.\n")
        append("- Promise visits, calls or money only when the goal authorises it.\n")
        when (selection.relationship) {
            PARENT -> append("- To a parent or elder: full words, no abbreviations or slang, gentle tone; showing care is fine.\n")
            SIBLING -> append("- To a sibling: casual; teasing only if the chat already has it.\n")
            YOUNGER -> append("- To a younger relative: warm and clear.\n")
            IN_LAW -> append("- To a partner's family: polite, complete sentences, no teasing.\n")
            else -> {}
        }
        if (selection.conflict) append("- They are in a disagreement: no sarcasm, no \"always\" or \"never\", two or three sentences at most.\n")
    }

    override fun toneReading(selection: Selection, shortReply: Boolean, tense: Boolean): ToneRule? {
        val rel = selection.relationship
        // Matrix: never judged for parents, a partner's family, or when no relationship was chosen.
        if (!shortReply || !tense || rel == null || rel == PARENT || rel == IN_LAW) return null
        return ToneRule(DISPLEASURE.id, "Possible displeasure", "A very short reply right after a disagreement.")
    }
}
