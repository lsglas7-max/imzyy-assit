package com.imzz.galaxyassistant.accessibility

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.imzz.galaxyassistant.storage.GameMemory

object GestureController {
    fun tapNormalized(context: Context, x: Double, y: Double): Boolean {
        val service = GalaxyAccessibilityService.instance ?: return false
        val metrics = metrics(context)
        val packageName = service.rootInActiveWindow?.packageName?.toString()
            ?.takeIf { it.isNotBlank() } ?: GalaxyAccessibilityService.currentPackageName
        val profile = GameMemory.profile(context, packageName)
        val px = (x.coerceIn(0.0, 1.0) * metrics.widthPixels).toFloat()
        val py = (y.coerceIn(0.0, 1.0) * metrics.heightPixels).toFloat()
        return service.tap(px, py, profile.tapDurationMs)
    }

    fun swipeNormalized(context: Context, x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        val service = GalaxyAccessibilityService.instance ?: return false
        val metrics = metrics(context)
        val packageName = service.rootInActiveWindow?.packageName?.toString()
            ?.takeIf { it.isNotBlank() } ?: GalaxyAccessibilityService.currentPackageName
        val profile = GameMemory.profile(context, packageName)
        val adjustedX2 = (x1 + (x2 - x1) * profile.sensitivity).coerceIn(0.0, 1.0)
        val adjustedY2 = (y1 + (y2 - y1) * profile.sensitivity).coerceIn(0.0, 1.0)
        fun px(v: Double) = (v.coerceIn(0.0, 1.0) * metrics.widthPixels).toFloat()
        fun py(v: Double) = (v.coerceIn(0.0, 1.0) * metrics.heightPixels).toFloat()
        return service.swipe(px(x1), py(y1), px(adjustedX2), py(adjustedY2), profile.swipeDurationMs)
    }

    private fun metrics(context: Context): DisplayMetrics {
        @Suppress("DEPRECATION")
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return DisplayMetrics().also { manager.defaultDisplay.getRealMetrics(it) }
    }
}
