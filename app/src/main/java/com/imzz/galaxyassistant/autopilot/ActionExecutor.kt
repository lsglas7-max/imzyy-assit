package com.imzz.galaxyassistant.autopilot

import android.content.Context
import com.imzz.galaxyassistant.accessibility.GalaxyAccessibilityService
import com.imzz.galaxyassistant.accessibility.GestureController

object ActionExecutor {
    sealed class Result {
        data object Success : Result()
        data class Failed(val message: String) : Result()
        data class NeedsHuman(val message: String) : Result()
    }

    fun execute(context: Context, action: PlannedAction, ocrText: String, layout: String = ""): Result {
        if (ScreenUnderstanding.isSensitiveScreen(ocrText)) {
            return Result.NeedsHuman("Layar mungkin berisi autentikasi atau tindakan sensitif. Kontrol otomatis dihentikan demi keamanan.")
        }
        if (action.type == "type_text" && ScreenUnderstanding.hasSoftWords(ocrText)) {
            return Result.NeedsHuman("Ini layar kirim pesan/posting. Ketik dan kirim manual ya bre.")
        }
        if (action.type == "tap" && ScreenUnderstanding.tapsSoftTarget(layout, action.x, action.y)) {
            return Result.NeedsHuman("Tombol kirim/pesan/posting. Gw nggak tekan otomatis, lu yang tekan bre.")
        }
        return when (action.type) {
            "tap" -> if (GestureController.tapNormalized(context, action.x, action.y)) Result.Success
                else Result.Failed("Accessibility gesture belum tersedia atau ditolak Android.")
            "swipe" -> if (GestureController.swipeNormalized(context, action.x, action.y, action.x2, action.y2)) Result.Success
                else Result.Failed("Gesture swipe gagal dikirim.")
            "type_text" -> {
                val service = GalaxyAccessibilityService.instance
                    ?: return Result.Failed("Aktifkan Accessibility Service terlebih dahulu.")
                if (action.text.isBlank()) Result.Failed("Teks input kosong.")
                else if (service.setFocusedText(action.text)) Result.Success
                else Result.NeedsHuman("Kolom input aman yang sedang fokus tidak ditemukan. Silakan pilih kolomnya secara manual.")
            }
            "wait" -> Result.Success
            "ask_user" -> Result.NeedsHuman(action.reason.ifBlank { "AI perlu arahan pengguna." })
            "stop" -> Result.NeedsHuman(action.reason.ifBlank { "AI memilih berhenti." })
            else -> Result.Failed("Jenis aksi tidak didukung: ${action.type}")
        }
    }
}
