package com.imzz.galaxyassistant.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Manifest endpoint required by VoiceInteractionService metadata on older Android versions.
 * Skylen's visible assistant UI performs speech input with SpeechRecognizer directly; this
 * fallback service fails explicitly rather than pretending to recognize audio it never captured.
 */
class SkylenRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: RecognitionService.Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: RecognitionService.Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: RecognitionService.Callback?) = Unit
}
