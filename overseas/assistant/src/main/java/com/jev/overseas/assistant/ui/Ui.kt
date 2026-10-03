package com.jev.overseas.assistant.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.jev.overseas.assistant.R

/** Colours, sizes and small view builders for the dark panel. */
class Ui(val context: Context) {

    val base = color(R.color.bg_base)
    val surface = color(R.color.bg_surface)
    val raised = color(R.color.bg_raised)
    val line = color(R.color.line)
    val text = color(R.color.text_primary)
    val secondary = color(R.color.text_secondary)
    val muted = color(R.color.text_muted)
    val accent = color(R.color.accent)
    val onAccent = color(R.color.on_accent)
    val ok = color(R.color.ok)
    val warn = color(R.color.warn)
    val bad = color(R.color.bad)
    val info = color(R.color.info)

    val mono: Typeface = Typeface.MONOSPACE
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    fun dp(v: Number): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** S colour bands: display only, not a calibrated judgement. */
    fun scoreColor(score: Double): Int = when {
        score >= 4.0 -> ok
        score >= 3.0 -> accent
        else -> warn
    }

    // ------------------------------------------------------------------ backgrounds

    fun rounded(fill: Int, radiusDp: Number, stroke: Int? = null, strokeDp: Number = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(strokeDp), stroke)
        }

    fun pressable(view: View, fill: Int, radiusDp: Number, stroke: Int? = null) {
        view.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(fill, radiusDp, stroke), null)
        view.isClickable = true
        view.isFocusable = true
    }

    // ------------------------------------------------------------------ text

    fun label(
        value: CharSequence,
        sizeSp: Float = 13f,
        color: Int = text,
        bold: Boolean = false,
        monospace: Boolean = false,
        lines: Int = 0,
    ): TextView = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setLineSpacing(0f, 1.3f)
        typeface = when {
            monospace -> mono
            bold -> medium
            else -> Typeface.DEFAULT
        }
        if (lines > 0) {
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
        }
        includeFontPadding = false
    }

    /** Small sentence-case tag with a coloured outline, e.g. "Top pick · Direct". */
    fun tag(value: String, color: Int): TextView = label(value, 11f, color).apply {
        letterSpacing = 0.02f
        setPadding(dp(6), dp(2), dp(6), dp(2))
        background = rounded(0x00000000, 8, color)
        // Keep tags to their text width even inside a vertical column.
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun icon(res: Int, tint: Int, sizeDp: Int = 18, onClick: (() -> Unit)? = null, description: String? = null): View {
        val image = ImageView(context).apply {
            setImageResource(res)
            setColorFilter(tint)
            contentDescription = description
        }
        if (onClick == null) return image.apply { layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)) }
        // A 48 dp touch target around a small glyph.
        return FrameLayout(context).apply {
            addView(image, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            minimumWidth = dp(40)
            contentDescription = description
            pressable(this, 0x00000000, 20)
            setOnClickListener { onClick() }
        }
    }

    // ------------------------------------------------------------------ controls

    fun primaryButton(value: String, enabled: Boolean = true, onClick: () -> Unit): TextView =
        label(value, 13f, if (enabled) onAccent else muted, bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            pressable(this, if (enabled) accent else raised, 12)
            isEnabled = enabled
            if (enabled) setOnClickListener { onClick() }
        }

    fun ghostButton(value: String, color: Int = text, onClick: () -> Unit): TextView =
        label(value, 13f, color).apply {
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            pressable(this, 0x00000000, 12, line)
            setOnClickListener { onClick() }
        }

    fun chip(value: String, selected: Boolean, onClick: () -> Unit): TextView =
        label(value, 13f, if (selected) accent else secondary).apply {
            gravity = Gravity.CENTER
            minHeight = dp(36)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            pressable(this, if (selected) raised else 0x00000000, 18, if (selected) accent else line)
            setOnClickListener { onClick() }
        }

    /** A 4 dp progress bar, [fraction] of it filled with [color]. */
    fun bar(fraction: Double, color: Int): View = object : View(context) {
        private val track = rounded(raised, 2)
        private val fill = rounded(color, 2)
        override fun onDraw(canvas: android.graphics.Canvas) {
            track.setBounds(0, 0, width, height); track.draw(canvas)
            val w = (width * fraction.coerceIn(0.0, 1.0)).toInt()
            if (w > 0) { fill.setBounds(0, 0, w, height); fill.draw(canvas) }
        }
    }.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)) }

    // ------------------------------------------------------------------ layout

    fun column(paddingDp: Int = 0, gapDp: Int = 0): LinearLayout = object : LinearLayout(context) {
        override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
            if (gapDp > 0 && childCount > 0 && params is LayoutParams) params.topMargin = maxOf(params.topMargin, dp(gapDp))
            super.addView(child, index, params)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
    }

    fun row(gapDp: Int = 6): LinearLayout = object : LinearLayout(context) {
        override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
            if (childCount > 0 && params is LayoutParams) params.leftMargin = maxOf(params.leftMargin, dp(gapDp))
            super.addView(child, index, params)
        }
    }.apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    /** A wrapping row of chips. */
    fun flow(): FlowLayout = FlowLayout(context, dp(6), dp(6))

    fun card(fill: Int = surface, stroke: Int = line, paddingDp: Int = 10, gapDp: Int = 0): LinearLayout =
        column(paddingDp, gapDp).apply { background = rounded(fill, 10, stroke) }

    fun spacer(): View = View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

    fun weighted(view: View, weight: Float = 1f): View = view.apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
    }

    fun matchWidth(view: View): View = view.apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

/** Lays children out left to right and wraps to a new line when the row is full. */
class FlowLayout(context: Context, private val hGap: Int, private val vGap: Int) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            child.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                x = 0; y += rowHeight + vGap; rowHeight = 0
            }
            x += child.measuredWidth + hGap
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + rowHeight + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                x = 0; y += rowHeight + vGap; rowHeight = 0
            }
            child.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + child.measuredWidth, paddingTop + y + child.measuredHeight)
            x += child.measuredWidth + hGap
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
    }
}
