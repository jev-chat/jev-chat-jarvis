package com.jev.overseas.core.scene

import com.jev.overseas.core.scene.Shared.behavior
import com.jev.overseas.core.scene.Shared.question

/**
 * Romance: from a first match to an established partner, and exes.
 *
 * Three layers. Detection is literal and the same at every stage. Interpretation
 * compares the behaviour with what is ordinary at the stage. Above the baseline
 * the user picks a direction first; at the baseline a reply can be drafted
 * directly. The assistant never declares feelings on the user's behalf.
 */
object RomanceScene : SceneSpec {

    override val scene = Scene.ROMANCE

    private const val PREFIX = "This is a chat between two people with a romantic connection. "

    val JUST_CONNECTED = RelationshipType("just_connected", Scene.ROMANCE, "Just connected",
        PREFIX + "They have only just connected, for example on a dating app. Ordinary between them: friendly, polite talk; no flirting yet and no saying they miss each other.")
    val TALKING = RelationshipType("talking", Scene.ROMANCE, "Talking stage",
        PREFIX + "They are in the early, flirtatious stage before dating. Ordinary between them: light, mutual flirting; no confession of feelings and no \"I love you\".")
    val DATING = RelationshipType("dating", Scene.ROMANCE, "Dating",
        PREFIX + "They are dating. Ordinary between them: closeness, saying they miss each other and wanting to meet; saying \"I love you\" is not yet ordinary.")
    val ESTABLISHED = RelationshipType("established", Scene.ROMANCE, "Partner",
        PREFIX + "They are established partners. Ordinary between them: full intimacy; missing each other, \"I love you\" and pet names.")
    val EX_FRIENDS = RelationshipType("ex_friends", Scene.ROMANCE, "Ex, still friends",
        "This is a chat between two people who used to be a couple and are now friends. Ordinary between them: chatting like friends; no flirting and no saying they miss each other.")
    val EX_DISTANT = RelationshipType("ex_distant", Scene.ROMANCE, "Ex, rarely talk",
        "This is a chat between two people who used to be a couple and now rarely talk. Ordinary between them: polite, brief contact; reaching out at all is unusual.")

    override val relationships = listOf(JUST_CONNECTED, TALKING, DATING, ESTABLISHED, EX_FRIENDS, EX_DISTANT)

    const val UNSPECIFIED_NOTE = PREFIX + "The stage of the relationship is not known."
    const val CONFLICT_NOTE = " They are currently in an argument or not speaking, possibly offline."

    private val EARLY = setOf(JUST_CONNECTED, TALKING)
    private val COUPLE = setOf(DATING, ESTABLISHED)
    private val EX = setOf(EX_FRIENDS, EX_DISTANT)

    // ------------------------------------------------------------------ behaviours

