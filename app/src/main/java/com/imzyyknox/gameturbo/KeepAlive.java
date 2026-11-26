package com.imzyyknox.gameturbo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Detak penjaga lewat AlarmManager. Alarm disimpan sistem (bukan proses aplikasi), jadi tetap hidup walau proses hub dibunuh
 * dan membangunkan {@link KeepAliveReceiver} yang menyalakan hub lagi. Alarm tepat waktu (USE_EXACT_ALARM) membuat start
 * foreground-service dari alarm diizinkan Android 12+. Rantai berhenti sendiri bila pengguna menutup hub (tombol X) atau
 * mematikan ANTI HILANG. Catatan jujur: Paksa berhenti dari pengguna menghapus semua alarm; itu tidak bisa dilawan aplikasi biasa.
 */
final class KeepAlive {
    private KeepAlive() { }

    private static final int REQ = 7001;
    static final String ACTION = "com.imzyyknox.gameturbo.KEEPALIVE";

    /** Hub diminta hidup dan ANTI HILANG nyala. */
    static boolean wanted(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
        return sp.getBoolean("hub_was_on", false) && sp.getBoolean("hub_antivanish", true);
    }

    /** Layar menyala = cek tiap 25 detik; layar mati = tiap 3 menit supaya hemat baterai. */
    static long nextDelay(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive() ? 25_000L : 180_000L;
        } catch (Throwable e) { return 60_000L; }
    }

    private static PendingIntent pi(Context c) {
        return PendingIntent.getBroadcast(c, REQ, new Intent(c, KeepAliveReceiver.class).setAction(ACTION),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void arm(Context c, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long at = SystemClock.elapsedRealtime() + Math.max(1000L, delayMs);
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi(c));
                else
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi(c));
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi(c));
            }
        } catch (Throwable ignored) { }
    }

    static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi(c));
        } catch (Throwable ignored) { }
    }

    static boolean canExact(Context c) {
        try {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        } catch (Throwable e) { return false; }
    }
}
