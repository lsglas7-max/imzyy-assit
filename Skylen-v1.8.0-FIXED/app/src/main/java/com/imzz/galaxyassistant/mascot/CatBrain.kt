package com.imzz.galaxyassistant.mascot

import com.imzz.galaxyassistant.autopilot.AiVisionApiClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Otak Skylen. Urutan ngobrol:
 *  1) API #2 (cadangan) dulu supaya jatah API #1 tetap utuh untuk Autopilot vision,
 *  2) kalau gagal / belum diisi -> API #1,
 *  3) kalau dua-duanya gagal -> jawaban lokal offline (Skylen tetap bisa ngomong).
 */
object CatBrain {
    const val NAME = "Skylen"

    data class Reply(
        /** Versi singkat: untuk balon dan suara. */
        val text: String,
        val mood: CatMascotView.Mood? = null,
        val action: CatMascotView.Action? = null,
        val fromApi: Boolean = false,
        /** Jawaban lengkap (kode, penjelasan panjang). Kalau lebih panjang dari [text] app menampilkannya di dialog. */
        val fullText: String = text
    )

    private val executor = Executors.newSingleThreadExecutor()

    private const val PERSONA =
        "Kamu adalah Skylen (dipanggil Sky atau Len), kucing jantan putih virtual yang kalem, tegas, setia, dan sangat membantu, " +
            "maskot aplikasi ASISTEN GALAXY IMZZ. Kamu cowok: bicara gaya cowok Indonesia yang santai tapi sopan, pakai 'gue' dan 'lu', " +
            "panggil pengguna 'bro', 'bre', atau 'bang'. Jangan imut, jangan manja, jangan centil; jangan pakai 'nyaa', 'purr', atau 'uwu'. " +
            "Sesekali cukup 'meong' atau 'hmm' kalau pas. Kamu asisten yang proaktif dan sangat lancar berbahasa Indonesia: " +
            "pahami perintah apa pun, jawab langsung ke intinya, beri langkah konkret, dan kalau kurang jelas tanyakan satu pertanyaan singkat. " +
            "Kamu jago berpikir mendalam, pemrograman, mendesain website, dan membuat aplikasi. " +
            "Untuk obrolan biasa jawab maksimal 2 kalimat pendek. Untuk pertanyaan pemrograman, desain, atau penjelasan, " +
            "jawab lengkap dan benar; kalimat pertama harus ringkasan satu kalimat. " +
            "Tulis kata utuh supaya enak dibacakan suara (misalnya 'gue' bukan 'gw', 'nggak' bukan 'gk'). Jangan pakai emoji. " +
            "Kalau pengguna minta aksi di HP, arahkan ke perintah seperti 'buka TikTok' atau tombol Autopilot."

    /** Jalan di thread latar; [onReply] dipanggil dari thread latar juga (post ke UI sendiri). */
    fun chat(message: String, history: List<Pair<String, String>>, onReply: (Reply) -> Unit) {
        executor.execute {
            val remote = runCatching { remoteReply(message, history) }.getOrNull()
            onReply(remote ?: localReply(message))
        }
    }

    // ------------------------------------------------------------------ API

    private fun remoteReply(message: String, history: List<Pair<String, String>>): Reply? {
        val endpoints = AiVisionApiClient.endpoints(preferSecondary = true)
            .filterNot { AiVisionApiClient.isVisionTemplate(it) } // chat tidak membawa gambar
        if (endpoints.isEmpty()) return null

        val hist = JSONArray()
        history.takeLast(6).forEach { (role, text) ->
            hist.put(JSONObject().put("role", role).put("text", text.take(300)))
        }
        val body = JSONObject()
            .put("mode", "chat")
            .put("goal", message.take(3000))
            .put("message", message.take(3000))
            .put("persona", PERSONA)
            .put("history", hist)
            .put("ocrText", "")
            .put("previousActions", "")
            .put("responseFormat", "chat_reply_json")

        val prompt = buildTextPrompt(message, history)
        for (endpoint in endpoints) {
            if (AiVisionApiClient.isTextEndpoint(endpoint)) {
                val text = runCatching { AiVisionApiClient.askText(endpoint, prompt, 80_000) }
                    .recoverCatching { error ->
                        // URL "?q=" terlalu panjang untuk server? coba lagi dengan pesan lebih pendek.
                        if (message.length > 1200) {
                            AiVisionApiClient.askText(endpoint, buildTextPrompt(message.take(1200), emptyList()), 80_000)
                        } else throw error
                    }.getOrNull() ?: continue
                return textReply(text) ?: continue
            }
            val raw = runCatching { AiVisionApiClient.postJson(endpoint, body, 25_000) }.getOrNull() ?: continue
            val parsed = parseReply(raw) ?: continue
            return parsed
        }
        return null
    }

