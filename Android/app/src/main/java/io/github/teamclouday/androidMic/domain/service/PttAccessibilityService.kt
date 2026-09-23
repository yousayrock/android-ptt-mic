package io.github.teamclouday.androidMic.domain.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Filters only volume-up while a stream is active; all other keys keep normal behavior. */
class PttAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        sendPttAction(PTT_RELEASE_ACTION)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP || !PttAccessibilityBridge.isStreamActive) {
            return false
        }

        when {
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> sendPttAction(PTT_PRESS_ACTION)
            event.action == KeyEvent.ACTION_UP -> sendPttAction(PTT_RELEASE_ACTION)
        }
        return true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        sendPttAction(PTT_RELEASE_ACTION)
        return super.onUnbind(intent)
    }

    private fun sendPttAction(action: String) {
        if (!PttAccessibilityBridge.isStreamActive) return
        startService(Intent(this, ForegroundService::class.java).setAction(action))
    }
}

object PttAccessibilityBridge {
    @Volatile
    var isStreamActive: Boolean = false
}