    val R01 = behavior("R01", "Everyday chat", "share everyday news or ask how the user is",
        "Such as \"Guess what happened at work\" or \"How was your day?\".",
        "A serious topic (\"We need to talk.\"), or a message that is only about feelings for the user.")
    val R02 = behavior("R02", "Asks about you", "ask the user a question about the user",
        "Such as \"What do you do?\", \"How was your weekend?\" or \"wbu?\".",
        "No question to the user (\"lol nice\").")
    val R03 = behavior("R03", "Shares a feeling", "say how they feel about something, without blaming the user for it",
        "Such as \"ugh today was awful\" or \"I felt really alone last night.\".",
        "They state no feeling, they blame the user (\"You left me alone all night.\"), or they only say that they miss, love, or are thinking of the user.")
    val R04 = behavior("R04", "Everyday plan or favour",
        "propose an everyday plan, ask about an arrangement, or ask the user to do an everyday favour",
        "Such as \"Dinner Friday?\" or \"Can you grab milk?\".",
        "No plan, arrangement or favour, or a decision about the relationship itself (\"Should we move in together?\").")
    val R05 = behavior("R05", "Flirts", "compliment the user or tease the user in a flirtatious way",
        "Such as \"you're kind of cute when you're nerdy\", \"can't stop thinking about last night\" or \"stop, you're making me blush\".",
        "Friendly but not flirtatious (\"haha that's funny\"). Saying they miss or are thinking of the user, with no compliment and no teasing, does not count.")
    val R06 = behavior("R06", "Says they miss you", "say they miss the user or are thinking of the user",
        "Such as \"miss you\", \"thinking of you\", \"I've been thinking about you all day\" or \"wish you were here\".",
        "No such statement (\"How was your day?\").")
    val R23 = behavior("R23", "Says they love you", "say they love the user",
        "Such as \"love you\", \"I love you\" or \"ily\".",
        "They love something else (\"love that for you\", \"I love that bar\"), or say nothing of the kind.")
    val R07 = behavior("R07", "Wants to meet", "suggest meeting, calling, or doing something together",
        "Such as \"we should grab a drink sometime\", \"free Thursday?\" or \"can I see you?\".",
        "No suggestion to meet or call (\"I love that bar\", \"wish you were here\").")
    val R08 = behavior("R08", "Wants more contact", "say they want to talk again, stay in touch, or be friends",
        "Such as \"Can we talk?\", \"I'd like us to stay friends.\" or \"It's been ages, how are you?\".",
        "A practical matter only (\"When can I pick up my stuff?\").")
    val R09 = behavior("R09", "Asks what you two are",
        "ask what the two of them are, or ask the user to define or change the relationship",
        "Such as \"so what are we?\", \"Are we exclusive?\" or \"what are you looking for on here?\".",
        "An ordinary question (\"what are you up to tonight?\").")
    val R10 = behavior("R10", "Wants to get back together",
        "say they want to get back together, or that breaking up was a mistake",
        "Such as \"I think we made a mistake.\", \"Can we try again?\" or \"I want to try again.\".",
        "They miss something else (\"I miss the dog.\"), or only say they miss or love the user without asking to get back together.")
    val R11 = behavior("R11", "Has a complaint", "complain about something the user did or did not do",
        "Such as \"I waited for your call last night.\", \"You said you'd be home by 7.\" or \"I just wish you'd pick up sometimes.\".",
        "They describe their own feeling without blaming the user (\"I felt really alone last night.\").")
    val R12 = behavior("R12", "Wants an explanation", "ask the user to explain something the user did",
        "Such as \"Why didn't you tell me?\" or \"Where were you last night?\".",
        "An ordinary question (\"What time are you home?\").")
    val R13 = behavior("R13", "Wants reassurance",
        "ask for reassurance about the user's feelings or about the relationship",
        "Such as \"Are we ok?\" or \"Do you even still want this?\".",
        "A question about the user's wellbeing (\"Are you ok?\").")
    val R14 = behavior("R14", "A decision for you both",
        "ask the user to decide or agree on something that affects both of them beyond today",
        "Such as \"Should we book the trip?\", \"Do you want to meet my parents?\" or \"I think we should move in together.\".",
        "An everyday choice (\"Pizza or Thai tonight?\").")
    val R15 = behavior("R15", "Apologises", "apologise or try to make up",
        "Such as \"I'm sorry about earlier.\" or \"Can we start over?\".",
        "A passing courtesy (\"Sorry, running 10 mins late.\").")
    val R16 = behavior("R16", "A practical matter to sort out",
        "raise a practical matter the two of them still need to sort out, such as belongings, money, a lease, children, or pets",
        "Such as \"When can I pick up my stuff?\" or \"You still owe me for the deposit.\".",
        "No practical matter (\"Hope you're doing ok.\").")
    val R17 = behavior("R17", "Brings up the past",
        "bring up blame or grievances about things that happened in the past, not about the current topic",
        "Such as \"You never cared about anything but work.\" or \"Just like last Christmas.\".",
        "They complain only about something current (\"You said you'd call last night.\").")
    val R18 = behavior("R18", "Sets a boundary",
        "say they need space, or ask the user to stop contacting them or to stop raising a topic",
        "Such as \"I need some space tonight.\" or \"Please stop texting me.\".",
        "They end the chat for an ordinary reason (\"I'm going to bed, night.\").")
    val R19 = behavior("R19", "Not interested",
        "say they are not interested in the user romantically, are seeing someone, or want to be just friends",
        "Such as \"I think we're better as friends\" or \"I'm actually seeing someone\".",
        "They are only busy (\"I'm not sure I'm free this week\").")
    val R20 = behavior("R20", "Wants to end or pause",
        "say they want to end, pause, or reconsider the relationship",
        "Such as \"I can't do this anymore.\" or \"Maybe we should take a break.\".",
        "They cancel a plan (\"I can't do dinner anymore, something came up.\").")
    val R21 = behavior("R21", "Ending the chat", "signal that they are ending the chat for now",
        "Such as \"anyway gotta go\", \"night!\" or \"ttyl\".",
        "A short pause (\"brb\"), or a bare acknowledgement such as \"k\" or \"ok\".", Role.PASSIVE)
    val R22 = behavior("R22", "Turns you down",
        "turn down a suggestion from the user without offering another time or option",
        "Such as \"I'm pretty busy this week\" or \"can't sorry\", after the user suggested something.",
        "They decline but offer an alternative (\"can't Friday, how about Sunday?\"), or the user suggested nothing.")

