package id.imzyy.xl

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import org.json.JSONObject

data class MrFile(
    val path: String,
    val urls: List<String>,
    val sha512: String?,
    val sha1: String?,
    val size: Long,
    val clientUnsupported: Boolean,
)

data class MrInfo(
    val name: String,
    val version: String,
    val summary: String,
    val deps: Map<String, String>,
    val files: List<MrFile>,
)

/** Modrinth .mrpack (format v1) reader and installer. */
object Mrpack {
    private val allowedHosts = setOf("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com")

    private fun readLimited(zin: ZipInputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = zin.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) error("modrinth.index.json terlalu besar")
        }
        return out.toByteArray()
    }

    fun readIndex(ctx: Context, uri: Uri): MrInfo {
        val raw = ctx.contentResolver.openInputStream(uri) ?: error("Tidak bisa membuka file")
        raw.use { stream ->
            ZipInputStream(stream).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    if (e.name == "modrinth.index.json") {
                        val json = JSONObject(String(readLimited(zin, 8_000_000), Charsets.UTF_8))
                        if (json.optString("game") != "minecraft") error("Bukan modpack Minecraft")
                        if (json.optInt("formatVersion") != 1) error("formatVersion ${json.optInt("formatVersion")} tidak didukung")
                        val depsJson = json.optJSONObject("dependencies") ?: JSONObject()
                        val deps = LinkedHashMap<String, String>()
                        depsJson.keys().forEach { k -> deps[k] = depsJson.getString(k) }
                        val arr = json.getJSONArray("files")
                        val files = ArrayList<MrFile>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val urls = ArrayList<String>()
                            val ua = o.getJSONArray("downloads")
                            for (j in 0 until ua.length()) urls.add(ua.getString(j))
                            val h = o.optJSONObject("hashes")
                            files.add(
                                MrFile(
                                    path = o.getString("path"),
                                    urls = urls,
                                    sha512 = h?.optString("sha512")?.ifEmpty { null },
                                    sha1 = h?.optString("sha1")?.ifEmpty { null },
                                    size = o.optLong("fileSize"),
                                    clientUnsupported = o.optJSONObject("env")?.optString("client") == "unsupported",
                                ),
                            )
                        }
                        return MrInfo(json.optString("name", "Modpack"), json.optString("versionId"), json.optString("summary"), deps, files)
                    }
                    e = zin.nextEntry
                }
            }
        }
        error("modrinth.index.json tidak ada: ini bukan file .mrpack yang valid")
    }

    private fun safeFile(root: File, rel: String): File {
        require(rel.isNotEmpty() && !rel.startsWith("/") && !rel.contains("\\")) { "Path tidak aman: $rel" }
        val f = File(root, rel).canonicalFile
        require(f.path.startsWith(root.path + File.separator)) { "Path keluar dari folder instance: $rel" }
        return f
    }

    private fun hostOk(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = url.removePrefix("https://").substringBefore('/').substringBefore(':').lowercase()
        return host in allowedHosts
    }

    fun install(ctx: Context, uri: Uri, info: MrInfo, target: File, say: (String) -> Unit, onProgress: (Float) -> Unit) {
        target.mkdirs()
        val root = target.canonicalFile
        val todo = info.files.filter { !it.clientUnsupported }
        say("Mengunduh ${todo.size} file (verifikasi hash)…")

        val pool = Executors.newFixedThreadPool(4)
        val done = AtomicInteger(0)
        val failures = ConcurrentLinkedQueue<String>()
        val tasks = todo.map { f ->
            Callable<Unit> {
                try {
                    val dest = safeFile(root, f.path)
                    if (!(dest.exists() && f.size > 0 && dest.length() == f.size)) {
                        val url = f.urls.firstOrNull { hostOk(it) } ?: error("Tidak ada host unduhan yang diizinkan")
                        Net.download(url, dest, f.sha512, f.sha1)
                    }
                } catch (e: Exception) {
                    failures.add("${f.path}: ${e.message}")
                }
                onProgress(done.incrementAndGet().toFloat() / todo.size.coerceAtLeast(1) * 0.9f)
            }
        }
        pool.invokeAll(tasks)
        pool.shutdown()
        failures.take(5).forEach { say("✗ $it") }
        if (failures.isNotEmpty()) error("${failures.size} file gagal diunduh; import dibatalkan sebelum menimpa overrides")

        say("Menyalin overrides…")
        var copied = 0
        val raw = ctx.contentResolver.openInputStream(uri) ?: error("Tidak bisa membuka file")
        raw.use { stream ->
            ZipInputStream(stream).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val n = e.name
                    val rel = when {
                        n.startsWith("overrides/") -> n.removePrefix("overrides/")
                        n.startsWith("client-overrides/") -> n.removePrefix("client-overrides/")
                        else -> null
                    }
                    if (rel != null && !e.isDirectory && rel.isNotEmpty()) {
                        val dest = safeFile(root, rel)
                        dest.parentFile?.mkdirs()
                        FileOutputStream(dest).use { out -> zin.copyTo(out) }
                        copied++
                    }
                    e = zin.nextEntry
                }
            }
        }
        onProgress(1f)
        say("✓ Selesai: ${todo.size} file + $copied override")
    }
}
