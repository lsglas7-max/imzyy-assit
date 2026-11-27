package com.imzz.galaxyassistant.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

object ScreenCaptureManager {
    fun createPermissionIntent(activity: Activity): Intent {
        val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    fun startService(context: Context, resultCode: Int, data: Intent) {
        val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
    }
}