    val LOYALTY_TEST = behavior("loyalty_test", "Ties it to whether you care",
        "say that the user's answer or action would prove whether the user loves or cares about them",
        "Such as \"If you loved me you'd come.\".",
        "A wish with no such test (\"I'd love it if you came.\").", Role.CUE)
    val ULTIMATUM = behavior("ultimatum", "Ultimatum",
        "say the relationship will end, or the user will be punished, unless the user does what they ask",
        "Such as \"If you go out tonight, we're done.\".",
        "A strong preference with no such consequence (\"I'd really rather you stayed in tonight.\").", Role.CUE)

    val LOW_EFFORT = question("tone.low_effort", "Low-effort reply",
        "Is the latest message from the other person a short, low-effort reply that gives the user nothing to respond to?",
        "Such as \"lol\", \"haha\", \"nice\", \"cool\", \"k\", \"yeah\" or \"ok\" on its own.",
        "It engages or asks something back (\"haha that's so funny, what happened next?\").", Role.CUE)
    val DISPLEASURE = question("tone.displeasure", "Possible displeasure",
        "Is the latest message from the other person a short reply of a kind that often signals displeasure between two people who are romantically involved?",
        "Such as \"k\", \"k.\", \"Fine.\", \"Whatever.\", \"Sure.\", \"ok.\", \"Do what you want.\", \"I'm fine.\", \"nvm\", \"forget it\" or \"Wow.\".",
        "A short reply that is clearly warm or neutral (\"ok!\", \"okay sounds good\", \"kk\", \"haha ok\", \"sure, see you at 7\").", Role.CUE)

    private val ALL = listOf(
        R01, R02, R03, R04, R05, R06, R23, R07, R08, R09, R10, R11, R12, R13, R14, R15, R16, R17, R18, R19, R20, R21, R22,
        Shared.K11, Shared.AGREEMENT_CLAIMED, Shared.REPEATED, LOYALTY_TEST, ULTIMATUM, Shared.HISTORY_NEEDED,
    )

    override fun behaviors(selection: Selection) = ALL
    override fun toneQuestions(selection: Selection) = listOf(LOW_EFFORT, DISPLEASURE)

    override val priority = listOf(
        "R18", "R19", "R20", "R10", "R09", "R13", "R14", "R23", "R11", "R12", "R17", "R15", "R16",
        "R06", "R05", "R07", "R08", "R04", "R02", "R03", "R22", "R01",
    )

    // ------------------------------------------------------------------ baseline readings

    override fun reading(behaviorId: String, selection: Selection, hits: Set<String>): Reading? {
        val rel = selection.relationship ?: return null
        fun above(note: String) = Reading(Position.ABOVE, note)
        fun at(note: String = "Ordinary at this stage.") = Reading(Position.AT, note)
        return when (behaviorId) {
            "R06" -> when (rel) {
                JUST_CONNECTED -> above("Saying they miss you is early for someone you've only just connected with.")
                TALKING -> above("Saying they miss you goes a step beyond light flirting: interest is growing.")
                EX_FRIENDS, EX_DISTANT -> above("An ex saying they miss you may want to get closer again.")
                else -> at()
            }
            "R23" -> when (rel) {
                JUST_CONNECTED -> above("Saying they love you is very early.")
                TALKING -> above("Saying they love you at this stage is a confession.")
                DATING -> above("A first \"I love you\" is a big step at this stage.")
                ESTABLISHED -> at()
                else -> above("A strong signal from an ex.")
            }
            "R05" -> when (rel) {
                JUST_CONNECTED -> above("Flirting: they are showing interest.")
                EX_FRIENDS, EX_DISTANT -> above("Flirting from an ex goes beyond where you two are now.")
                else -> at()
            }
            "R07" -> when (rel) {
                JUST_CONNECTED -> above("They are suggesting a first meeting.")
                TALKING -> above("They want to move things forward.")
                EX_FRIENDS, EX_DISTANT -> above("They want to be in contact again.")
                else -> at("An everyday plan at this stage.")
            }
            "R08" -> when (rel) {
                in COUPLE -> at()
                TALKING -> at()
                else -> above("They are asking for more contact than is ordinary between you now.")
            }
            "R09" -> when (rel) {
                JUST_CONNECTED -> above("They are asking what you are looking for.")
                TALKING -> above("They want to define what this is.")
                DATING -> above("They want to know where this is going.")
                ESTABLISHED -> Reading(Position.RARE, "Unusual between established partners: they may want reassurance.")
                else -> above("From an ex, this may be leading up to getting back together.")
            }
            "R10" -> above("They want to get back together. Only you can decide that.")
            "R01" -> if (rel == EX_DISTANT) Reading(Position.RARE, "You rarely talk, so reaching out at all is unusual.") else at()
            "R11" -> when (rel) {
                JUST_CONNECTED -> Reading(Position.RARE, "A complaint this early is unusual.")
                TALKING -> Reading(Position.AT, "A complaint at this stage shows they already have expectations of you.")
                in EX -> Reading(Position.AT, "From an ex, this is usually about the past or something unfinished.")
                else -> at("Complaints come up between partners. Treat it as a complaint.")
            }
            else -> null
        }
    }

