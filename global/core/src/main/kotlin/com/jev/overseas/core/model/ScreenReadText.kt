package com.jev.overseas.core.model

/** Plain-text rendering of a [ScreenRead], one row per line. Used for replay comparisons and reports. */
object ScreenReadText {

    fun render(read: ScreenRead): String {
        val sb = StringBuilder()
        sb.append("page=").append(read.page)
            .append(" stable=").append(read.stable)
            .append(" older=").append(read.canScrollToOlder)
            .append(" newer=").append(read.canScrollToNewer)
            .append('\n')
        for (note in read.notes) sb.append("note: ").append(note).append('\n')
        for (row in read.rows) sb.append(line(row)).append('\n')
        return sb.toString()
    }

    fun line(row: MessageRecord): String {
        val sb = StringBuilder()
        sb.append(row.speaker.name.padEnd(7)).append(' ').append(row.kind.name.padEnd(13)).append(' ')
        sb.append((row.timeLabel ?: "-").padEnd(6)).append(' ')
        sb.append(if (row.text == null) "-" else quoted(row.text))
        row.durationLabel?.let { sb.append(" duration=").append(it) }
        if (row.edited) sb.append(" edited")
        row.quote?.let { sb.append(" quote(").append(it.from).append(")=").append(if (it.text == null) "-" else quoted(it.text)) }
        if (row.clipped) sb.append(" clipped")
        if (!row.complete) sb.append(" INCOMPLETE")
        return sb.toString()
    }

    private fun quoted(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
