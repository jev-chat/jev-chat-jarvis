package com.jev.overseas.core.net

import com.jev.overseas.core.json.MiniJson
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Why a model call failed. [retryable] failures may be sent again unchanged, once. */
class ModelException(
    val kind: Kind,
    val status: Int?,
    detail: String,
) : Exception(detail) {
    enum class Kind(val retryable: Boolean) {
        /** 401 / 403: the key is missing, wrong, or out of credit. */
        AUTH(false),
        /** Other 4xx: the request itself was rejected. */
        BAD_REQUEST(false),
        RATE_LIMITED(true),
        SERVER(true),
        TIMEOUT(true),
        NETWORK(true),
        /** A 2xx reply that is not the JSON shape we need. */
        BAD_RESPONSE(false),
        CANCELLED(false),
    }
}

/** Token and cost figures as the provider reports them. */
data class Usage(val inputTokens: Int, val outputTokens: Int, val cost: Double)

/** One answer from the decisions endpoint, already checked for shape. */
sealed class Answer {
    /** Probability that the answer to a yes/no question is yes. */
    data class Noul(val yes: Double) : Answer()

    /** [probabilities] has one entry per level, in level order. */
    data class Score(val score: Double, val probabilities: List<Double>, val confidence: Double) : Answer()
}

data class DecisionsResult(
    /** Only well-formed answers. A question that came back malformed or missing is absent. */
    val answers: Map<String, Answer>,
    val usage: Usage?,
    val model: String?,
)

data class ChatResult(val content: String, val usage: Usage?)

/** The two model routes the assistant uses. Implementations make exactly one HTTP attempt per call. */
interface ModelGateway {
    fun decisions(state: Map<String, Any?>, questions: Map<String, Any?>): DecisionsResult

    fun chat(system: String, user: String, temperature: Double, maxTokens: Int, json: Boolean): ChatResult
}

data class ModelConfig(
    val apiKey: String,
    val jevModel: String = DEFAULT_JEV_MODEL,
    val draftModel: String = DEFAULT_DRAFT_MODEL,
    val decisionsUrl: String = DEFAULT_DECISIONS_URL,
    val chatUrl: String = DEFAULT_CHAT_URL,
    val keyUrl: String = DEFAULT_KEY_URL,
    /** Whole-request deadline. Normal calls take 0.3–2 s; a call past this is retried once. */
    val timeoutMs: Int = 20_000,
) {
    /** Never prints the key. */
    override fun toString(): String = "ModelConfig(jevModel=$jevModel, draftModel=$draftModel, key=${if (apiKey.isBlank()) "none" else "set"})"

    companion object {
        const val DEFAULT_KEY_URL = "https://openrouter.ai/api/v1/key"
        const val DEFAULT_JEV_MODEL = "typesafe/jev-1.13"
        const val DEFAULT_DRAFT_MODEL = "deepseek/deepseek-chat-v3.1"
        const val DEFAULT_DECISIONS_URL = "https://openrouter.ai/api/alpha/decisions"
        const val DEFAULT_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions"
    }
}

data class HttpResponse(val status: Int, val body: String)

/** One request, no retries. Throws [IOException] on transport failure. */
fun interface HttpTransport {
    fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpResponse

    fun get(url: String, headers: Map<String, String>, timeoutMs: Int): HttpResponse =
        throw UnsupportedOperationException("GET is not supported by this transport")
}

/** What OpenRouter reports about the key itself. Amounts are in US dollars; [limit] is null when unlimited. */
data class KeyInfo(val label: String?, val usage: Double, val limit: Double?, val limitRemaining: Double?)

/**
 * Plain [HttpURLConnection]; works on the JVM and on Android.
 *
 * [timeoutMs] is a deadline for the whole request, not only for silence on the
 * socket: OpenRouter keeps a slow request alive by sending blank bytes, so a read
 * timeout alone never fires (a drafting call took 38 s on 2026-10-03). A watchdog
 * disconnects at the deadline and the call fails as a timeout, which the
 * assistant retries once.
 */
