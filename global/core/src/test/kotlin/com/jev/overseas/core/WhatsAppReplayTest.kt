package com.jev.overseas.core

import com.jev.overseas.core.dump.DumpJson
import com.jev.overseas.core.dump.DumpReader
import com.jev.overseas.core.model.MessageKind.DATE_DIVIDER
import com.jev.overseas.core.model.MessageKind.DELETED
import com.jev.overseas.core.model.MessageKind.IMAGE
import com.jev.overseas.core.model.MessageKind.SYSTEM_NOTICE
import com.jev.overseas.core.model.MessageKind.TEXT
import com.jev.overseas.core.model.MessageKind.VOICE
import com.jev.overseas.core.model.PageKind
import com.jev.overseas.core.model.ScreenRead
import com.jev.overseas.core.model.ScreenReadText
import com.jev.overseas.core.model.Speaker.OTHER
import com.jev.overseas.core.model.Speaker.SELF
import com.jev.overseas.core.model.Speaker.SYSTEM
import com.jev.overseas.core.whatsapp.WhatsAppAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Replays the recordings of the scripted test chat (WhatsApp 2.26.38.73)
 * through the adapter. The expectations in this file are the test script itself;
 * the golden files pin the full output of every recording.
 */
class WhatsAppReplayTest {

    private val dumps = File(System.getProperty("jev.fixtures"), "screendumps/whatsapp-2.26.38.73")
    private val golden = File("src/test/resources/golden/whatsapp-2.26.38.73")
    private val adapter = WhatsAppAdapter()

    private fun read(name: String): ScreenRead = adapter.read(DumpReader.read(File(dumps, name).readText()))

    private data class Expected(val speaker: Any, val kind: Any, val text: String?)

    private fun assertRows(read: ScreenRead, expected: List<Expected>) {
        assertEquals(
            expected.joinToString("\n") { "${it.speaker} ${it.kind} ${it.text}" },
            read.rows.joinToString("\n") { "${it.speaker} ${it.kind} ${it.text}" },
        )
    }

    @Test fun topOfChatMatchesTheScript() {
        val read = read("dump-0005.json")
        assertEquals(PageKind.CONVERSATION, read.page)
        assertTrue(read.stable)
        assertFalse("top of the chat: nothing older", read.canScrollToOlder)
        assertTrue(read.canScrollToNewer)
        assertRows(read, listOf(
            Expected(SYSTEM, DATE_DIVIDER, "今天"),
            Expected(SYSTEM, SYSTEM_NOTICE, read.rows[1].text),
            Expected(SYSTEM, SYSTEM_NOTICE, read.rows[2].text),
            Expected(OTHER, TEXT, "1"),
            Expected(OTHER, TEXT, "Hi, this is a test chat for the reply assistant."),
            Expected(SELF, TEXT, "Got it, thanks for helping."),
            Expected(OTHER, TEXT, "Can you send me the report by Friday?"),
            Expected(SELF, TEXT, "I'll check and let you know."),
            Expected(OTHER, TEXT, "OK"),
            Expected(OTHER, TEXT, "OK"),
            Expected(SELF, TEXT, "OK"),
            Expected(OTHER, TEXT, "Any update on this?"),
            Expected(SELF, TEXT, "Friday might be tight."),
        ))
        val quoteFromOther = read.rows[11].quote!!
        assertEquals(SELF, quoteFromOther.from)
        assertEquals("I'll check and let you know.", quoteFromOther.text)
        val quoteFromSelf = read.rows[12].quote!!
        assertEquals(OTHER, quoteFromSelf.from)
        assertEquals("Can you send me the report by Friday?", quoteFromSelf.text)
        assertTrue("last row touches the list's bottom edge", read.rows.last().clipped)
        assertTrue(read.rows.all { it.complete })
    }

    @Test fun bottomOfChatMatchesTheScript() {
        val read = read("dump-0004.json")
        assertTrue(read.stable)
        assertTrue(read.canScrollToOlder)
        assertFalse("bottom of the chat: nothing newer", read.canScrollToNewer)
        assertRows(read, listOf(
            Expected(OTHER, IMAGE, null),
            Expected(OTHER, VOICE, null),
            Expected(OTHER, TEXT, "Just to recap what we agreed on the call. First, the draft goes to the client on Monday. " +
                "Second, I will send you the figures tomorrow morning. Third, we review everything together on " +
                "Wednesday afternoon. Let me know if I missed anything."),
            Expected(SELF, TEXT, "Sounds good to me."),
            Expected(OTHER, TEXT, "See you Wednesday"),
            Expected(SELF, DELETED, read.rows[5].text),
            Expected(OTHER, TEXT, "Thanks, speak soon."),
        ))
        assertEquals("0:02", read.rows[1].durationLabel)
        assertTrue(read.rows[4].edited)
        assertFalse(read.rows[3].edited)
        assertEquals("06:57", read.rows[6].timeLabel)
        assertNull(read.rows[6].quote)
        assertTrue(read.rows.all { it.complete })
    }

