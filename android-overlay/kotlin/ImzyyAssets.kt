package id.imzyy.xl

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Repairs missing/corrupt vanilla asset objects before Minecraft starts rendering. */
object ImzyyAssets {
    private const val TAG = "IMZYY_ASSETS"
    private const val ASSET_BASE = "https://resources.download.minecraft.net"

    /**
     * Ensures every object referenced by the selected asset index is present and has the
     * expected size. Downloaded objects are SHA-1 checked before becoming visible to Minecraft.
     *
     * Returns the number of repaired files. This must be called from a worker thread.
     */
    fun repair(
        context: Context,
        assetIndexId: String,
        versionId: String,
        instanceDir: String? = null,
        customGameDir: String? = null,
    ): Int {
        require(assetIndexId.matches(INDEX_ID)) {
            "Asset index Minecraft tidak valid untuk versi $versionId"
        }

        // Mirror MinecraftActivity.startMinecraft(): assets may live in an isolated instance,
        // the app external-files root, or the app files root. Repair the exact directory that
        // the upstream launcher will select, rather than always repairing the shared root.
        val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
        val instanceBase = instanceDir?.takeIf { it.isNotBlank() }?.let(::File)
            ?: customGameDir?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(externalRoot, "instances/vanilla_$versionId")
        val searchRoots = listOf(instanceBase, externalRoot, context.filesDir)
            .distinctBy { it.absolutePath }
        val assetsRoot = searchRoots
            .map { File(it, "assets") }
            .firstOrNull { candidate ->
                val indexes = File(candidate, "indexes")
                indexes.isDirectory && indexes.listFiles()?.isNotEmpty() == true
            }
            ?: File(externalRoot, "assets")
        val indexesDir = File(assetsRoot, "indexes")
        val indexFile = File(indexesDir, "$assetIndexId.json")
        val metadataCandidates = searchRoots.asSequence()
            .mapNotNull { root -> findVersionMetadata(root, versionId) }
            .toList()
        val versionMetadata = metadataCandidates.firstOrNull { it.optJSONObject("assetIndex") != null }
            ?: metadataCandidates.firstOrNull()
        Log.i(TAG, "Asset root terpilih untuk $versionId: ${assetsRoot.absolutePath}")
        val assetMetadata = versionMetadata?.optJSONObject("assetIndex")
        val expectedIndexSha1 = assetMetadata?.optString("sha1")?.takeIf { it.matches(HEX_40) }
        val indexUrl = assetMetadata?.optString("url")?.takeIf { it.startsWith("https://") }

        var indexJson: JSONObject? = null
        if (indexFile.isFile) {
            indexJson = runCatching {
                val raw = indexFile.readText(Charsets.UTF_8)
                if (expectedIndexSha1 != null && !sha1(raw.toByteArray(Charsets.UTF_8)).equals(expectedIndexSha1, ignoreCase = true)) {
                    throw IllegalStateException("Hash asset index tidak cocok")
                }
                JSONObject(raw).takeIf { it.optJSONObject("objects") != null }
                    ?: throw IllegalStateException("Format asset index tidak valid")
            }.getOrElse {
                Log.w(TAG, "Asset index $assetIndexId rusak; unduh ulang", it)
                indexFile.delete()
                null
            }
        }

        val resolvedIndex = indexJson ?: run {
            val url = indexUrl ?: throw IllegalStateException(
                "Asset index $assetIndexId hilang/rusak dan URL metadata versi $versionId tidak ditemukan. " +
                    "Buka launcher lalu unduh ulang file game versi ini."
            )
            val indexHash = expectedIndexSha1 ?: throw IllegalStateException(
                "SHA-1 asset index $assetIndexId tidak tersedia di metadata versi $versionId"
            )
            indexesDir.mkdirs()
            downloadVerified(url, indexFile, indexHash, null)
            val raw = indexFile.readText(Charsets.UTF_8)
            JSONObject(raw).takeIf { it.optJSONObject("objects") != null }
                ?: throw IllegalStateException("Asset index $assetIndexId tidak berisi daftar objects")
        }

        val objects = resolvedIndex.getJSONObject("objects")
        val objectsDir = File(assetsRoot, "objects")
        val missing = ArrayList<AssetObject>()
        val seenHashes = HashSet<String>() // Different logical names can share the same SHA-1 object.
        val keys = objects.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val item = objects.optJSONObject(name) ?: continue
            val hash = item.optString("hash").lowercase()
            if (!hash.matches(HEX_40)) continue
            val size = item.optLong("size", -1L)
            if (size < 0L) continue
            val target = File(File(objectsDir, hash.substring(0, 2)), hash)
            if ((!target.isFile || target.length() != size) && seenHashes.add(hash)) {
                missing.add(AssetObject(hash, size, target))
            }
        }