class UrlConnectionTransport : HttpTransport {
    override fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpResponse =
        send(url, "POST", headers, body, timeoutMs)

    override fun get(url: String, headers: Map<String, String>, timeoutMs: Int): HttpResponse =
        send(url, "GET", headers, null, timeoutMs)

    private fun send(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Int): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        val expired = AtomicBoolean(false)
        val watchdog = WATCHDOG.schedule({ expired.set(true); conn.disconnect() }, timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        try {
            conn.requestMethod = method
            conn.connectTimeout = minOf(timeoutMs, 15_000)
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            // Read in chunks and check the deadline between them: a server that trickles bytes never
            // trips the read timeout, and disconnect() from the watchdog does not unblock every JVM's read.
            val text = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (expired.get()) throw SocketTimeoutException("deadline of $timeoutMs ms passed")
                }
                out.toString(Charsets.UTF_8.name())
            } ?: ""
            if (expired.get()) throw SocketTimeoutException("deadline of $timeoutMs ms passed")
            return HttpResponse(status, text)
        } catch (e: IOException) {
            if (expired.get() && e !is SocketTimeoutException) throw SocketTimeoutException("deadline of $timeoutMs ms passed")
            throw e
        } finally {
            watchdog.cancel(false)
            conn.disconnect()
        }
    }

    private companion object {
        val WATCHDOG: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "jev-http-deadline").apply { isDaemon = true }
        }
    }
}

/**
 * OpenRouter: Jev on the decisions endpoint, the drafting model on chat
 * completions. The key is sent only in the Authorization header and never
 * appears in an exception message.
 */
