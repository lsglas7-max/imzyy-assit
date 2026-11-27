package com.imzz.galaxyassistant.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * Pendengar suara di dalam app (tanpa dialog Google). Satu panggilan [start] = satu sesi dengar.
 * Semua method harus dipanggil dari UI thread (aturan SpeechRecognizer).
 *
 * @param onText hasil akhir ucapan.
 * @param onPartial teks sementara saat pengguna masih bicara.
 * @param onListening true saat mikrofon aktif, false saat berhenti.
 * @param onStatus pesan untuk ditampilkan (error, petunjuk).
 * @param onNeedPermission izin RECORD_AUDIO belum ada.
 * @param onFinished sesi dengar selesai; argumen true = aman diulang (mode ngobrol), false = berhenti (error fatal).
 */
class VoiceListener(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onNeedPermission: () -> Unit,
    private val onFinished: (Boolean) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var delivered = false

    val isActive: Boolean get() = active

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(): Boolean {
        if (active) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onNeedPermission()
            return false
        }
        if (!isAvailable()) {
            onStatus("Pengenal suara tidak tersedia di HP ini. Pasang / aktifkan aplikasi Google.")
            return false
        }
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        active = true
        delivered = false
        val started = runCatching { engine.startListening(intent) }.isSuccess
        if (!started) {
            active = false
            destroy()
            onStatus("Gagal membuka mikrofon. Coba lagi.")
            return false
        }
        return true
    }

    /** Hentikan sesi dengar (dipakai saat app ke latar atau mode ngobrol dimatikan). */
    fun stop() {
        active = false
        runCatching { recognizer?.cancel() }
        onListening(false)
    }

    fun destroy() {
        active = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun bestText(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onListening(true)
            onStatus("Gue dengerin, ngomong aja bro...")
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onEndOfSpeech() {
            onListening(false)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = bestText(partialResults)
            if (text.isNotBlank()) onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            active = false
            onListening(false)
            val text = bestText(results)
            if (text.isNotBlank() && !delivered) {
                delivered = true
                onText(text)
            }
            onFinished(true)
        }

        override fun onError(error: Int) {
            active = false
            onListening(false)
            var retry = true
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Unit
                SpeechRecognizer.ERROR_CLIENT -> Unit
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    destroy()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    retry = false
                    onNeedPermission()
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    retry = false
                    onStatus("Pengenal suara butuh internet. Cek koneksi lu bro.")
                }
                SpeechRecognizer.ERROR_AUDIO -> {
                    retry = false
                    onStatus("Mikrofon lagi dipakai app lain atau bermasalah.")
                }
                else -> {
                    retry = false
                    onStatus("Pengenal suara error (kode $error).")
                }
            }
            onFinished(retry)
        }
    }
}
