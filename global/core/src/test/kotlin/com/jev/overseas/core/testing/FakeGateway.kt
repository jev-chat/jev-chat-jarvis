package com.jev.overseas.core.testing

import com.jev.overseas.core.engine.ChatMessage
import com.jev.overseas.core.engine.Conversation
import com.jev.overseas.core.engine.Sender
import com.jev.overseas.core.net.Answer
import com.jev.overseas.core.net.ChatResult
import com.jev.overseas.core.net.DecisionsResult
import com.jev.overseas.core.net.ModelGateway
import com.jev.overseas.core.net.Usage

/**
 * A scripted [ModelGateway]. Each route has a handler that may return a result or
 * throw (a ModelException, or anything else to test robustness). Every request is
 * recorded so tests can assert on what was sent and how often.
 */
class FakeGateway(
    var onDecisions: (state: Map<String, Any?>, questions: Map<String, Any?>) -> DecisionsResult =
        { _, questions -> Answers.all(questions) },
    var onChat: (system: String, user: String) -> ChatResult =
        { _, _ -> ChatResult(Answers.replies("Sure, that works.", "Yes, happy to."), USAGE) },
) : ModelGateway {

    data class Call(
        val route: String,
        val state: Map<String, Any?>? = null,
        val questions: Map<String, Any?>? = null,
        val system: String? = null,
        val user: String? = null,
    ) {
        /** True for a candidate check (its state carries the reply being checked). */
        val isCheck: Boolean get() = state?.containsKey("candidate_reply") == true
    }

    private val log = ArrayList<Call>()

    val calls: List<Call> get() = synchronized(log) { log.toList() }
    val chats: List<Call> get() = calls.filter { it.route == "chat" }
    val decisions: List<Call> get() = calls.filter { it.route == "decisions" }

    override fun decisions(state: Map<String, Any?>, questions: Map<String, Any?>): DecisionsResult {
        synchronized(log) { log.add(Call("decisions", state = state, questions = questions)) }
        return onDecisions(state, questions)
    }

    override fun chat(system: String, user: String, temperature: Double, maxTokens: Int, json: Boolean): ChatResult {
        synchronized(log) { log.add(Call("chat", system = system, user = user)) }
        return onChat(system, user)
    }

    companion object {
        val USAGE = Usage(100, 20, 0.0001)
    }
}

/** Builders for decisions answers. */
object Answers {

    fun noul(p: Double) = Answer.Noul(p)

    /** All probability on one level. */
    fun level(level: Int, size: Int = 6) =
        Answer.Score(level.toDouble(), List(size) { if (it == level) 1.0 else 0.0 }, 0.9)

    /** A probability-weighted score from explicit probabilities. */
    fun spread(vararg probabilities: Double) =
        Answer.Score(probabilities.withIndex().sumOf { it.index * it.value }, probabilities.toList(), 0.8)

    /**
     * An answer for every question in [questions]. Yes/no questions get [noul] (null
     * leaves the question out); score questions get [score].
     */
    fun all(
        questions: Map<String, Any?>,
        noul: (String) -> Double? = { 0.05 },
        score: (String, Int) -> Answer.Score? = { _, size -> level(0, size) },
        usage: Usage? = FakeGateway.USAGE,
    ): DecisionsResult {
        val out = LinkedHashMap<String, Answer>()
        for ((id, q) in questions) {
            val m = q as Map<*, *>
            when (m["type"]) {
                "noul" -> noul(id)?.let { out[id] = Answer.Noul(it) }
                "score" -> score(id, (m["criteria"] as List<*>).size)?.let { out[id] = it }
            }
        }
        return DecisionsResult(out, usage, "fake")
    }

    fun replies(first: String, second: String) =
        """{"replies":[{"label":"a","text":"$first"},{"label":"b","text":"$second"}]}"""
}

fun them(text: String, readable: Boolean = true) = ChatMessage(Sender.OTHER, text, readable)
fun me(text: String) = ChatMessage(Sender.USER, text)
fun chat(vararg messages: ChatMessage) = Conversation(messages.toList())
