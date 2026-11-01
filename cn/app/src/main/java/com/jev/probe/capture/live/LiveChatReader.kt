package com.jev.probe.capture.live

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.jev.probe.capture.AnalysisCoordinator
import com.jev.probe.capture.VisionJpeg
import com.jev.probe.capture.ocr.MlKitOcr
import com.jev.probe.capture.ocr.OcrLine
import com.jev.probe.core.ChatSnapshot
import com.jev.probe.core.Msg
import com.jev.probe.core.Prefs
import com.jev.probe.overlay.OverlayController

/**
 * Subscribes to [LiveFrameHub], OCRs each new frame, parses a chat, and
 * hands a [ChatSnapshot] to [AnalysisCoordinator]. Created when live capture
 * starts and torn down when it stops.
 *
 * A frame that arrives while OCR is still running is dropped — we would
 * rather skip a beat than queue a backlog of the same screen.
 */
internal class LiveChatReader(
    private val ctx: Context,
    private val prefs: Prefs,
    private val overlay: OverlayController,
    private val coordinator: AnalysisCoordinator,
) : LiveFrameHub.Listener {

    private val ocr = MlKitOcr()
    @Volatile private var ocrBusy = false
    @Volatile private var stopped = false
    private var lastMsgs: List<Msg> = emptyList()

    fun start() {
        stopped = false
        lastMsgs = emptyList()
        overlay.showIdle(null)
        coordinator.attachOverlay()
        LiveFrameHub.observe(this)
        Thread({ MlKitOcr.warmUp() }, "jev-ocr-warm").apply { isDaemon = true; start() }
        Log.i(TAG, "live reader started")
    }

    fun stop() {
        stopped = true
        LiveFrameHub.remove(this)
        coordinator.leaveConversation()
        overlay.hide()
        lastMsgs = emptyList()
        Log.i(TAG, "live reader stopped")
    }

    override fun onFrame(bitmap: Bitmap, capturedAtMs: Long) {
        if (stopped) return
        if (ocrBusy) {
            Log.d(TAG, "live ocr skip (busy)")
            return
        }
        process(bitmap, manual = false)
    }

    override fun onAnalyzeNow() {
        if (stopped) return
        val bmp = LiveFrameHub.latestBitmap
        if (bmp == null || bmp.isRecycled) {
            overlay.toast("还没有画面")
            return
        }
        process(bmp, manual = true)
    }

    override fun onLiveChanged(live: Boolean) {
        if (!live && !stopped) stop()
    }

    private fun process(bitmap: Bitmap, manual: Boolean) {
        if (ocrBusy) {
            if (manual) overlay.toast("正在识别")
            return
        }
        if (bitmap.isRecycled) return
        val copy = try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            null
        } ?: return
        ocrBusy = true
        val w = copy.width
        val h = copy.height
        val exclude = excludeBoxes(w, h)
        ocr.scaleX = 1f
        ocr.scaleY = 1f
        ocr.originX = 0
        ocr.originY = 0
        ocr.recognize(copy, null) { lines ->
            val jpeg = try {
                VisionJpeg.encode(copy, prefs)
            } catch (_: Exception) {
                null
            }
            runCatching { if (!copy.isRecycled) copy.recycle() }
            ocrBusy = false
            if (stopped) return@recognize
            handleLines(lines, w, h, exclude, jpeg, manual)
        }
    }

    private fun handleLines(
        lines: List<OcrLine>,
        w: Int,
        h: Int,
        exclude: List<OcrChatParser.Box>,
        jpeg: ByteArray?,
        manual: Boolean,
    ) {
        Log.i(TAG, "live ocr lines=${lines.size}")
        val parsed = OcrChatParser.parse(lines.map { it.toParserLine() }, w, h, exclude)
        Log.i(
            TAG,
            "live parsed titleLen=${parsed.title?.length ?: 0} msgs=${parsed.messages.size} isChat=${parsed.isChat}",
        )
        if (!parsed.isChat) {
            overlay.showIdle(null)
            return
        }
        val aligned = OcrDedupe.align(lastMsgs, parsed.messages)
        lastMsgs = aligned
        val snapshot = ChatSnapshot(parsed.title, aligned, image = jpeg)
        Log.i(TAG, "live snapshot msgs=${snapshot.messages.size} visionBytes=${snapshot.image?.size ?: 0}")
        coordinator.ingest(
            snapshot,
            AnalysisCoordinator.LIVE_PKG,
            AnalysisCoordinator.LIVE_WINDOW_ID,
            if (manual) AnalysisCoordinator.Mode.MANUAL else AnalysisCoordinator.Mode.LIVE,
            bindSession = true,
        )
    }

    private fun excludeBoxes(frameW: Int, frameH: Int): List<OcrChatParser.Box> {
        val screen = overlay.visibleScreenRect() ?: return emptyList()
        val dm = ctx.resources.displayMetrics
        if (dm.widthPixels <= 0 || dm.heightPixels <= 0) return emptyList()
        val sx = frameW.toFloat() / dm.widthPixels
        val sy = frameH.toFloat() / dm.heightPixels
        return listOf(
            OcrChatParser.Box(
                (screen.left * sx).toInt(),
                (screen.top * sy).toInt(),
                (screen.right * sx).toInt(),
                (screen.bottom * sy).toInt(),
            ),
        )
    }

    private fun OcrLine.toParserLine() = OcrChatParser.Line(
        text,
        OcrChatParser.Box(bounds.left, bounds.top, bounds.right, bounds.bottom),
    )

    companion object {
        private const val TAG = "JEVASSIST"
    }
}
