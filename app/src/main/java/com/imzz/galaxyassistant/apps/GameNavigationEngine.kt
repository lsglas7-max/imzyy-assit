package com.imzz.galaxyassistant.apps

object GameNavigationEngine {
    fun contextualGoal(appLabel: String, requestedGame: String): String {
        return if (appLabel.contains("roblox", ignoreCase = true)) {
            "Roblox sedang terbuka. Cari game bernama '$requestedGame' menggunakan UI pencarian Roblox. Cocokkan judul hasil pencarian; jika ada beberapa hasil mirip, minta pengguna memilih dan jangan masuk secara asal."
        } else {
            "Aplikasi '$appLabel' sedang terbuka. Bantu navigasi berdasarkan instruksi: $requestedGame. Amati setiap perubahan layar dan berhenti jika ada aksi sensitif."
        }
    }
}
