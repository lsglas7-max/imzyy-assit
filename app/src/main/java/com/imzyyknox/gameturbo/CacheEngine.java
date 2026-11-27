package com.imzyyknox.gameturbo;

import android.content.Context;
import android.os.StatFs;
import android.os.storage.StorageManager;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Bersihkan cache SEMUA aplikasi sekaligus tanpa membuka aplikasi satu per satu.
 * Memakai API resmi StorageManager.allocateBytes: saat aplikasi meminta ruang lebih besar dari yang kosong,
 * Android sendiri menghapus cache aplikasi lain untuk memenuhinya. Berkas sementara langsung dihapus lagi.
 * Hasil bisa lebih kecil dari pembersihan manual: sistem yang memutuskan cache mana yang dilepas.
 */
final class CacheEngine {
    static final class Result { long freed; boolean ok; String msg; }

    /** Ukuran cache satu aplikasi, atau -1 bila Usage Access belum aktif / tidak terbaca. */
    static long cacheOf(Context c, String pkg) {
        try {
            android.app.usage.StorageStatsManager sm = (android.app.usage.StorageStatsManager) c.getSystemService(Context.STORAGE_STATS_SERVICE);
            return sm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, android.os.Process.myUserHandle()).getCacheBytes();
        } catch (Throwable e) { return -1; }
    }

    static Result reclaim(Context c) {
        Result r = new Result();
        File f = new File(c.getFilesDir(), "imzyy_reclaim.tmp");
        long total = 0;
        try {
            StorageManager sm = (StorageManager) c.getSystemService(Context.STORAGE_SERVICE);
            // Dua putaran: putaran pertama melepas cache terbesar, putaran kedua (ruang sisa lebih longgar) menyapu sisanya.
            for (int pass = 0; pass < 2; pass++) {
                long freeBefore = new StatFs(c.getFilesDir().getPath()).getAvailableBytes();
                long alloc = sm.getAllocatableBytes(StorageManager.UUID_DEFAULT);
                long want = alloc - (pass == 0 ? 16L : 64L) * 1024 * 1024;
                if (want <= freeBefore) {
                    if (pass == 0) { r.ok = true; r.msg = "Tidak ada cache yang bisa dilepas sistem"; }
                    break;
                }
                try (FileOutputStream o = new FileOutputStream(f)) {
                    sm.allocateBytes(o.getFD(), want);
                }
                f.delete();
                long gained = Math.max(0, new StatFs(c.getFilesDir().getPath()).getAvailableBytes() - freeBefore);
                total += gained;
                r.ok = true; r.msg = "Cache dilepas sistem";
                if (gained < 32L * 1024 * 1024) break;   // sisa tidak berarti, tidak perlu putaran lagi
            }
            r.freed = total;
        } catch (Throwable e) {
            r.freed = total;
            r.ok = total > 0;
            r.msg = total > 0 ? "Cache dilepas sistem (sebagian)" : "Sistem menolak: " + e.getClass().getSimpleName();
        } finally {
            if (f.exists()) f.delete();
        }
        return r;
    }

    /** Sampah aman yang bisa dihapus aplikasi biasa: cache eksternal sendiri dan folder .thumbnails (dibuat ulang otomatis). */
    static long junk(Context c) {
        long n = 0;
        try {
            File[] ex = c.getExternalCacheDirs();
            if (ex != null) for (File d : ex) if (d != null) { n += Boost.size(d); Boost.wipe(d, d); }
        } catch (Throwable ignored) { }
        try {
            if (android.os.Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager()) {
                File root = android.os.Environment.getExternalStorageDirectory();
                for (String rel : new String[]{".thumbnails", "DCIM/.thumbnails", "Pictures/.thumbnails"}) {
                    File d = new File(root, rel);
                    if (d.isDirectory()) { n += Boost.size(d); Boost.wipe(d, d); }
                }
            }
        } catch (Throwable ignored) { }
        return n;
    }
}
