package com.jev.overseas.probe.dump

import com.jev.overseas.core.dump.DumpMode
import com.jev.overseas.core.dump.NodeRecord

/**
 * Removes text the dump must not keep.
 *
 * STRUCTURE: every free-text field is dropped; only lengths remain.
 *
 * FULL: text is kept only inside scrollable containers (the message list).
 * Everything outside them is dropped: the contact name and status in the top
 * bar, button labels, and the input box. Input boxes and password fields are
 * dropped wherever they are. Strings shown in the top bar are also replaced by
 * [TOP_BAR_TOKEN] where they recur inside the list (for example the contact
 * name in a quoted reply's header), so the contact's name is not stored. A
 * top-bar string that is only a clock time is not used as a mask: it would blank
 * the timestamps of messages sent in that minute.
 */
object Redactor {

    const val TOP_BAR_TOKEN = "<TOPBAR>"

    /** The top bar is assumed to sit within this fraction of the screen height. */
    private const val TOP_BAR_FRACTION = 0.18
    private const val MIN_MASK_LENGTH = 3
    private val CLOCK_TIME = Regex("""\d{1,2}[:.]\d{2}(\s?[AaPp][Mm])?""")

    data class Outcome(val nodes: List<NodeRecord>, val maskCount: Int, val replacements: Int)

    fun redact(nodes: List<NodeRecord>, mode: DumpMode, screenHeight: Int): Outcome {
        if (mode == DumpMode.STRUCTURE) {
            return Outcome(nodes.map(::stripText), maskCount = 0, replacements = 0)
        }
        val topBarLimit = (screenHeight * TOP_BAR_FRACTION).toInt()
        val masks = nodes
            .filter { !it.inScrollable && it.bounds[3] in 1..topBarLimit }
            .mapNotNull { it.text?.trim() }
            .filter { it.length >= MIN_MASK_LENGTH && !CLOCK_TIME.matches(it) }
            .distinct()
            .sortedByDescending { it.length }

        var replacements = 0
        fun mask(value: String?): String? {
            if (value == null) return null
            var out: String = value
            for (m in masks) {
                if (out.contains(m)) {
                    replacements += out.split(m).size - 1
                    out = out.replace(m, TOP_BAR_TOKEN)
                }
            }
            return out
        }

        val redacted = nodes.map { node ->
            if (!node.inScrollable || node.isEditable || node.isPassword) {
                stripText(node)
            } else {
                node.copy(
                    text = mask(node.text),
                    contentDescription = mask(node.contentDescription),
                    hint = mask(node.hint),
                    stateDescription = mask(node.stateDescription),
                    paneTitle = mask(node.paneTitle),
                    tooltip = mask(node.tooltip),
                )
            }
        }
        return Outcome(redacted, masks.size, replacements)
    }

    private fun stripText(node: NodeRecord): NodeRecord = node.copy(
        text = null,
        contentDescription = null,
        hint = null,
        stateDescription = null,
        paneTitle = null,
        tooltip = null,
    )
}
