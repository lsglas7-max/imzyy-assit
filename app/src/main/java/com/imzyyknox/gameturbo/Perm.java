package com.imzyyknox.gameturbo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Izin diminta SEKALI saja secara otomatis. Setelah itu fitur hanya memberi tahu izin belum aktif,
 * tidak membuka Pengaturan lagi. Tap manual pada baris izin, atau tombol REFRESH IZIN, tetap bisa membukanya lagi.
 */
final class Perm {
    private Perm() { }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("turbo", Context.MODE_PRIVATE); }

    /** true hanya pada panggilan pertama untuk key ini (sekaligus menandai sudah pernah diminta). */
    static boolean first(Context c, String key) {
        SharedPreferences p = sp(c);
        if (p.getBoolean("ask_" + key, false)) return false;
        p.edit().putBoolean("ask_" + key, true).apply();
        return true;
    }

    /** REFRESH IZIN: lupakan catatan "sudah pernah diminta" supaya izin yang masih kurang bisa diminta sekali lagi. */
    static void resetAll(Context c) {
        SharedPreferences p = sp(c);
        SharedPreferences.Editor e = p.edit();
        for (String k : p.getAll().keySet()) {
            if (k.startsWith("ask_") || k.startsWith("wiz_")) e.remove(k);
        }
        e.putBoolean("write_settings_prompted", false);
        e.apply();
    }
}