        if (missing.isEmpty()) {
            Log.i(TAG, "Asset $assetIndexId ($versionId) lengkap; tidak ada file yang perlu diperbaiki")
            return 0
        }

        Log.i(TAG, "Memulihkan ${missing.size} asset yang hilang/rusak untuk $versionId")
        val workers = Executors.newFixedThreadPool(minOf(4, missing.size))
        try {
            val tasks = missing.map { asset ->
                workers.submit(Callable {
                    val path = "${asset.hash.substring(0, 2)}/${asset.hash}"
                    downloadVerified("$ASSET_BASE/$path", asset.target, asset.hash, asset.size)
                    Log.d(TAG, "Asset pulih: ${asset.hash}")
                    true
                })
            }
            tasks.forEach { it.get() }
        } finally {
            workers.shutdownNow()
            workers.awaitTermination(2, TimeUnit.SECONDS)
        }

        // Final pass catches any truncated file or filesystem failure before starting the JVM.
        val incomplete = missing.firstOrNull { !it.target.isFile || it.target.length() != it.size }
        check(incomplete == null) { "Asset ${incomplete?.hash} belum berhasil dipulihkan" }
        Log.i(TAG, "Selesai: ${missing.size} asset berhasil dipulihkan untuk $versionId")
        return missing.size
    }

    private data class AssetObject(val hash: String, val size: Long, val target: File)

    private fun findVersionMetadata(gameRoot: File, versionId: String): JSONObject? {
        val versions = File(gameRoot, "versions")
        val safeId = versionId.replace('/', '_').replace('\\', '_')
        val candidates = listOf(
            File(versions, "$safeId/$safeId.json"),
            File(versions, "$safeId.json"),
        )
        candidates.firstOrNull { it.isFile }?.let { file ->
            return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
        }

        // Custom loader versions can use a version id that is not a plain Minecraft version.
        // Search only the versions tree and stop at the first exact file-name match.
        if (versions.isDirectory) {
            val match = versions.walkTopDown().maxDepth(4)
                .firstOrNull { it.isFile && it.name == "$safeId.json" }
            if (match != null) return runCatching { JSONObject(match.readText(Charsets.UTF_8)) }.getOrNull()
        }
        return null
    }

    private fun downloadVerified(url: String, target: File, expectedSha1: String?, expectedSize: Long?) {
        require(url.startsWith("https://")) { "Asset URL bukan HTTPS" }
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".imzyy-part")
        var lastError: Exception? = null
        repeat(3) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "IMZYY-XL-Launcher/asset-repair")
                connection.setRequestProperty("Accept", "application/octet-stream, application/json")
                val status = connection.responseCode
                if (status !in 200..299) throw IllegalStateException("HTTP $status saat mengunduh asset")

                val digest = MessageDigest.getInstance("SHA-1")
                var bytes = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            bytes += count.toLong()
                        }
                        output.fd.sync()
                    }
                }

                if (expectedSize != null && bytes != expectedSize) {
                    throw IllegalStateException("Ukuran asset tidak cocok ($bytes/$expectedSize)")
                }
                val gotHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (expectedSha1 != null && !gotHash.equals(expectedSha1, ignoreCase = true)) {
                    throw IllegalStateException("SHA-1 asset tidak cocok")
                }

                if (target.exists() && !target.delete()) throw IllegalStateException("Tidak bisa mengganti asset lama")
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                return
            } catch (e: Exception) {
                lastError = e
                temp.delete()
                if (attempt < 2) Thread.sleep(500L * (attempt + 1))
            } finally {
                connection?.disconnect()
            }
        }
        throw IllegalStateException("Gagal mengunduh asset ${target.name}: ${lastError?.message}", lastError)
    }

    private fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val HEX_40 = Regex("[0-9a-f]{40}")
    private val INDEX_ID = Regex("[A-Za-z0-9._-]{1,128}")
}
