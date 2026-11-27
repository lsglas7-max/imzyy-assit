package com.imzz.galaxyassistant.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.imzz.galaxyassistant.apps.AppInfo

class AppCatalogDatabase(context: Context) : SQLiteOpenHelper(context, "galaxy_apps.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE apps (package_name TEXT PRIMARY KEY, label TEXT NOT NULL, launcher_activity TEXT NOT NULL, is_game INTEGER NOT NULL, last_seen INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is intentionally simple; migrations belong here when the schema changes.
    }

    fun replaceAll(apps: List<AppInfo>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("apps", null, null)
            apps.forEach { app ->
                val values = ContentValues().apply {
                    put("package_name", app.packageName)
                    put("label", app.label)
                    put("launcher_activity", app.launcherActivity)
                    put("is_game", if (app.isGame) 1 else 0)
                    put("last_seen", System.currentTimeMillis())
                }
                writableDatabase.insertWithOnConflict("apps", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun all(): List<AppInfo> {
        val result = mutableListOf<AppInfo>()
        readableDatabase.rawQuery(
            "SELECT package_name, label, launcher_activity, is_game FROM apps ORDER BY label COLLATE NOCASE",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += AppInfo(
                    packageName = cursor.getString(0),
                    label = cursor.getString(1),
                    launcherActivity = cursor.getString(2),
                    isGame = cursor.getInt(3) == 1
                )
            }
        }
        return result
    }
}
