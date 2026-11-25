package id.imzyy.xl

import android.content.Context
import java.io.File

/** Finds game folders (those holding options.txt / mods / saves / versions) inside the app's own storage. */
object InstanceLocator {
    private val skip = setOf(
        "assets", "libraries", "runtimes", "saves", "mods", "resourcepacks", "shaderpacks",
        "logs", "cache", "crash-reports", "natives", "jre", "runtime", "lib", "cache",
    )

    fun find(ctx: Context): List<File> {
        val ext = ctx.getExternalFilesDir(null)
        val roots = listOfNotNull(ext, ctx.filesDir).distinct()
        val out = LinkedHashSet<File>()
        var budget = 4000

        fun walk(d: File, depth: Int) {
            if (budget-- <= 0 || depth > 5) return
            val kids = d.listFiles() ?: return
            val names = kids.map { it.name }.toSet()
            if ("options.txt" in names || "saves" in names || "mods" in names || ("versions" in names && "assets" in names)) {
                out.add(d)
            }
            for (k in kids) {
                if (k.isDirectory && k.name !in skip && !k.name.startsWith(".git")) walk(k, depth + 1)
            }
        }
        roots.forEach { walk(it, 0) }
        return out.sortedBy { it.path.length }
    }

    fun shortName(f: File): String {
        val parts = f.path.split('/').filter { it.isNotEmpty() }
        return parts.takeLast(3).joinToString("/")
    }
}
