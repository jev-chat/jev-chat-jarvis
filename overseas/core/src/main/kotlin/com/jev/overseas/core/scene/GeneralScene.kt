package com.jev.overseas.core.scene

import com.jev.overseas.core.scene.Shared.behavior

/**
 * General: an unclear relationship, or errands (landlords, agents, sellers,
 * customer service, tutors). No relationship-based reading is done here, and a
 * short reply is never given a tone reading.
 */
object GeneralScene : SceneSpec {

    override val scene = Scene.GENERAL

    val UNSPECIFIED = RelationshipType("unspecified", Scene.GENERAL, "Not sure",
        "The relationship between the two people is not known. Replies are polite and neutral, and assume neither closeness nor formality.")
    val ERRANDS = RelationshipType("errands", Scene.GENERAL, "Errands and services",
        "This is a practical exchange with someone such as a landlord, an agent, a seller, customer service, or a tutor. Replies are polite, clear and to the point; prices, times, terms and documents are ordinary topics.")

    override val relationships = listOf(UNSPECIFIED, ERRANDS)

    val B01 = behavior("B01", "States a price or terms", "state a price, a fee, or terms",
        "Such as \"Rent is £850 pcm, bills not included.\" or \"It's $40, pickup only.\".",
        "No price or terms (\"Is the flat still available?\").", Role.ACTION)
    val B02 = behavior("B02", "Asks you to pay", "ask the user to pay money or a deposit",
        "Such as \"Can you send the deposit today to hold it?\".",
        "They only state an amount (\"The deposit is one month's rent.\").")
    val B03 = behavior("B03", "Asks for documents or details", "ask the user for documents or personal details",
        "Such as \"Can you send a copy of your passport and proof of income?\".",
        "They will send something themselves (\"I'll send the contract over.\").")
    val B04 = behavior("B04", "Offers a time slot", "offer an appointment or a time slot",
        "Such as \"I can do a viewing Thursday at 5 or Saturday at 11.\".",
        "They ask the user for a time without offering one (\"When are you free?\").")
    val B05 = behavior("B05", "Refuses your request", "refuse a request the user made",
        "Such as \"Sorry, we can't refund opened items.\" or \"The landlord won't go lower.\".",
        "They have not refused (\"Let me check with the landlord.\").")
    val B06 = behavior("B06", "Pushes you to decide fast",
        "say the user must decide or pay quickly, or that someone else is interested",
        "Such as \"I've got two other people viewing today.\" or \"Offer ends tonight.\".",
        "No time pressure (\"Let me know when you've decided.\").")

    private val ERRAND_BEHAVIORS = listOf(B01, B02, B03, B04, B05, B06)

    override fun behaviors(selection: Selection): List<Behavior> =
        Shared.SHARED_BEHAVIORS +
            (if (selection.relationship == ERRANDS) ERRAND_BEHAVIORS else emptyList()) +
            listOf(Shared.HISTORY_NEEDED)

    override fun toneQuestions(selection: Selection): List<Behavior> = emptyList()

    override val priority = listOf(
        "K09", "B05", "B02", "B03", "B06", "B01", "B04", "K06", "K07", "K04", "K05", "K13", "K02", "K08", "K03", "K01",
    )

    override fun reading(behaviorId: String, selection: Selection, hits: Set<String>): Reading? = null

    private val DIRECT = setOf("K01", "K03", "K08", "K10", "K11", "K12")

    override fun mustChoose(behaviorId: String, selection: Selection, reading: Reading?): Boolean = behaviorId !in DIRECT

    override fun defaultStance(behaviorId: String, selection: Selection): StanceOption? = when (behaviorId) {
        "K01", "K03", "K08" -> StanceOption("polite", "Reply politely", "Reply politely to what they said",
            mustInclude = listOf("responds politely to what they said"),
            mustAvoid = listOf("states a fact about the user that the user did not give"), hasDecision = false)
        else -> null
    }

    private val TIME = listOf(
        StanceOption("time_ok", "That works", "Confirm the time works",
            mustInclude = listOf("confirms a time they offered"),
            commitments = listOf("will attend at the confirmed time"),
            input = InputMode.OPTIONAL, inputHint = "Which slot? (optional)"),
        StanceOption("time_no", "Can't make it", "Say that time doesn't work",
            mustInclude = listOf("says the user cannot make the time offered"),
            mustAvoid = listOf("confirms a time"), apology = Apology.EXPECTED, apologyFor = "the time not working"),
        StanceOption("other_time", "Suggest another time", "Suggest another time",
            mustInclude = listOf("suggests the user's alternative time"),
            input = InputMode.REQUIRED, inputHint = "When can you do?"),
    )

