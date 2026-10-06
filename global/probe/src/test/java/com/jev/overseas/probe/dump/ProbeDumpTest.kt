package com.jev.overseas.probe.dump

import com.jev.overseas.core.dump.DumpJson
import com.jev.overseas.core.dump.DumpMode
import com.jev.overseas.core.dump.NodeRecord
import com.jev.overseas.core.dump.ScreenDump
import com.jev.overseas.core.dump.ScreenDumpFormat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeDumpTest {

    private fun node(
        index: Int,
        text: String? = null,
        desc: String? = null,
        bounds: List<Int> = listOf(0, 1000, 1440, 1100),
        flags: List<String> = emptyList(),
        inScrollable: Boolean = true,
    ) = NodeRecord(
        index = index, parent = index - 1, depth = 1, childIndex = 0,
        className = "android.widget.TextView", packageName = "com.whatsapp", viewId = null,
        text = text, textLength = text?.length ?: 0,
        contentDescription = desc, contentDescriptionLength = desc?.length ?: 0,
        hint = null, stateDescription = null, paneTitle = null, tooltip = null,
        bounds = bounds, flags = flags, actions = emptyList(),
        childCount = 0, missingChildren = 0, collection = null, collectionItem = null,
        drawingOrder = 0, uniqueId = null, extraKeys = emptyList(), inScrollable = inScrollable,
    )

    // ---- TestChatGuard

    @Test fun guardPassesOnOneSpecificSentence() {
        val r = TestChatGuard.evaluate(listOf("Hi, this is a test chat for the reply assistant."))
        assertTrue(r.passed)
    }

    @Test fun guardNeedsTwoOrdinarySentences() {
        assertFalse(TestChatGuard.evaluate(listOf("Sounds good to me.")).passed)
        assertTrue(TestChatGuard.evaluate(listOf("Sounds good to me.", "Thanks, speak soon.")).passed)
    }

    @Test fun guardCountsARepeatedSentenceOnce() {
        assertFalse(TestChatGuard.evaluate(listOf("Sounds good to me.", "sounds good to me")).passed)
    }

    @Test fun guardAcceptsCurlyApostropheAndExtraSpaces() {
        val r = TestChatGuard.evaluate(listOf("I’ll  check and let you know.", "Any update on this?"))
        assertEquals(2, r.genericMatches)
    }

    @Test fun guardRejectsAnOrdinaryChat() {
        assertFalse(TestChatGuard.evaluate(listOf("OK", "see you at 7", "k.")).passed)
    }

    // ---- Redactor

    @Test fun structureModeDropsAllTextButKeepsLengths() {
        val out = Redactor.redact(listOf(node(0, text = "secret", desc = "also secret")), DumpMode.STRUCTURE, 3168)
        val n = out.nodes.single()
        assertNull(n.text)
        assertNull(n.contentDescription)
        assertEquals(6, n.textLength)
        assertEquals(11, n.contentDescriptionLength)
    }

    @Test fun fullModeKeepsListTextAndDropsEverythingOutside() {
        val nodes = listOf(
            node(0, text = "Alex Example", bounds = listOf(200, 120, 900, 200), inScrollable = false),
            node(1, text = "Thanks, speak soon."),
            node(2, text = "half typed draft", flags = listOf("editable"), inScrollable = false),
        )
        val out = Redactor.redact(nodes, DumpMode.FULL, 3168).nodes
        assertNull(out[0].text)
        assertEquals("Thanks, speak soon.", out[1].text)
        assertNull(out[2].text)
    }

    @Test fun fullModeMasksTheTopBarNameInsideTheList() {
        val nodes = listOf(
            node(0, text = "Alex Example", bounds = listOf(200, 120, 900, 200), inScrollable = false),
            node(1, text = "Alex Example", desc = "Reply to Alex Example"),
        )
        val out = Redactor.redact(nodes, DumpMode.FULL, 3168)
        assertEquals(Redactor.TOP_BAR_TOKEN, out.nodes[1].text)
        assertEquals("Reply to ${Redactor.TOP_BAR_TOKEN}", out.nodes[1].contentDescription)
        assertEquals(1, out.maskCount)
        assertEquals(2, out.replacements)
    }

    @Test fun fullModeDoesNotMaskClockTimes() {
        val nodes = listOf(
            node(0, text = "06:57", bounds = listOf(400, 281, 524, 346), inScrollable = false),
            node(1, text = "06:57"),
        )
        val out = Redactor.redact(nodes, DumpMode.FULL, 3168)
        assertEquals("06:57", out.nodes[1].text)
        assertEquals(0, out.maskCount)
    }

    @Test fun fullModeDropsEditableAndPasswordNodesEvenInsideAList() {
        val nodes = listOf(
            node(0, text = "draft", flags = listOf("editable")),
            node(1, text = "hunter2", flags = listOf("password")),
        )
        val out = Redactor.redact(nodes, DumpMode.FULL, 3168).nodes
        assertNull(out[0].text)
        assertNull(out[1].text)
    }

    // ---- DumpJson

    @Test fun jsonEscapesQuotesBackslashesAndControlCharacters() {
        val dump = ScreenDump(
            meta = linkedMapOf("schema" to ScreenDumpFormat.SCHEMA, "n" to 1, "ok" to true, "none" to null),
            nodes = listOf(node(0, text = "say \"hi\"\\ \n\tend\u0001")),
        )
        val json = DumpJson.encode(dump)
        assertTrue(json.contains("\"text\": \"say \\\"hi\\\"\\\\ \\n\\tend\\u0001\""))
        assertTrue(json.contains("\"meta\": {\"schema\": \"jev.screendump/1\", \"n\": 1, \"ok\": true, \"none\": null}"))
        assertTrue(json.contains("\"bounds\": [0, 1000, 1440, 1100]"))
    }
}
