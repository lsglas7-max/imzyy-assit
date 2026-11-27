package com.imzz.galaxyassistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.imzz.galaxyassistant.autopilot.AutopilotEngine

class GalaxyAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            // If Android reports a touch-interaction event, manual input takes priority.
            AutopilotEngine.pause("Manual touch detected")
        }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val foreground = event.packageName?.toString().orEmpty()
            if (foreground.isNotBlank() && foreground != packageName) {
                currentPackageName = foreground
                sendBroadcast(
                    android.content.Intent(ACTION_FOREGROUND_APP)
                        .setPackage(packageName)
                        .putExtra("packageName", foreground)
                )
            }
        }
    }

    override fun onInterrupt() {
        AutopilotEngine.stop("Accessibility interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        currentPackageName = ""
        super.onDestroy()
    }

    fun tap(x: Float, y: Float, durationMs: Long = 75L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, durationMs.coerceIn(40L, 800L))
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350L): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return dispatch(path, durationMs.coerceIn(80L, 1800L))
    }

    private fun dispatch(path: Path, durationMs: Long): Boolean {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun setFocusedText(value: String): Boolean {
        if (value.length > 100) return false
        val root = rootInActiveWindow ?: return false
        return try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            if (focused.isPassword || !focused.isEditable) return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            root.recycleCompat()
        }
    }

    companion object {
        const val ACTION_FOREGROUND_APP = "com.imzz.galaxyassistant.FOREGROUND_APP"
        @Volatile var instance: GalaxyAccessibilityService? = null
            private set
        @Volatile var currentPackageName: String = ""
            private set

        fun isAvailable(): Boolean = instance != null
    }
}

private fun AccessibilityNodeInfo.recycleCompat() {
    if (Build.VERSION.SDK_INT < 33) {
        @Suppress("DEPRECATION")
        recycle()
    }
}
