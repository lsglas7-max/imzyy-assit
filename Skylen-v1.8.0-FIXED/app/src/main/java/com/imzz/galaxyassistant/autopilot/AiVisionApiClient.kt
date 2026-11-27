package com.imzz.galaxyassistant.autopilot

import com.imzz.galaxyassistant.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal, bounded HTTP client for chat endpoints. No screenshots, contacts or device files are uploaded. */
object AiVisionApiClient {
    fun endpoint(): String = BuildConfig.AI_API_URL.trim()
    fun endpoint2(): String = BuildConfig.AI_API_URL_2.trim()

    fun endpoints(preferSecondary: Boolean = false): List<String> {
        val values = if (preferSecondary) listOf(endpoint2(), endpoint()) else listOf(endpoint(), endpoint2())
        return values.filter { it.startsWith("https://") && !isVisionTemplate(it) }.distinct()
    }

    fun isConfigured(): Boolean = endpoints().isNotEmpty()
    fun isVisionTemplate(value: String): Boolean = value.contains("{url}", true) || value.contains("/vision", true)
    fun isTextEndpoint(value: String): Boolean = value.contains("q=") || value.contains("{q}")

    fun askText(endpoint: String, prompt: String, timeoutMs: Int): String {
        val encoded = URLEncoder.encode(prompt.take(7000), Charsets.UTF_8.name())
        val target = when {
            endpoint.contains("{q}") -> endpoint.replace("{q}", encoded)
            endpoint.endsWith("q=") -> endpoint + encoded
            endpoint.contains("q=") -> endpoint.substringBefore("q=") + "q=" + encoded
            endpoint.contains("?") -> "$endpoint&q=$encoded"
            else -> "$endpoint?q=$encoded"
        }
        val raw = request(target, "GET", null, timeoutMs)
        return extractText(raw).ifBlank { raw }.take(10000)
    }

    fun postJson(endpoint: String, body: JSONObject, timeoutMs: Int): String =
        request(endpoint, "POST", body.toString(), timeoutMs)

    private fun request(url: String, method: String, body: String?, timeoutMs: Int): String {
        require(url.startsWith("https://", ignoreCase = true)) { "Endpoint harus memakai HTTPS." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs.coerceIn(3000, 30000)
            readTimeout = timeoutMs.coerceIn(3000, 30000)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "SkylenGalaxyAssistant/1.8")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Server AI HTTP $code: ${response.take(180)}")
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun extractText(raw: String): String {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
        for (key in listOf("reply", "response", "answer", "text", "result", "output", "message", "content")) {
            val value = obj.opt(key)
            if (value is String && value.isNotBlank()) return value
        }
        obj.optJSONObject("data")?.let { nested ->
            for (key in listOf("reply", "response", "answer", "text", "result", "output")) {
                val value = nested.optString(key)
                if (value.isNotBlank() && value != "null") return value
            }
        }
        obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        obj.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
        return raw
    }
}
