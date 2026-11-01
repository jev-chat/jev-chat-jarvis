package com.jev.probe.capture.live

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * Transparent trampoline: shows the system MediaProjection consent dialog,
 * hands the one-shot result to [LiveCaptureService], and finishes.
 *
 * Not exported. No layout. Consent data is never persisted — Android 14+
 * tokens are single-use, so the next start must come through here again.
 */
class LiveConsentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        if (savedInstanceState != null) return

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mgr == null) {
            denyAndFinish()
            return
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mgr.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
        } else {
            mgr.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_CAPTURE) // MediaProjection consent must return here
    }

    @Deprecated("startActivityForResult is the MediaProjection entry the platform documents")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) {
            finish()
            return
        }
        if (resultCode == RESULT_OK && data != null) {
            Log.i(TAG, "live consent granted")
            LiveCaptureService.start(this, resultCode, data)
        } else {
            Log.i(TAG, "live consent denied")
            denyAndFinish()
            return
        }
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun denyAndFinish() {
        Toast.makeText(this, "没有录屏授权，直播没开", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val TAG = "JEVASSIST"
        private const val REQ_CAPTURE = 71
    }
}
