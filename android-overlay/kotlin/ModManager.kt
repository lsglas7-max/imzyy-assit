package id.imzyy.xl

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

 data class ManagedMod(val file: File, val enabled: Boolean)

/** Safe, non-extracting .jar import and reversible enable/disable operations. */
object ModManager {
    private const val MAX_MOD_BYTES = 128L * 1024L * 1024L
    private const val DISABLED_SUFFIX = ".disabled"

    fun list(modsDir: File): List<ManagedMod> {
        if (!modsDir.isDirectory) return emptyList()
        return (modsDir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".jar", true) || it.name.endsWith(".jar$DISABLED_SUFFIX", true)) }
            .sortedBy { it.name.lowercase() }
            .map { ManagedMod(it, it.name.endsWith(".jar", true)) }
    }

    fun importJar(context: Context, uri: Uri, modsDir: File): File {
        modsDir.mkdirs()
        val displayName = queryName(context, uri) ?: "imported-mod.jar"
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._() -]"), "_")
            .take(120)
            .let { if (it.endsWith(".jar", true)) it else "$it.jar" }
        val temp = File(modsDir, ".$safeName.imzyy-import")
        var total = 0L
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("File tidak bisa dibuka")
            input.use { src ->
                temp.outputStream().buffered().use { dst ->
                    val buffer = ByteArray(64 * 1024)
                    var first = ByteArray(0)
                    while (true) {
                        val n = src.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        total += n
                        require(total <= MAX_MOD_BYTES) { "Ukuran mod melebihi 128 MB" }
                        if (first.size < 4) {
                            val take = minOf(4 - first.size, n)
                            first += buffer.copyOfRange(0, take)
                        }
                        dst.write(buffer, 0, n)
                    }
                    require(first.size == 4 && first[0] == 0x50.toByte() && first[1] == 0x4b.toByte()) {
                        "File bukan arsip JAR/ZIP yang valid"
                    }
                }
            }
            val destination = uniqueFile(modsDir, safeName)
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = false)
                temp.delete()
            }
            return destination
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    fun toggle(entry: ManagedMod): File {
        val old = entry.file
        require(old.isFile) { "File mod tidak ditemukan" }
        val newName = if (entry.enabled) old.name + DISABLED_SUFFIX else old.name.removeSuffix(DISABLED_SUFFIX)
        val destination = File(old.parentFile, newName)
        require(!destination.exists()) { "Nama file tujuan sudah ada" }
        require(old.renameTo(destination)) { "Gagal mengubah status mod" }
        return destination
    }

    fun remove(entry: ManagedMod): Boolean = entry.file.isFile && entry.file.delete()

    private fun uniqueFile(dir: File, base: String): File {
        var candidate = File(dir, base)
        if (!candidate.exists()) return candidate
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ".jar"
        var n = 2
        while (candidate.exists()) candidate = File(dir, "$stem-$n$ext").also { n++ }
        return candidate
    }

    private fun queryName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
