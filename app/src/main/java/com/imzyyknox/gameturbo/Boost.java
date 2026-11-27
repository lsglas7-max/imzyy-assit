package com.imzyyknox.gameturbo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import java.io.File;

public class Boost {
    static ActivityManager.MemoryInfo mem(Context c) {
        ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(m);
        return m;
    }
    static int killBg(Context c) {
        // Android 14+: killBackgroundProcesses() hanya berlaku untuk proses sendiri. Jalur sah: Force Stop lewat IMZYY Cleaner.
        // Default: pelepasan RAM senyap, tanpa membuka layar apa pun. Force Stop lewat Cleaner (membuka Info Aplikasi)
        // hanya bila pengguna menyalakan saklar "Hentikan app latar" sendiri.
        int n = RamManager.silentKill(c);
        if (AndroidInfo.needsCleanerForStopping() && c.getSharedPreferences("turbo", Context.MODE_PRIVATE).getBoolean("force_stop_bg", false))
            n += RamManager.queueForceStop(c, RamManager.maxApps(c));
        return n;
    }
    static long size(File f) { if (!f.exists()) return 0; if (f.isFile()) return f.length(); long n = 0; File[] a = f.listFiles(); if (a != null) for (File x : a) n += size(x); return n; }
    static void wipe(File f, File keep) { if (!f.exists()) return; if (f.isDirectory()) { File[] a = f.listFiles(); if (a != null) for (File x : a) wipe(x, keep); } if (!f.equals(keep)) f.delete(); }
    /** mode 0 = RAM + cache, 1 = RAM only, 2 = cache only. Blocking: call off the UI thread. */
    static String run(Context c, int mode) {
        long before = mem(c).availMem; int n = mode < 2 ? killBg(c) : 0; long kb = 0;
        if (mode != 1) { kb = size(c.getCacheDir()) >> 10; wipe(c.getCacheDir(), c.getCacheDir()); }
        try { Thread.sleep(900); } catch (Exception ignored) { }
        long mb = Math.max(0, (mem(c).availMem - before) >> 20);
        String processNote = RamManager.note(c, n);
        return "Selesai • " + processNote + " • ~" + mb + " MB tersedia • " + kb + " KB cache aplikasi IMZYY";
    }
}
