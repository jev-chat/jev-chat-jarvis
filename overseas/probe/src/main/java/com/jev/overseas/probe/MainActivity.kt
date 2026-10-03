package com.jev.overseas.probe

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Status screen: is the service on, how many dumps exist, shortcuts into Settings. */
class MainActivity : Activity() {

    private lateinit var serviceStatus: TextView
    private lateinit var dumpCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics
        ).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun line(text: String, sizeSp: Float = 15f) = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setPadding(0, pad / 2, 0, pad / 2)
        }.also { column.addView(it) }

        line(getString(R.string.main_title), 20f)
        serviceStatus = line("")
        dumpCount = line("")
        line(getString(R.string.main_how_to))
        column.addView(Button(this).apply {
            text = getString(R.string.main_open_accessibility)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        column.addView(Button(this).apply {
            text = getString(R.string.main_open_app_info)
            setOnClickListener {
                startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")
                ))
            }
        })
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })
    }

    override fun onResume() {
        super.onResume()
        serviceStatus.text = getString(
            if (isServiceEnabled()) R.string.main_service_on else R.string.main_service_off
        )
        val count = File(filesDir, ProbeService.DUMP_DIR).list()?.size ?: 0
        dumpCount.text = getString(R.string.main_dump_count, count)
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, ProbeService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
