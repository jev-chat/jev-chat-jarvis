package com.jev.probe.capture.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors

/**
 * On-device OCR via ML Kit's bundled Chinese recognizer. Bundled, not the
 * play-services variant: the model ships inside the APK, so it also works on a
 * phone with no Google Play services and never downloads anything.
 *
 * Coordinates: ML Kit sees the (possibly cropped) screenshot, so a line's box is
 * in bitmap space. We add the crop offset back and divide by the screenshot
 * scale, handing the caller SCREEN coordinates — the same space node bounds use.
 * Set [scaleX]/[scaleY] from the ScreenCapture result before each batch.
 *
 * Recognition runs on ML Kit's own threads; the callback is posted to the main
 * thread so overlay work needs no extra hop.
 */
class MlKitOcr : OcrEngine {

    /** Screenshot bitmap size / captured area size. Set per capture. */
    @Volatile var scaleX: Float = 1f
    @Volatile var scaleY: Float = 1f

    /** Where the captured area starts on screen (a window shot is not the whole
     *  display). Added back after unscaling, so boxes are screen coordinates. */
    @Volatile var originX: Int = 0
    @Volatile var originY: Int = 0

    private val main = Handler(Looper.getMainLooper())

    override fun recognize(bitmap: Bitmap, region: Rect?, cb: (List<OcrLine>) -> Unit) {
        // #18: the crop and the InputImage wrap used to run on the caller's
        // thread — which is the main thread (the screenshot callback posts
        // there). That is plain bitmap work, so it moved to one shared worker;
        // the callback still answers on the main thread exactly as before.
        EXEC.execute {
            val src: Bitmap
            val ox: Int
            val oy: Int
            val cropped: Boolean
            if (region != null) {
                val r = Rect(region)
                if (!r.intersect(0, 0, bitmap.width, bitmap.height) || r.width() < 8 || r.height() < 8) {
                    main.post { cb(emptyList()) }; return@execute
                }
                src = try {
                    Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
                } catch (e: Exception) {
                    Log.w(TAG, "ocr crop failed: ${e.javaClass.simpleName}")
                    main.post { cb(emptyList()) }; return@execute
                }
                ox = r.left; oy = r.top; cropped = true
            } else {
                src = bitmap; ox = 0; oy = 0; cropped = false
            }

            val sx = if (scaleX > 0f) scaleX else 1f
            val sy = if (scaleY > 0f) scaleY else 1f
            val wx = originX
            val wy = originY
            val image = try {
                InputImage.fromBitmap(src, 0)
            } catch (e: Exception) {
                if (cropped) src.recycle()
                main.post { cb(emptyList()) }; return@execute
            }

            client.process(image)
                .addOnSuccessListener { text ->
                    val lines = ArrayList<OcrLine>()
                    for (block in text.textBlocks) {
                        for (line in block.lines) {
                            val b = line.boundingBox ?: continue
                            val t = line.text.trim()
                            if (t.isEmpty()) continue
                            lines.add(OcrLine(t, Rect(
                                ((b.left + ox) / sx).toInt() + wx,
                                ((b.top + oy) / sy).toInt() + wy,
                                ((b.right + ox) / sx).toInt() + wx,
                                ((b.bottom + oy) / sy).toInt() + wy)))
                        }
                    }
                    lines.sortBy { it.bounds.top }
                    if (cropped) src.recycle()
                    main.post { cb(lines) }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "ocr failed: ${e.javaClass.simpleName}")
                    if (cropped) src.recycle()
                    main.post { cb(emptyList()) }
                }
        }
    }

    companion object {
        private const val TAG = "JEVASSIST"

        /** One shared worker for the bitmap prep (crop + InputImage wrap).
         *  Serial on purpose: crops of one screenshot keep their order, and a
         *  bitmap is only recycled in the LAST OCR callback of its batch, by
         *  which point every crop of that batch has already run. Daemon
         *  thread, so it never blocks process exit. */
        private val EXEC = Executors.newSingleThreadExecutor { r ->
            Thread(r, "jev-ocr").apply { isDaemon = true }
        }

        /** One recognizer for the process: creating it loads the bundled model. */
        private val client: TextRecognizer by lazy {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }

        /**
         * Build the recognizer ahead of time. First use loads the bundled model,
         * which is the one slow step here — call this from a worker thread when
         * the service connects so the first real OCR does not pay for it on the
         * main thread (the screenshot callback runs there).
         */
        fun warmUp() { runCatching { client } }
    }
}
