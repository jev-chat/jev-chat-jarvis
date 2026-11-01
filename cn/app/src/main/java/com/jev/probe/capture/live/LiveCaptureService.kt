package com.jev.probe.capture.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.jev.probe.MainActivity
import com.jev.probe.core.Prefs
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MediaProjection foreground service: half-res screen frames every
 * [Prefs.liveIntervalMs], published to [LiveFrameHub] only when the picture
 * actually changed.
 *
 * Platform order (Android 14+ will throw otherwise):
 * 1. [startForeground] with [ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION]
 * 2. [MediaProjectionManager.getMediaProjection]
 * 3. [MediaProjection.registerCallback] then [MediaProjection.createVirtualDisplay]
 *
 * Consent tokens are single-use from Android 14. This service never writes
 * resultCode/data to disk; the next start must go through [LiveConsentActivity].
 */
class LiveCaptureService : Service() {

    private val stopping = AtomicBoolean(false)
    private val lock = Any()

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var intervalMs: Long = Prefs.DEFAULT_LIVE_INTERVAL_MS
    private var lastSig: LongArray? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "live projection onStop")
            Handler(Looper.getMainLooper()).post { stopCapture("projection") }
        }
    }

    private val grabRunnable = object : Runnable {
        override fun run() {
            if (stopping.get()) return
            runCatching { grabOnce() }
                .onFailure { Log.w(TAG, "live grab failed: ${it.javaClass.simpleName}") }
            if (!stopping.get()) {
                captureHandler?.postDelayed(this, intervalMs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        Log.i(TAG, "live service onCreate (foreground mediaProjection)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture("app")
                return START_NOT_STICKY
            }
            ACTION_STOP_NOTIF -> {
                stopCapture("notification")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = extraData(intent)
                if (code == 0 || data == null) {
                    failStart("授权结果空")
                } else {
                    beginProjection(code, data)
                }
            }
            else -> Log.w(TAG, "live onStartCommand ignored action=${intent?.action}")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val reason = pendingStopReason.getAndSet(null) ?: "destroy"
        stopCapture(reason)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginProjection(resultCode: Int, data: Intent) {
        if (stopping.get()) return
        if (projection != null) {
            Log.i(TAG, "live already running; ignore new start (token is single-use after stop)")
            return
        }
        intervalMs = Prefs(this).liveIntervalMs
        val dm = resources.displayMetrics
        val width = (dm.widthPixels / 2).coerceAtLeast(1)
        val height = (dm.heightPixels / 2).coerceAtLeast(1)
        val density = dm.densityDpi

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = try {
            mgr.getMediaProjection(resultCode, data)
        } catch (e: Throwable) {
            failStart(e.javaClass.simpleName)
            return
        }
        if (mp == null) {
            failStart("getMediaProjection 空")
            return
        }

        val thread = HandlerThread("jev-live-capture").also { it.start() }
        val handler = Handler(thread.looper)
        captureThread = thread
        captureHandler = handler

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        synchronized(lock) {
            projection = mp
            imageReader = reader
        }

        // registerCallback BEFORE createVirtualDisplay (Android 14+).
        mp.registerCallback(projectionCallback, handler)
        val display = try {
            mp.createVirtualDisplay(
                "jev-live",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )
        } catch (e: Throwable) {
            failStart(e.javaClass.simpleName)
            return
        }
        if (display == null) {
            failStart("VirtualDisplay 空")
            return
        }
        synchronized(lock) { virtualDisplay = display }

        LiveFrameHub.setLive(true)
        lastSig = null
        Log.i(TAG, "live projection ready ${width}x${height} dpi=$density interval=${intervalMs}ms")
        handler.postDelayed(grabRunnable, FIRST_GRAB_DELAY_MS)
    }

    private fun grabOnce() {
        val reader = synchronized(lock) { imageReader } ?: return
        val image = reader.acquireLatestImage() ?: run {
            Log.d(TAG, "live frame empty")
            return
        }
        val bmp = try {
            imageToBitmap(image)
        } catch (e: Throwable) {
            Log.w(TAG, "live frame convert failed: ${e.javaClass.simpleName}")
            null
        } finally {
            image.close()
        } ?: return

        val gray = toGray(bmp, HASH_W, HASH_H)
        val sig = FrameChange.signature(gray, HASH_W, HASH_H)
        if (!FrameChange.changed(lastSig, sig)) {
            Log.d(TAG, "live frame unchanged")
            bmp.recycle()
            return
        }
        lastSig = sig
        Log.i(TAG, "live frame changed ${bmp.width}x${bmp.height}")
        LiveFrameHub.publish(bmp, SystemClock.elapsedRealtime())
    }

    private fun stopCapture(reason: String) {
        if (!stopping.compareAndSet(false, true)) {
            Log.i(TAG, "live stop already in progress reason=$reason")
            return
        }
        Log.i(TAG, "live stop reason=$reason")
        releaseAll()
        LiveFrameHub.setLive(false)
        lastSig = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun releaseAll() {
        captureHandler?.removeCallbacksAndMessages(null)
        captureHandler = null
        captureThread?.quitSafely()
        captureThread = null
        synchronized(lock) {
            runCatching { virtualDisplay?.release() }
            virtualDisplay = null
            runCatching { imageReader?.close() }
            imageReader = null
            val p = projection
            projection = null
            if (p != null) {
                runCatching { p.unregisterCallback(projectionCallback) }
                runCatching { p.stop() }
            }
        }
    }

    private fun failStart(msg: String) {
        Log.w(TAG, "live start failed: $msg")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, "直播没开：$msg", Toast.LENGTH_SHORT).show()
        }
        stopCapture("start-failed")
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Jev 直播中",
            NotificationManager.IMPORTANCE_LOW,
        )
        ch.setShowBadge(false)
        ch.description = "录屏取帧，只在手机本地识别聊天画面"
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveCaptureService::class.java).setAction(ACTION_STOP_NOTIF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Jev 直播中")
            .setContentText("只在手机本地识别聊天画面")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stop)
            .build()
    }

    companion object {
        private const val TAG = "JEVASSIST"
        private const val CHANNEL_ID = "jev_live"
        private const val NOTIF_ID = 7
        private const val HASH_W = 32
        private const val HASH_H = 64
        private const val FIRST_GRAB_DELAY_MS = 200L

        const val ACTION_START = "com.jev.probe.live.START"
        const val ACTION_STOP = "com.jev.probe.live.STOP"
        const val ACTION_STOP_NOTIF = "com.jev.probe.live.STOP_NOTIF"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"

        /**
         * Step-2 hook: our overlay is part of the display and will be baked
         * into captured frames. This step does not hide it; OCR can flip this
         * to skip a frame or hide-then-grab later.
         */
        @Volatile
        var isOwnOverlayVisible: Boolean = true

        /** Set just before [stop] so onDestroy can log "app" instead of "destroy". */
        private val pendingStopReason = java.util.concurrent.atomic.AtomicReference<String?>(null)

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            val i = Intent(ctx, LiveCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ctx.startForegroundService(i)
        }

        /**
         * App-side stop. Uses [Context.stopService] so we never start a
         * mediaProjection FGS without a consent token (Android 14 would throw).
         */
        fun stop(ctx: Context) {
            pendingStopReason.set("app")
            val stopped = ctx.stopService(Intent(ctx, LiveCaptureService::class.java))
            if (!stopped) {
                pendingStopReason.set(null)
                LiveFrameHub.setLive(false)
            }
        }

        private fun extraData(intent: Intent): Intent? {
            return if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_DATA)
            }
        }

        internal fun imageToBitmap(image: Image): Bitmap {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height
            val rowPadding = rowStride - pixelStride * width
            buffer.rewind()
            val padded = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888,
            )
            padded.copyPixelsFromBuffer(buffer)
            if (rowPadding == 0) return padded
            val tight = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(tight).drawBitmap(padded, 0f, 0f, null)
            padded.recycle()
            return tight
        }

        internal fun toGray(src: Bitmap, w: Int, h: Int): IntArray {
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            if (small !== src) small.recycle()
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                pixels[i] = (r * 299 + g * 587 + b * 114) / 1000
            }
            return pixels
        }
    }
}
