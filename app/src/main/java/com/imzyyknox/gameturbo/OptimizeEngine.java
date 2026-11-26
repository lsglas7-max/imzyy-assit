package com.imzyyknox.gameturbo;

import android.app.ActivityManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.StatFs;
import android.os.storage.StorageManager;
import java.util.List;
import java.util.Locale;

/**
 * OPTIMALKAN PONSEL yang berjalan penuh di dalam IMZYY (floating window / app), tanpa membuka
 * Perawatan perangkat Samsung atau aplikasi lain. Hanya memakai langkah yang memang bisa dilakukan aplikasi biasa:
 * 1. Meminta sistem melepas cache semua aplikasi (StorageManager.allocateBytes).
 * 2. Meminta penghentian proses latar (hanya efektif di Android 13 ke bawah).
 * 3. Membersihkan cache IMZYY sendiri.
 * Hasil ditampilkan apa adanya (RAM, cache, ruang bebas), tidak ada hasil palsu.
 */
final class OptimizeEngine {
    private OptimizeEngine() { }

    interface Step { void line(String msg); }

    static final class Report {
        boolean ok;
        String summary = "";
    }

    /** Total cache semua aplikasi; -1 bila Usage Access belum aktif. */
    static long totalCache(Context c) {
        try {
            final StorageStatsManager sm = (StorageStatsManager) c.getSystemService(Context.STORAGE_STATS_SERVICE);
            PackageManager pm = c.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            final java.util.concurrent.atomic.AtomicLong t = new java.util.concurrent.atomic.AtomicLong();
            final java.util.concurrent.atomic.AtomicInteger okc = new java.util.concurrent.atomic.AtomicInteger();
            final java.util.concurrent.atomic.AtomicBoolean denied = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(6);
            for (final ApplicationInfo ai : apps) {
                ex.execute(() -> {
                    if (denied.get()) return;
                    try {
                        t.addAndGet(sm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, android.os.Process.myUserHandle()).getCacheBytes());
                        okc.incrementAndGet();
                    } catch (SecurityException se) { denied.set(true); }
                    catch (Exception ignored) { }
                });
            }
            ex.shutdown();
            try { ex.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            return denied.get() || okc.get() == 0 ? -1 : t.get();
        } catch (Throwable e) { return -1; }
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return String.format(Locale.US, "%.0f KB", b / 1024d);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / (1024d * 1024));
        return String.format(Locale.US, "%.2f GB", b / (1024d * 1024 * 1024));
    }

    /** Blocking. Panggil dari thread background. */
    static Report run(Context c, Step step) {
        Report rep = new Report();
        ActivityManager.MemoryInfo m0 = Boost.mem(c);
        long ramBefore = m0.availMem;
        long freeBefore = new StatFs(c.getFilesDir().getPath()).getAvailableBytes();

        step.line("1/4 Memindai cache aplikasi");
        long cacheBefore = totalCache(c);

        step.line("2/4 Sistem melepas cache semua aplikasi");
        CacheEngine.Result cr = CacheEngine.reclaim(c);
        if (!cr.ok) step.line("Cache sistem: " + cr.msg);

        step.line(AndroidInfo.needsCleanerForStopping() ? "3/4 Menghentikan app latar lewat Cleaner" : "3/4 Menghentikan proses latar");
        int asked = Boost.killBg(c);

        step.line("4/4 Membersihkan cache IMZYY");
        long own = Boost.size(c.getCacheDir()) + (c.getExternalCacheDir() == null ? 0 : Boost.size(c.getExternalCacheDir()));
        Boost.wipe(c.getCacheDir(), c.getCacheDir());
        if (c.getExternalCacheDir() != null) Boost.wipe(c.getExternalCacheDir(), c.getExternalCacheDir());
        own += CacheEngine.junk(c);

        try { Thread.sleep(700); } catch (Exception ignored) { }

        long cacheAfter = totalCache(c);
        long ramAfter = Boost.mem(c).availMem;
        long freeAfter = new StatFs(c.getFilesDir().getPath()).getAvailableBytes();
        long ramMb = Math.max(0, (ramAfter - ramBefore) >> 20);
        long freed = Math.max(0, freeAfter - freeBefore);

        StringBuilder sb = new StringBuilder("Dioptimalkan");
        sb.append(" • RAM +").append(ramMb).append(" MB");
        if (cacheBefore >= 0 && cacheAfter >= 0) sb.append(" • cache ").append(size(cacheBefore)).append(" ke ").append(size(cacheAfter));
        else sb.append(" • ruang +").append(size(freed));
        sb.append(" • ").append(RamManager.note(c, asked));
        String mi = RamManager.meminfo();
        if (!mi.isEmpty()) sb.append(" • ").append(mi);

        rep.ok = cr.ok || ramMb > 0 || own > 0;
        rep.summary = rep.ok ? sb.toString() : "Optimalkan tidak mengubah apa pun (sistem menolak)";

        if (rep.ok) {
            SharedPreferences sp = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
            sp.edit().putLong("last_opt", System.currentTimeMillis()).putString("last_opt_info", "RAM +" + ramMb + " MB • bebas +" + size(freed)).apply();
        }
        return rep;
    }
}
