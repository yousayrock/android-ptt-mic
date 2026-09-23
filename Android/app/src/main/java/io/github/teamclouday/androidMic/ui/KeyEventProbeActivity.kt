package io.github.teamclouday.androidMic.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Device diagnostic for identifying side-button events before assigning a PTT key. */
class KeyEventProbeActivity : Activity() {
    private lateinit var logView: TextView
    private val logFile by lazy { File(filesDir, "key-event-probe.log") }
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "XS17 reports the side key as volume up. Enable PTT side button input in Android Accessibility settings to capture it during a stream, including when the screen is off. The service filters volume up only while streaming; all other keys pass through. Events appear below and are saved on this device."
            textSize = 16f
        })
        root.addView(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "Clear log"
            setOnClickListener {
                logFile.writeText("")
                logView.text = "No key events captured yet."
            }
        })
        val scroll = ScrollView(this)
        logView = TextView(this).apply {
            textSize = 13f
            text = logFile.takeIf { it.exists() }?.readText()?.ifBlank { "No key events captured yet." }
                ?: "No key events captured yet."
        }
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val record = listOf(
            "time=${timeFormat.format(Date())}",
            "action=${if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else if (event.action == KeyEvent.ACTION_UP) "UP" else event.action}",
            "keyCode=${event.keyCode}",
            "keyName=${KeyEvent.keyCodeToString(event.keyCode)}",
            "repeat=${event.repeatCount}",
            "scanCode=${event.scanCode}",
            "deviceId=${event.deviceId}",
            "source=${event.source}",
            "flags=${event.flags}",
            "eventTime=${event.eventTime}"
        ).joinToString(" ")
        runOnUiThread {
            if (!::logView.isInitialized) return@runOnUiThread
            if (logView.text == "No key events captured yet.") logView.text = ""
            logView.append("\n$record")
            logFile.appendText("$record\n")
        }
        return super.dispatchKeyEvent(event)
    }
}
