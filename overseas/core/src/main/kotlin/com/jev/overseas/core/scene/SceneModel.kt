package com.jev.overseas.core.scene

/** The five presets the user chooses from. */
enum class Scene(val id: String, val label: String, val conflictLabel: String?) {
    WORK("work", "Work", "In a dispute"),
    ROMANCE("romance", "Romance", "In an argument"),
    FRIENDS("friends", "Friends", "Fallen out"),
    FAMILY("family", "Family", "Fallen out"),
    GENERAL("general", "General", null),
}

/** Who the other person is within a scene. [note] is the sentence given to the models. */
data class RelationshipType(
    val id: String,
    val scene: Scene,
    val label: String,
    val note: String,
)

/** Relative to what is ordinary for the relationship type. */
enum class Direction(val id: String, val label: String, val note: String) {
    CLOSER("closer", "Move closer", "The user wants to move one step closer than what is ordinary between them."),
    KEEP("keep", "Keep as it is", "The user wants to stay at what is ordinary between them, without moving closer."),
    MORE_DISTANCE("more_distance", "More distance", "The user wants more distance than what is ordinary between them."),
}

data class Selection(
    val scene: Scene,
    val relationship: RelationshipType? = null,
    val conflict: Boolean = false,
)

enum class Role {
    /** Something the other person is doing that normally calls for a reply. */
    ACTION,
    /** Thanks, acknowledgements, updates, sign-offs: a reply is optional. */
    PASSIVE,
    /** A signal shown as its own card (deadline, pressure, claimed agreement). */
    CUE,
}

/**
 * One yes/no question to Jev about the other person's latest turn.
 * [yes] and [no] are the criteria; they carry the examples.
 */
data class Behavior(
    val id: String,
    val label: String,
    val question: String,
    val yes: String,
    val no: String,
    val role: Role = Role.ACTION,
)

/**
 * Where a behaviour sits relative to the relationship's baseline. DEPENDS is a
 * matrix cell that says "depends on the relationship / the chat history / the
 * family's habits": the program cannot tell, so the user always chooses first.
 */
enum class Position { ABOVE, AT, BELOW, RARE, CUE, DEPENDS }

data class Reading(val position: Position, val note: String)

/** One level of a Score question. [short] is the label shown next to a number. */
data class Level(val short: String, val what: String, val examples: List<String> = emptyList())

enum class InputMode { NONE, OPTIONAL, REQUIRED }

/**
 * Whether a reply for a stance carries a short apology.
 * Decided by the scene, the relationship and the stance, not by a switch.
 */
enum class Apology(val wire: String) {
    /** One short courtesy apology is part of the goal (and checked), never an admission of fault. */
    EXPECTED("expected"),
    /** At most one short sorry, only where a person would naturally say it. */
    OPTIONAL("optional"),
    /** No apology: saying sorry would undercut the stance. */
    AVOID("avoid"),
}

/**
 * One thing the user can decide to do. Choosing it fixes the goal before any
 * reply is drafted: what the reply must do, must not do, and may promise.
 */
data class StanceOption(
    val id: String,
    val label: String,
    /** One editable line describing the goal. */
    val summary: String,
    val mustInclude: List<String> = emptyList(),
    val mustAvoid: List<String> = emptyList(),
    /** [mustAvoid] entries that no longer apply once the user supplies details. */
    val avoidUnlessDetail: List<String> = emptyList(),
    val commitments: List<String> = emptyList(),
    /** False when there is nothing to decide (a polite or ordinary reply): G is then not scored. */
    val hasDecision: Boolean = true,
    val input: InputMode = InputMode.NONE,
    val inputHint: String = "",
    /** The reply may only ask a question. */
    val clarifyOnly: Boolean = false,
    /** Choosing this means sending nothing. */
    val noReply: Boolean = false,
    val direction: Direction? = null,
    /** The user chose to accept responsibility, so admitting fault is not a violation. */
    val allowsFaultAdmission: Boolean = false,
    /** The stance itself includes saying how the user feels about the other person. */
    val allowsDeclaration: Boolean = false,
    /** Hard checks that only ask the user to look for this stance (see Goal.confirmChecks). */
    val confirmChecks: Set<String> = emptySet(),
    val apology: Apology = Apology.OPTIONAL,
    /** What the expected apology is for, as the reply would say it ("the delay"). */
    val apologyFor: String = "",
    /** Relationship id to a different apology level, for example OPTIONAL toward someone who reports to the user. */
    val apologyByRelationship: Map<String, Apology> = emptyMap(),
) {
    /** The stance is itself an apology ("Admit and apologise"): nothing is added, and "Don't apologise" is not offered. */
    val isApology: Boolean get() = mustInclude.any { it.startsWith("apologises") }

    fun apologyFor(relationshipId: String?): Apology = relationshipId?.let { apologyByRelationship[it] } ?: apology
}

data class StanceGroup(
    /** The behaviour this group answers. */
    val behaviorId: String,
    val title: String,
    val options: List<StanceOption>,
)

/** Everything one scene contributes. Shared pieces live in [Shared]. */
interface SceneSpec {
    val scene: Scene
    val relationships: List<RelationshipType>

    /** Behaviour and cue questions to ask for this selection. */
    fun behaviors(selection: Selection): List<Behavior>

    /** Tone questions to ask; which one is shown is decided after the answers arrive. */
    fun toneQuestions(selection: Selection): List<Behavior>

    /** Baseline reading of a detected behaviour, or null when the scene has nothing to say. */
    fun reading(behaviorId: String, selection: Selection, hits: Set<String>): Reading?

    /** Options for answering a behaviour, or null when it needs no stance of its own. */
    fun stances(behaviorId: String, selection: Selection, hits: Set<String>): StanceGroup?

    /** True when no reply may be drafted for this behaviour before the user chooses. */
    fun mustChoose(behaviorId: String, selection: Selection, reading: Reading?): Boolean

    /** The goal used when a reply can be drafted without asking the user. */
    fun defaultStance(behaviorId: String, selection: Selection): StanceOption?

    /** Behaviour IDs in the order used to pick which one the stance options answer. */
    val priority: List<String>

    /** Extra options offered when the conflict toggle is on. */
    val conflictStances: List<StanceOption>

    fun eLevels(selection: Selection): List<Level>

    /** Six lists of example messages, one per friction level. */
    val frictionExamples: List<List<String>>

    /** Labels of the two reply variants. */
    fun variants(selection: Selection): Pair<String, String>

    /** Scene-specific drafting rules, as prompt text. */
    fun styleRules(selection: Selection): String

    /** Which tone question, if any, applies after the answers are known. */
    fun toneReading(selection: Selection, shortReply: Boolean, tense: Boolean): ToneRule?

    /** Scene-specific sentences for the panel, given what was detected. */
    fun notices(selection: Selection, hits: Set<String>): List<String> = emptyList()
}

/** Which tone question to show and how to word the card. */
data class ToneRule(val questionId: String, val title: String, val detail: String)
