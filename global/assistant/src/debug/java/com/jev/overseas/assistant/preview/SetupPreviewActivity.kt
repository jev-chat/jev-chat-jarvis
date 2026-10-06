package com.jev.overseas.assistant.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import com.jev.overseas.assistant.MainActivity
import com.jev.overseas.assistant.R
import java.io.File

/**
 * Debug only. The setup screen with a placeholder key, the service shown as on and
 * the tested WhatsApp version, so it can be shown without the real key. It also
 * draws the whole page, at full height, to `files/snapshots/setup.png` (used for
 * the README images):
 * `adb exec-out run-as com.jev.overseas cat files/snapshots/setup.png > setup.png`
 */
class SetupPreviewActivity : MainActivity() {

    override fun maskedKey(): String = "sk-or-…a1b2"

    override fun serviceEnabled(): Boolean = true

    override fun whatsappVersion(): String = getString(R.string.whatsapp_version, "2.26.38.73")

    override fun onResume() {
        super.onResume()
        page.postDelayed({ snapshot() }, SNAPSHOT_DELAY_MS)
    }

    private fun snapshot() {
        if (page.width == 0 || page.height == 0) return
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.draw(Canvas(bitmap.apply { eraseColor(ui.base) }))
        val dir = File(filesDir, "snapshots").apply { mkdirs() }
        File(dir, "setup.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val SNAPSHOT_DELAY_MS = 1500L
    }
}
