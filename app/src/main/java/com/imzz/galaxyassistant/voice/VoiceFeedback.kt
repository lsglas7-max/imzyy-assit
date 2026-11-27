package com.imzz.galaxyassistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.imzz.galaxyassistant.mascot.CatBrain
import java.util.Locale

/**
 * Suara Skylen memakai Android TextToSpeech lokal (tanpa API, tanpa internet).
 * - [speakingListener] dipanggil saat mulai/selesai bicara -> dipakai untuk menggerakkan mulut kucing.
 * - [textListener] dipanggil dengan kalimat yang diucapkan -> dipakai untuk balon ucapan.
 * Keduanya bisa dipanggil dari thread non-UI; pendengar wajib pindah ke UI thread sendiri.
 */
object VoiceFeedback {
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var pending: String? = null

    @Volatile var speakingListener: ((Boolean) -> Unit)? = null
    @Volatile var textListener: ((String) -> Unit)? = null

    /** Suara kucing: pitch tinggi + kadang ditambah "meow~" pada ucapan sistem. */
    @Volatile var catVoice = true

    fun initialize(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val engine = tts
                val result = engine?.setLanguage(Locale("id", "ID"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine?.language = Locale("id")
                }
                engine?.setPitch(1.45f)
                engine?.setSpeechRate(1.05f)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        speakingListener?.invoke(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        speakingListener?.invoke(false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        speakingListener?.invoke(false)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        speakingListener?.invoke(false)
                    }
                })
                pending?.let { line ->
                    pending = null
                    speakLocal(line)
                }
            }
        }
    }

    /**
     * @param decorate true = boleh ditambah gaya kucing ("meow~"). Balasan Skylen sendiri pakai false.
     */
    fun speak(message: String, decorate: Boolean = true) {
        if (message.isBlank()) return
        val line = if (decorate && catVoice) CatBrain.catify(message) else message
        textListener?.invoke(line)
        speakLocal(line)
    }

    private fun speakLocal(message: String) {
        val engine = tts
        if (ready && engine != null) {
            engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "galaxy_${System.currentTimeMillis()}")
        } else {
            // TTS belum siap (baru dibuka): simpan kalimat terakhir, diucapkan begitu siap.
            pending = message
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
        speakingListener = null
        textListener = null
    }
}
