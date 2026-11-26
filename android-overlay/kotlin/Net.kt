package id.imzyy.xl

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class HttpFailure(val code: Int, url: String) : Exception("HTTP $code untuk $url")

/** Small dependency-free HTTPS helper. HTTPS only, hash-verified downloads. */
object Net {
    private const val UA = "IMZYY-XL-Launcher/" + ImzyyConfig.VERSION

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Hanya HTTPS yang diizinkan: $url" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        return c
    }

    fun getText(url: String): String {
        val c = open(url)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw HttpFailure(code, url)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Opens [url] (HTTPS, redirects followed) and hands the stream to [block]. */
    fun stream(url: String, block: (java.io.InputStream) -> Unit) {
        val c = open(url)
        c.readTimeout = 60_000
        try {
            val code = c.responseCode
            if (code !in 200..299) throw HttpFailure(code, url)
            c.inputStream.use(block)
        } finally {
            c.disconnect()
        }
    }

    /** Downloads [url] to [dest]; the file is only kept when its hash matches. */
    fun download(url: String, dest: File, sha512: String?, sha1: String?, onBytes: (Long) -> Unit = {}) {
        require(sha512 != null || sha1 != null) { "File tanpa hash ditolak: ${dest.name}" }
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val c = open(url)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw HttpFailure(code, url)
            val md = MessageDigest.getInstance(if (sha512 != null) "SHA-512" else "SHA-1")
            c.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            val want = (sha512 ?: sha1)!!.lowercase()
            if (got != want) error("Hash tidak cocok untuk ${dest.name}")
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
            }
        } finally {
            c.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }
}
