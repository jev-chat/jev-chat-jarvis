package com.jev.overseas.core.engine

import com.jev.overseas.core.model.MessageKind
import com.jev.overseas.core.model.MessageRecord
import com.jev.overseas.core.model.Speaker

enum class Sender(val wire: String) { USER("user"), OTHER("other"), UNKNOWN("unknown") }

/** One chat message as the models see it. Media is a placeholder, never a guess at its content. */
data class ChatMessage(
    val sender: Sender,
    val text: String,
    val readable: Boolean = true,
    val replyTo: ChatMessage? = null,
    /** What kind of media this is when it is not plain text: photo, video, document, voice, deleted, unsupported. */
    val media: String? = null,
) {
    fun toState(): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>("from" to sender.wire, "text" to text)
        if (replyTo != null) m["replying_to"] = mapOf("from" to replyTo.sender.wire, "text" to replyTo.text)
        return m
    }
}

/**
 * The messages read from the chat, oldest first, split into the other person's
 * latest turn (what is analysed) and everything before it (context).
 */
class Conversation(all: List<ChatMessage>, windowSize: Int = DEFAULT_WINDOW) {

    val messages: List<ChatMessage> = all.takeLast(windowSize)

    /** Every message the other person sent after the user's last message. Empty when the user wrote last. */
    val latestTurn: List<ChatMessage>

    /** Everything before [latestTurn]. */
    val earlier: List<ChatMessage>

    /** How many messages the user has sent since the other person last wrote. */
    val userTrailing: Int = messages.takeLastWhile { it.sender == Sender.USER }.size

    /** True when the newest message's sender could not be read, so the latest turn is not known. */
    val senderUncertain: Boolean = messages.lastOrNull()?.sender == Sender.UNKNOWN

    init {
        // Everything after the user's last message, when the newest is theirs. A message of unknown sender
        // inside it stays (marked unknown) instead of cutting their turn short; unknown ones at the
        // start of the run go to the earlier context.
        val turn = if (messages.lastOrNull()?.sender == Sender.OTHER) {
            messages.takeLastWhile { it.sender != Sender.USER }.dropWhile { it.sender == Sender.UNKNOWN }
        } else emptyList()
        latestTurn = turn
        earlier = messages.dropLast(turn.size)
    }

    val isEmpty: Boolean get() = messages.isEmpty()

    /** False when the latest turn is only photos, voice notes or deleted messages. */
    val latestTurnReadable: Boolean get() = latestTurn.any { it.readable }

    /**
     * Their latest turn is only files Jev cannot open (a PDF, a photo without a
     * caption, a sticker, a video). The messages around a file usually say what it
     * is for, so it is still analysed. A voice note or a deleted message is content
     * Jev cannot know, and is not.
     */
    val latestTurnFilesOnly: Boolean
        get() = latestTurn.isNotEmpty() && !latestTurnReadable && latestTurn.all { it.media in FILE_MEDIA }

    val latestText: String get() = latestTurn.filter { it.readable }.joinToString("\n") { it.text }

    val features: Features by lazy { Features.of(this) }

    /** Abbreviations found anywhere in the window, with their expansions. */
    val glossary: Map<String, String> by lazy { Lexicon.glossaryFor(messages.map { it.text }) }

    /** "UK", "US", or null when the user's own messages do not show it. */
    val userSpelling: String? by lazy { Spelling.detect(messages.filter { it.sender == Sender.USER }.map { it.text }) }

    /** True when the other person's readable messages are mostly in Latin script. */
    val looksEnglish: Boolean by lazy {
        val text = messages.filter { it.readable && it.sender != Sender.USER }.joinToString(" ") { it.text }
        val letters = text.count { it.isLetter() }
        letters == 0 || text.count { it in 'a'..'z' || it in 'A'..'Z' }.toDouble() / letters >= 0.8
    }

