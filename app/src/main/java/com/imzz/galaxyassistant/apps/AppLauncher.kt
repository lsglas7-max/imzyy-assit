package com.imzz.galaxyassistant.apps

import android.content.Context
import android.content.Intent

object AppLauncher {
    data class LaunchResult(val success: Boolean, val message: String)

    fun launch(context: Context, app: AppInfo): LaunchResult {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?: return LaunchResult(false, "Aplikasi ditemukan, tetapi tidak menyediakan launcher activity.")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            LaunchResult(true, "Perintah membuka ${app.label} sudah dikirim ke Android.")
        } catch (error: Exception) {
            LaunchResult(false, "Gagal membuka ${app.label}: ${error.localizedMessage ?: "kesalahan tidak diketahui"}")
        }
    }
}
