package com.imzyyknox.gameturbo;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Manajemen proses RAM untuk Android 14 sampai 17.
 *
 * Sejak Android 14, ActivityManager.killBackgroundProcesses() hanya berlaku untuk proses aplikasi sendiri,
 * jadi pemanggilan lama tidak lagi membebaskan RAM aplikasi lain. Jalur yang masih sah untuk aplikasi biasa adalah
 * menekan "Force stop" di Info Aplikasi lewat IMZYY Cleaner (Aksesibilitas). RamManager memilih HANYA aplikasi
 * yang benar-benar dipakai belakangan ini (UsageStats), bukan yang sedang di depan, bukan launcher/keyboard/aplikasi
 * sistem, bukan yang sudah berhenti, lalu mengantre Force Stop dan mengembalikan pengguna ke aplikasi/game semula.
 */
final class RamManager {
    private RamManager() { }

    static final int DEFAULT_MAX = 8;
    private static final long WINDOW_MS = 90L * 60 * 1000;
    private static final long MIN_IDLE_MS = 12L * 60 * 1000; // jangan hentikan aplikasi yang baru dipakai

    static final class Cand { String pkg, label; long last; }

    static boolean hasUsage(Context c) {
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            return ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName()) == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable e) { return false; }
    }

    /** Aplikasi yang aman dihentikan, terbaru dipakai lebih dulu (kemungkinan paling banyak memegang RAM). */
    static ArrayList<Cand> candidates(Context c, int max, String[] fgOut) {
        ArrayList<Cand> out = new ArrayList<>();
        if (!hasUsage(c)) return out;
        try {
            UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents ev = um.queryEvents(now - WINDOW_MS, now);
            UsageEvents.Event e = new UsageEvents.Event();
            HashMap<String, Long> last = new HashMap<>();
            String fg = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int t = e.getEventType();
                String p = e.getPackageName();
                if (p == null) continue;
                if (t == UsageEvents.Event.MOVE_TO_FOREGROUND) { fg = p; Long o = last.get(p); if (o == null || o < e.getTimeStamp()) last.put(p, e.getTimeStamp()); }
                else if (t == UsageEvents.Event.MOVE_TO_BACKGROUND) { if (p.equals(fg)) fg = null; Long o = last.get(p); if (o == null || o < e.getTimeStamp()) last.put(p, e.getTimeStamp()); }
            }
            if (fgOut != null && fgOut.length > 0) fgOut[0] = fg;

            HashSet<String> skip = new HashSet<>();
            skip.add(c.getPackageName());
            if (fg != null) skip.add(fg);
            SharedPreferences sp = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
            String game = sp.getString("saved_game_package", "");
            if (!game.isEmpty()) skip.add(game);
            for (String k : sp.getString("ram_keep", "").split(",")) if (!k.trim().isEmpty()) skip.add(k.trim());
            PackageManager pm = c.getPackageManager();
            ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            if (home != null && home.activityInfo != null) skip.add(home.activityInfo.packageName);
            String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.contains("/")) skip.add(ime.substring(0, ime.indexOf('/')));

            for (Map.Entry<String, Long> en : last.entrySet()) {
                String p = en.getKey();
                // Smart RAM: hanya kandidat yang idle lama; jangan sentuh aplikasi baru saja dipakai.
                if (skip.contains(p) || now - en.getValue() < MIN_IDLE_MS) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    if ((ai.flags & ApplicationInfo.FLAG_STOPPED) != 0) continue;   // sudah berhenti, tidak ada yang perlu dihentikan
                    if (pm.getLaunchIntentForPackage(p) == null) continue;
                    Cand cd = new Cand(); cd.pkg = p; cd.label = String.valueOf(pm.getApplicationLabel(ai)); cd.last = en.getValue();
                    out.add(cd);
                } catch (Throwable ignored) { }
            }
            // Prioritaskan app paling lama tidak aktif, bukan yang baru dipakai.
            java.util.Collections.sort(out, (a, b) -> Long.compare(a.last, b.last));
            while (out.size() > max) out.remove(out.size() - 1);
        } catch (Throwable ignored) { }
        return out;
    }

    /**
     * Lepas RAM app latar secara senyap (killBackgroundProcesses): tanpa membuka layar apa pun, tanpa pindah dari game.
     * Sasaran sama dengan Force Stop terpilih: bukan app depan, launcher, keyboard, game tersimpan, aplikasi sistem.
     * Return jumlah app yang diminta dilepas.
     */
    static int silentKill(Context c) {
        android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        int n = 0;
        try {
            if (maxApps(c) <= 0) return 0; // RAM cukup: tidak perlu mengganggu aplikasi latar
            if (hasUsage(c)) {
                for (Cand cd : candidates(c, 64, null)) { am.killBackgroundProcesses(cd.pkg); n++; }
            } else {
                PackageManager pm = c.getPackageManager();
                String game = c.getSharedPreferences("turbo", Context.MODE_PRIVATE).getString("saved_game_package", "");
                ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
                String homePkg = home != null && home.activityInfo != null ? home.activityInfo.packageName : "";
                HashSet<String> seen = new HashSet<>();
                for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                    String p = r.activityInfo.packageName;
                    if (p.equals(c.getPackageName()) || p.equals(game) || p.equals(homePkg) || !seen.add(p)) continue;
                    if ((r.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    am.killBackgroundProcesses(p); n++;
                }
            }
        } catch (Throwable ignored) { }
        return n;
    }

    /** Antre Force Stop lewat Cleaner. Return jumlah aplikasi yang diantrekan. Memulihkan aplikasi di depan setelah selesai. */
    static int queueForceStop(Context c, int max) {
        if (!CleanerService.enabled()) return 0;
        String[] fg = new String[1];
        if (max <= 0) return 0;
        ArrayList<Cand> list = candidates(c, max, fg);
        if (list.isEmpty()) return 0;
        CleanerService.returnTo(fg[0]);
        for (Cand cd : list) CleanerService.enqueue(cd.pkg, CleanerService.FORCE_STOP, cd.label);
        return list.size();
    }

    static int maxApps(Context c) {
        int i = c.getSharedPreferences("turbo", Context.MODE_PRIVATE).getInt("ram_max", 1);
        int userCap = i == 0 ? 4 : i == 2 ? 12 : DEFAULT_MAX;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        // Hindari cleanup agresif ketika RAM masih cukup. Tambah batas bertahap hanya saat tekanan tinggi.
        long availPct = mi.totalMem > 0 ? mi.availMem * 100 / mi.totalMem : 100;
        if (availPct >= 28) return 0;
        if (availPct >= 18) return Math.min(2, userCap);
        if (availPct >= 10) return Math.min(5, userCap);
        return userCap;
    }

    /** Kalimat jujur untuk laporan hasil. */
    static String note(Context c, int n) {
        if (!c.getSharedPreferences("turbo", Context.MODE_PRIVATE).getBoolean("force_stop_bg", false))
            return n + " app latar dilepas dari RAM (otomatis, tanpa pindah layar)" + (AndroidInfo.needsCleanerForStopping() ? " • hasil bisa terbatas di " + AndroidInfo.label() : "");
        if (!AndroidInfo.needsCleanerForStopping()) return n + " permintaan proses latar";
        if (!c.getSharedPreferences("turbo", Context.MODE_PRIVATE).getBoolean("force_stop_bg", false)) return "Force Stop app latar OFF (nyalakan di menu bila perlu)";
        if (!CleanerService.enabled()) return AndroidInfo.label() + ": aktifkan IMZYY Cleaner agar app lain bisa dihentikan";
        if (!hasUsage(c)) return AndroidInfo.label() + ": aktifkan Usage Access agar app aktif terdeteksi";
        return n == 0 ? "Tidak ada app latar yang perlu dihentikan" : n + " app dikirim ke Force Stop lewat Cleaner";
    }

    /** MemAvailable, zRAM/swap dari /proc/meminfo (bisa dibaca aplikasi biasa). */
    static String meminfo() {
        long total = -1, avail = -1, swapT = -1, swapF = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String s;
            while ((s = r.readLine()) != null) {
                if (s.startsWith("MemTotal:")) total = kb(s);
                else if (s.startsWith("MemAvailable:")) avail = kb(s);
                else if (s.startsWith("SwapTotal:")) swapT = kb(s);
                else if (s.startsWith("SwapFree:")) swapF = kb(s);
            }
        } catch (Throwable ignored) { }
        if (total <= 0 || avail < 0) return "";
        int used = (int) (100 - avail * 100 / total);
        String z = swapT > 0 ? String.format(java.util.Locale.US, " • zRAM %.1f/%.1f GB", (swapT - swapF) / 1048576f, swapT / 1048576f) : "";
        return "RAM " + used + "%" + z;
    }

    private static long kb(String s) {
        try { return Long.parseLong(s.replaceAll("[^0-9]", "")); } catch (Throwable e) { return -1; }
    }
}
