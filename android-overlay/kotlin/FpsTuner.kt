package id.imzyy.xl

import java.io.File

/** Writes documented Minecraft options.txt keys and keeps the first original backup. */
object FpsTuner {
    enum class Profile(val label: String, val values: List<Pair<String, String>>) {
        SAVER(
            "HEMAT FPS",
            listOf(
                "renderDistance" to "4", "simulationDistance" to "5", "particles" to "2",
                "entityShadows" to "false", "ao" to "0", "graphicsMode" to "0", "fancyGraphics" to "false",
                "biomeBlendRadius" to "0", "mipmapLevels" to "0", "entityDistanceScaling" to "0.5",
                "maxFps" to "60", "enableVsync" to "false", "renderClouds" to "\"false\"", "cloudStatus" to "\"off\"",
            ),
        ),
        BALANCED(
            "SEIMBANG",
            listOf(
                "renderDistance" to "8", "simulationDistance" to "8", "particles" to "1",
                "entityShadows" to "false", "ao" to "1", "graphicsMode" to "0", "fancyGraphics" to "false",
                "biomeBlendRadius" to "1", "mipmapLevels" to "2", "entityDistanceScaling" to "0.75",
                "maxFps" to "90", "enableVsync" to "false", "renderClouds" to "\"fast\"", "cloudStatus" to "\"fast\"",
            ),
        ),
        QUALITY(
            "KUALITAS",
            listOf(
                "renderDistance" to "12", "simulationDistance" to "10", "particles" to "0",
                "entityShadows" to "true", "ao" to "2", "graphicsMode" to "1", "fancyGraphics" to "true",
                "biomeBlendRadius" to "2", "mipmapLevels" to "4", "entityDistanceScaling" to "1.0",
                "maxFps" to "120", "enableVsync" to "false", "renderClouds" to "\"true\"", "cloudStatus" to "\"fancy\"",
            ),
        ),
    }

    fun read(instance: File): Map<String, String> {
        val f = File(instance, "options.txt")
        if (!f.isFile) return emptyMap()
        return f.readLines().mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()
    }

    fun apply(instance: File, profile: Profile): Int = write(instance, profile.values)

    fun applyMouse(
        instance: File,
        sensitivity: Float,
        scrollSensitivity: Float,
        invertY: Boolean,
        rawMouseInput: Boolean,
        swapButtons: Boolean,
    ): Int {
        val attack = if (swapButtons) "key.mouse.right" else "key.mouse.left"
        val use = if (swapButtons) "key.mouse.left" else "key.mouse.right"
        return write(instance, listOf(
            "mouseSensitivity" to sensitivity.coerceIn(0f, 1f).toString(),
            "mouseWheelSensitivity" to scrollSensitivity.coerceIn(1f, 10f).toString(),
            "invertYMouse" to invertY.toString(),
            "rawMouseInput" to rawMouseInput.toString(),
            "key_key.attack" to attack,
            "key_key.use" to use,
        ))
    }

    fun applyRenderDistance(instance: File, renderDistance: Int, simulationDistance: Int): Int {
        return write(instance, listOf(
            "renderDistance" to renderDistance.coerceIn(2, 32).toString(),
            "simulationDistance" to simulationDistance.coerceIn(2, 12).toString(),
        ))
    }

    private fun write(instance: File, values: List<Pair<String, String>>): Int {
        instance.mkdirs()
        val f = File(instance, "options.txt")
        val backup = File(instance, "options.txt.imzyy.bak")
        if (f.exists() && !backup.exists()) f.copyTo(backup)
        val lines = if (f.exists()) f.readLines().toMutableList() else mutableListOf()
        var changed = 0
        for ((key, value) in values) {
            val line = "$key:$value"
            val i = lines.indexOfFirst { it.startsWith("$key:") }
            if (i >= 0) {
                if (lines[i] != line) { lines[i] = line; changed++ }
            } else {
                lines.add(line); changed++
            }
        }
        f.writeText(lines.joinToString("\n") + "\n")
        return changed
    }

    fun restore(instance: File): Boolean {
        val backup = File(instance, "options.txt.imzy.bak")
        if (!backup.exists()) return false
        backup.copyTo(File(instance, "options.txt"), overwrite = true)
        return true
    }
}
