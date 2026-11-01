package com.jev.probe.capture

import android.graphics.Bitmap
import android.util.Log
import com.jev.probe.core.Prefs
import com.jev.probe.jev.VisionImage
import java.io.ByteArrayOutputStream

/**
 * Downscale + JPEG (q=85) for TypeSafe vision judge. Shared by the
 * accessibility screenshot path and live-frame path.
 *
 * Recycles the scaled intermediate; the original [bmp] stays with the caller.
 * Null when the switch is off, the provider is not TypeSafe direct, or the
 * shot is outside TypeSafe's pixel window.
 */
object VisionJpeg {
    private const val TAG = "JEVASSIST"

    fun encode(bmp: Bitmap, prefs: Prefs): ByteArray? {
        if (!prefs.visionJudge || prefs.judgeProvider != Prefs.PROVIDER_TYPESAFE) return null
        val size = VisionImage.targetSize(bmp.width, bmp.height) ?: return null
        val (tw, th) = size
        var scaled: Bitmap? = null
        return try {
            val src = if (tw == bmp.width && th == bmp.height) bmp else {
                Bitmap.createScaledBitmap(bmp, tw, th, true).also { scaled = it }
            }
            val out = ByteArrayOutputStream()
            if (!src.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                Log.w(TAG, "vision jpeg compress failed")
                null
            } else {
                val bytes = out.toByteArray()
                Log.i(TAG, "vision jpeg ${bmp.width}x${bmp.height} -> ${tw}x${th} bytes=${bytes.size}")
                bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "vision jpeg failed: ${e.javaClass.simpleName}")
            null
        } finally {
            val s = scaled
            if (s != null && s !== bmp) s.recycle()
        }
    }
}
