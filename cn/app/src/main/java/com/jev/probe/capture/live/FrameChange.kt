package com.jev.probe.capture.live

/**
 * Cheap perceptual hash over a caller-scaled grayscale thumbnail.
 *
 * No Android types: the service downscales the Bitmap to [width]×[height]
 * and converts it to 0..255 luminance; this object only hashes those pixels
 * and compares two hashes.
 *
 * Hash is average-hash against mid-gray (128), packed into a [LongArray].
 * Uniform black (0) and uniform white (255) therefore differ in every bit,
 * which is what we want for "the screen actually changed".
 */
object FrameChange {

    /** Hamming distance above this fraction of packed bits counts as a change. */
    const val CHANGE_PERCENT = 3

    /**
     * Pack [gray] (row-major, 0..255, length at least width*height) into a
     * signature whose size depends only on width×height.
     */
    fun signature(gray: IntArray, width: Int, height: Int): LongArray {
        require(width > 0 && height > 0) { "width/height must be positive" }
        val n = width * height
        require(gray.size >= n) { "gray.size=${gray.size} < $n" }
        val out = LongArray((n + 63) ushr 6)
        for (i in 0 until n) {
            if (gray[i] >= 128) {
                out[i ushr 6] = out[i ushr 6] or (1L shl (i and 63))
            }
        }
        return out
    }

    /**
     * True when [cur] is the first frame ([prev] is null), the two signatures
     * have different lengths, or their Hamming distance exceeds
     * [CHANGE_PERCENT] percent of the packed bit count.
     */
    fun changed(prev: LongArray?, cur: LongArray): Boolean {
        if (prev == null) return true
        if (prev.size != cur.size) return true
        var dist = 0
        for (i in cur.indices) {
            dist += (prev[i] xor cur[i]).countOneBits()
        }
        val totalBits = cur.size * 64
        return dist * 100 > totalBits * CHANGE_PERCENT
    }
}
