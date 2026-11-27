package com.imzz.galaxyassistant.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.imzz.galaxyassistant.storage.AppCatalogDatabase

object AppScanner {
    fun scan(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val unique = linkedMapOf<String, AppInfo>()
        resolved.forEach { item ->
            val activityInfo = item.activityInfo ?: return@forEach
            val packageName = activityInfo.packageName
            if (packageName == context.packageName) return@forEach
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
                val label = pm.getApplicationLabel(appInfo).toString()
                unique[packageName] = AppInfo(
                    packageName = packageName,
                    label = label,
                    launcherActivity = activityInfo.name.orEmpty(),
                    isGame = GameDetectionEngine.isProbablyGame(appInfo, packageName, label)
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // App may have been uninstalled while the package list was being read.
            }
        }

        val sorted = unique.values.sortedWith(compareBy<AppInfo> { !it.isGame }.thenBy { it.label.lowercase() })
        val database = AppCatalogDatabase(context.applicationContext)
        try {
            database.replaceAll(sorted)
        } finally {
            database.close()
        }
        return sorted
    }
}
