package com.jev.probe.capture.live

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * In-process bus for the live-capture pipeline.
 *
 * The capture service publishes a new [Bitmap] only when [FrameChange] says
 * the screen actually moved. OCR (step 2) observes this hub; it does not talk
 * to [com.jev.probe.capture.live.LiveCaptureService] directly.
 *
 * Ownership: the hub owns [latestBitmap] and recycles it when a newer frame
 * arrives or when live mode stops. Listeners run on the main thread and must
 * [Bitmap.copy] if they need the pixels after returning.
 */
object LiveFrameHub {

    interface Listener {
        fun onFrame(bitmap: Bitmap, capturedAtMs: Long) {}
        fun onLiveChanged(live: Boolean) {}
        /** Overlay menu "立即分析这一屏": re-run the current frame, skip the new-message gate. */
        fun onAnalyzeNow() {}
    }

    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var isLive: Boolean = false
        private set

    @Volatile
    var capturedAtMs: Long = 0L
        private set

    private var latest: Bitmap? = null

    /** Current frame, or null if live is off / no frame yet. Hub-owned. */
    val latestBitmap: Bitmap?
        get() = synchronized(lock) { latest }

    fun observe(listener: Listener) {
        listeners.add(listener)
    }

    fun requestAnalyzeNow() {
        main.post { listeners.forEach { it.onAnalyzeNow() } }
    }

    fun remove(listener: Listener) {
        listeners.remove(listener)
    }

    fun setLive(live: Boolean) {
        val fire: Boolean
        synchronized(lock) {
            fire = isLive != live
            isLive = live
            if (!live) {
                latest?.let { if (!it.isRecycled) it.recycle() }
                latest = null
                capturedAtMs = 0L
            }
        }
        if (fire) {
            main.post { listeners.forEach { it.onLiveChanged(live) } }
        }
    }

    /**
     * Take ownership of [bitmap]. Recycles the previous frame. Drops (and
     * recycles) [bitmap] if live mode is already off.
     */
    fun publish(bitmap: Bitmap, capturedAtMs: Long) {
        val dropped: Bitmap?
        synchronized(lock) {
            if (!isLive) {
                if (!bitmap.isRecycled) bitmap.recycle()
                return
            }
            dropped = latest
            latest = bitmap
            this.capturedAtMs = capturedAtMs
        }
        if (dropped != null && dropped !== bitmap && !dropped.isRecycled) {
            dropped.recycle()
        }
        main.post {
            val snap: Bitmap?
            val t: Long
            synchronized(lock) {
                snap = latest
                t = this.capturedAtMs
            }
            if (snap != null && !snap.isRecycled) {
                listeners.forEach { it.onFrame(snap, t) }
            }
        }
    }
}
