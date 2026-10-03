package com.jev.overseas.probe.dump

/**
 * Decides whether the screen shows the scripted test chat. Message text is
 * only written to disk when this passes; any other chat is saved as structure
 * only, so a tap in the wrong conversation cannot record private content.
 *
 * Passing needs one sentence that would not occur by chance, or two different
 * ordinary sentences from the script on the same screen.
 */
object TestChatGuard {

    data class Result(val specificMatches: Int, val genericMatches: Int) {
        val passed: Boolean get() = specificMatches >= 1 || genericMatches >= 2
    }

    private val specific = listOf(
        "this is a test chat for the reply assistant",
        "just to recap what we agreed on the call",
    )

    private val generic = listOf(
        "got it, thanks for helping",
        "can you send me the report by friday",
        "i'll check and let you know",
        "any update on this",
        "friday might be tight",
        "sounds good to me",
        "see you wednesday",
        "thanks, speak soon",
    )

    fun evaluate(texts: Iterable<String>): Result {
        val normalized = texts.map(::normalize)
        val specificHits = specific.count { marker -> normalized.any { it.contains(marker) } }
        val genericHits = generic.count { marker -> normalized.any { it.contains(marker) } }
        return Result(specificHits, genericHits)
    }

    /** Lowercase, straight apostrophes, single spaces. */
    internal fun normalize(text: String): String =
        text.lowercase()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("\\s+"), " ")
            .trim()
}
