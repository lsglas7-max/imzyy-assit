package com.imzz.galaxyassistant.autopilot

import kotlin.math.abs

object ScreenUnderstanding {
    /** Autentikasi, pembayaran, penghapusan: layar yang memuat ini selalu menghentikan kontrol otomatis. */
    private val hardWords = listOf(
        "password", "passcode", "otp", "one time password", "captcha", "payment", "checkout",
        "purchase", "buy now", "top up", "transfer", "delete account", "confirm deletion",
        "hapus akun", "pembayaran", "beli", "bayar", "kata sandi", "verifikasi", "login",
        "masuk akun", "ubah email", "ubah password"
    )

    /**
     * Pesan/kirim: kata ini muncul normal di menu app seperti TikTok (tab "Pesan"), jadi layar yang memuatnya
     * tidak otomatis berhenti. Yang diblok: mengetik teks, atau mengetuk tepat di dekat kata tersebut.
     */
    private val softWords = listOf("send", "kirim", "chat", "pesan", "message", "post", "posting", "unggah", "upload")

    fun isSensitiveScreen(ocrText: String): Boolean {
        val text = ocrText.lowercase()
        return hardWords.any { text.contains(it) }
    }

    fun hasSoftWords(ocrText: String): Boolean {
        val text = ocrText.lowercase()
        return softWords.any { text.contains(it) }
    }

    /** True kalau titik (x,y) berada dekat teks "kirim/pesan/post" di layout OCR. */
    fun tapsSoftTarget(layout: String, x: Double, y: Double): Boolean {
        for (line in layout.lineSequence()) {
            val space = line.indexOf(' ')
            if (space <= 0) continue
            val label = line.substring(space + 1).lowercase()
            if (softWords.none { label.contains(it) }) continue
            val parts = line.substring(0, space).split(',')
            val lx = parts.getOrNull(0)?.toDoubleOrNull() ?: continue
            val ly = parts.getOrNull(1)?.toDoubleOrNull() ?: continue
            if (abs(lx - x) < 0.12 && abs(ly - y) < 0.05) return true
        }
        return false
    }

    fun summarize(ocrText: String): String = ocrText
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(12)
        .joinToString(" • ")
        .take(700)
}