    private val ALWAYS_CHOOSE = setOf(
        "R02", "R04", "R07", "R08", "R09", "R10", "R11", "R12", "R13", "R14", "R15", "R16", "R17", "R18", "R19", "R20",
    )

    override fun mustChoose(behaviorId: String, selection: Selection, reading: Reading?): Boolean =
        behaviorId in ALWAYS_CHOOSE || reading?.position == Position.ABOVE ||
            (selection.relationship == null && behaviorId in setOf("R05", "R06", "R23"))

    override fun defaultStance(behaviorId: String, selection: Selection): StanceOption? = when (behaviorId) {
        "R06" -> StanceOption("in_kind", "Reply in kind", "Reply warmly, the way you two normally talk",
            mustInclude = listOf("responds warmly to what they said"), hasDecision = false, allowsDeclaration = true)
        "R23" -> StanceOption("in_kind", "Say it back", "Say it back, the way you two normally do",
            mustInclude = listOf("responds lovingly to what they said"), hasDecision = false, allowsDeclaration = true)
        "R05" -> StanceOption("in_kind", "Play along", "Reply in the same playful tone",
            mustInclude = listOf("responds playfully to what they said"), hasDecision = false, allowsDeclaration = true)
        "R03" -> SUPPORT
        "R01" -> StanceOption("chat", "Chat back", "Reply naturally to what they shared",
            mustInclude = listOf("responds to what they shared"),
            mustAvoid = listOf("states a fact about the user's day or life that the user did not give"), hasDecision = false)
        "R22" -> StanceOption("gracious", "Take it well", "Accept it gracefully and leave the door open",
            mustInclude = listOf("accepts what they said without pushing"),
            mustAvoid = listOf("pushes them to change their mind"), hasDecision = false)
        else -> null
    }

    // ------------------------------------------------------------------ stances

    private val SUPPORT = StanceOption("support", "Be supportive", "Show you understand and are on their side",
        mustInclude = listOf("responds to the feeling they described"),
        mustAvoid = listOf("gives advice they did not ask for"), hasDecision = false)

    private val HOLD = StanceOption("hold", "Don't answer that yet", "Reply kindly without giving an answer on this yet",
        mustInclude = listOf("responds kindly to what they said"),
        mustAvoid = listOf("says the user misses or loves them", "gives a decision about the relationship"),
        hasDecision = false)

    private fun direction(
        d: Direction, label: String, summary: String,
        include: List<String>, avoid: List<String> = emptyList(),
        decision: Boolean = true, declares: Boolean = false,
        input: InputMode = InputMode.NONE, hint: String = "", commitments: List<String> = emptyList(),
    ) = StanceOption("dir_${d.id}", label, summary, include, avoid, emptyList(), commitments, decision, input, hint,
        direction = d, allowsDeclaration = declares)

