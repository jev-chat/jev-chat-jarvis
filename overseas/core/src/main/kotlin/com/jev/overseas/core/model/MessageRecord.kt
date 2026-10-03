package com.jev.overseas.core.model

enum class Speaker { SELF, OTHER, SYSTEM, UNKNOWN }

enum class MessageKind {
    TEXT,
    IMAGE,
    VOICE,
    /** A "this message was deleted" placeholder. */
    DELETED,
    /** A file (PDF and the like). Its file name is not read. */
    DOCUMENT,
    DATE_DIVIDER,
    /** Encryption notice, "added as a contact", and similar rows from the app itself. */
    SYSTEM_NOTICE,
    /** A row the adapter could not classify. Its text, if any, is kept for inspection. */
    UNKNOWN,
}

/** The message a reply refers to. [text] is the quoted excerpt as the app shows it. */
data class Quote(val from: Speaker, val text: String?)

/**
 * One row of a chat as read from a single screen.
 *
 * This is the per-screen record. Identity across screens (local IDs, overlap
 * removal) belongs to the stitching step and is not decided here.
 */
data class MessageRecord(
    val speaker: Speaker,
    val kind: MessageKind,
    /** Body text; null for media without a caption. */
    val text: String?,
    /** The time label as shown, for example "06:53". Not parsed. */
    val timeLabel: String?,
    val edited: Boolean,
    val quote: Quote?,
    /** Voice note length as shown, for example "0:02". */
    val durationLabel: String?,
    /** Row top and bottom in screen pixels. */
    val top: Int,
    val bottom: Int,
    /** True when the row touches the top or bottom edge of the list. */
    val clipped: Boolean,
    /** False when the adapter could not read the parts this kind of row should have. */
    val complete: Boolean,
) {
    /**
     * A message from one of the two people. A row the adapter could not classify
     * (a video, a document, a sticker, a location…) still counts: it has a sender
     * and it is part of who wrote last, so dropping it would misread the turn.
     */
    val isChatMessage: Boolean
        get() = kind == MessageKind.TEXT || kind == MessageKind.IMAGE ||
            kind == MessageKind.VOICE || kind == MessageKind.DELETED || kind == MessageKind.DOCUMENT || kind == MessageKind.UNKNOWN
}

enum class PageKind { CONVERSATION, GROUP_CONVERSATION, CHAT_LIST, OTHER }

/** What one screen yields. */
data class ScreenRead(
    val page: PageKind,
    /** Rows in on-screen order, top to bottom. Empty unless [page] is CONVERSATION. */
    val rows: List<MessageRecord>,
    val canScrollToOlder: Boolean,
    val canScrollToNewer: Boolean,
    /**
     * False when row positions contradict each other, which happens when the tree
     * was read while the list was still moving. Speaker is derived from position,
     * so an unstable read must be repeated, not used.
     */
    val stable: Boolean,
    /** Human-readable reasons for [stable] being false, or for rows left out. */
    val notes: List<String>,
) {
    val messages: List<MessageRecord> get() = rows.filter { it.isChatMessage }
}
