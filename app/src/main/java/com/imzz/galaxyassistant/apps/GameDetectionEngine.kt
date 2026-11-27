package com.imzz.galaxyassistant.apps

import android.content.pm.ApplicationInfo

object GameDetectionEngine {
    fun isProbablyGame(info: ApplicationInfo, packageName: String, label: String): Boolean {
        val categoryMatch = android.os.Build.VERSION.SDK_INT >= 26 && info.category == ApplicationInfo.CATEGORY_GAME
        val hints = listOf("game", "games", "roblox", "minecraft", "pubg", "free fire", "efootball", "genshin")
        val text = "$packageName $label".lowercase()
        return categoryMatch || hints.any { text.contains(it) }
    }
}
