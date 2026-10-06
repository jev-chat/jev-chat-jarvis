package com.jev.overseas.core

import com.jev.overseas.core.session.EditableBox
import com.jev.overseas.core.session.FillProcedure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FillProcedureTest {

    /** A box that ignores the first [ignoreWrites] writes, as an unfocused field sometimes does. */
    private class Box(val ignoreWrites: Int = 0, val transform: (String) -> String = { it }) : EditableBox {
        var text: String? = null
        val log = ArrayList<String>()
        private var writes = 0
        override fun setText(text: String): Boolean {
            log.add("set")
            if (writes++ >= ignoreWrites) this.text = transform(text)
            return true
        }
        override fun currentText(): String? = text
        override fun focus(): Boolean { log.add("focus"); return true }
    }

    private val waits = ArrayList<Long>()
    private fun fill(box: Box, text: String) = FillProcedure.fill(box, text) { waits.add(it) }

    @Test fun firstWriteTakes() {
        val box = Box()
        assertTrue(fill(box, "See you then."))
        assertEquals(listOf("set"), box.log)
        assertEquals(listOf(150L), waits)
    }

    @Test fun focusAndRetryOnce() {
        val box = Box(ignoreWrites = 1)
        assertTrue(fill(box, "See you then."))
        assertEquals(listOf("set", "focus", "set"), box.log)
    }

    @Test fun givesUpAfterTheRetry() {
        val box = Box(ignoreWrites = 5)
        assertFalse(fill(box, "See you then."))
        assertEquals(listOf("set", "focus", "set"), box.log)
    }

    @Test fun changedTextIsAFailure() {
        assertFalse(fill(Box(transform = { it.take(5) }), "See you then."))
        assertTrue(fill(Box(transform = { "$it " }), "See you then."))
    }

    @Test fun matching() {
        assertTrue(FillProcedure.matches("ok ", "ok"))
        assertFalse(FillProcedure.matches(null, "ok"))
        assertFalse(FillProcedure.matches(" ok", "ok"))
    }
}
