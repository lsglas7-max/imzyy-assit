package com.imzz.galaxyassistant.assistant

import android.service.voice.VoiceInteractionService

/** System entry point for the device's selected digital assistant. */
class SkylenVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Keep this service lightweight; UI and microphone work start only when invoked.
        runCatching { setDisabledShowContext(0) }
    }
}
