package com.imzz.galaxyassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.imzz.galaxyassistant.autopilot.AutopilotEngine
import com.imzz.galaxyassistant.screen.ScreenCaptureService
import com.imzz.galaxyassistant.voice.VoiceFeedback

class EmergencyStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AutopilotEngine.stop("EMERGENCY STOP ditekan. Semua aksi AI dihentikan.")
        context.stopService(Intent(context, ScreenCaptureService::class.java))
        VoiceFeedback.speak("Emergency stop aktif. Semua kontrol otomatis dihentikan.")
    }
}