    private fun intimacy(behaviorId: String): List<StanceOption> = when (behaviorId) {
        "R06" -> listOf(
            direction(Direction.CLOSER, "Say it back", "Say you miss them too",
                listOf("says the user misses them too"), declares = true),
            direction(Direction.KEEP, "Kind, without saying it back", "Respond kindly without saying you miss them",
                listOf("responds kindly to what they said"),
                listOf("says the user misses them", "suggests meeting"), decision = false),
            direction(Direction.MORE_DISTANCE, "Polite, move on", "Reply politely and steer away from the subject",
                listOf("replies politely"),
                listOf("says the user misses them", "invites further contact"), decision = false),
        )
        "R23" -> listOf(
            direction(Direction.CLOSER, "Say it back", "Say you love them too",
                listOf("says the user loves them too"), declares = true),
            direction(Direction.KEEP, "Warm, not saying it back", "Respond warmly without saying it back; you may say you need time",
                listOf("responds warmly to what they said"),
                listOf("says the user loves them"), decision = false),
            direction(Direction.MORE_DISTANCE, "Say I don't feel the same", "Say kindly that you don't feel the same way",
                listOf("says the user does not feel the same way"),
                listOf("says the user loves them")),
        )
        "R05" -> listOf(
            direction(Direction.CLOSER, "Flirt back", "Flirt back at about their level",
                listOf("responds in a flirtatious way"), decision = false, declares = true),
            direction(Direction.KEEP, "Friendly, no flirting", "Reply in a friendly way without flirting back",
                listOf("replies in a friendly way"), listOf("flirts back"), decision = false),
            direction(Direction.MORE_DISTANCE, "Change the subject", "Reply briefly and change the subject",
                listOf("replies briefly"), listOf("flirts back", "compliments them"), decision = false),
        )
        "R07" -> listOf(
            direction(Direction.CLOSER, "Yes, let's fix a time", "Say yes and fix a time",
                listOf("agrees to meet", "asks or proposes when"),
                input = InputMode.OPTIONAL, hint = "When are you free? (optional)",
                commitments = listOf("will meet at a time to be agreed")),
            direction(Direction.KEEP, "I'd like to, another time", "Say you'd like to, but not at that time",
                listOf("says the user would like to, but not at that time"),
                listOf("agrees to the time they proposed"),
                input = InputMode.OPTIONAL, hint = "When could you? (optional)"),
            direction(Direction.MORE_DISTANCE, "Not for now", "Say you'd rather not meet for now",
                listOf("says the user does not want to meet for now"),
                listOf("agrees to meet", "suggests another time")).copy(apology = Apology.EXPECTED, apologyFor = "saying no for now"),
        )
        "R08" -> listOf(
            direction(Direction.CLOSER, "Yes, let's talk more", "Say you'd like to be in touch more",
                listOf("says the user would like to be in touch more")),
            direction(Direction.KEEP, "Sure, as things are", "Say that's fine, keeping things as they are now",
                listOf("agrees to stay in touch as things are now"),
                listOf("says the user misses them", "suggests getting closer")),
            direction(Direction.MORE_DISTANCE, "I'd rather not", "Say you'd rather not be in more contact",
                listOf("says the user does not want more contact"),
                listOf("agrees to talk more")),
        )
        "R09" -> listOf(
            direction(Direction.CLOSER, "I want something serious", "Say you want this to be a real relationship",
                listOf("says the user wants a committed relationship with them"), declares = true),
            direction(Direction.KEEP, "Take it slowly", "Say you'd like to take it slowly",
                listOf("says the user wants to take it slowly"),
                listOf("says the user wants to be exclusive", "says the user is not interested")),
            direction(Direction.MORE_DISTANCE, "Just friends", "Say you see them as a friend",
                listOf("says the user sees them as a friend"),
                listOf("says the user has romantic feelings for them")),
        )
        "R10" -> listOf(
            direction(Direction.CLOSER, "I'd like to try again", "Say you'd like to try again",
                listOf("says the user wants to try again"), declares = true),
            direction(Direction.KEEP, "I need time", "Say you need time to think, or would rather talk in person",
                listOf("says the user needs time or wants to talk in person"),
                listOf("agrees to get back together", "refuses to get back together")),
            direction(Direction.MORE_DISTANCE, "I don't want to", "Say you don't want to get back together",
                listOf("says the user does not want to get back together"),
                listOf("leaves the door open to trying again")),
        )
        else -> emptyList()
    }

    private val ARRANGEMENT = listOf(
        StanceOption("yes", "Yes", "Say yes to what they propose",
            mustInclude = listOf("agrees to what they propose"),
            commitments = listOf("will do what they propose")),
        StanceOption("no", "No", "Say you can't or would rather not",
            mustInclude = listOf("says the user cannot or would rather not"),
            mustAvoid = listOf("agrees to what they propose"), apology = Apology.EXPECTED, apologyFor = "saying no"),
        StanceOption("other", "Another time or way", "Suggest another time or another way",
            mustInclude = listOf("suggests the user's alternative"),
            input = InputMode.REQUIRED, inputHint = "What would work for you?"),
        StanceOption("later", "Confirm later", "Say you'll confirm later",
            mustInclude = listOf("says the user will confirm later"),
            mustAvoid = listOf("agrees to what they propose", "refuses what they propose"),
            commitments = listOf("will confirm later")),
    )

