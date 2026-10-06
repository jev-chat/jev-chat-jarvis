package com.jev.overseas.core

import com.jev.overseas.core.json.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MiniJsonTest {

    @Test fun parsesNestedValuesAndKeepsKeyOrder() {
        val v = MiniJson.parseObject("""{"b": [1, -2, 3.5, true, false, null], "a": {"x": "y"}, "e": []}""")
        assertEquals(listOf("b", "a", "e"), v.keys.toList())
        assertEquals(listOf(1L, -2L, 3.5, true, false, null), v["b"])
        assertEquals(mapOf("x" to "y"), v["a"])
        assertEquals(emptyList<Any?>(), v["e"])
    }

    @Test fun parsesStringEscapes() {
        val v = MiniJson.parseObject("{\"s\": \"a\\\"b\\\\c\\n\\t\\u0041\\u200e\"}")
        assertEquals("a\"b\\c\n\tA‎", v["s"])
    }

    @Test fun parsesExponentAsDouble() {
        assertEquals(1500.0, MiniJson.parse("1.5e3"))
        assertNull(MiniJson.parse(" null "))
    }

    @Test fun rejectsMalformedInput() {
        for (bad in listOf("{", "{\"a\" 1}", "[1,]", "\"open", "{} x", "tru", "{\"a\": 01x}")) {
            assertThrows(bad, MiniJson.JsonException::class.java) { MiniJson.parse(bad) }
        }
    }
}
