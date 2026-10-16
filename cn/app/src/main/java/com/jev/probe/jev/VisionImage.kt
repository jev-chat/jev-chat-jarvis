package com.jev.probe.jev

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * TypeSafe Jev image-input size rules (preview as of 2026-10-14).
 *
 * Each side must be 256..1568 px and the pixel count 78_400..2_458_624;
 * the API returns 400 "Invalid image dimensions" otherwise and does not
 * rescale. [targetSize] is the matching shrink (never upscale); null means
 * the source is too small to send.
 *
 * Pure Kotlin on purpose: unit-tested on the JVM with no Android types.
 */
internal object VisionImage {
    const val MIN_SIDE = 256
    const val MAX_SIDE = 1568
    const val MIN_PIXELS = 78_400
    const val MAX_PIXELS = 2_458_624

    /**
     * Target JPEG pixel size for a screenshot of [w]×[h], or null if it
     * cannot be made to fit the TypeSafe window without upscaling.
     *
     * 1. Scale so the long side is ≤ [MAX_SIDE], keeping aspect ratio.
     * 2. If total pixels still exceed [MAX_PIXELS], scale down again.
     * 3. If either side is then < [MIN_SIDE] or pixels < [MIN_PIXELS], null.
     */
    fun targetSize(w: Int, h: Int): Pair<Int, Int>? {
        if (w <= 0 || h <= 0) return null
        var tw = w
        var th = h
        val longSide = maxOf(tw, th)
        if (longSide > MAX_SIDE) {
            if (tw >= th) {
                th = ((th.toDouble() * MAX_SIDE) / tw).roundToInt().coerceAtLeast(1)
                tw = MAX_SIDE
            } else {
                tw = ((tw.toDouble() * MAX_SIDE) / th).roundToInt().coerceAtLeast(1)
                th = MAX_SIDE
            }
        }
        var pixels = tw.toLong() * th
        if (pixels > MAX_PIXELS) {
            val s = sqrt(MAX_PIXELS.toDouble() / pixels)
            tw = (tw * s).roundToInt().coerceAtLeast(1)
            th = (th * s).roundToInt().coerceAtLeast(1)
            val long2 = maxOf(tw, th)
            if (long2 > MAX_SIDE) {
                val s2 = MAX_SIDE.toDouble() / long2
                tw = (tw * s2).roundToInt().coerceAtLeast(1)
                th = (th * s2).roundToInt().coerceAtLeast(1)
            }
            pixels = tw.toLong() * th
        }
        if (tw < MIN_SIDE || th < MIN_SIDE) return null
        if (tw > MAX_SIDE || th > MAX_SIDE) return null
        if (pixels < MIN_PIXELS || pixels > MAX_PIXELS) return null
        return tw to th
    }
}