    @Test fun twoIdenticalMessagesStayTwoRows() {
        val oks = read("dump-0005.json").rows.filter { it.text == "OK" }
        assertEquals(listOf(OTHER, OTHER, SELF), oks.map { it.speaker })
        assertEquals(3, oks.map { it.top }.distinct().size)
    }

    @Test fun readsTakenWhileTheListMovedAreFlaggedUnstable() {
        for (name in listOf("dump-0002.json", "dump-0006.json", "dump-0007.json")) {
            assertFalse(name, read(name).stable)
        }
        for (name in listOf("dump-0001.json", "dump-0003.json", "dump-0004.json", "dump-0005.json")) {
            assertTrue(name, read(name).stable)
        }
    }

    @Test fun chatListIsNotReadAsAConversation() {
        val read = read("dump-0008.json")
        assertEquals(PageKind.CHAT_LIST, read.page)
        assertTrue(read.rows.isEmpty())
    }

    @Test fun everyRecordingReplaysToItsGoldenOutput() {
        val update = System.getenv("JEV_UPDATE_GOLDEN") == "1"
        val names = dumps.list()!!.filter { it.endsWith(".json") }.sorted()
        assertEquals(8, names.size)
        for (name in names) {
            val actual = ScreenReadText.render(read(name))
            val file = File(golden, name.removeSuffix(".json") + ".txt")
            if (update) {
                file.parentFile.mkdirs()
                file.writeText(actual)
            }
            assertTrue("Missing golden file $file (run once with JEV_UPDATE_GOLDEN=1)", file.exists())
            assertEquals(name, file.readText(), actual)
        }
    }

    @Test fun dumpFilesRoundTripThroughReaderAndWriter() {
        for (name in dumps.list()!!.filter { it.endsWith(".json") }) {
            val original = File(dumps, name).readText()
            assertEquals(name, original, DumpJson.encode(DumpReader.read(original)))
        }
    }

    /**
     * A group chat is never read as one-to-one. The group's structure was recorded
     * on 2026-10-03 (fixtures/.../group-structure-2026-10-03.txt, text masked): an
     * incoming message carries the sender's name (name_in_group) and initials avatar.
     */
    @Test fun aGroupChatIsRecognised() {
        val oneToOne = DumpReader.read(File(dumps, "dump-0004.json").readText())
        assertEquals(PageKind.CONVERSATION, adapter.read(oneToOne).page)
        val row = oneToOne.nodes.first { it.viewId == "com.whatsapp:id/main_layout" }
        for (id in listOf("com.whatsapp:id/name_in_group_tv", "com.whatsapp:id/group_profile_initials")) {
            val extra = row.copy(index = oneToOne.nodes.size, parent = row.index, viewId = id, text = null, textLength = 0)
            val group = oneToOne.copy(nodes = oneToOne.nodes + extra)
            val read = adapter.read(group)
            assertEquals(id, PageKind.GROUP_CONVERSATION, read.page)
            assertTrue(read.rows.isEmpty())
        }
    }

    /**
     * A PDF from them (row structure recorded 2026-10-03, document-row-structure-2026-10-03.txt).
     * Its page count sits in an "info" field, the id system notices use; it was once read as a notice
     * and dropped, so their file was missed and the turn before it was analysed instead.
     */
    @Test fun aDocumentIsAMessageNotANotice() {
        val dump = DumpReader.read(File(dumps, "dump-0004.json").readText())
        val list = dump.nodes.first { it.viewId == "android:id/list" && it.isScrollable }
        val lastRow = dump.nodes.filter { it.parent == list.index }.maxBy { it.childIndex }
        val main = dump.nodes.first { it.viewId == "com.whatsapp:id/main_layout" && it.parent == lastRow.index }
        val n = dump.nodes.size
        val nodes = dump.nodes.map { if (it.index == lastRow.index) it.copy(viewId = "com.whatsapp:id/conversation_row_document") else it }
            .filterNot { it.parent == main.index || it.viewId == "com.whatsapp:id/message_text" && it.bounds[1] >= main.bounds[1] && it.bounds[3] <= main.bounds[3] }
            .plus(main.copy(index = n, parent = main.index, viewId = "com.whatsapp:id/document_frame", text = null, textLength = 0))
            .plus(main.copy(index = n + 1, parent = n, viewId = "com.whatsapp:id/info", text = "1 page", textLength = 6))
        val read = adapter.read(dump.copy(nodes = nodes))
        val last = read.messages.last()
        assertEquals(com.jev.overseas.core.model.MessageKind.DOCUMENT, last.kind)
        assertTrue(last.complete)
        assertTrue(read.rows.none { it.kind == SYSTEM_NOTICE && it.text == "1 page" })
        val c = com.jev.overseas.core.engine.Conversation.fromRows(read.rows)
        assertEquals("[document]", c.messages.last().text)
    }
}
