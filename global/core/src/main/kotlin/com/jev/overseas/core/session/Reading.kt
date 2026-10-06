package com.jev.overseas.core.session

import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.Sender

/** Reads the chat that is on screen now. Implemented on the Android side; blocking, called off the main thread. */
fun interface ChatReader {
    /** For analysis: the newest message and, where the reader can, some screens of earlier context. */
    fun readCurrent(): ReadResult

    /**
     * For checks (has the chat changed, is it the same chat): the bottom screen
     * only. Its fingerprint must match [readCurrent]'s, which is built from the
     * newest messages.
     */
    fun readLatest(): ReadResult = readCurrent()

    /** "Read further back": like [readCurrent], with a wider window (about 10 screens, 48 messages). */
    fun readMore(): ReadResult = readCurrent()

    /** Drop anything kept from earlier reads (called when the user closes the panel). */
    fun forget() {}
}

sealed class ReadResult {
    /**
     * [chatId] and [fingerprint] are hashes. They stay in memory, are never logged
     * and never sent to a model.
     */
    data class Ok(
        val conversation: Conversation,
        val report: ReadReport,
        val chatId: String,
        val fingerprint: String,
    ) : ReadResult()

    /** The chat list, search, a profile page: not a one-to-one conversation. */
    object NotConversation : ReadResult()

    /** Still moving after the retries. */
    object Unstable : ReadResult()

    /** WhatsApp is not in front. */
    object Unavailable : ReadResult()

    /** The chat is scrolled up and the newest messages could not be reached. Reading older ones would answer the wrong turn. */
    object NotAtLatest : ReadResult()

    /** Another chat or page came to the front while earlier messages were being read. */
    object ChatChanged : ReadResult()

    /** A group chat. Jev reads one-to-one chats only. */
    object GroupChat : ReadResult()
}

/** What was read, for the panel. Counts only; no chat text. */
data class ReadReport(
    val messages: Int,
    val latestTurn: Int,
    val screens: Int = 1,
    val continuous: Boolean = true,
    val unreadable: Int = 0,
    val stopReason: String = "current screen only",
    /** How many times the list was scrolled down to reach the newest message first. */
    val scrolledToLatest: Int = 0,
    /** False when the reader scrolled up for context and could not confirm it got back to the bottom. */
    val returnedToBottom: Boolean = true,
    /** True when the earlier messages came from a read in the last 15 minutes and only the bottom screen was read now. */
    val cached: Boolean = false,
)

/** What changed between two reads of the same chat, as the banner says it. */
object ChatDiff {

    /**
     * "2 new messages from them" when the new read is the old one plus messages
     * from the other person; otherwise a general sentence, since an edit, a
     * deletion or a message from the user cannot be counted reliably.
     */
    fun describe(old: Conversation, new: Conversation): ChatChange {
        val added = newFromThem(old, new)
        return if (added != null && added > 0) {
            ChatChange(if (added == 1) "1 new message from them" else "$added new messages from them", added)
        } else {
            ChatChange(GENERAL, null)
        }
    }

    const val GENERAL = "The chat changed since this was read"

    private fun newFromThem(old: Conversation, new: Conversation): Int? {
        val last = old.messages.lastOrNull() ?: return null
        val index = new.messages.indexOfLast { it.sender == last.sender && it.text == last.text }
        if (index < 0) return null
        val after = new.messages.drop(index + 1)
        return if (after.all { it.sender == Sender.OTHER }) after.size else null
    }
}

data class ChatChange(val message: String, val newFromThem: Int?, val otherChat: Boolean = false)

/**
 * The scene and relationship the user picked for each chat, keyed by the chat
 * id (a hash, never the name). Kept on the device so a known chat is analysed
 * at once and a new chat asks first.
 */
interface SelectionMemory {
    fun get(chatId: String): com.jev.overseas.core.scene.Selection?
    fun put(chatId: String, selection: com.jev.overseas.core.scene.Selection)
}
