package com.jev.overseas.core.whatsapp

import com.jev.overseas.core.dump.NodeRecord
import com.jev.overseas.core.dump.ScreenDump
import com.jev.overseas.core.model.MessageKind
import com.jev.overseas.core.model.MessageRecord
import com.jev.overseas.core.model.PageKind
import com.jev.overseas.core.model.Quote
import com.jev.overseas.core.model.ScreenRead
import com.jev.overseas.core.model.Speaker

/**
 * Turns one recorded WhatsApp screen into message rows.
 *
 * Rules come from recordings of WhatsApp 2.26.38.73 (see
 * fixtures/screendumps/whatsapp-2.26.38.73). They rely on resource IDs and
 * positions, never on UI wording, so the app's display language does not matter.
 * Left-to-right layout is assumed.
 *
 * One-to-one chats are read. A group chat is recognised (structure recorded
 * 2026-10-03, `group-structure-2026-10-03.txt`) and reported as such, never read
 * as a one-to-one chat.
 */
class WhatsAppAdapter(
    /**
     * Strings that stand for the other person's name in a quoted reply's header:
     * the live conversation title, and the mask a recording uses in its place.
     */
    private val otherPersonNames: Set<String> = setOf(MASKED_TITLE),
) {

    fun read(dump: ScreenDump): ScreenRead {
        val tree = Tree(dump.nodes)
        val page = pageKind(tree)
        if (page != PageKind.CONVERSATION) {
            return ScreenRead(page, emptyList(), false, false, stable = true, notes = emptyList())
        }
        val list = dump.nodes.firstOrNull { it.viewId == ID_LIST && it.isScrollable }
            ?: return ScreenRead(page, emptyList(), false, false, stable = true,
                notes = listOf("Conversation page without a message list"))

        val notes = ArrayList<String>()
        val rows = ArrayList<MessageRecord>()
        var inconsistentRows = 0
        for (row in tree.childrenOf(list)) {
            val parts = tree.descendantsOf(row)
            if (parts.isEmpty() && row.viewId == null) continue // spacer rows carry nothing
            if (!positionsAgree(row, parts)) inconsistentRows++
            rows.addAll(readRow(row, parts, list))
        }
        if (inconsistentRows > 0) {
            notes.add("$inconsistentRows row(s) have children outside the row's own bounds")
        }
        val moved = recordedRowsMoved(dump)
        if (moved > 0) notes.add("Recording reports $moved row(s) moved during capture")

        return ScreenRead(
            page = page,
            rows = rows,
            canScrollToOlder = ACTION_SCROLL_BACKWARD in list.actions,
            canScrollToNewer = ACTION_SCROLL_FORWARD in list.actions,
            stable = inconsistentRows == 0 && moved == 0,
            notes = notes,
        )
    }

    private fun pageKind(tree: Tree): PageKind = when {
        // A sender name above someone's message, or the group's initials avatar, exists only in group chats.
        tree.hasId(ID_CONTACT_NAME) && tree.hasId(ID_ENTRY) && GROUP_IDS.any(tree::hasId) -> PageKind.GROUP_CONVERSATION
        tree.hasId(ID_CONTACT_NAME) && tree.hasId(ID_ENTRY) -> PageKind.CONVERSATION
        tree.hasId(ID_LIST_HOST) || tree.hasId(ID_LIST_ROW_NAME) -> PageKind.CHAT_LIST
        else -> PageKind.OTHER
    }

    /** One list row yields a date divider (when it carries one) and then its content. */
    private fun readRow(row: NodeRecord, parts: List<NodeRecord>, list: NodeRecord): List<MessageRecord> {
        val out = ArrayList<MessageRecord>(2)
        val top = row.bounds[1]
        val bottom = row.bounds[3]
        val clipped = top <= list.bounds[1] || bottom >= list.bounds[3]

        fun record(
            speaker: Speaker, kind: MessageKind, text: String?, complete: Boolean,
            time: String? = null, edited: Boolean = false, quote: Quote? = null, duration: String? = null,
        ) = MessageRecord(speaker, kind, text, time, edited, quote, duration, top, bottom, clipped, complete)

        parts.firstOrNull { it.viewId == ID_DATE_DIVIDER }?.let {
            out.add(record(Speaker.SYSTEM, MessageKind.DATE_DIVIDER, it.text, complete = it.text != null))
        }

        val main = parts.firstOrNull { it.viewId == ID_MAIN_LAYOUT }
        val body = parts.firstOrNull { it.viewId == ID_MESSAGE_TEXT }?.text
        val time = parts.firstOrNull { it.viewId == ID_DATE }?.text
        val kind = when (row.viewId) {
            ID_ROW_TEXT -> MessageKind.TEXT
            ID_ROW_IMAGE -> MessageKind.IMAGE
            ID_ROW_VOICE -> MessageKind.VOICE
            ID_ROW_DOCUMENT -> MessageKind.DOCUMENT
            else -> when {
                main != null && parts.any { it.viewId == ID_ICON } && body != null -> MessageKind.DELETED
                // A message bubble is never a notice, even when it holds an "info" field (a PDF's page count does).
                main != null -> MessageKind.UNKNOWN
                parts.any { it.viewId == ID_INFO } -> MessageKind.SYSTEM_NOTICE
                else -> null // nothing besides a date divider
            }
        } ?: return out

        if (kind == MessageKind.SYSTEM_NOTICE) {
            val notice = parts.first { it.viewId == ID_INFO }.text
            out.add(record(Speaker.SYSTEM, kind, notice, complete = notice != null))
            return out
        }

        val speaker = speakerOf(main, parts, list)
        val complete = main != null && speaker != Speaker.UNKNOWN && when (kind) {
            MessageKind.TEXT, MessageKind.DELETED -> body != null
            MessageKind.IMAGE -> parts.any { it.viewId == ID_IMAGE }
            MessageKind.VOICE -> parts.any { it.viewId == ID_AUDIO_PLAYER }
            MessageKind.DOCUMENT -> parts.any { it.viewId == ID_DOCUMENT_FRAME }
            else -> false
        }
        out.add(record(
            speaker = speaker,
            kind = kind,
            text = body,
            complete = complete,
            time = time,
            edited = parts.any { it.viewId == ID_EDIT_LABEL },
            quote = quoteOf(parts),
            duration = if (kind == MessageKind.VOICE) parts.firstOrNull { it.viewId == ID_VOICE_DURATION }?.text else null,
        ))
        return out
    }

    /**
     * The bubble hugs the sender's side: a smaller left margin means the other
     * person, a smaller right margin means the user. A delivery-status icon exists
     * only on the user's own messages; when it contradicts the position, the
     * speaker is reported as unknown.
     */
    private fun speakerOf(main: NodeRecord?, parts: List<NodeRecord>, list: NodeRecord): Speaker {
        if (main == null) return Speaker.UNKNOWN
        val leftMargin = main.bounds[0] - list.bounds[0]
        val rightMargin = list.bounds[2] - main.bounds[2]
        val byPosition = when {
            leftMargin < rightMargin -> Speaker.OTHER
            rightMargin < leftMargin -> Speaker.SELF
            else -> Speaker.UNKNOWN
        }
        val hasStatusIcon = parts.any { it.viewId == ID_STATUS }
        return if (hasStatusIcon && byPosition == Speaker.OTHER) Speaker.UNKNOWN else byPosition
    }

    private fun quoteOf(parts: List<NodeRecord>): Quote? {
        if (parts.none { it.viewId == ID_QUOTE_HOLDER }) return null
        val title = parts.firstOrNull { it.viewId == ID_QUOTE_TITLE }?.text
        val from = when {
            title == null -> Speaker.UNKNOWN
            title in otherPersonNames -> Speaker.OTHER
            else -> Speaker.SELF // in a one-to-one chat the only other label is the user's own ("You")
        }
        return Quote(from, parts.firstOrNull { it.viewId == ID_QUOTE_TEXT }?.text)
    }

    /** A row's bubble must lie inside the row. It does not when parent and child were read at different moments. */
    private fun positionsAgree(row: NodeRecord, parts: List<NodeRecord>): Boolean {
        val main = parts.firstOrNull { it.viewId == ID_MAIN_LAYOUT } ?: return true
        return main.bounds[1] >= row.bounds[1] - TOLERANCE_PX && main.bounds[3] <= row.bounds[3] + TOLERANCE_PX
    }

    private fun recordedRowsMoved(dump: ScreenDump): Int {
        val stability = dump.meta["stability"] as? Map<*, *> ?: return 0
        return (stability["rowsMoved"] as? Number)?.toInt() ?: 0
    }

    private class Tree(nodes: List<NodeRecord>) {
        private val children: Map<Int, List<NodeRecord>> = nodes.groupBy { it.parent }
        private val ids: Set<String> = nodes.mapNotNullTo(HashSet()) { it.viewId }

        fun hasId(id: String) = id in ids

        fun childrenOf(node: NodeRecord): List<NodeRecord> =
            children[node.index].orEmpty().sortedBy { it.childIndex }

        /** Document order. */
        fun descendantsOf(node: NodeRecord): List<NodeRecord> {
            val out = ArrayList<NodeRecord>()
            val stack = ArrayDeque<NodeRecord>()
            childrenOf(node).asReversed().forEach { stack.addLast(it) }
            while (stack.isNotEmpty()) {
                val n = stack.removeLast()
                out.add(n)
                childrenOf(n).asReversed().forEach { stack.addLast(it) }
            }
            return out
        }
    }

    companion object {
        /** What the probe writes in place of the conversation title inside the list. */
        const val MASKED_TITLE = "<TOPBAR>"

        private const val TOLERANCE_PX = 2
        private const val ACTION_SCROLL_BACKWARD = "SCROLL_BACKWARD"
        private const val ACTION_SCROLL_FORWARD = "SCROLL_FORWARD"

        private const val WA = "com.whatsapp:id/"
        private const val ID_LIST = "android:id/list"
        private const val ID_CONTACT_NAME = WA + "conversation_contact_name"
        private const val ID_ENTRY = WA + "entry"
        private const val ID_LIST_HOST = WA + "conversation_list_view_host"
        private const val ID_LIST_ROW_NAME = WA + "conversations_row_contact_name"
        private const val ID_ROW_TEXT = WA + "conversation_row_text"
        private const val ID_ROW_IMAGE = WA + "conversation_row_image"
        private const val ID_ROW_VOICE = WA + "conversation_row_voice_note"
        /** Recorded 2026-10-03 (document-row-structure-2026-10-03.txt). */
        private const val ID_ROW_DOCUMENT = WA + "conversation_row_document"
        private const val ID_DOCUMENT_FRAME = WA + "document_frame"
        private const val ID_MAIN_LAYOUT = WA + "main_layout"
        private const val ID_MESSAGE_TEXT = WA + "message_text"
        private const val ID_DATE = WA + "date"
        private const val ID_STATUS = WA + "status"
        private const val ID_EDIT_LABEL = WA + "edit_label"
        private const val ID_QUOTE_HOLDER = WA + "quoted_message_holder"
        private const val ID_QUOTE_TITLE = WA + "quoted_title"
        private const val ID_QUOTE_TEXT = WA + "quoted_text"
        private const val ID_ICON = WA + "icon"
        private const val ID_IMAGE = WA + "image"
        private const val ID_AUDIO_PLAYER = WA + "conversation_row_audio_player_view"
        private const val ID_VOICE_DURATION = WA + "description"
        private const val ID_DATE_DIVIDER = WA + "conversation_row_date_divider"
        private const val ID_INFO = WA + "info"
        /** Seen only in group chats (recorded 2026-10-03; absent from every one-to-one recording). */
        private val GROUP_IDS = listOf(WA + "name_in_group", WA + "name_in_group_tv",
            WA + "conversation_row_name_in_group_name_and_role_container", WA + "group_profile_initials", WA + "groupPhotoCameraIcon")
    }
}
