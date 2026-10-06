package com.jev.overseas.core

import com.jev.overseas.core.json.MiniJson
import com.jev.overseas.core.net.Answer
import com.jev.overseas.core.net.HttpResponse
import com.jev.overseas.core.net.HttpTransport
import com.jev.overseas.core.net.ModelConfig
import com.jev.overseas.core.net.ModelException
import com.jev.overseas.core.net.ModelException.Kind
import com.jev.overseas.core.net.OpenRouterGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class OpenRouterGatewayTest {

    private val key = "test-key-0123456789abcdef"

    private class Recorder(var respond: (String) -> HttpResponse) : HttpTransport {
        val urls = ArrayList<String>()
        val headers = ArrayList<Map<String, String>>()
        val bodies = ArrayList<String?>()
        val methods = ArrayList<String>()
        override fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpResponse {
            urls.add(url); this.headers.add(headers); bodies.add(body); methods.add("POST")
            return respond(url)
        }
        override fun get(url: String, headers: Map<String, String>, timeoutMs: Int): HttpResponse {
            urls.add(url); this.headers.add(headers); bodies.add(null); methods.add("GET")
            return respond(url)
        }
    }

    private fun gateway(t: Recorder, apiKey: String = key) = OpenRouterGateway(ModelConfig(apiKey = apiKey), t)

    private val questions = mapOf<String, Any?>("q1" to mapOf("type" to "noul"), "s1" to mapOf("type" to "score"))

    private fun ok(body: String) = Recorder { HttpResponse(200, body) }

    private fun expectKind(kind: Kind, block: () -> Unit): ModelException {
        try {
            block()
        } catch (e: ModelException) {
            assertEquals(kind, e.kind)
            assertFalse("key leaked into message", e.message!!.contains(key))
            return e
        }
        fail("expected $kind")
        throw AssertionError()
    }

    @Test fun decisionsRequestShape() {
        val t = ok("""{"answers":{"q1":{"type":"noul","noul":0.8}},"usage":{"input_tokens":10,"output_tokens":2,"cost":0.0003},"model":"m"}""")
        val r = gateway(t).decisions(mapOf("latest_messages" to listOf<Any>()), questions)
        assertEquals(ModelConfig.DEFAULT_DECISIONS_URL, t.urls.single())
        assertEquals("Bearer $key", t.headers.single()["Authorization"])
        val body = MiniJson.parseObject(t.bodies.single()!!)
        assertEquals(ModelConfig.DEFAULT_JEV_MODEL, body["model"])
        assertTrue(body["state"] is Map<*, *>)
        assertEquals(setOf("q1", "s1"), (body["questions"] as Map<*, *>).keys)
        assertEquals(0.8, (r.answers["q1"] as Answer.Noul).yes, 1e-9)
        assertEquals(0.0003, r.usage!!.cost, 1e-12)
        assertEquals(10, r.usage!!.inputTokens)
    }

    @Test fun chatRequestShape() {
        val t = ok("""{"choices":[{"message":{"content":"hi"}}],"usage":{"prompt_tokens":5,"completion_tokens":1,"cost":0.00001}}""")
        val r = gateway(t).chat("sys", "usr", 0.7, 320, json = true)
        assertEquals("hi", r.content)
        assertEquals(5, r.usage!!.inputTokens)
        val body = MiniJson.parseObject(t.bodies.single()!!)
        assertEquals(ModelConfig.DEFAULT_DRAFT_MODEL, body["model"])
        assertEquals(mapOf("type" to "json_object"), body["response_format"])
        assertEquals(2, (body["messages"] as List<*>).size)
        assertTrue(body.containsKey("frequency_penalty"))
    }

    @Test fun chatWithoutContentIsBadResponse() {
        expectKind(Kind.BAD_RESPONSE) { gateway(ok("""{"choices":[]}""")).chat("s", "u", 0.7, 10, false) }
    }

    @Test fun statusCodesMapToKinds() {
        val cases = mapOf(401 to Kind.AUTH, 402 to Kind.AUTH, 403 to Kind.AUTH, 408 to Kind.TIMEOUT, 429 to Kind.RATE_LIMITED,
            500 to Kind.SERVER, 503 to Kind.SERVER, 400 to Kind.BAD_REQUEST, 404 to Kind.BAD_REQUEST, 422 to Kind.BAD_REQUEST)
        for ((status, kind) in cases) {
            val e = expectKind(kind) {
                gateway(Recorder { HttpResponse(status, """{"error":{"message":"bad key $key"}}""") }).decisions(emptyMap(), questions)
            }
            assertEquals(status, e.status)
        }
    }

    @Test fun errorTextNeverContainsTheKey() {
        val e = expectKind(Kind.AUTH) { gateway(Recorder { HttpResponse(401, """{"error":{"message":"bad key $key"}}""") }).decisions(emptyMap(), questions) }
        assertTrue(e.message!!.contains("[key]"))
        assertFalse(e.message!!.contains(key))
    }

    /** A body that is not the provider's error object may echo the request; it is never copied into the message. */
    @Test fun rawErrorBodyIsNeverUsed() {
        val e = expectKind(Kind.AUTH) { gateway(Recorder { HttpResponse(401, "plain text with $key and the chat in it") }).decisions(emptyMap(), questions) }
        assertFalse(e.message!!.contains(key))
        assertFalse(e.message!!.contains("chat"))
    }

    @Test fun nonJsonIsBadResponse() {
        expectKind(Kind.BAD_RESPONSE) { gateway(ok("<html>oops</html>")).decisions(emptyMap(), questions) }
    }

    @Test fun missingAnswersIsBadResponse() {
        expectKind(Kind.BAD_RESPONSE) { gateway(ok("""{"result":{}}""")).decisions(emptyMap(), questions) }
    }

    @Test fun malformedAnswersAreAbsentNotFilled() {
        val qs = linkedMapOf<String, Any?>(
            "outOfRange" to mapOf("type" to "noul"), "notNumber" to mapOf("type" to "noul"), "fine" to mapOf("type" to "noul"),
            "badSum" to mapOf("type" to "score"), "gap" to mapOf("type" to "score"), "good" to mapOf("type" to "score"),
            "absent" to mapOf("type" to "noul"),
        )
        val body = """{"answers":{
            "outOfRange":{"type":"noul","noul":1.4},
            "notNumber":{"type":"noul","noul":"high"},
            "fine":{"type":"noul","noul":0.0},
            "badSum":{"type":"score","score":2.0,"probabilities":{"0":0.2,"1":0.2,"2":0.2}},
            "gap":{"type":"score","score":1.0,"probabilities":{"0":0.5,"2":0.5}},
            "good":{"type":"score","score":1.2,"probabilities":{"1":0.8,"0":0.0,"2":0.2},"confidence":0.7},
            "unasked":{"type":"noul","noul":0.5}
        }}"""
        val r = gateway(ok(body)).decisions(emptyMap(), qs)
        assertEquals(setOf("fine", "good"), r.answers.keys)
        val good = r.answers["good"] as Answer.Score
        assertEquals(listOf(0.0, 0.8, 0.2), good.probabilities)
        assertEquals(0.7, good.confidence, 1e-9)
    }

    @Test fun timeoutAndNetwork() {
        expectKind(Kind.TIMEOUT) { gateway(Recorder { throw SocketTimeoutException("slow") }).decisions(emptyMap(), questions) }
        expectKind(Kind.NETWORK) { gateway(Recorder { throw IOException("down") }).decisions(emptyMap(), questions) }
    }

    @Test fun blankKeyIsAuthWithoutRequest() {
        val t = ok("{}")
        expectKind(Kind.AUTH) { gateway(t, apiKey = " ").decisions(emptyMap(), questions) }
        expectKind(Kind.AUTH) { gateway(t, apiKey = "").keyInfo() }
        assertTrue(t.urls.isEmpty())
    }

    @Test fun keyInfoUsesGetAndParsesData() {
        val t = ok("""{"data":{"label":"test-label","usage":0.0235,"limit":5.0,"limit_remaining":4.9765,"is_free_tier":false}}""")
        val info = gateway(t).keyInfo()
        assertEquals(listOf("GET"), t.methods)
        assertEquals(ModelConfig.DEFAULT_KEY_URL, t.urls.single())
        assertEquals("Bearer $key", t.headers.single()["Authorization"])
        assertEquals(0.0235, info.usage, 1e-9)
        assertEquals(5.0, info.limit!!, 1e-9)
        assertEquals(4.9765, info.limitRemaining!!, 1e-9)
    }

    @Test fun keyInfoUnlimitedAndErrors() {
        val info = gateway(ok("""{"data":{"label":"x","usage":0,"limit":null}}""")).keyInfo()
        assertNull(info.limit)
        expectKind(Kind.AUTH) { gateway(Recorder { HttpResponse(401, """{"error":{"message":"No auth credentials found"}}""") }).keyInfo() }
        expectKind(Kind.BAD_RESPONSE) { gateway(ok("""{"nodata":1}""")).keyInfo() }
    }

    @Test fun configToStringHidesKey() {
        assertFalse(ModelConfig(apiKey = key).toString().contains(key))
    }
}
