package com.jev.overseas.core.stitch

import com.jev.overseas.core.model.MessageRecord

/** How two neighbouring screens were joined. */
enum class Boundary {
    /** Two or more rows matched: the screens overlap and the join is reliable. */
    OVERLAP,
    /** One row matched and it sat on the list edge in both reads: very likely the same message. */
    EDGE_ROW,
    /** One row matched but not on both edges: kept as two messages, the join may be wrong. */
    UNCERTAIN,
    /** Nothing matched: the screens are put next to each other and a gap is possible. */
    NO_OVERLAP,
}

data class Join(val rows: List<MessageRecord>, val overlap: Int, val boundary: Boundary)

/**
 * Joins screens read while scrolling up. WhatsApp's
 * rows carry no ids, so screens are aligned by content and position: the
 * longest run of rows at the bottom of the older screen that equals the run at
 * the top of what is already read is the overlap. Matching runs of rows, not
 * single rows, is what keeps two identical "OK" messages from being merged.
 */
object Stitcher {

    /** Puts [older] (a screen further up) in front of [existing] (everything read so far, oldest first). */
    fun prepend(older: List<MessageRecord>, existing: List<MessageRecord>): Join {
        if (older.isEmpty()) return Join(existing, 0, Boundary.NO_OVERLAP)
        if (existing.isEmpty()) return Join(older, 0, Boundary.NO_OVERLAP)
        for (k in minOf(older.size, existing.size) downTo 1) {
            val tail = older.takeLast(k)
            val head = existing.take(k)
            if (!tail.indices.all { same(tail[it], head[it]) }) continue
            if (k >= 2) return Join(older.dropLast(k) + existing, k, Boundary.OVERLAP)
            // A single matching row counts only when it was cut by the list edge in both reads.
            return if (older.last().clipped && existing.first().clipped) {
                Join(older.dropLast(1) + existing, 1, Boundary.EDGE_ROW)
            } else {
                Join(older + existing, 0, Boundary.UNCERTAIN)
            }
        }
        return Join(older + existing, 0, Boundary.NO_OVERLAP)
    }

    /**
     * True when every row of [screen] is already in [rows] as one contiguous run:
     * the list did not move, or jumped back to a part already read (on the device,
     * WhatsApp sometimes snaps to the bottom while it loads older messages). Such
     * a screen adds nothing and must not be joined.
     */
    fun alreadyRead(screen: List<MessageRecord>, rows: List<MessageRecord>): Boolean {
        if (screen.isEmpty()) return true
        if (screen.size > rows.size) return false
        return (0..rows.size - screen.size).any { start -> screen.indices.all { same(screen[it], rows[start + it]) } }
    }

    /**
     * The same row seen in two reads. A row cut by the list edge may miss its time
     * label or other parts, so those are compared only when both reads have them.
     */
    fun same(a: MessageRecord, b: MessageRecord): Boolean {
        if (a.speaker != b.speaker || a.kind != b.kind || a.text != b.text) return false
        if (a.quote?.from != b.quote?.from || a.quote?.text != b.quote?.text) {
            // A quote block cut off at the edge may be missing in one read.
            if (!(a.clipped || b.clipped)) return false
        }
        if (a.timeLabel != null && b.timeLabel != null && a.timeLabel != b.timeLabel) return false
        if (a.complete && b.complete && a.edited != b.edited) return false
        if (a.durationLabel != null && b.durationLabel != null && a.durationLabel != b.durationLabel) return false
        return true
    }
}