    override fun stances(behaviorId: String, selection: Selection, hits: Set<String>): StanceGroup? {
        val reading = reading(behaviorId, selection, hits)
        val above = reading?.position == Position.ABOVE || selection.relationship == null
        return when (behaviorId) {
            "R06" -> StanceGroup(behaviorId, "They say they miss you", intimacy("R06") + HOLD)
            "R23" -> StanceGroup(behaviorId, "They say they love you", intimacy("R23") + HOLD)
            "R05" -> StanceGroup(behaviorId, "They're flirting", intimacy("R05"))
            "R07" -> if (above) StanceGroup(behaviorId, "They want to meet", intimacy("R07"))
            else StanceGroup(behaviorId, "They're suggesting a plan", ARRANGEMENT)
            "R08" -> StanceGroup(behaviorId, "They want more contact", intimacy("R08"))
            "R09" -> StanceGroup(behaviorId, "They ask what you two are", intimacy("R09") + HOLD)
            "R10" -> StanceGroup(behaviorId, "They want to get back together", intimacy("R10"))
            "R04" -> StanceGroup(behaviorId, "They're suggesting a plan", ARRANGEMENT)
            "R02" -> StanceGroup(behaviorId, "They ask about you", listOf(
                Shared.ANSWER,
                StanceOption("answer_ask_back", "Answer and ask back", "Answer with your details and ask them something back",
                    mustInclude = listOf("answers their question using the user's details", "asks them a question back"),
                    input = InputMode.REQUIRED, inputHint = "Your answer, in a few words"),
                StanceOption("keep_light", "Keep it light", "Reply lightly without going into detail",
                    mustInclude = listOf("replies to their question in a light way"),
                    mustAvoid = listOf("states a fact about the user that the user did not give"), hasDecision = false),
            ))
            "R03" -> StanceGroup(behaviorId, "They're sharing how they feel", listOf(
                SUPPORT,
                StanceOption("ask_what_happened", "Ask what happened", "Ask them what happened",
                    mustInclude = listOf("asks what happened"), mustAvoid = listOf("gives advice"), hasDecision = false),
                StanceOption("brief", "Keep it brief", "Acknowledge it briefly",
                    mustInclude = listOf("acknowledges how they feel"), hasDecision = false),
            ))
            "R11", "R12" -> StanceGroup(behaviorId, "They have a complaint", Shared.complaintStances())
            "R13" -> StanceGroup(behaviorId, "They want reassurance", listOf(
                StanceOption("reassure", "Reassure them", "Tell them things are fine between you and that you care",
                    mustInclude = listOf("reassures them about the relationship"), allowsDeclaration = true),
                StanceOption("talk_properly", "Let's talk properly", "Say you'd like to talk about it properly",
                    mustInclude = listOf("says the user wants to talk about it properly"),
                    mustAvoid = listOf("says everything is fine")),
                StanceOption("not_now", "Not now", "Say you can't get into it right now and will talk later",
                    mustInclude = listOf("says the user cannot talk about it right now"),
                    commitments = listOf("will talk about it later"), apology = Apology.EXPECTED, apologyFor = "not being able to talk right now"),
            ))
            "R14" -> StanceGroup(behaviorId, "A decision for you both", listOf(
                StanceOption("agree", "Agree", "Say yes", mustInclude = listOf("agrees to what they propose"),
                    commitments = listOf("will do what they propose")),
                StanceOption("disagree", "Disagree", "Say you don't want that",
                    mustInclude = listOf("says the user does not want that"), mustAvoid = listOf("agrees to what they propose")),
                StanceOption("think", "I need to think", "Say you need time to think about it",
                    mustInclude = listOf("says the user needs time to think"),
                    mustAvoid = listOf("agrees to what they propose", "refuses what they propose")),
                StanceOption("not_ready", "Not ready yet", "Say you're not ready for that yet",
                    mustInclude = listOf("says the user is not ready yet"),
                    mustAvoid = listOf("agrees to what they propose", "says the user will never want it")),
            ))
            "R15" -> StanceGroup(behaviorId, "They're apologising", listOf(
                StanceOption("accept", "Accept", "Accept the apology", mustInclude = listOf("accepts their apology")),
                StanceOption("accept_talk", "Accept, talk later", "Accept the apology and say you'd still like to talk about it",
                    mustInclude = listOf("accepts their apology", "says the user would still like to talk about it")),
                StanceOption("need_time", "I need a bit more time", "Say you need a bit more time",
                    mustInclude = listOf("says the user needs more time"), mustAvoid = listOf("says everything is fine now")),
                StanceOption("not_accept", "Don't accept", "Say you're not ready to accept that",
                    mustInclude = listOf("says the user does not accept the apology yet"), apology = Apology.AVOID),
            ))
            "R16" -> StanceGroup(behaviorId, "A practical matter", listOf(
                StanceOption("agree_arrangement", "Agree", "Agree to their arrangement",
                    mustInclude = listOf("agrees to the arrangement they propose"),
                    commitments = listOf("will do what the arrangement requires")),
                StanceOption("other_arrangement", "Propose another arrangement", "Propose a different arrangement",
                    mustInclude = listOf("proposes the user's arrangement"),
                    input = InputMode.REQUIRED, inputHint = "Your arrangement"),
                StanceOption("practical_only", "Only the practical matter", "Deal with the practical matter and nothing else",
                    mustInclude = listOf("responds to the practical matter"),
                    mustAvoid = listOf("talks about feelings or the relationship"),
                    input = InputMode.OPTIONAL, inputHint = "Your answer on the practical matter (optional)", apology = Apology.AVOID),
            ))
            "R17" -> StanceGroup(behaviorId, "They're bringing up the past", listOf(
                StanceOption("respond", "Respond to it", "Respond to what they brought up",
                    mustInclude = listOf("responds to what they brought up"),
                    input = InputMode.OPTIONAL, inputHint = "What do you want to say? (optional)"),
                StanceOption("not_that", "Don't take that up", "Say you don't want to go over the past",
                    mustInclude = listOf("says the user does not want to go over the past"),
                    mustAvoid = listOf("argues about the past")),
                Shared.POLITE_CLOSE,
            ))
            "R18" -> StanceGroup(behaviorId, "They've set a boundary", listOf(Shared.NO_REPLY, Shared.BRIEF_ACK))
            "R19" -> StanceGroup(behaviorId, "They're not interested", listOf(Shared.POLITE_CLOSE, Shared.NO_REPLY))
            "R20" -> StanceGroup(behaviorId, "They want to end or pause", listOf(
                StanceOption("work_on_it", "I want to work on this", "Say you want to work on the relationship",
                    mustInclude = listOf("says the user wants to work on the relationship"), allowsDeclaration = true),
                StanceOption("agree_end", "Agree to end or pause", "Agree to end it or take a break",
                    mustInclude = listOf("agrees to end or pause the relationship")),
                StanceOption("talk_in_person", "Talk in person", "Ask to talk in person or by phone",
                    mustInclude = listOf("asks to talk in person or by phone"),
                    mustAvoid = listOf("agrees to end the relationship", "argues about it in the message")),
                StanceOption("need_time_think", "I need time", "Say you need time to think",
                    mustInclude = listOf("says the user needs time to think"),
                    mustAvoid = listOf("agrees to end the relationship")),
            ))
            else -> null
        }
    }

