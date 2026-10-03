package com.jev.probe.capture

/** Main-thread session state. A return to the same chat never revives an old request. */
internal class ConversationSession {
    data class Target(
        val pkg: String,
        val windowId: Int,
        val title: String?,
        val messagesSignature: String? = null
    ) {
        /**
         * Conversation identity = app + window + chat title. Animated list nodes,
         * read receipts and timestamp ticks change [messagesSignature] between two
         * extractions of the SAME chat; treating that as "left the conversation"
         * invalidated in-flight analyses the instant their result came back, so
         * candidates flashed and the overlay hid itself (seen on Soul and QQ
         * alike). Content changes are handled by the snapshot-signature path in
         * the capture service, which re-runs analysis for genuinely new messages.
         */
        fun sameConversation(other: Target): Boolean =
            pkg == other.pkg && windowId == other.windowId && title == other.title
    }
    data class Token(val target: Target, val revision: Long)

    var target: Target? = null
        private set
    private var revision = 0L

    fun observe(next: Target?): Boolean {
        val changed = if (target == null || next == null) target != next
                      else !target!!.sameConversation(next)
        if (!changed) { target = next; return false }
        target = next
        invalidate()
        return true
    }

    fun invalidate() { revision++ }

    fun token(): Token? = target?.let { Token(it, revision) }

    fun begin(): Token? {
        invalidate()
        return token()
    }

    fun accepts(token: Token): Boolean {
        val current = target ?: return false
        return token.target.sameConversation(current) && token.revision == revision
    }
}
