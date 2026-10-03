package com.jev.overseas.core.session

/** What is in WhatsApp's message box right now. */
enum class BoxState {
    /** Empty, or only showing its hint. */
    EMPTY,
    /** The user has typed something of their own. */
    HAS_TEXT,
    /** The box could not be found. */
    MISSING,
}

/**
 * The message box as the fill procedure needs it. Implemented on the Android
 * side over the accessibility node; nothing here can send a message.
 */
interface EditableBox {
    /** ACTION_SET_TEXT. Returns what the action call returned. */
    fun setText(text: String): Boolean

    /** The box's text now, or null when it shows only its hint or cannot be read. */
    fun currentText(): String?

    /** ACTION_FOCUS. */
    fun focus(): Boolean
}

/** Where a chosen reply goes: the message box (Fill) or the clipboard (Copy). */
interface ReplyTarget {
    fun boxState(): BoxState

    /** Writes [text] into the message box and confirms it is there. Never sends. */
    fun fill(text: String): Boolean

    fun copy(text: String)
}

/**
 * Write, wait, read back; if it did not take, focus and try once more.
 * The caller falls back to the clipboard when this
 * returns false.
 */
object FillProcedure {

    const val SETTLE_MS = 150L

    fun fill(box: EditableBox, text: String, wait: (Long) -> Unit = { Thread.sleep(it) }): Boolean {
        box.setText(text)
        wait(SETTLE_MS)
        if (matches(box.currentText(), text)) return true
        box.focus()
        box.setText(text)
        wait(SETTLE_MS)
        return matches(box.currentText(), text)
    }

    /** WhatsApp may trim trailing whitespace; anything else is a mismatch. */
    fun matches(actual: String?, expected: String): Boolean =
        actual != null && actual.trimEnd() == expected.trimEnd()
}