    override val conflictStances = listOf(
        StanceOption("make_up", "Make up", "Say you want to make up",
            mustInclude = listOf("says the user wants to make up"), mustAvoid = listOf("argues the point again")),
        StanceOption("apologise", "Apologise", "Apologise for your part",
            mustInclude = listOf("apologises for the user's part"), allowsFaultAdmission = true,
            input = InputMode.OPTIONAL, inputHint = "What are you apologising for? (optional)"),
        StanceOption("stand_firm", "Stand by my view", "Say calmly that you still see it your way",
            mustInclude = listOf("says the user still sees it their way"), mustAvoid = listOf("apologises"), apology = Apology.AVOID),
        StanceOption("say_upset", "Say what upset me", "Say what upset you",
            mustInclude = listOf("says what upset the user, using the user's own details"),
            input = InputMode.REQUIRED, inputHint = "What upset you", apology = Apology.AVOID),
        StanceOption("cool_off", "Cool off, talk later", "Say you want to cool off and talk later",
            mustInclude = listOf("says the user wants to cool off and talk later"),
            mustAvoid = listOf("argues the point again"), commitments = listOf("will talk later")),
        StanceOption("in_person", "Not by text", "Say you'd rather talk in person or by phone",
            mustInclude = listOf("says the user would rather talk in person or by phone"),
            mustAvoid = listOf("argues the point again")),
    )

    // ------------------------------------------------------------------ scoring and drafting

