package com.jev.probe.capture

/**
 * Shared crop / side / merge constants for screenshot OCR (accessibility
 * whole-screen path) and live-frame parsing. Keep [TOP_CROP] / [BOTTOM_CROP]
 * identical to the values [ChatCaptureService] historically used.
 */
object OcrLayout {
    /** Top status + title bar, as a fraction of image height. */
    const val TOP_CROP = 0.12f

    /** Keep-region ends here; below is the input area (bottom 16%). */
    const val BOTTOM_CROP = 0.84f

    /** Line center within this fraction of image width of the midline → system. */
    const val SYSTEM_CENTER_BAND = 0.08f

    /** Centered system hints must also be narrower than this fraction of width. */
    const val SYSTEM_MAX_WIDTH = 0.60f

    /** Horizontal center below this fraction of width → other. */
    const val OTHER_MAX_CENTER = 0.45f

    /** Horizontal center above this fraction of width → me. */
    const val ME_MIN_CENTER = 0.55f

    /** Middle band: left edge below this fraction of width → other, else me. */
    const val OTHER_LEFT_FALLBACK = 0.20f

    /** Same-side lines closer than this × previous line height merge into one bubble. */
    const val MERGE_GAP = 0.8f

    /** Group-chat nickname above a bubble: at most this many characters. */
    const val NICK_MAX_LEN = 8

    /** OcrDedupe: normalized edit-distance similarity at or above this is "same". */
    const val DEDUPE_SIMILARITY = 0.85
}
