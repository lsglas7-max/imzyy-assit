package com.imzyy.xllauncher.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Bridge to the Amethyst source tree merged into the same APK by
 * scripts/prepare-amethyst.py. Amethyst retains ownership of the real JVM,
 * renderer, game process, audio, and native touchscreen input pipeline.
 */
object AmethystBootstrapper {
    private const val AMETHYST_LAUNCHER = "net.kdt.pojavlaunch.LauncherActivity"

    data class Check(val title: String, val ready: Boolean, val detail: String)

    fun inspect(context: Context): List<Check> {
        val applicationBootstrap = classAvailable(context, "net.kdt.pojavlaunch.PojavApplication")
        val storageGate = classAvailable(context, "net.kdt.pojavlaunch.TestStorageActivity")
        val launcher = classAvailable(context, AMETHYST_LAUNCHER)
        val gameActivity = classAvailable(context, "net.kdt.pojavlaunch.MainActivity")
        val tools = classAvailable(context, "net.kdt.pojavlaunch.Tools")
        val jreBridge = classAvailable(context, "net.kdt.pojavlaunch.utils.JREUtils")
        val gameService = classAvailable(context, "net.kdt.pojavlaunch.services.GameService")
        val gameSurface = classAvailable(context, "net.kdt.pojavlaunch.MinecraftGLSurface")
        val touchInput = classAvailable(context, "net.kdt.pojavlaunch.utils.TouchControllerInputView")
        val nativeDir = runCatching { File(context.applicationInfo.nativeLibraryDir) }.getOrNull()
        val nativeCount = nativeDir?.listFiles()?.count { it.isFile && it.name.endsWith(".so") } ?: 0
        val arm64 = "arm64-v8a" in android.os.Build.SUPPORTED_ABIS

        return listOf(
            Check("Amethyst application bootstrap", applicationBootstrap, if (applicationBootstrap) "Upstream PojavApplication is present" else "Application bootstrap class missing"),
            Check("Amethyst storage/bootstrap gate", storageGate, if (storageGate) "Upstream TestStorageActivity is present" else "Storage/bootstrap activity missing"),
            Check("Amethyst version/profile launcher", launcher, if (launcher) "Embedded in this APK" else "Engine source not included in this build"),
            Check("Minecraft game surface", gameActivity && gameSurface, if (gameActivity && gameSurface) "Native game Activity and render surface are present" else "Game surface classes are missing"),
            Check("JVM and process bootstrap", tools && jreBridge && gameService, if (tools && jreBridge && gameService) "Amethyst Tools, JRE bridge and GameService are present" else "One or more bootstrap classes are missing"),
            Check("Native touchscreen input", touchInput, if (touchInput) "Amethyst in-game TouchControllerInputView is present" else "Native touch input class is missing"),
            Check("ARM64 device support", arm64, android.os.Build.SUPPORTED_ABIS.joinToString()),
            Check("Native libraries packaged", nativeCount > 0, "$nativeCount native .so libraries visible in the installed APK")
        )
    }

    fun launch(activity: Activity): Boolean {
        val launcherPresent = inspect(activity).firstOrNull { it.title == "Amethyst version/profile launcher" }?.ready == true
        require(launcherPresent) {
            "Amethyst belum terintegrasi di APK ini. Jalankan scripts/build-amethyst.sh atau workflow GitHub Actions."
        }
        activity.startActivity(Intent().setClassName(activity.packageName, AMETHYST_LAUNCHER))
        return true
    }

    /**
     * Resolve the active Minecraft profile directory, not Amethyst's outer storage root.
     * Mods, resource packs, shader packs, options and worlds live here. Custom profile
     * gameDir settings are honored; DIR_GAME_NEW is the safe default before profiles load.
     */
    fun gameDirectory(context: Context, create: Boolean = false): File? {
        return runCatching {
            val loader = context.classLoader
            val tools = loader.loadClass("net.kdt.pojavlaunch.Tools")
            val activeProfileDir = runCatching {
                val profiles = loader.loadClass("net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles")
                runCatching { profiles.getMethod("load").invoke(null) }
                val profileType = loader.loadClass("net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile")
                val profile = profiles.getMethod("getCurrentProfile").invoke(null)
                tools.getMethod("getGameDirPath", profileType).invoke(null, profile) as? File
            }.getOrNull()
            val defaultProfileDir = runCatching {
                val field = tools.getDeclaredField("DIR_GAME_NEW").apply { isAccessible = true }
                when (val value = field.get(null)) {
                    is File -> value
                    is String -> File(value)
                    else -> null
                }
            }.getOrNull()
            val candidates = listOfNotNull(activeProfileDir, defaultProfileDir).distinctBy { it.absolutePath }
            candidates.firstOrNull { it.isDirectory }
                ?: if (create) candidates.firstOrNull()?.takeIf { it.mkdirs() || it.isDirectory } else null
        }.getOrNull()?.takeIf { it.isDirectory }
    }

    /** Base .minecraft root: version metadata, libraries, assets and launcher_profiles.json. */
    fun defaultGameDirectory(context: Context, create: Boolean = false): File? {
        val directory = runCatching {
            val tools = context.classLoader.loadClass("net.kdt.pojavlaunch.Tools")
            val field = tools.getDeclaredField("DIR_GAME_NEW").apply { isAccessible = true }
            when (val value = field.get(null)) {
                is File -> value
                is String -> File(value)
                else -> null
            }
        }.getOrNull() ?: return null
        if (create) return directory.takeIf { it.mkdirs() || it.isDirectory }
        return directory.takeIf { it.isDirectory }
    }

    /** Amethyst stores custom touch layouts outside the active .minecraft profile. */
    fun controlMapDirectory(context: Context, create: Boolean = false): File? {
        val directory = runCatching {
            val tools = context.classLoader.loadClass("net.kdt.pojavlaunch.Tools")
            val field = tools.getDeclaredField("CTRLMAP_PATH").apply { isAccessible = true }
            when (val value = field.get(null)) {
                is File -> value
                is String -> File(value)
                else -> null
            }
        }.getOrNull() ?: return null
        if (create) return directory.takeIf { it.mkdirs() || it.isDirectory }
        return directory.takeIf { it.isDirectory }
    }

    private fun classAvailable(context: Context, name: String): Boolean =
        runCatching { context.classLoader.loadClass(name); true }.getOrDefault(false)
}
