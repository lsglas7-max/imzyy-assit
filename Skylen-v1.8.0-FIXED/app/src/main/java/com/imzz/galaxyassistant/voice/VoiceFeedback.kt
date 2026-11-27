package com.imzz.galaxyassistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.imzz.galaxyassistant.mascot.CatBrain
import java.util.Locale

/**
 * Suara Skylen memakai Android TextToSpeech lokal (tanpa API, tanpa internet kalau suaranya offline).
 * - Memilih suara Bahasa Indonesia yang kemungkinan besar cowok (Google "idb"/"idd"), nada dibuat lebih dalam.
 * - Suara bisa diganti lewat [selectVoice] dan nada lewat [cyclePitch]; pilihan disimpan.
 * - [speakingListener] dipanggil saat mulai/selesai bicara -> menggerakkan mulut kucing.
 * - [textListener] dipanggil dengan kalimat yang diucapkan -> balon ucapan.
 * Keduanya bisa dipanggil dari thread non-UI; pendengar wajib pindah ke UI thread sendiri.
 */
object VoiceFeedback {
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var pending: String? = null
    @Volatile private var appContext: Context? = null

    @Volatile private var speakingFlag = false
    @Volatile private var speakStart = 0L
    @Volatile private var currentStamp = ""

    @Volatile var speakingListener: ((Boolean) -> Unit)? = null
    @Volatile var textListener: ((String) -> Unit)? = null

    /** Tambahan "meong." kadang-kadang pada ucapan sistem. */
    @Volatile var catVoice = true

    private const val PREFS = "skylen"
    private const val KEY_VOICE = "tts_voice"
    private const val KEY_PITCH = "tts_pitch_index"

    private val pitchLevels = listOf("Berat" to 0.72f, "Cowok" to 0.88f, "Ringan" to 1.0f)
    private var pitchIndex = 1

    /** True selama Skylen bicara (otomatis kedaluwarsa 25 detik kalau mesin TTS lupa melapor). */
    val isSpeaking: Boolean
        get() = speakingFlag && System.currentTimeMillis() - speakStart < 25_000L

    fun initialize(context: Context) {
        if (tts != null) return
        val app = context.applicationContext
        appContext = app
        pitchIndex = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_PITCH, 1).coerceIn(0, pitchLevels.lastIndex)
        tts = TextToSpeech(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val engine = tts
                val result = engine?.setLanguage(Locale("id", "ID"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine?.language = Locale("id")
                }
                applySavedVoice()
                engine?.setPitch(pitchLevels[pitchIndex].second)
                engine?.setSpeechRate(1.0f)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (belongsToCurrent(utteranceId)) {
                            speakingFlag = true
                            speakingListener?.invoke(true)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null && utteranceId.endsWith("_last") && belongsToCurrent(utteranceId)) finishSpeaking()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (belongsToCurrent(utteranceId)) finishSpeaking()
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (belongsToCurrent(utteranceId)) finishSpeaking()
                    }
                })
                pending?.let { line ->
                    pending = null
                    speakLocal(line)
                }
            }
        }
    }

    private fun belongsToCurrent(utteranceId: String?): Boolean =
        utteranceId != null && currentStamp.isNotEmpty() && utteranceId.startsWith("galaxy_$currentStamp")

    private fun finishSpeaking() {
        speakingFlag = false
        speakingListener?.invoke(false)
    }

    // ------------------------------------------------------------------ pilihan suara

    /** Semua suara Bahasa Indonesia yang terpasang; yang offline di depan. */
    fun indonesianVoices(): List<Voice> {
        val engine = tts ?: return emptyList()
        if (!ready) return emptyList()
        val all = runCatching { engine.voices }.getOrNull().orEmpty()
        return all
            .filter { it.locale.language == "id" || it.locale.language == "in" }
            .sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { it.name }))
    }

    /** Tebakan: suara Google Indonesia "idb" dan "idd" biasanya cowok. API Android tidak memberi tahu gender. */
    fun likelyMale(voice: Voice): Boolean = voice.name.contains("idb") || voice.name.contains("idd")

    fun labelFor(voice: Voice, index: Int): String {
        val short = voice.name.substringAfter("-x-", voice.name)
        val male = if (likelyMale(voice)) " • kemungkinan cowok" else ""
        val net = if (voice.isNetworkConnectionRequired) " • online" else ""
        return "Suara ${index + 1} • $short$male$net"
    }

    fun selectedVoiceName(): String? = runCatching { tts?.voice?.name }.getOrNull()

    fun selectVoice(voice: Voice) {
        runCatching { tts?.voice = voice }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY_VOICE, voice.name)?.apply()
        tts?.setPitch(pitchLevels[pitchIndex].second)
    }

    private fun applySavedVoice() {
        val engine = tts ?: return
        val voices = indonesianVoices()
        if (voices.isEmpty()) return
        val saved = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getString(KEY_VOICE, null)
        val pick = voices.firstOrNull { it.name == saved }
            ?: voices.firstOrNull { likelyMale(it) && !it.isNetworkConnectionRequired }
            ?: voices.firstOrNull { likelyMale(it) }
            ?: voices.firstOrNull { !it.isNetworkConnectionRequired }
            ?: voices.first()
        runCatching { engine.voice = pick }
    }

    fun pitchLabel(): String = pitchLevels[pitchIndex].first

    fun cyclePitch() {
        pitchIndex = (pitchIndex + 1) % pitchLevels.size
        tts?.setPitch(pitchLevels[pitchIndex].second)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putInt(KEY_PITCH, pitchIndex)?.apply()
    }

    // ------------------------------------------------------------------ bicara

    /**
     * @param decorate true = boleh ditambah "meong." kecil. Balasan Skylen sendiri pakai false.
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
            val parts = chunk(normalizeForSpeech(message))
            if (parts.isEmpty()) return
            val stamp = System.currentTimeMillis().toString()
            currentStamp = stamp
            speakStart = System.currentTimeMillis()
            speakingFlag = true
            parts.forEachIndexed { i, part ->
                val last = i == parts.lastIndex
                val id = "galaxy_${stamp}_$i" + if (last) "_last" else ""
                engine.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
            }
        } else {
            // TTS belum siap (baru dibuka): simpan kalimat terakhir, diucapkan begitu siap.
            pending = message
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        speakingFlag = false
    }

    // Teks dibersihkan supaya enak dibacakan: singkatan gaul diucapkan utuh, simbol/emoji dibuang.
    private val symbols = Regex("[\\p{So}\\p{Cs}•·►▶*`#_~]")
    private val urls = Regex("https?://\\S+")
    private val gw = Regex("\\bgw\\b", RegexOption.IGNORE_CASE)
    private val yg = Regex("\\byg\\b", RegexOption.IGNORE_CASE)
    private val dgn = Regex("\\bdgn\\b", RegexOption.IGNORE_CASE)
    private val gk = Regex("\\b(gk|gak|ga)\\b", RegexOption.IGNORE_CASE)

    private fun normalizeForSpeech(text: String): String {
        var t = text.replace(urls, "link")
        t = t.replace(symbols, " ")
        t = t.replace(gw, "gue").replace(yg, "yang").replace(dgn, "dengan").replace(gk, "nggak")
        return t.replace(Regex("\\s+"), " ").trim().take(900)
    }

    private fun chunk(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
            if (sb.isNotEmpty() && sb.length + sentence.length > 280) {
                out.add(sb.toString())
                sb.setLength(0)
            }
            if (sentence.length > 280) {
                sentence.chunked(280).forEach { out.add(it) }
            } else {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(sentence)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
        speakingFlag = false
        speakingListener = null
        textListener = null
    }
}
