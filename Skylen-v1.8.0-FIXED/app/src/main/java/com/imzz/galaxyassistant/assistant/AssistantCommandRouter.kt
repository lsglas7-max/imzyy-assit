package com.imzz.galaxyassistant.assistant

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.imzz.galaxyassistant.accessibility.AccessibilityController
import com.imzz.galaxyassistant.accessibility.GalaxyAccessibilityService
import com.imzz.galaxyassistant.mascot.CatMascotView
import com.imzz.galaxyassistant.overlay.OverlayCatService
import java.net.URLEncoder
import java.util.Locale

/** The Android action layer. Chat/AI is called only when a command is not recognized here. */
object AssistantCommandRouter {
    data class Result(
        val message: String,
        val handled: Boolean = true,
        val closeAssistant: Boolean = false
    )

    fun execute(activity: Activity, input: String, cat: CatMascotView? = null): Result? {
        val original = input.trim()
        if (original.isBlank()) return null
        val command = original
            .replace(Regex("^(skylen|sky|len|bro|bre)[,!: ]+", RegexOption.IGNORE_CASE), "")
            .trim()
        val lower = command.lowercase(Locale.ROOT)

        when {
            lower in setOf("stop ngomong", "diam", "diem", "stop bicara", "berhenti bicara") -> {
                com.imzz.galaxyassistant.voice.VoiceFeedback.stopSpeaking()
                return Result("Oke bro, gue diam dulu.")
            }
            lower.contains("matikan kucing") || lower.contains("sembunyikan kucing") || lower.contains("tutup overlay") -> {
                OverlayCatService.stop(activity)
                return Result("Overlay Skylen gue matikan.")
            }
            lower.contains("tampilkan kucing") || lower.contains("munculkan kucing") || lower.contains("nyalakan overlay") -> {
                if (android.provider.Settings.canDrawOverlays(activity)) {
                    OverlayCatService.start(activity)
                    return Result("Siap bro, Skylen muncul di atas aplikasi dan bisa lu geser.")
                }
                activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
                return Result("Izinkan Tampil di Atas Aplikasi dulu, lalu aktifkan overlay Skylen.")
            }
            lower in setOf("joget", "joget dong", "skylen joget", "kucing joget", "dance") || lower.contains("suruh kucing joget") -> {
                cat?.playAction(CatMascotView.Action.DANCE)
                cat?.setMood(CatMascotView.Mood.HAPPY, 4000)
                return Result("Oke, gue joget dulu bro!")
            }
            lower.contains("lompat") && (lower.contains("kucing") || lower.contains("sky") || lower == "lompat") -> {
                cat?.playAction(CatMascotView.Action.HOP)
                return Result("Hup! Gitu bro.")
            }
            lower.contains("lambai") || lower.contains("dadah") -> {
                cat?.playAction(CatMascotView.Action.WAVE)
                return Result("Yo bro, gue lambaiin!")
            }
        }

        val tap = Regex("^(?:tap|ketuk|ketuk layar|sentuh)\\s+(\\d{1,5})[ ,]+(\\d{1,5})$", RegexOption.IGNORE_CASE).find(command)
        if (tap != null) {
            if (!GalaxyAccessibilityService.isConnected()) {
                AccessibilityController.openSettings(activity)
                return Result("Aktifkan Skylen Manual Gesture Helper di Accessibility dulu. Setelah aktif, ulangi perintah tap.")
            }
            val ok = GalaxyAccessibilityService.tap(tap.groupValues[1].toFloat(), tap.groupValues[2].toFloat())
            return Result(if (ok) "Ketukan layar dikirim sesuai koordinat yang lu sebut." else "Android menolak ketukan itu. Coba lagi.")
        }
        val swipe = Regex("^(?:swipe|geser|usap)\\s+(\\d{1,5})[ ,]+(\\d{1,5})[ ,]+(?:ke\\s+)?(\\d{1,5})[ ,]+(\\d{1,5})(?:[ ,]+(\\d{2,4}))?$", RegexOption.IGNORE_CASE).find(command)
        if (swipe != null) {
            if (!GalaxyAccessibilityService.isConnected()) {
                AccessibilityController.openSettings(activity)
                return Result("Aktifkan Skylen Manual Gesture Helper di Accessibility dulu, lalu ulangi perintah geser.")
            }
            val ok = GalaxyAccessibilityService.swipe(
                swipe.groupValues[1].toFloat(), swipe.groupValues[2].toFloat(),
                swipe.groupValues[3].toFloat(), swipe.groupValues[4].toFloat(),
                swipe.groupValues[5].toLongOrNull() ?: 350L
            )
            return Result(if (ok) "Geseran layar dikirim sesuai koordinat perintah lu." else "Android menolak geseran itu.")
        }
        if (lower in setOf("kembali", "back", "tekan kembali")) {
            if (!GalaxyAccessibilityService.isConnected()) {
                AccessibilityController.openSettings(activity)
                return Result("Aktifkan izin Accessibility untuk mengirim tombol Kembali.")
            }
            return Result(if (GalaxyAccessibilityService.back()) "Oke, gue tekan Kembali." else "Tombol Kembali gagal dikirim.")
        }

        val downloadMatch = Regex("^(?:tolong\\s+)?(?:download|unduh|install|instal|pasang|cari aplikasi|cari app)\\s+(?:aplikasi\\s+|app\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(command)
        if (downloadMatch != null) {
            val target = cleanTarget(downloadMatch.groupValues[1])
            if (target.isBlank()) return Result("Sebut nama aplikasinya, bro.")
            openPlayStoreSearch(activity, target)
            return Result("Gue bukain pencarian Google Play untuk $target. Pilih aplikasinya lalu tekan Install; pemasangan tetap lu konfirmasi di Play Store.", closeAssistant = true)
        }

        val openMatch = Regex("^(?:(?:gue|aku|saya)\\s+)?(?:(?:mau|ingin|pengen)\\s+)?(?:(?:tolong|coba|bisa)\\s+)*(?:buka|bukain|open|jalankan|launch|mainkan|mainin)\\s+(?:aplikasi\\s+|app\\s+|game\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(command)
        if (openMatch != null) {
            val target = cleanTarget(openMatch.groupValues[1])
            if (target.isBlank()) return Result("Nama aplikasinya apa, bro?")
            val app = findLaunchableApp(activity, target)
            if (app != null) {
                return try {
                    val launch = activity.packageManager.getLaunchIntentForPackage(app.first)
                        ?: throw IllegalStateException("Aplikasi tidak menyediakan halaman peluncur.")
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(launch)
                    val isGame = lower.contains("mainin") || lower.contains("mainkan") || lower.contains("game")
                    Result(
                        if (isGame) "${app.second} sudah gue buka. Gue bisa bantu perintah tap dan swipe manual, tapi belum bisa menjamin main otomatis jago di semua game." 
                        else "Siap bro, ${app.second} gue buka sekarang.",
                        closeAssistant = true
                    )
                } catch (error: Exception) {
                    Result("Gagal membuka ${app.second}: ${error.message ?: "Android menolak permintaan"}.")
                }
            }
            openPlayStoreSearch(activity, target)
            return Result("Aplikasi $target belum terdeteksi terpasang. Gue bukain hasil pencarian Google Play supaya lu bisa instal dengan aman.", closeAssistant = true)
        }

        val settingsMatch = Regex("^(?:buka|open)\\s+(?:pengaturan|settings|setelan)(?:\\s+hp)?$", RegexOption.IGNORE_CASE).matches(command)
        if (settingsMatch) {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return Result("Pengaturan HP gue buka, bro.", closeAssistant = true)
        }

        val webSearch = Regex("^(?:cari di google|search web|googling|cari di internet)\\s+(.+)$", RegexOption.IGNORE_CASE).find(command)
        if (webSearch != null) {
            val query = URLEncoder.encode(webSearch.groupValues[1].trim(), Charsets.UTF_8.name())
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return Result("Gue bukain hasil pencarian web-nya.", closeAssistant = true)
        }

        return null
    }

    private fun cleanTarget(value: String): String = value
        .replace(Regex("\\b(dong|ya|deh|sekarang|secepatnya|please|bro|bre|tolong)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("[.!?,]+$"), "")
        .trim()
        .let { raw ->
            when (raw.lowercase(Locale.ROOT)) {
                "wa", "w a" -> "whatsapp"
                "yt", "youtube" -> "youtube"
                "ig" -> "instagram"
                "fb" -> "facebook"
                "tt" -> "tiktok"
                else -> raw
            }
        }

    private fun findLaunchableApp(activity: Activity, query: String): Pair<String, String>? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val entries = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val needle = normalize(query)
        if (needle.isBlank()) return null
        val ranked = entries.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == activity.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(activity.packageManager).toString() }.getOrDefault(packageName)
            val name = normalize(label)
            val pkg = normalize(packageName.substringAfterLast('.'))
            val score = when {
                name == needle -> 1000
                name.startsWith(needle) -> 850
                name.contains(needle) -> 700
                pkg == needle -> 650
                pkg.contains(needle) -> 500
                needle.contains(name) && name.length >= 4 -> 400
                else -> 0
            }
            if (score == 0) null else Triple(packageName, label, score)
        }.distinctBy { it.first }.sortedByDescending { it.third }
        return ranked.firstOrNull()?.let { it.first to it.second }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]"), "")

    private fun openPlayStoreSearch(activity: Activity, query: String) {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(market)
        } catch (_: Exception) {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
