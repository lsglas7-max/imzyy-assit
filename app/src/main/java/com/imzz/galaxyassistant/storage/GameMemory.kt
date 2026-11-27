package com.imzz.galaxyassistant.storage

import android.content.Context
import org.json.JSONObject

data class ControlProfile(
    val tapDurationMs: Long = 75L,
    val swipeDurationMs: Long = 350L,
    val sensitivity: Float = 1.0f
)

object GameMemory {
    private const val PREFS = "galaxy_game_memory"
    private const val KEY_PROFILE = "profile_"

    fun profile(context: Context, packageName: String? = null): ControlProfile {
        val key = KEY_PROFILE + (packageName?.takeIf { it.isNotBlank() } ?: "default")
        val jsonText = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
            ?: return ControlProfile()
        return runCatching {
            val json = JSONObject(jsonText)
            ControlProfile(
                tapDurationMs = json.optLong("tap", 75L).coerceIn(40L, 800L),
                swipeDurationMs = json.optLong("swipe", 350L).coerceIn(80L, 1800L),
                sensitivity = json.optDouble("sensitivity", 1.0).toFloat().coerceIn(0.4f, 2.0f)
            )
        }.getOrDefault(ControlProfile())
    }

    fun saveProfile(context: Context, profile: ControlProfile, packageName: String? = null) {
        val json = JSONObject()
            .put("tap", profile.tapDurationMs.coerceIn(40L, 800L))
            .put("swipe", profile.swipeDurationMs.coerceIn(80L, 1800L))
            .put("sensitivity", profile.sensitivity.coerceIn(0.4f, 2.0f))
        val key = KEY_PROFILE + (packageName?.takeIf { it.isNotBlank() } ?: "default")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, json.toString()).apply()
    }
}
