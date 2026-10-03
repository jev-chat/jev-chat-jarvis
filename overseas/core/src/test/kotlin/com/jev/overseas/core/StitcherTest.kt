package com.jev.overseas.core

import com.jev.overseas.core.dump.DumpReader
import com.jev.overseas.core.model.MessageKind
import com.jev.overseas.core.model.MessageRecord
import com.jev.overseas.core.model.Quote
import com.jev.overseas.core.model.Speaker
import com.jev.overseas.core.stitch.Boundary
import com.jev.overseas.core.stitch.Stitcher
import com.jev.overseas.core.whatsapp.WhatsAppAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

class StitcherTest {

    private fun row(speaker: Speaker, text: String?, time: String? = "06:5x", kind: MessageKind = MessageKind.TEXT, quote: Quote? = null) =
        MessageRecord(speaker, kind, text, time, false, quote, null, 0, 10, clipped = false, complete = true)

    /** The scripted test chat, as rows. Two identical "OK" from them in the same minute. */
    private val chat = listOf(
        row(Speaker.SYSTEM, "Today", null, MessageKind.DATE_DIVIDER),
        row(Speaker.OTHER, "Hi, this is a test chat for the reply assistant.", "06:41"),
        row(Speaker.SELF, "Got it, thanks for helping.", "06:42"),
        row(Speaker.OTHER, "Can you send me the report by Friday?", "06:42"),
        row(Speaker.SELF, "I'll check and let you know.", "06:43"),
        row(Speaker.OTHER, "OK", "06:43"),
        row(Speaker.OTHER, "OK", "06:43"),
        row(Speaker.SELF, "OK", "06:44"),
        row(Speaker.OTHER, "Any update on this?", "06:44", quote = Quote(Speaker.SELF, "I'll check and let you know.")),
        row(Speaker.SELF, "Friday might be tight.", "06:55", quote = Quote(Speaker.OTHER, "Can you send me the report by Friday?")),
        row(Speaker.OTHER, "k.", "06:55"),
        row(Speaker.OTHER, null, "06:55", MessageKind.IMAGE),
        row(Speaker.OTHER, null, "06:55", MessageKind.VOICE),
        row(Speaker.OTHER, "Just to recap what we agreed on the call.", "06:56"),
        row(Speaker.SELF, "Sounds good to me.", "06:56"),
        row(Speaker.OTHER, "See you Wednesday", "06:56"),
        row(Speaker.SELF, null, "06:57", MessageKind.DELETED),
        row(Speaker.OTHER, "Thanks, speak soon.", "06:57"),
    )

    /** A screen showing rows [from, to); the rows on the list edges are marked clipped. */
    private fun screen(from: Int, to: Int, clipTop: Boolean = true, clipBottom: Boolean = true) =
        chat.subList(from, to).mapIndexed { i, r ->
            val edge = (i == 0 && clipTop) || (i == to - from - 1 && clipBottom)
            r.copy(clipped = edge, timeLabel = if (edge && i == 0) null else r.timeLabel, complete = !(edge && i == 0))
        }

    /** Reads bottom-up, as the reader does, and joins. */
    private fun stitch(vararg screens: List<MessageRecord>): Pair<List<MessageRecord>, List<Boundary>> {
        var rows = screens.last()
        val joins = ArrayList<Boundary>()
        for (s in screens.dropLast(1).reversed()) {
            val j = Stitcher.prepend(s, rows)
            rows = j.rows; joins.add(j.boundary)
        }
        return rows to joins
    }

    private fun texts(rows: List<MessageRecord>) = rows.map { "${it.speaker}:${it.kind}:${it.text}" }

    @Test fun overlappingScreensRebuildTheChat() {
        val (rows, joins) = stitch(screen(0, 8, clipTop = false), screen(5, 13), screen(10, 18, clipBottom = false))
        assertEquals(texts(chat), texts(rows))
        assertTrue(joins.all { it == Boundary.OVERLAP })
    }

    @Test fun twoIdenticalOksAreKept() {
        // The overlap starts inside the run of OKs.
        val (rows, _) = stitch(screen(0, 7, clipTop = false), screen(5, 18, clipBottom = false))
        assertEquals(3, rows.count { it.text == "OK" })
        assertEquals(texts(chat), texts(rows))
    }

    @Test fun singleEdgeRowMatch() {
        val (rows, joins) = stitch(screen(0, 6, clipTop = false), screen(5, 18, clipBottom = false))
        assertEquals(listOf(Boundary.EDGE_ROW), joins)
        assertEquals(texts(chat), texts(rows))
    }

    @Test fun singleMatchAwayFromTheEdgeIsNotMerged() {
        val older = screen(0, 6, clipTop = false, clipBottom = false)
        val newer = screen(5, 18, clipTop = false, clipBottom = false)
        val j = Stitcher.prepend(older, newer)
        assertEquals(Boundary.UNCERTAIN, j.boundary)
        assertEquals(older.size + newer.size, j.rows.size)
    }

    @Test fun noOverlapIsReported() {
        val j = Stitcher.prepend(screen(0, 5), screen(8, 18))
        assertEquals(Boundary.NO_OVERLAP, j.boundary)
        assertEquals(15, j.rows.size)
    }

    @Test fun clippedRowWithoutTimeStillMatches() {
        val older = screen(0, 9, clipTop = false)
        val newer = screen(7, 18, clipBottom = false) // first row clipped, its time missing
        val j = Stitcher.prepend(older, newer)
        assertEquals(Boundary.OVERLAP, j.boundary)
        assertEquals(texts(chat), texts(j.rows))
    }

    @Test fun randomWindowsWithOverlapRebuildTheChat() {
        val random = Random(11)
        repeat(300) {
            // Windows of 4..9 rows, each overlapping the next by 2..3 rows.
            val bounds = ArrayList<Pair<Int, Int>>()
            var end = chat.size
            while (true) {
                val start = maxOf(0, end - (4 + random.nextInt(6)))
                bounds.add(start to end)
                if (start == 0) break
                end = start + 2 + random.nextInt(2)
            }
            val screens = bounds.reversed().mapIndexed { i, (a, b) -> screen(a, b, clipTop = a > 0, clipBottom = b < chat.size) }
            val (rows, _) = stitch(*screens.toTypedArray())
            assertEquals(texts(chat), texts(rows))
        }
    }

    @Test fun aScreenAlreadyReadIsRecognised() {
        val (rows, _) = stitch(screen(5, 13), screen(10, 18, clipBottom = false))
        // Jumped back to the bottom, or did not move: nothing new.
        assertTrue(Stitcher.alreadyRead(screen(10, 18, clipBottom = false), rows))
        assertTrue(Stitcher.alreadyRead(screen(5, 13), rows))
        // Genuinely older rows are not.
        assertEquals(false, Stitcher.alreadyRead(screen(0, 8, clipTop = false), rows))
        // Rows that exist but not next to each other are not a screen already read.
        assertEquals(false, Stitcher.alreadyRead(listOf(chat[5], chat[8]), rows))
    }

    @Test fun realRecordingsOfTheSameScreenJoinCompletely() {
        val dir = File(System.getProperty("jev.fixtures"), "screendumps/whatsapp-2.26.38.73")
        val adapter = WhatsAppAdapter()
        val a = adapter.read(DumpReader.read(File(dir, "dump-0003.json").readText())).rows
        val b = adapter.read(DumpReader.read(File(dir, "dump-0005.json").readText())).rows
        val j = Stitcher.prepend(a, b)
        assertEquals(Boundary.OVERLAP, j.boundary)
        assertEquals(b.size, j.rows.size)
    }
}
