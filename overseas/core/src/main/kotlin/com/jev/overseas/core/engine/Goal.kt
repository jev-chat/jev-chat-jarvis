package com.jev.overseas.core.engine

import com.jev.overseas.core.scene.Apology
import com.jev.overseas.core.scene.Direction
import com.jev.overseas.core.scene.StanceOption

/**
 * What the reply is for. Fixed before any reply is drafted, so that the drafting
 * model writes inside it and Jev checks against it.
 */
data class Goal(
    val stanceId: String?,
    val stanceLabel: String,
    /** One line the user can read and edit. */
    val summary: String,
    val direction: Direction?,
    val mustInclude: List<String>,
    val mustAvoid: List<String>,
    /** The only promises the reply may make. */
    val commitments: List<String>,
    /** What the user typed for this goal. Treated as the user's own facts. */
    val userDetails: String?,
    /** False when there is nothing to decide; G is then not scored. */
    val hasDecision: Boolean,
    val clarifyOnly: Boolean,
    val allowsFaultAdmission: Boolean,
    val allowsDeclaration: Boolean,
    val version: Int,
    /**
     * Hard checks that only ask the user to look rather than block: a "yes" or an
     * unsure answer means "check before sending". Used where the user may well
     * want the thing (an own goal that admits fault) or must approve it (fault
     * admitted to a client).
     */
    val confirmChecks: Set<String> = emptySet(),
    /** Whether the reply carries a short apology, from the scene, relationship and stance. */
    val apology: Apology = Apology.OPTIONAL,
    val apologyFor: String? = null,
) {
    val userFacts: List<String>
        get() = if (userDetails.isNullOrBlank()) emptyList() else listOf("The user says: ${userDetails.trim()}")

    fun toState(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "summary" to summary,
        "must_include" to mustInclude,
        "must_avoid" to mustAvoid,
        "authorized_commitments" to commitments,
    ).apply {
        // What the user typed for a stance is part of what they want, and G is scored against it.
        if (stanceId != null && !userDetails.isNullOrBlank()) put("user_details", listOf(userDetails.trim()))
        put("apology", apology.wire)
        if (apology == Apology.EXPECTED && !apologyFor.isNullOrBlank()) put("apology_for", apologyFor)
        put("allows_fault_admission", allowsFaultAdmission)
    }
}

/** Switches that add a restriction to any goal. */
enum class GoalSwitch(val label: String, val avoid: String) {
    NO_NEW_PROMISES("No new promises", "makes a new promise"),
    NO_REASONS("Don't explain why", "gives a reason or an excuse"),
    NO_APOLOGY("Don't apologise", "apologises");

    companion object {
        /**
         * The switches that make sense for a stance. A switch that contradicts the
         * stance would block every draft: no "Don't apologise" on an apology,
         * no "No new promises" on a stance that promises, no "Don't explain why" on
         * an explanation.
         */
        fun applicable(stance: StanceOption?): List<GoalSwitch> = values().filter { sw ->
            stance == null || when (sw) {
                NO_APOLOGY -> !stance.isApology
                NO_NEW_PROMISES -> stance.commitments.isEmpty()
                NO_REASONS -> stance.mustInclude.none { it.startsWith("explains") }
            }
        }
    }
}

object GoalBuilder {

    /** The goal for a stance the user picked, with whatever details they typed. */
    fun fromStance(
        option: StanceOption,
        details: String? = null,
        switches: Set<GoalSwitch> = emptySet(),
        version: Int = 1,
        /** The relationship id, for stances whose apology differs by relationship. */
        relationshipId: String? = null,
    ): Goal {
        val detail = details?.trim()?.takeIf { it.isNotEmpty() }
        val active = switches.filter { it in GoalSwitch.applicable(option) }
        // "Don't apologise" is the user's override for this round; otherwise the stance decides.
        val apology = if (GoalSwitch.NO_APOLOGY in active) Apology.AVOID else option.apologyFor(relationshipId)
        val apologyFor = option.apologyFor.ifBlank { null }
        val avoid = option.mustAvoid
            .filterNot { detail != null && it in option.avoidUnlessDetail }
            .plus(active.map { it.avoid })
            .plus(if (apology == Apology.AVOID && !option.isApology) listOf(GoalSwitch.NO_APOLOGY.avoid) else emptyList())
            .distinct()
        val commitments = buildList {
            addAll(option.commitments)
            if (detail != null) add("what the user's own details say: $detail")
        }
        val summary = if (detail != null) "${option.summary} ($detail)" else option.summary
        // What the user typed must reach the reply, so it gets its own check.
        val include = buildList {
            addAll(option.mustInclude)
            // An expected apology is part of the goal, so it is drafted, checked and counted in G.
            if (apology == Apology.EXPECTED && !option.isApology) add(apologyItem(apologyFor))
            if (detail != null) add(detailItem(detail))
        }
        return Goal(
            stanceId = option.id,
            stanceLabel = option.label,
            summary = summary,
            direction = option.direction,
            mustInclude = include,
            mustAvoid = avoid,
            commitments = commitments,
            userDetails = detail,
            hasDecision = option.hasDecision,
            clarifyOnly = option.clarifyOnly,
            allowsFaultAdmission = option.allowsFaultAdmission,
            allowsDeclaration = option.allowsDeclaration,
            version = version,
            confirmChecks = option.confirmChecks,
            apology = if (option.isApology) Apology.EXPECTED else apology,
            apologyFor = apologyFor,
        )
    }

    /** The required item for what the user typed with a stance. */
    fun detailItem(detail: String) = "$DETAIL_PREFIX\"${detail.trim()}\""

    const val DETAIL_PREFIX = "uses the user's own detail: "

    /** A courtesy, checked as a required item; admits_fault still checks that it admits nothing. */
    fun apologyItem(about: String?) = if (about.isNullOrBlank()) "says sorry briefly" else "says sorry briefly for $about"

    /**
     * The goal after the user rewrote the summary line. Their words replace the
     * template: the old include and avoid lists may no longer match what they
     * mean, so only the switches are kept, plus one check that every point the
     * user wrote made it into the reply.
     */
    fun fromEditedSummary(previous: Goal?, summary: String, switches: Set<GoalSwitch> = emptySet()): Goal {
        val text = summary.trim()
        return Goal(
            stanceId = null,
            stanceLabel = "Your own goal",
            summary = text,
            // The previous stance's direction belonged to that stance, not to what the user wrote.
            direction = null,
            // Everything the user wrote should reach the reply; the check makes a dropped point visible.
            mustInclude = listOf("covers every point in the goal summary"),
            mustAvoid = switches.map { it.avoid },
            commitments = listOf("what the goal summary itself says the user will do"),
            userDetails = text,
            hasDecision = true,
            clarifyOnly = false,
            // The user wrote this themselves, so fault or feelings may be what they mean. The checks are
            // still asked, but only to make the user look before sending, never to block.
            allowsFaultAdmission = false,
            allowsDeclaration = false,
            version = (previous?.version ?: 0) + 1,
            confirmChecks = setOf("admits_fault", "unstated_declaration"),
            apology = if (GoalSwitch.NO_APOLOGY in switches) Apology.AVOID else Apology.OPTIONAL,
        )
    }
}
