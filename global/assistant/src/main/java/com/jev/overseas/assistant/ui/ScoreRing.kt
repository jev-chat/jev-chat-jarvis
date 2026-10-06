package com.jev.overseas.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Locale

/**
 * The S ring: 0–5 around the
 * circle, the number and "/ 5" in the middle, always the same size. While Jev is still scoring, an accent arc runs round the track and
 * the centre says "Scoring". When the score arrives the number rolls up from 0
 * (about 400 ms), honouring the system animation scale.
 */
class ScoreRing(context: Context, private val ui: Ui, private val sizeDp: Int) : View(context) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ui.raised }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ui.mono
        textAlign = Paint.Align.CENTER
        color = ui.text
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ui.secondary
    }
    private val oval = RectF()

    private var target: Double? = null
    private var shown = 0.0
    private var scoring = false
    private var sweepStart = 0f
    private var caption: String? = null
    private var animator: ValueAnimator? = null

    /** A final score. [animate] rolls the number up from zero. */
    fun setScore(score: Double, color: Int, animate: Boolean) {
        stop()
        scoring = false
        caption = null
        target = score
        arc.color = color
        if (animate && animationsOn()) {
            animator = ValueAnimator.ofFloat(0f, score.toFloat()).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                addUpdateListener { shown = (it.animatedValue as Float).toDouble(); invalidate() }
                start()
            }
        } else {
            shown = score
            invalidate()
        }
    }

    /** Waiting for Jev: a moving arc and "Scoring". */
    fun setScoring() {
        stop()
        scoring = true
        caption = "Scoring"
        target = null
        arc.color = ui.accent
        if (animationsOn()) {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { sweepStart = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        invalidate()
    }

    /** No score: "Not scored" or "Blocked". */
    fun setEmpty(text: String) {
        stop()
        scoring = false
        target = null
        caption = text
        invalidate()
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = ui.dp(sizeDp)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = width * 0.085f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        val inset = stroke / 2 + 1
        oval.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(oval, 0f, 360f, false, track)
        val cx = width / 2f
        val cy = height / 2f
        when {
            scoring -> {
                canvas.drawArc(oval, sweepStart - 90f, 70f, false, arc)
                small.textSize = width * 0.15f
                canvas.drawText(caption ?: "", cx, cy + small.textSize / 3, small)
            }
            target != null -> {
                canvas.drawArc(oval, -90f, (360.0 * (shown / 5.0)).toFloat(), false, arc)
                // "4.5" with a small "/ 5" under it: the scale is always visible, no verdict word.
                number.textSize = width * 0.27f
                small.textSize = width * 0.14f
                canvas.drawText(String.format(Locale.US, "%.1f", shown), cx, cy + number.textSize * 0.15f, number)
                canvas.drawText("/ 5", cx, cy + number.textSize * 0.15f + small.textSize * 1.15f, small)
            }
            else -> {
                small.textSize = width * 0.14f
                val words = (caption ?: "").split(" ")
                if (words.size >= 2) {
                    canvas.drawText(words[0], cx, cy - small.textSize * 0.15f, small)
                    canvas.drawText(words.drop(1).joinToString(" "), cx, cy + small.textSize * 0.95f, small)
                } else {
                    canvas.drawText(caption ?: "", cx, cy + small.textSize / 3, small)
                }
            }
        }
        contentDescription = when {
            scoring -> "Scoring"
            target != null -> String.format(Locale.US, "Score %.1f out of 5", target)
            else -> caption
        }
    }

    private fun animationsOn(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}