    override fun eLevels(selection: Selection): List<Level> {
        val rel = selection.relationship
        val mismatch = when (rel) {
            JUST_CONNECTED, TALKING -> listOf("I can't stop thinking about you, I think you might be the one.", "Noted.")
            in EX -> listOf("Come over whenever, you know where the key is babe.", "Your message has been received.")
            else -> listOf("Ok. Noted.", "I apologise for the oversight and will arrange an alternative.")
        }
        val best = when (rel) {
            JUST_CONNECTED, TALKING -> listOf("Ha, worth it though? Which bit of the Lakes did you do?")
            in EX -> listOf("Saturday morning works. I'll have everything boxed up by the door.", "That's kind of you to say. I hope you're doing well.")
            else -> listOf("I'm sorry I missed our call last night and left you waiting.", "I love that you want to. I'm not there yet though.")
        }
        return listOf(
            Level("Contempt", "The reply mocks, insults, or shows contempt for the other person, or is crude when nothing in the chat invites it.",
                listOf("Oh my god, it was one call. Get over it.", "Still playing the victim, I see.")),
            Level("Pressure or blame", "The reply pressures or blames the other person: it makes them feel guilty, belittles what they feel, demands a response, or drags up old arguments.",
                listOf("You're overreacting.", "Funny how you care now, you never did before.")),
            Level("Warmth mismatch", "The warmth of the reply does not match the relationship described in relationship_note: it is noticeably more intimate, or noticeably colder and more formal, than fits.",
                mismatch),
            Level("Doesn't engage", "The reply does not engage with what the other person actually said: it is generic, scripted, overdone, or gives them nothing to respond to.",
                listOf("That sounds nice! What are your other hobbies?", "I sincerely apologise for missing our scheduled call.", "I'm the worst, I'm so so sorry, please please forgive me.", "nice")),
            Level("One odd phrase", "The reply picks up on what the other person said and its warmth fits the relationship; one phrase is slightly unnatural and a fluent speaker would likely reword it.",
                listOf("I'm sorry I missed our call last night and caused you to wait.")),
            Level("Fits", "The reply picks up on what the other person said, its warmth fits the relationship described in relationship_note, and it is the right length and register for their chat.",
                best),
        )
    }

    override val frictionExamples = listOf(
        listOf("What time are you home?", "I'd rather stay in tonight.", "I need some space tonight.", "I think we're better as friends."),
        listOf("I felt really alone last night.", "I'm a bit sad about how today went."),
        listOf("You said you'd call and you didn't.", "I waited for your call last night."),
        listOf("You always do this.", "You never think about me.", "You obviously don't care."),
        listOf("You're pathetic.", "You're such a selfish idiot."),
        listOf("You'll regret this, I'll make sure of it.", "I'll send those photos to everyone."),
    )

    override fun variants(selection: Selection) = when (selection.relationship) {
        JUST_CONNECTED, TALKING -> "lighter" to "more direct"
        in EX -> "shorter" to "a little warmer"
        else -> "shorter" to "more acknowledging"
    }

    override fun styleRules(selection: Selection): String = buildString {
        append("Romance chat rules:\n")
        append("- No therapy voice and no customer-service voice.\n")
        append("- One apology at most, about the specific thing. No self-abasement.\n")
        append("- Do not tell the other person what they really feel. Do not give advice when they are only sharing a feeling.\n")
        append("- No jokes or teasing when they are complaining or seem displeased.\n")
        append("- Pet names and sign-offs such as \"x\" only if the user's own earlier messages use them.\n")
        append("- Do not declare feelings, say the user misses or loves them, or decide anything about the relationship unless the goal says so.\n")
        when (selection.relationship) {
            JUST_CONNECTED -> append("- Just connected: one or two sentences, pick up what they said, add one easy question, no run of questions, no comments on appearance, no pet names.\n")
            TALKING -> append("- Talking stage: light; teasing no stronger than theirs; never ask why they were slow to reply.\n")
            DATING -> append("- Dating: the user may say they want to see them and are happy; no tone of a long-established couple.\n")
            ESTABLISHED -> append("- Partners: fully affectionate within the user's own habits; an apology names the specific thing.\n")
            EX_FRIENDS -> append("- Ex, now friends: write as a friend would; no relitigating the past; no flirting.\n")
            EX_DISTANT -> append("- Ex, rarely in touch: polite and brief; no relitigating the past; no flirting.\n")
            else -> {}
        }
        if (selection.conflict) {
            append("- They are in an argument: no jokes, no \"always\" or \"never\", no point-by-point rebuttal, two or three sentences at most.\n")
        }
    }

    override fun toneReading(selection: Selection, shortReply: Boolean, tense: Boolean): ToneRule? {
        if (!shortReply) return null
        // Matrix: an ex's short reply is ordinary and never judged, even when things are tense
        // (the question bank's "any stage" note does not apply to exes).
        if (selection.relationship in EX) return null
        val displeasure = ToneRule(DISPLEASURE.id, "Possible displeasure",
            "A very short reply of a kind that often signals displeasure between partners.")
        val lowEffort = ToneRule(LOW_EFFORT.id, "Low engagement",
            "A very short reply that gives you nothing to respond to.")
        if (tense) return displeasure
        return when (selection.relationship) {
            JUST_CONNECTED, TALKING -> lowEffort
            DATING, ESTABLISHED -> displeasure
            else -> null
        }
    }
}
