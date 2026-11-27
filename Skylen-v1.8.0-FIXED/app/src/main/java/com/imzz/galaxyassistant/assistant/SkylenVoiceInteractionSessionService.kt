package com.imzz.galaxyassistant.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class SkylenVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = SkylenVoiceInteractionSession(this)
}

private class SkylenVoiceInteractionSession(private val appContext: android.content.Context) : VoiceInteractionSession(appContext) {
    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        // Use our assistant activity as the voice surface instead of a blank system session panel.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        val assistant = Intent(appContext, AssistantEntryActivity::class.java).apply {
            putExtra(AssistantEntryActivity.EXTRA_AUTO_LISTEN, true)
            putExtra(AssistantEntryActivity.EXTRA_SOURCE, "power_assistant")
        }
        runCatching { startAssistantActivity(assistant) }
            .onFailure {
                // ACTION_ASSIST on the activity remains a fallback for OEM implementations.
                assistant.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { appContext.startActivity(assistant) }
            }
    }
}
