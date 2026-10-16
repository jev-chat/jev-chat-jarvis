package com.jev.probe.jev

import android.util.Log
import com.jev.probe.core.Analysis
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Choice
import com.jev.probe.core.Prefs
import com.jev.probe.core.RankedReply
import com.jev.probe.core.Score
import com.jev.probe.core.kb.ChatContext
import org.json.JSONObject

/**
 * The Jev judgment route only: the 7 judgment questions in one call, and the
 * ranking question over already-drafted candidates. Reads judgeProvider /
 * judgeBaseUrl / judgeKey / judgeModel from [Prefs]; nothing generative here.
 */
class JudgeClient(private val prefs: Prefs) {

    /**
     * The 7 judgment questions (fast, ~1s). Errors are returned, not thrown.
     *
     * @param ctx D-stage knowledge context; null or empty means the request body
     *        is byte-for-byte what v1.2 sent.
     */
    fun judge(snapshot: ChatSnapshot, relationship: String, ctx: ChatContext? = null): Analysis {
        val start = System.currentTimeMillis()
        return try {
            val answers = postDecisions(
                snapshot, relationship, ctx,
                JevQuestions.judge(),
                allowImage = true
            )
            Analysis(
                trueIntent = parseChoice(answers.optJSONObject("true_intent")),
                dangerLevel = parseScore(answers.optJSONObject("danger_level")),
                sheNeeds = parseChoice(answers.optJSONObject("she_needs")),
                shouldReplyNow = answers.optJSONObject("should_reply_now")?.optDouble("noul"),
                bestAction = parseChoice(answers.optJSONObject("best_action")),
                tensionResolved = answers.optJSONObject("tension_resolved")?.optDouble("noul"),
                literalQuestion = answers.optJSONObject("literal_question")?.optDouble("noul"),
                rankedReplies = emptyList(),
                latencyMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            Log.w(TAG, "judge failed: ${e.message}")
            Analysis(null, null, null, null, null, null, null, emptyList(),
                System.currentTimeMillis() - start, error = e.message ?: "判断接口请求失败")
        }
    }

    /** Ask Jev which of the candidate replies is best; throws on failure. */
    fun rank(
        snapshot: ChatSnapshot,
        relationship: String,
        candidates: List<String>,
        ctx: ChatContext? = null
    ): List<RankedReply> {
        val questions = JSONObject().put("best_reply",
            JevQuestions.rankQuestion(candidates).getJSONObject("best_reply"))
        val answers = postDecisions(snapshot, relationship, ctx, questions, allowImage = false)
        return parseRanked(answers.optJSONObject("best_reply"), candidates)
    }

    /**
     * POST one decisions request, with the knowledge fields when there are any.
     *
     * Defensive retry: whether the live `alpha/decisions` endpoint accepts the
     * new `background` / `history` state fields or rejects unknown ones with a
     * 4xx is not verified against production yet (see the A-stage report). If a
     * request carrying them comes back 4xx, it is sent again once without them,
     * so an unverified field can degrade the analysis but never break it.
     *
     * Image fallback (vision judge): a request carrying a screenshot that
     * returns 4xx or any other failure is sent again once without the image
     * (status + byte count only in the log). This runs first so it cannot
     * swallow the background/history fallback, and the other way around.
     *
     * [allowImage] is true only for [judge]; [rank] never attaches a screenshot.
     */
    private fun postDecisions(
        snapshot: ChatSnapshot,
        relationship: String,
        ctx: ChatContext?,
        questions: JSONObject,
        allowImage: Boolean
    ): JSONObject {
        val background = ctx?.background(relationship) ?: ""
        val history = ctx?.history ?: emptyList()
        val enriched = background.isNotBlank() || history.isNotEmpty()
        val jpeg = if (allowImage) snapshot.image else null

        fun once(withImage: Boolean, withCtx: Boolean): JSONObject {
            val state = if (withCtx)
                JevQuestions.buildState(snapshot, relationship, background, history)
            else
                JevQuestions.buildState(snapshot, relationship)
            return send(state, questions, if (withImage) jpeg else null)
        }

        return try {
            once(jpeg != null, enriched)
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            if (jpeg != null) {
                val status = (e as? ApiException)?.status
                Log.w(TAG, "judge with image HTTP ${status ?: "-"} bytes=${jpeg.size}; retrying without image")
                try {
                    once(false, enriched)
                } catch (e2: InterruptedException) {
                    throw e2
                } catch (e2: ApiException) {
                    if (enriched && e2.status != null && e2.status in 400..499) {
                        Log.w(TAG, "judge HTTP ${e2.status} with background/history; retrying plain")
                        once(false, false)
                    } else throw e2
                }
            } else if (e is ApiException && enriched && e.status != null && e.status in 400..499) {
                Log.w(TAG, "judge HTTP ${e.status} with background/history; retrying plain")
                once(false, false)
            } else throw e
        }
    }

    private fun send(state: JSONObject, questions: JSONObject, jpeg: ByteArray? = null): JSONObject {
        val url = prefs.judgeEndpoint()
        val built = JudgeRequest.build(HttpJson.headersFor(url), jpeg)
        val body = JSONObject()
            .put("model", prefs.judgeModel)
            .put("state", state)
            .put("questions", questions)
        val asset = built.asset
        if (asset != null) {
            state.put("screenshot", built.screenshotRef)
            state.put("screenshot_note", built.screenshotNote)
            body.put("assets", JSONObject().put(asset.ref, JSONObject()
                .put("type", asset.type)
                .put("format", asset.format)
                .put("data_base64", asset.dataBase64)))
            Log.i(TAG, "judge request with image bytes=${jpeg?.size ?: 0}")
        }
        val resp = HttpJson.post(url, prefs.judgeKey, body, Route.JUDGE, built.headers)
        return resp.optJSONObject("answers") ?: JSONObject()
    }

    private fun parseChoice(o: JSONObject?): Choice? {
        o ?: return null
        val probs = HashMap<String, Double>()
        o.optJSONObject("probabilities")?.let { p ->
            p.keys().forEach { k -> probs[k] = p.optDouble(k) }
        }
        return Choice(o.optString("choice"), o.optDouble("confidence", 0.0), probs)
    }

    private fun parseScore(o: JSONObject?): Score? {
        o ?: return null
        val legend = o.optJSONObject("legend")
        val maxLevel = legend?.keys()?.asSequence()?.mapNotNull { it.toIntOrNull() }?.maxOrNull() ?: 9
        return Score(o.optDouble("score", 0.0), o.optDouble("confidence", 0.0), maxLevel)
    }

    private fun parseRanked(o: JSONObject?, candidates: List<String>): List<RankedReply> {
        val keys = listOf("reply_a", "reply_b", "reply_c")
        val probs = o?.optJSONObject("probabilities")
        val list = candidates.mapIndexed { i, text ->
            RankedReply(text, probs?.optDouble(keys.getOrElse(i) { "" }, 0.0) ?: 0.0)
        }
        return list.sortedByDescending { it.prob }
    }

    companion object { private const val TAG = "JEVASSIST" }
}