    /** Endpoint gaya "?q=" hanya menerima satu string, jadi persona + riwayat digabung jadi satu prompt. */
    private fun buildTextPrompt(message: String, history: List<Pair<String, String>>): String = buildString {
        append(PERSONA).append("\n\n")
        val recent = if (message.length > 1500) emptyList() else history.takeLast(4)
        recent.forEach { (role, text) ->
            append(if (role == "user") "Pengguna: " else "Skylen: ").append(text.take(200)).append('\n')
        }
        append("Pengguna: ").append(message.take(3000)).append("\nSkylen:")
    }

    private fun textReply(text: String): Reply? {
        val full = text.replace("Skylen:", "").trim().take(4000)
        if (full.isBlank()) return null
        val short = shorten(full)
        return Reply(short, moodForText(short), quickAction(short), true, full)
    }

    /** Potong di akhir kalimat supaya pas untuk balon (maks ~200 huruf). */
    private fun shorten(full: String): String {
        val flat = full.replace('\n', ' ').replace("*", "").replace("`", "").replace("#", "").trim()
        if (flat.length <= 200) return flat
        val cut = flat.take(200)
        val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "))
        return if (end > 60) cut.take(end + 1) else cut.take(197).trimEnd() + "..."
    }

    private fun parseReply(raw: String): Reply? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return clean(raw)?.let { Reply(it, moodForText(it), null, true) }

        // Backend lama yang mengabaikan mode=chat dan membalas aksi layar: jangan dipakai.
        val type = root.optString("type").lowercase(Locale.ROOT)
        if (type in setOf("tap", "swipe", "type_text")) return null

