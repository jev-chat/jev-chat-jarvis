package com.jev.overseas.core

import com.jev.overseas.core.engine.ChatMessage
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.Sender
import com.jev.overseas.core.engine.Spelling
import com.jev.overseas.core.model.MessageKind
import com.jev.overseas.core.model.MessageRecord
import com.jev.overseas.core.model.Quote
import com.jev.overseas.core.model.Speaker
import com.jev.overseas.core.testing.chat
import com.jev.overseas.core.testing.me
import com.jev.overseas.core.testing.them
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {

    private fun row(speaker: Speaker, kind: MessageKind, text: String?, quote: Quote? = null) =
        MessageRecord(speaker, kind, text, "10:00", false, quote, null, 0, 10, false, true)

    @Test fun latestTurnIsEverythingAfterTheUsersLastMessage() {
        val c = chat(them("hi"), me("hey"), them("can you do friday?"), them("by 5"), them("thx"))
        assertEquals(listOf("can you do friday?", "by 5", "thx"), c.latestTurn.map { it.text })
        assertEquals(listOf("hi", "hey"), c.earlier.map { it.text })
        assertEquals(0, c.userTrailing)
        assertEquals("can you do friday?\nby 5\nthx", c.latestText)
    }

    @Test fun userWroteLast() {
        val c = chat(them("hi"), me("hey"), me("you there?"))
        assertTrue(c.latestTurn.isEmpty())
        assertEquals(2, c.userTrailing)
        assertEquals(3, c.earlier.size)
    }

    @Test fun unknownLastSender() {
        val c = chat(them("hi"), ChatMessage(Sender.UNKNOWN, "??"))
        assertTrue(c.senderUncertain)
        assertTrue(c.latestTurn.isEmpty())
        assertFalse(chat(them("hi")).senderUncertain)
    }

    @Test fun latestTurnOfOnlyMediaIsUnreadable() {
        val c = Conversation.fromRows(listOf(
            row(Speaker.SELF, MessageKind.TEXT, "look"),
            row(Speaker.OTHER, MessageKind.IMAGE, null),
            row(Speaker.OTHER, MessageKind.VOICE, null),
            row(Speaker.OTHER, MessageKind.DELETED, null),
        ))
        assertEquals(listOf("[photo]", "[voice message]", "[deleted message]"), c.latestTurn.map { it.text })
        assertFalse(c.latestTurnReadable)
        assertEquals("", c.latestText)
        assertEquals("only media or deleted messages", c.features.shape)
    }

    @Test fun captionedImageIsReadable() {
        val c = Conversation.fromRows(listOf(row(Speaker.OTHER, MessageKind.IMAGE, " is this the one? ")))
        assertEquals("[photo] is this the one?", c.latestTurn.single().text)
        assertTrue(c.latestTurnReadable)
    }

    @Test fun quoteIsMappedAndKeptSeparate() {
        val c = Conversation.fromRows(listOf(
            row(Speaker.SELF, MessageKind.TEXT, "I'll send it Friday"),
            row(Speaker.OTHER, MessageKind.TEXT, "ok", Quote(Speaker.SELF, "I'll send it Friday")),
        ))
        val m = c.latestTurn.single()
        assertEquals("ok", m.text)
        assertEquals(Sender.USER, m.replyTo!!.sender)
        assertEquals(mapOf("from" to "user", "text" to "I'll send it Friday"), m.toState()["replying_to"])
    }

    @Test fun longTextIsCutAt700() {
        val c = Conversation.fromRows(listOf(row(Speaker.OTHER, MessageKind.TEXT, "a".repeat(900), Quote(Speaker.OTHER, "b".repeat(900)))))
        assertEquals(700, c.latestTurn.single().text.length)
        assertEquals(700, c.latestTurn.single().replyTo!!.text.length)
    }

    @Test fun windowKeepsTheLast24() {
        val msgs = (1..30).map { if (it % 2 == 0) me("m$it") else them("m$it") }
        val c = Conversation(msgs)
        assertEquals(24, c.messages.size)
        assertEquals("m7", c.messages.first().text)
    }

    /** A row the adapter could not classify is still a message with a sender; dividers and notices are not. */
    @Test fun fromRowsDropsDividersAndSystemRows() {
        val c = Conversation.fromRows(listOf(
            row(Speaker.SYSTEM, MessageKind.DATE_DIVIDER, "Today"),
            row(Speaker.SYSTEM, MessageKind.SYSTEM_NOTICE, "Messages are end-to-end encrypted"),
            row(Speaker.OTHER, MessageKind.UNKNOWN, "???"),
            row(Speaker.OTHER, MessageKind.TEXT, "hey"),
        ))
        assertEquals(listOf("???", "hey"), c.messages.map { it.text })
    }

    @Test fun anUnreadableFileFromThemIsTheirTurn() {
        val c = Conversation.fromRows(listOf(
            row(Speaker.SELF, MessageKind.TEXT, "can you send the contract?"),
            row(Speaker.OTHER, MessageKind.UNKNOWN, null),
        ))
        assertEquals(1, c.latestTurn.size)
        assertEquals("[unsupported message]", c.latestTurn[0].text)
        assertFalse(c.latestTurnReadable)
        assertTrue(c.latestTurnFilesOnly)
        assertEquals(0, c.userTrailing)
    }

    @Test fun unknownSpeakerRowBecomesUnknownSender() {
        val c = Conversation.fromRows(listOf(row(Speaker.OTHER, MessageKind.TEXT, "a"), row(Speaker.UNKNOWN, MessageKind.TEXT, "b")))
        assertTrue(c.senderUncertain)
    }

    @Test fun looksEnglish() {
        assertTrue(chat(them("Can you send it?")).looksEnglish)
        assertFalse(chat(them("你明天有空吗")).looksEnglish)
        assertFalse(chat(them("Ты придёшь завтра?")).looksEnglish)
        assertTrue(chat(them("😂😂")).looksEnglish)
        assertTrue(chat(them("")).looksEnglish)
        // The user's own messages are not judged.
        assertTrue(chat(me("你好"), them("hi")).looksEnglish)
    }

    @Test fun shortReplyBoundaries() {
        assertTrue(chat(them("k")).features.shortReply)
        assertTrue(chat(them("sounds good man")).features.shortReply) // 3 words, 15 characters
        assertFalse(chat(them("sounds good mate")).features.shortReply) // 16 characters
        assertFalse(chat(them("ok see you then")).features.shortReply) // 4 words
        assertFalse(chat(them("ok"), them("k")).features.shortReply) // two messages
    }

    @Test fun shapeWording() {
        assertEquals("one word, no punctuation at the end", chat(them("k")).features.shape)
        assertEquals("one word, ends with a full stop", chat(them("Fine.")).features.shape)
        assertEquals("3 words, ends with a question mark", chat(them("are you coming?")).features.shape)
        assertEquals("2 words, ends with an exclamation mark", chat(them("great news!")).features.shape)
        assertEquals("2 messages in a row, 3 words in total", chat(them("hey"), them("you free?")).features.shape)
        assertEquals("no new message from the other person", chat(me("hi")).features.shape)
    }

    @Test fun comparisonNeedsThreeEarlierMessages() {
        val two = chat(them("one two three four five six"), them("one two three four five six"), me("ok"), them("k"))
        assertNull(two.features.comparedWithUsual)
        val three = chat(them("one two three four five six"), them("one two three four five six"), them("one two three four five six"), me("ok"), them("k"))
        assertTrue(three.features.comparedWithUsual!!.startsWith("much shorter"))
        val same = chat(them("a b c"), them("a b c"), them("a b c"), me("ok"), them("x y z"))
        assertTrue(same.features.comparedWithUsual!!.startsWith("about the same"))
        assertEquals("about the same length as their earlier messages", same.features.toState()["compared_with_their_earlier_messages"])
    }

    @Test fun spelling() {
        assertEquals("UK", Spelling.detect(listOf("my favourite colour", "see you at the centre")))
        assertEquals("US", Spelling.detect(listOf("my favorite color")))
        assertNull(Spelling.detect(listOf("hello there")))
        assertNull(Spelling.detect(listOf("colour", "color")))
        assertEquals("UK", chat(me("I'll organise it"), them("ok")).userSpelling)
        // Only the user's words count.
        assertNull(chat(them("I'll organise it"), me("ok")).userSpelling)
    }

    /** A message whose sender could not be read, inside their turn, no longer cuts the turn short. */
    @Test fun unknownSenderInsideTheirTurnKeepsTheTurn() {
        val c = Conversation(listOf(
            com.jev.overseas.core.testing.me("can you do friday?"),
            com.jev.overseas.core.testing.them("hmm"),
            ChatMessage(Sender.UNKNOWN, "[unsupported message]", readable = false),
            com.jev.overseas.core.testing.them("actually no, sorry"),
        ))
        assertEquals(3, c.latestTurn.size)
        assertEquals(listOf("can you do friday?"), c.earlier.map { it.text })
        // Unknown right after the user's message belongs to the context, not to their turn.
        val d = Conversation(listOf(
            com.jev.overseas.core.testing.me("ok"),
            ChatMessage(Sender.UNKNOWN, "?", readable = true),
            com.jev.overseas.core.testing.them("see you then"),
        ))
        assertEquals(listOf("see you then"), d.latestTurn.map { it.text })
    }
}