    companion object {
        const val DEFAULT_WINDOW = 24
        val FILE_MEDIA = setOf("photo", "document", "unsupported")
        private const val MAX_CHARS = 700

        /** The wider window used by "Read further back". */
        const val WIDE_WINDOW = 48

        /** Chat messages only; date dividers and system notices are left out. */
        fun fromRows(rows: List<MessageRecord>, window: Int = DEFAULT_WINDOW): Conversation =
            Conversation(rows.filter { it.isChatMessage }.map(::toMessage), window)

        private fun toMessage(row: MessageRecord): ChatMessage {
            val sender = when (row.speaker) {
                Speaker.SELF -> Sender.USER
                Speaker.OTHER -> Sender.OTHER
                else -> Sender.UNKNOWN
            }
            val caption = row.text?.trim()?.takeIf { it.isNotEmpty() }
            val (text, readable) = when (row.kind) {
                MessageKind.TEXT -> (caption ?: "") to (caption != null)
                MessageKind.IMAGE -> (if (caption != null) "[photo] $caption" else "[photo]") to (caption != null)
                MessageKind.VOICE -> "[voice message]" to false
                MessageKind.DELETED -> "[deleted message]" to false
                MessageKind.DOCUMENT -> (if (caption != null) "[document] $caption" else "[document]") to (caption != null)
                else -> (caption ?: "[unsupported message]") to (caption != null)
            }
            val media = when (row.kind) {
                MessageKind.TEXT -> null
                MessageKind.IMAGE -> "photo"
                MessageKind.VOICE -> "voice"
                MessageKind.DELETED -> "deleted"
                MessageKind.DOCUMENT -> "document"
                else -> if (caption == null) "unsupported" else null
            }
            val quote = row.quote?.let { q ->
                val from = when (q.from) {
                    Speaker.SELF -> Sender.USER
                    Speaker.OTHER -> Sender.OTHER
                    else -> Sender.UNKNOWN
                }
                ChatMessage(from, (q.text ?: "").take(MAX_CHARS))
            }
            return ChatMessage(sender, text.take(MAX_CHARS), readable, quote, media)
        }
    }
}

/**
 * Things about the latest turn that can be counted or compared. They are computed
 * here, in code, and given to the model as plain words: Jev is not reliable at
 * counting or comparing lengths.
 */
data class Features(
    /** The whole latest turn is one very short text message. */
    val shortReply: Boolean,
    val latestWords: Int,
    val shape: String,
    val comparedWithUsual: String?,
) {
    fun toState(): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>("latest_reply_shape" to shape)
        if (comparedWithUsual != null) m["compared_with_their_earlier_messages"] = comparedWithUsual
        return m
    }

    companion object {
        fun of(conversation: Conversation): Features {
            val turn = conversation.latestTurn
            val texts = turn.filter { it.readable }.map { it.text.trim() }
            val words = texts.sumOf { wordCount(it) }
            val single = turn.size == 1 && texts.size == 1
            val short = single && words <= 3 && texts[0].length <= 15
            val shape = when {
                turn.isEmpty() -> "no new message from the other person"
                texts.isEmpty() -> "only media or deleted messages"
                single -> buildString {
                    append(if (words == 1) "one word" else "$words words")
                    append(
                        when (texts[0].last()) {
                            '.' -> ", ends with a full stop"
                            '!' -> ", ends with an exclamation mark"
                            '?' -> ", ends with a question mark"
                            else -> ", no punctuation at the end"
                        }
                    )
                }
                else -> "${turn.size} messages in a row, $words words in total"
            }
            val usual = conversation.earlier
                .filter { it.sender == Sender.OTHER && it.readable }
                .map { wordCount(it.text) }
                .sorted()
            val compared = if (usual.size >= 3 && words > 0) {
                val median = usual[usual.size / 2]
                when {
                    words * 3 <= median -> "much shorter than their earlier messages, which are typically about $median words"
                    words >= median * 3 && median > 0 -> "much longer than their earlier messages"
                    else -> "about the same length as their earlier messages"
                }
            } else null
            return Features(short, words, shape, compared)
        }

        private fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }
}

/** British or American spelling, judged from the user's own words. */
object Spelling {
    private val UK_STRICT = Regex("\\b(colour|favour|behaviour|organis\\w*|realis\\w*|apologis\\w*|recognis\\w*|prioritis\\w*|centre|mum|whilst|cheque|programme)\\b", RegexOption.IGNORE_CASE)
    private val US_STRICT = Regex("\\b(color|favor|behavior|organiz\\w*|realiz\\w*|apologiz\\w*|recogniz\\w*|prioritiz\\w*|center|mom|gotten)\\b", RegexOption.IGNORE_CASE)

    fun detect(texts: List<String>): String? {
        val joined = texts.joinToString(" ")
        val uk = UK_STRICT.findAll(joined).count()
        val us = US_STRICT.findAll(joined).count()
        return when {
            uk > us -> "UK"
            us > uk -> "US"
            else -> null
        }
    }
}