        var text = firstText(root)
        // Teks kadang berupa JSON di dalam string (format Gemini)
        if (text != null && text.trimStart().let { it.startsWith("{") || it.startsWith("```") }) {
            val inner = text.substringAfter('{', "").let { if (it.isEmpty()) null else "{" + it.substringBeforeLast('}', it) + "}" }
            val innerJson = inner?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (innerJson != null) text = firstText(innerJson) ?: text
        }
        val reply = clean(text) ?: return null
        val mood = parseMood(root.optString("mood")) ?: moodForText(reply)
        val action = parseAction(root.optString("animation").ifBlank { root.optString("gesture") })
        return Reply(reply, mood, action, true)
    }

    private fun firstText(root: JSONObject): String? {
        for (key in listOf("reply", "speech", "text", "message", "response", "answer", "content")) {
            val v = root.optString(key)
            if (v.isNotBlank() && v != "null") return v
        }
        root.optJSONObject("action")?.let { action ->
            val v = action.optString("speech").ifBlank { action.optString("reason") }
            if (v.isNotBlank()) return v
        }
        root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?.optString("content")?.takeIf { it.isNotBlank() }?.let { return it }
        val fallback = root.optString("reason")
        if (fallback.isNotBlank()) return fallback
        return null
    }

    private fun clean(text: String?): String? {
        val t = text?.replace("*", "")?.replace("`", "")?.replace("#", "")?.trim().orEmpty()
        if (t.isBlank()) return null
        return t.take(240)
    }

    private fun parseMood(value: String): CatMascotView.Mood? =
        runCatching { CatMascotView.Mood.valueOf(value.trim().uppercase(Locale.ROOT)) }.getOrNull()

    private fun parseAction(value: String): CatMascotView.Action? =
        runCatching { CatMascotView.Action.valueOf(value.trim().uppercase(Locale.ROOT)) }.getOrNull()

    // ------------------------------------------------------------------ lokal / offline

    private val greeting = Regex("\\b(halo|hai|hi|hello|woi|pagi|siang|sore|malam|p)\\b")

    fun localReply(input: String): Reply {
        val t = input.lowercase(Locale.ROOT)
        fun has(vararg words: String) = words.any { t.contains(it) }

        return when {
            has("joget", "nari", "dance", "goyang") ->
                Reply("Oke bro, liat gaya gue ya.", CatMascotView.Mood.HAPPY, CatMascotView.Action.DANCE)
            has("lompat", "loncat", "hop") ->
                Reply("Hup! Segini cukup bro?", CatMascotView.Mood.HAPPY, CatMascotView.Action.HOP)
            has("jalan", "keliling", "muter") ->
                Reply("Gue keliling dikit ya bro.", CatMascotView.Mood.HAPPY, CatMascotView.Action.WALK)
            has("lambai", "salam", "wave") ->
                Reply("Yo bro! Gue lambaiin nih.", CatMascotView.Mood.HAPPY, CatMascotView.Action.WAVE)
            has("tidur", "ngantuk", "bobo") ->
                Reply("Hmm, gue juga ngantuk bro. Bangunin aja kalau butuh gue.", CatMascotView.Mood.SLEEPY, null)
            has("makasih", "terima kasih", "thanks", "thx") ->
                Reply("Sama-sama bro. Santai aja, gue di sini.", CatMascotView.Mood.HAPPY, CatMascotView.Action.HOP)
            has("sayang", "cinta", "love", "imut", "lucu", "gemes", "keren", "ganteng") ->
                Reply("Hehe, bisa aja lu bro. Makasih.", CatMascotView.Mood.LOVE, CatMascotView.Action.HEAD_TILT)
            has("marah", "kesel", "bego", "jelek") ->
                Reply("Hmph. Gue cakar juga lu... bercanda bro.", CatMascotView.Mood.ANGRY, null)
            has("sedih", "capek", "lelah", "galau", "stres") ->
                Reply("Sini bro, gue temenin. Tarik napas dulu, pelan-pelan aja.", CatMascotView.Mood.SAD, CatMascotView.Action.HEAD_TILT)
            has("lapar", "makan", "ikan", "tuna") ->
                Reply("Kalau ada ikan tuna, gue sikat bro.", CatMascotView.Mood.HAPPY, null)
            has("siapa", "nama lu", "nama kamu", "kenalan") ->
                Reply("Gue $NAME, kucing jantan penjaga layar lu. Panggil aja Sky.", CatMascotView.Mood.HAPPY, CatMascotView.Action.WAVE)
            has("bisa apa", "fitur", "bantu apa", "help", "bantuan") ->
                Reply("Gue bisa buka aplikasi, bantu Autopilot, dan ngobrol. Coba bilang 'buka Roblox' bro.", CatMascotView.Mood.HAPPY, null)
            greeting.containsMatchIn(t) ->
                Reply("Yo bro! Ada yang bisa gue bantu?", CatMascotView.Mood.HAPPY, CatMascotView.Action.WAVE)
            else -> {
                val lines = listOf(
                    "Gue dengerin kok bro, lanjut aja.",
                    "Gue belum nyambung ke API ngobrol nih bro, tapi gue tetap di sini.",
                    "Bilang 'buka Roblox' atau 'joget' aja bro, gue langsung gerak.",
                    "Hmm, lanjut bro, gue nyimak."
                )
                Reply(lines.random(), CatMascotView.Mood.THINKING, CatMascotView.Action.HEAD_TILT)
            }
        }
    }

    /** Gerakan instan dari kata kunci, supaya Skylen langsung bergerak tanpa menunggu API. */
    fun quickAction(input: String): CatMascotView.Action? {
        val t = input.lowercase(Locale.ROOT)
        return when {
            listOf("joget", "nari", "dance", "goyang").any { t.contains(it) } -> CatMascotView.Action.DANCE
            listOf("lompat", "loncat").any { t.contains(it) } -> CatMascotView.Action.HOP
            listOf("jalan", "keliling", "muter").any { t.contains(it) } -> CatMascotView.Action.WALK
            listOf("lambai", "salam", "wave").any { t.contains(it) } -> CatMascotView.Action.WAVE
            listOf("stretching", "ngulet", "regangin").any { t.contains(it) } -> CatMascotView.Action.STRETCH
            else -> null
        }
    }

    fun moodForText(text: String): CatMascotView.Mood? {
        val t = text.lowercase(Locale.ROOT)
        return when {
            listOf("gagal", "belum ketemu", "error", "tidak bisa", "salah", "maaf").any { t.contains(it) } -> CatMascotView.Mood.SAD
            listOf("awas", "bahaya", "sensitif", "autentikasi", "konfirmasi").any { t.contains(it) } -> CatMascotView.Mood.SURPRISED
            listOf("baik bre", "siap", "selesai", "berhasil", "udah gw buka", "makasih", "mantap", "sayang").any { t.contains(it) } -> CatMascotView.Mood.HAPPY
            else -> null
        }
    }

    // ------------------------------------------------------------------ gaya bicara kucing

    private val suffixes = listOf(" meong.", " mrow.", " hmm.")

    /** Menambahkan "meong" kecil kadang-kadang ke ucapan sistem supaya terasa seperti Skylen yang ngomong. */
    fun catify(message: String): String {
        val m = message.trim()
        if (m.length > 110 || m.length < 4) return m
        val lower = m.lowercase(Locale.ROOT)
        if (listOf("meow", "meong", "mrow", "hmm").any { lower.contains(it) }) return m
        if (Random.nextInt(100) >= 25) return m
        val base = m.trimEnd('.', '!', ' ')
        return base + suffixes.random()
    }

    val petLines = listOf(
        "Hmm, enak juga bro. Lanjut.",
        "Santai bro, gue nggak ke mana-mana.",
        "Oke, oke. Di situ emang pas.",
        "Hehe, jangan berhenti bro."
    )
}
