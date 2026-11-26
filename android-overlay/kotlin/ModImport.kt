package id.imzyy.xl

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** Installs mods from a single .jar or from a .zip that contains .jar files (any folder depth). */
object ModImport {
    fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i) ?: ""
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: ""
    }

    /** Last path component only; rejects anything that is not a plain *.jar file name. */
    private fun safeJarName(raw: String): String? {
        val n = raw.substringAfterLast('/').substringAfterLast('\\')
        if (n.isEmpty() || n.startsWith(".") || !n.endsWith(".jar", ignoreCase = true)) return null
        if (n.contains("..")) return null
        return n
    }

    /** Returns the number of mod files written into [modsDir]. */
    fun install(ctx: Context, uri: Uri, modsDir: File, say: (String) -> Unit): Int {
        modsDir.mkdirs()
        val name = displayName(ctx, uri)
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Tidak bisa membuka file")
        if (name.endsWith(".jar", ignoreCase = true)) {
            val safe = safeJarName(name) ?: error("Nama file tidak valid: $name")
            input.use { src -> FileOutputStream(File(modsDir, safe)).use { src.copyTo(it) } }
            say("✓ $safe")
            return 1
        }
        var count = 0
        input.use { raw ->
            ZipInputStream(raw).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val safe = safeJarName(e.name)
                        if (safe != null) {
                            FileOutputStream(File(modsDir, safe)).use { zip.copyTo(it) }
                            say("✓ $safe")
                            count++
                        }
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }
        if (count == 0) error("Tidak ada file .jar di dalam zip")
        return count
    }
}
