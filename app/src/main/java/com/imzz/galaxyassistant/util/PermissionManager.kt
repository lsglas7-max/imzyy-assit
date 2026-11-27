package com.imzz.galaxyassistant.util

import android.content.Context
import com.imzz.galaxyassistant.accessibility.AccessibilityController
import com.imzz.galaxyassistant.accessibility.GalaxyAccessibilityService
import com.imzz.galaxyassistant.screen.ScreenCaptureService

object PermissionManager {
    fun accessibilityReady(context: Context): Boolean =
        AccessibilityController.isEnabled(context) && GalaxyAccessibilityService.isAvailable()

    fun screenCaptureReady(): Boolean = ScreenCaptureService.isRunning
}