class OpenRouterGateway(
    private val config: ModelConfig,
    private val transport: HttpTransport = UrlConnectionTransport(),
) : ModelGateway {

    private val headers: Map<String, String>
        get() = mapOf(
            "Authorization" to "Bearer ${config.apiKey}",
            "HTTP-Referer" to "https://github.com/jev-chat/jev-chat-jarvis",
            "X-Title" to "Jev Assistant",
        )

    override fun decisions(state: Map<String, Any?>, questions: Map<String, Any?>): DecisionsResult {
        val body = linkedMapOf<String, Any?>("model" to config.jevModel, "state" to state, "questions" to questions)
        val root = post(config.decisionsUrl, body)
        val raw = root["answers"] as? Map<*, *>
            ?: throw ModelException(ModelException.Kind.BAD_RESPONSE, 200, "Decisions reply has no answers")
        val answers = LinkedHashMap<String, Answer>()
        for (id in questions.keys) {
            parseAnswer(raw[id])?.let { answers[id] = it }
        }
        return DecisionsResult(answers, parseUsage(root["usage"], "input_tokens", "output_tokens"), root["model"] as? String)
    }

    override fun chat(system: String, user: String, temperature: Double, maxTokens: Int, json: Boolean): ChatResult {
        val body = linkedMapOf<String, Any?>(
            "model" to config.draftModel,
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to user),
            ),
            "temperature" to temperature,
            "max_tokens" to maxTokens,
            // Without this the drafting model sometimes repeats the same two replies until it runs out of tokens.
            "frequency_penalty" to 0.4,
        )
        if (json) body["response_format"] = mapOf("type" to "json_object")
        val root = post(config.chatUrl, body)
        val content = ((root["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>)
            ?.let { it["message"] as? Map<*, *> }
            ?.get("content") as? String
        if (content.isNullOrBlank()) {
            throw ModelException(ModelException.Kind.BAD_RESPONSE, 200, "Chat reply has no content")
        }
        return ChatResult(content, parseUsage(root["usage"], "prompt_tokens", "completion_tokens"))
    }

    /**
     * "Test connection": is the key valid, and how much credit is left. Free; no
     * model is called.
     */
    fun keyInfo(): KeyInfo {
        val root = send { transport.get(config.keyUrl, headers, config.timeoutMs) }
        val data = root["data"] as? Map<*, *>
            ?: throw ModelException(ModelException.Kind.BAD_RESPONSE, 200, "Key reply has no data")
        return KeyInfo(
            label = data["label"] as? String,
            usage = (data["usage"] as? Number)?.toDouble() ?: 0.0,
            limit = (data["limit"] as? Number)?.toDouble(),
            limitRemaining = (data["limit_remaining"] as? Number)?.toDouble(),
        )
    }

    private fun post(url: String, body: Map<String, Any?>): Map<String, Any?> =
        send { transport.postJson(url, headers, MiniJson.encode(body), config.timeoutMs) }

    private fun send(request: () -> HttpResponse): Map<String, Any?> {
        if (config.apiKey.isBlank()) throw ModelException(ModelException.Kind.AUTH, null, "No API key set")
        val response = try {
            request()
        } catch (e: SocketTimeoutException) {
            throw ModelException(ModelException.Kind.TIMEOUT, null, "The request timed out")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ModelException(ModelException.Kind.CANCELLED, null, "Cancelled")
        } catch (e: IOException) {
            if (Thread.currentThread().isInterrupted) {
                throw ModelException(ModelException.Kind.CANCELLED, null, "Cancelled")
            }
            throw ModelException(ModelException.Kind.NETWORK, null, "Network error: ${e.javaClass.simpleName}")
        }
        val status = response.status
        if (status !in 200..299) {
            val kind = when (status) {
                401, 402, 403 -> ModelException.Kind.AUTH
                408 -> ModelException.Kind.TIMEOUT
                429 -> ModelException.Kind.RATE_LIMITED
                in 500..599 -> ModelException.Kind.SERVER
                else -> ModelException.Kind.BAD_REQUEST
            }
            throw ModelException(kind, status, "HTTP $status: ${errorMessage(response.body)}")
        }
        return try {
            MiniJson.parseObject(response.body)
        } catch (e: IllegalArgumentException) {
            throw ModelException(ModelException.Kind.BAD_RESPONSE, status, "The reply was not valid JSON")
        }
    }

    /**
     * The provider's own error text, shortened. The raw body is never used: it can
     * echo parts of the request, and this text may reach the diagnostic log.
     */
    private fun errorMessage(body: String): String {
        val message = runCatching {
            val error = MiniJson.parseObject(body)["error"]
            (error as? Map<*, *>)?.get("message") as? String ?: error as? String
        }.getOrNull()
        return (message ?: "no error message").replace(config.apiKey, "[key]").take(200)
    }

    private fun parseAnswer(raw: Any?): Answer? {
        val m = raw as? Map<*, *> ?: return null
        return when (m["type"]) {
            "noul" -> (m["noul"] as? Number)?.toDouble()?.takeIf { it in 0.0..1.0 }?.let { Answer.Noul(it) }
            "score" -> {
                val score = (m["score"] as? Number)?.toDouble() ?: return null
                val probs = m["probabilities"] as? Map<*, *> ?: return null
                val ordered = probs.entries
                    .map { (k, v) -> (k.toString().toIntOrNull() ?: return null) to ((v as? Number)?.toDouble() ?: return null) }
                    .sortedBy { it.first }
                if (ordered.isEmpty() || ordered.map { it.first } != ordered.indices.toList()) return null
                val total = ordered.sumOf { it.second }
                if (total < 0.9 || total > 1.1) return null
                Answer.Score(score, ordered.map { it.second }, (m["confidence"] as? Number)?.toDouble() ?: 0.0)
            }
            else -> null
        }
    }

    private fun parseUsage(raw: Any?, inKey: String, outKey: String): Usage? {
        val m = raw as? Map<*, *> ?: return null
        return Usage(
            (m[inKey] as? Number)?.toInt() ?: 0,
            (m[outKey] as? Number)?.toInt() ?: 0,
            (m["cost"] as? Number)?.toDouble() ?: 0.0,
        )
    }
}