    override fun stances(behaviorId: String, selection: Selection, hits: Set<String>): StanceGroup? = when (behaviorId) {
        "K02" -> StanceGroup(behaviorId, "They're asking you something", listOf(
            Shared.ANSWER,
            StanceOption("find_out", "I'll check", "Say you'll check and come back",
                mustInclude = listOf("says the user will check"), mustAvoid = listOf("states the answer"),
                commitments = listOf("will check and come back")),
            StanceOption("rather_not", "Rather not say", "Say politely that you'd rather not answer",
                mustInclude = listOf("says the user would rather not answer"),
                mustAvoid = listOf("states a fact about the user that the user did not give")),
        ))
        "K04", "B04" -> StanceGroup(behaviorId, "They're proposing a time", TIME)
        "K05" -> StanceGroup(behaviorId, "They're asking a favour", listOf(
            StanceOption("yes", "Yes", "Say yes", mustInclude = listOf("agrees to what they ask"),
                commitments = listOf("will do what they ask")),
            StanceOption("no", "No", "Say you can't", mustInclude = listOf("says the user cannot do it"),
                mustAvoid = listOf("agrees to what they ask"), apology = Apology.EXPECTED, apologyFor = "not being able to help"),
            Shared.CLARIFY,
        ))
        "B01" -> StanceGroup(behaviorId, "They've stated a price or terms", listOf(
            StanceOption("accept", "Accept", "Accept the price or terms",
                mustInclude = listOf("accepts the price or terms they stated"),
                commitments = listOf("accepts the stated price or terms")),
            StanceOption("counter", "Counter or add a condition", "Make a counter-offer or state a condition",
                mustInclude = listOf("states the user's counter-offer or condition"),
                mustAvoid = listOf("accepts the price or terms as stated"),
                input = InputMode.REQUIRED, inputHint = "Your offer or condition", apology = Apology.AVOID),
            Shared.CLARIFY,
            StanceOption("decline", "No, thanks", "Say you're no longer interested",
                mustInclude = listOf("says the user is not going ahead"),
                mustAvoid = listOf("accepts the price or terms")),
        ))
        "B02" -> StanceGroup(behaviorId, "They're asking you to pay", listOf(
            StanceOption("agree_pay", "Agree to pay", "Agree to pay as asked",
                mustInclude = listOf("agrees to pay as asked"), commitments = listOf("will pay as asked")),
            StanceOption("not_yet", "Not yet, questions first", "Say you won't pay yet and ask what you need to know",
                mustInclude = listOf("says the user will not pay yet", "asks a question about the payment"),
                mustAvoid = listOf("agrees to pay"),
                input = InputMode.OPTIONAL, inputHint = "What do you need to know? (optional)", apology = Apology.AVOID),
            StanceOption("wont_pay", "Won't pay", "Say you won't pay this",
                mustInclude = listOf("says the user will not pay"), mustAvoid = listOf("agrees to pay"), apology = Apology.AVOID),
        ))
        "B03" -> StanceGroup(behaviorId, "They're asking for documents or details", listOf(
            StanceOption("provide", "Agree to provide", "Agree to send what they ask for",
                mustInclude = listOf("agrees to provide what they asked for"),
                mustAvoid = listOf("includes any personal data in the reply"),
                commitments = listOf("will send what they asked for")),
            StanceOption("ask_why", "Ask what it's for", "Ask what it is needed for before sending anything",
                mustInclude = listOf("asks what the documents or details are needed for"),
                mustAvoid = listOf("agrees to provide them"), clarifyOnly = true),
            StanceOption("wont_provide", "Won't provide", "Say you won't provide that",
                mustInclude = listOf("says the user will not provide that"), mustAvoid = listOf("agrees to provide it"), apology = Apology.AVOID),
        ))
        "B05" -> StanceGroup(behaviorId, "They refused your request", listOf(
            StanceOption("accept_refusal", "Accept it", "Accept their answer",
                mustInclude = listOf("accepts their answer"), hasDecision = false),
            StanceOption("push_once", "Ask once more", "Ask once more, with your reason",
                mustInclude = listOf("asks again, giving the user's reason"),
                input = InputMode.REQUIRED, inputHint = "Your reason"),
            StanceOption("other_way", "Ask for another option", "Ask whether there is another way",
                mustInclude = listOf("asks whether there is another option")),
            StanceOption("end", "End it", "Say you'll leave it there",
                mustInclude = listOf("says the user will leave it there")),
        ))
        "B06" -> StanceGroup(behaviorId, "They're pushing for a quick decision", listOf(
            StanceOption("need_time", "I need time", "Say you need time to decide",
                mustInclude = listOf("says the user needs time to decide"),
                mustAvoid = listOf("agrees to go ahead", "agrees to pay"), apology = Apology.AVOID),
            StanceOption("go_ahead", "Go ahead now", "Say you want to go ahead",
                mustInclude = listOf("says the user wants to go ahead"),
                commitments = listOf("will go ahead on the stated terms")),
            StanceOption("ask_first", "Questions first", "Ask what you need to know before deciding",
                mustInclude = listOf("asks a question about the offer"),
                mustAvoid = listOf("agrees to go ahead", "agrees to pay"), clarifyOnly = true,
                input = InputMode.OPTIONAL, inputHint = "What do you need to know? (optional)"),
        ))
        "K06", "K07" -> StanceGroup(behaviorId, "They have a complaint", Shared.complaintStances("Ask what exactly is wrong"))
        "K09" -> StanceGroup(behaviorId, "They've set a boundary", listOf(Shared.NO_REPLY, Shared.BRIEF_ACK))
        "K13" -> StanceGroup(behaviorId, "They turned you down", listOf(
            StanceOption("gracious", "Take it well", "Accept it politely",
                mustInclude = listOf("accepts what they said without pushing"), hasDecision = false),
        ))
        else -> null
    }

