package com.imzyy.xllauncher.runtime

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Installs official Minecraft version metadata, client jar, Maven artifacts and assets
 * into a user-selected SAF directory. It deliberately does not claim to launch the game:
 * a compatible Android JVM + LWJGL/renderer bridge is still required for that.
 */
class MinecraftInstaller(
    private val context: Context,
    private val treeUri: Uri,
    private val onProgress: (Progress) -> Unit,
    private val isCancelled: () -> Boolean = { false }
) {
    data class Progress(val phase: String, val completed: Int, val total: Int)
    data class Result(val version: String, val installedFiles: Int, val skippedFiles: Int)

    private var completed = 0
    private var total = 1
    private var installed = 0
    private var skipped = 0

    fun install(versionId: String): Result {
        require(versionId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Version ID tidak valid." }
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Folder game tidak bisa dibuka. Pilih ulang folder melalui file picker Android.")
        require(root.isDirectory && root.canWrite()) { "Folder game tidak memiliki izin tulis." }

        emit("Mengambil manifest resmi Mojang")
        val manifest = fetchJson(MANIFEST_URL)
        val versions = manifest.optJSONArray("versions") ?: error("Manifest Mojang tidak memiliki daftar versi.")
        var versionUrl: String? = null
        for (i in 0 until versions.length()) {
            val item = versions.optJSONObject(i) ?: continue
            if (item.optString("id") == versionId) {
                versionUrl = item.optString("url").takeIf { it.isNotBlank() }
                break
            }
        }
        val metadataUrl = versionUrl ?: error("Versi $versionId tidak ditemukan pada manifest Mojang.")
        val metadata = fetchJson(metadataUrl)

        val client = metadata.optJSONObject("downloads")?.optJSONObject("client")
            ?: error("Metadata versi ini tidak menyediakan client jar yang bisa diunduh otomatis.")
        val libraries = metadata.optJSONArray("libraries") ?: org.json.JSONArray()
        val assetIndex = metadata.optJSONObject("assetIndex")
        val assetIndexUrl = assetIndex?.optString("url")?.takeIf { it.isNotBlank() }

        val artifactList = mutableListOf<Artifact>()
        for (i in 0 until libraries.length()) {
            val lib = libraries.optJSONObject(i) ?: continue
            val artifact = lib.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            val path = artifact.optString("path").ifBlank { mavenPath(lib.optString("name")) }
            val url = artifact.optString("url")
            if (path.isNotBlank() && url.isNotBlank()) {
                artifactList.add(Artifact(path, url, artifact.optString("sha1"), artifact.optLong("size", -1)))
            }
        }

        var assets = org.json.JSONObject()
        var assetObjects = JSONObject()
        if (assetIndexUrl != null) {
            assets = fetchJsonVerified(
                assetIndexUrl,
                assetIndex?.optString("sha1").orEmpty(),
                assetIndex?.optLong("size", -1L) ?: -1L
            )
            assetObjects = assets.optJSONObject("objects") ?: JSONObject()
        }

        total = 2 + (if (assetIndexUrl != null) 1 else 0) + artifactList.size + assetObjects.length()
        completed = 0
        emit("Menyiapkan instalasi $versionId")

        val versionDir = ensureDir(root, "versions/$versionId")
        val clientFile = versionDir.findFile("$versionId.jar")
        saveArtifact(clientFile, versionDir,
            "$versionId.jar", Artifact("$versionId.jar", client.optString("url"), client.optString("sha1"), client.optLong("size", -1)), "Mengunduh client Minecraft")

        // Keep the exact version JSON beside the client jar for launch-time metadata parsing.
        val versionJson = versionDir.findFile("$versionId.json") ?: versionDir.createFile("application/json", "$versionId.json")
            ?: error("Tidak dapat membuat metadata versi $versionId.")
        versionJson.uri.let { uri ->
            context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(metadata.toString(2)) }
        }
        mark("Menyimpan metadata versi")

        for (artifact in artifactList) {
            checkCancelled()
            val parent = ensureDir(root, "libraries/${artifact.path.substringBeforeLast('/', "")}")
            saveArtifact(parent.findFile(artifact.path.substringAfterLast('/')), parent,
                artifact.path.substringAfterLast('/'), artifact, "Library ${artifact.path.substringAfterLast('/')}")
        }

        if (assetIndexUrl != null && assetIndex != null) {
            val indexId = assetIndex.optString("id").ifBlank { versionId }
            val indexDir = ensureDir(root, "assets/indexes")
            val indexDoc = indexDir.findFile("$indexId.json") ?: indexDir.createFile("application/json", "$indexId.json")
                ?: error("Tidak dapat membuat assets index.")
            context.contentResolver.openOutputStream(indexDoc.uri, "wt")!!.bufferedWriter().use { it.write(assets.toString()) }
            mark("Menyimpan assets index")

            val objectDir = ensureDir(root, "assets/objects")
            val keys = assetObjects.keys()
            while (keys.hasNext()) {
                checkCancelled()
                val logicalName = keys.next()
                val obj = assetObjects.optJSONObject(logicalName) ?: continue
                val hash = obj.optString("hash")
                if (!hash.matches(Regex("[a-fA-F0-9]{40}"))) continue
                val relative = "${hash.substring(0, 2)}/$hash"
                val parent = ensureDir(objectDir, relative.substringBeforeLast('/'))
                saveArtifact(parent.findFile(hash), parent, hash,
                    Artifact(hash, "https://resources.download.minecraft.net/$relative", hash, obj.optLong("size", -1)),
                    "Asset ${completed + 1}/$total")
            }
        }

        // The selected version is only marked installed after metadata, client, libraries and assets complete.
        emit("Memverifikasi hasil instalasi")
        val savedMeta = versionDir.findFile("$versionId.json") ?: error("Metadata versi hilang setelah instalasi.")
        require(savedMeta.length() > 0) { "Metadata versi kosong." }
        return Result(versionId, installed, skipped)
    }

    private data class Artifact(val path: String, val url: String, val sha1: String, val size: Long)

    private fun saveArtifact(existing: DocumentFile?, parent: DocumentFile, name: String, artifact: Artifact, phase: String) {
        checkCancelled()
        require(name.isNotBlank() && name != "." && name != ".." && !name.contains('/')) { "Nama file tidak aman." }
        val expectedHash = artifact.sha1.lowercase()
        if (existing != null && existing.isFile && existing.length() > 0 && expectedHash.matches(Regex("[a-f0-9]{40}"))) {
            val currentHash = sha1(existing.uri)
            if (currentHash.equals(expectedHash, true) && (artifact.size < 0 || existing.length() == artifact.size)) {
                skipped++
                mark("Sudah ada: $name")
                return
            }
        }
        // Remove stale files/directories before creating the replacement. DocumentFile providers
        // may otherwise create duplicate names instead of replacing the previous entry.
        existing?.delete()
        require(artifact.url.startsWith("https://")) { "URL download tidak aman." }
        var lastError: Exception? = null
        repeat(2) {
            try {
                val connection = (URL(artifact.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "IMZYY-XL-Launcher/4.0")
                }
                try {
                    connection.connect()
                    require(connection.url.protocol.equals("https", true)) { "Redirect download tidak aman." }
                    require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode} saat mengunduh $name" }
                    val doc = parent.createFile("application/octet-stream", name) ?: error("Tidak dapat membuat $name")
                    val digest = MessageDigest.getInstance("SHA-1")
                    var size = 0L
                    try {
                        BufferedInputStream(connection.inputStream).use { input ->
                            context.contentResolver.openOutputStream(doc.uri, "w")?.use { raw ->
                                BufferedOutputStream(raw).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        checkCancelled()
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        output.write(buffer, 0, n)
                                        digest.update(buffer, 0, n)
                                        size += n
                                    }
                                    output.flush()
                                }
                            } ?: error("Tidak dapat membuka output $name")
                        }
                        val gotHash = digest.digest().joinToString("") { b -> "%02x".format(b) }
                        if (artifact.size >= 0 && size != artifact.size) error("Ukuran $name tidak cocok (${size}/${artifact.size}).")
                        if (expectedHash.matches(Regex("[a-f0-9]{40}")) && !gotHash.equals(expectedHash, true)) {
                            error("SHA-1 $name tidak cocok; file ditolak.")
                        }
                    } catch (e: Exception) {
                        doc.delete()
                        throw e
                    }
                    installed++
                    mark(phase)
                    return
                } finally { connection.disconnect() }
            } catch (e: Exception) { lastError = e }
        }
        throw lastError ?: IllegalStateException("Gagal mengunduh $name")
    }

    private fun fetchJson(url: String): JSONObject = fetchJsonVerified(url, "", -1L)

    private fun fetchJsonVerified(url: String, expectedSha1: String, expectedSize: Long): JSONObject {
        require(url.startsWith("https://")) { "Metadata harus memakai HTTPS." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 45_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "IMZYY-XL-Launcher/4.0")
        }
        return try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode} saat membaca metadata." }
            require(connection.url.protocol.equals("https", true)) { "Redirect metadata tidak aman." }
            val bytes = connection.inputStream.use { it.readBytes() }
            if (expectedSize >= 0) require(bytes.size.toLong() == expectedSize) { "Ukuran asset index tidak cocok." }
            if (expectedSha1.matches(Regex("(?i)[a-f0-9]{40}"))) {
                val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
                    .joinToString("") { b -> "%02x".format(b) }
                require(digest.equals(expectedSha1, true)) { "SHA-1 asset index tidak cocok; metadata ditolak." }
            }
            JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    private fun ensureDir(root: DocumentFile, relative: String): DocumentFile {
        var current = root
        if (relative.isBlank()) return current
        for (part in relative.split('/')) {
            require(part.isNotBlank() && part != "." && part != "..") { "Path instalasi tidak valid." }
            val existing = current.findFile(part)
            current = when {
                existing == null -> current.createDirectory(part)
                    ?: error("Tidak dapat membuat folder $part. Pilih folder dengan izin tulis penuh.")
                existing.isDirectory -> existing
                else -> error("Path $part sudah ada sebagai file, bukan folder.")
            }
        }
        return current
    }

    private fun mavenPath(coordinates: String): String {
        val p = coordinates.split(':')
        if (p.size < 3) return ""
        val group = p[0].replace('.', '/')
        val artifact = p[1]
        val version = p[2]
        val classifier = p.getOrNull(3)?.let { "-$it" } ?: ""
        return "$group/$artifact/$version/$artifact-$version$classifier.jar"
    }

    private fun sha1(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-1")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun mark(phase: String) { completed++; emit(phase) }
    private fun emit(phase: String) { onProgress(Progress(phase, completed.coerceAtMost(total), total)) }
    private fun checkCancelled() { if (isCancelled()) throw InterruptedException("Instalasi dibatalkan.") }

    companion object { const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json" }
}
