package com.jev.overseas.core

import com.jev.overseas.core.engine.Lexicon
import com.jev.overseas.core.testing.chat
import com.jev.overseas.core.testing.them
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiconTest {

    @Test fun onlyAbbreviationsThatOccur() {
        val g = Lexicon.glossaryFor(listOf("need it asap tbh", "lmk"))
        assertEquals(setOf("asap", "tbh", "lmk"), g.keys)
        assertEquals("as soon as possible", g["asap"])
    }

    @Test fun wordBoundaries() {
        assertTrue(Lexicon.glossaryFor(listOf("the beta release is ready", "potatoes")).isEmpty())
        assertEquals(setOf("eta"), Lexicon.glossaryFor(listOf("what's the eta?")).keys)
    }

    @Test fun caseInsensitive() {
        assertEquals(setOf("asap", "fyi", "eod"), Lexicon.glossaryFor(listOf("ASAP please", "FYI", "by EOD")).keys)
    }

    @Test fun singleLetterOnlyAsAWholeReply() {
        assertEquals(setOf("k"), Lexicon.glossaryFor(listOf("K")).keys)
        assertEquals(setOf("k"), Lexicon.glossaryFor(listOf("k.")).keys)
        assertTrue(Lexicon.glossaryFor(listOf("plan k is fine")).isEmpty())
    }

    @Test fun conversationGlossary() {
        assertEquals(setOf("pls", "tmrw"), chat(them("pls send tmrw")).glossary.keys)
    }
}