    override val conflictStances = emptyList<StanceOption>()

    override fun eLevels(selection: Selection): List<Level> = listOf(
        Level("Hostile", "The reply is insulting, sarcastic, or hostile toward the other person.",
            listOf("Took you long enough to reply.")),
        Level("Rude pressure", "The reply pressures or blames the other person, or makes demands in a rude way.",
            listOf("I need an answer now, I've been waiting for days.")),
        Level("Register mismatch", "The register of the reply clearly does not match the other person's messages: it is noticeably more casual or familiar, or noticeably more formal, than theirs.",
            listOf("sat works mate cheers x", "Dear Sir or Madam, I hereby confirm my attendance at the viewing scheduled for Saturday.")),
        Level("Unclear", "The reply leaves the other person unsure what the user wants or what happens next.",
            listOf("Either could work I guess.")),
        Level("One odd phrase", "The reply is polite and clear and matches the other person's register; one phrase is slightly unnatural and a fluent speaker would likely reword it.",
            listOf("Saturday at 11 is good for me, thank you. I will be present.")),
        Level("Fits", "The reply is polite and clear, matches the other person's register, and says what the user wants and what happens next.",
            listOf("Saturday at 11 works for me, thanks. See you then.")),
    )

    override val frictionExamples = listOf(
        listOf("The rent is due on the 1st.", "Sorry, we can't refund opened items.", "Can you send the deposit today?"),
        listOf("I was hoping to hear back sooner.", "That's disappointing."),
        listOf("You said the payment would arrive on Monday and it still hasn't."),
        listOf("You people never answer on time.", "You clearly don't care about your customers."),
        listOf("You're a liar and a cheat."),
        listOf("Pay by tonight or I'll post your details everywhere."),
    )

    override fun variants(selection: Selection) = "shorter" to "a little more polite"

    override fun styleRules(selection: Selection): String = buildString {
        append("General chat rules:\n")
        append("- Polite, clear, brief. The first sentence answers or states what the user wants.\n")
        append("- Match the formality of the other person's messages.\n")
        append("- No pet names, no teasing, no abbreviations or slang.\n")
        if (selection.relationship == ERRANDS) {
            append("- State times, amounts and quantities exactly as the user gave them. One matter per message.\n")
            append("- For a complaint: say what happened and what outcome is wanted. No emotional wording, no threats.\n")
        }
        append("- Never include the user's personal data (ID numbers, bank details, address) unless the user supplied it in this session and asked for it to be included.\n")
        append("- Never invent the user's identity, income, or schedule.\n")
    }

    override fun toneReading(selection: Selection, shortReply: Boolean, tense: Boolean): ToneRule? = null
}
