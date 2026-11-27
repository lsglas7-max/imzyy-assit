package com.imzyyknox.gameturbo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Menjaga IMZYY Cleaner (Aksesibilitas) tetap hidup.
 *
 * Android mematikan layanan aksesibilitas bila: aplikasi crash, aplikasi di-force-stop, update/instal ulang,
 * "Pause app activity if unused" / optimasi baterai Samsung, atau pengguna mematikannya. Aplikasi biasa TIDAK
 * boleh menyalakan aksesibilitasnya sendiri, kecuali izin WRITE_SECURE_SETTINGS sudah diberikan sekali lewat adb
 * (izin itu permanen sampai aplikasi di-uninstall). Dengan izin itu, guard ini menyalakan ulang layanan seketika.
 * Tanpa izin itu, guard hanya bisa memberi tahu dan membuka halaman layanan yang tepat (1 ketukan).
 */
final class AccessibilityGuard {
    private AccessibilityGuard() { }

    private static ContentObserver observer;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static long lastNotice;

    static ComponentName component(Context c) { return new ComponentName(c, CleanerService.class); }

    /** Layanan tercantum di daftar aktif sistem (bukan sekadar instance berjalan). */
    static boolean listed(Context c) {
        try {
            String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) return false;
            String me = component(c).flattenToString();
            String meShort = component(c).flattenToShortString();
            for (String x : cur.split(":")) if (x.equalsIgnoreCase(me) || x.equalsIgnoreCase(meShort)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    /** Nyalakan ulang bila perlu. Return true bila sudah aktif atau berhasil dinyalakan. */
    private static long lastAdd, lastToggle;

    /**
     * Nyalakan ulang bila perlu, dengan jeda. Dulu entri dihapus lalu dipasang lagi tiap beberapa detik selagi sistem masih
     * mengikat layanan, dan itulah yang membuat Cleaner mati-hidup. Sekarang: tercantum tapi belum berjalan = ditunggu;
     * baru bila tetap tidak berjalan lebih dari 90 detik, dicolek sekali (hapus lalu pasang).
     */
    static synchronized boolean ensure(Context c) {
        if (CleanerService.enabled()) return true;
        if (!GpuEngine.canSecure(c)) return false;
        long now = android.os.SystemClock.elapsedRealtime();
        try {
            if (!listed(c)) {
                if (now - lastAdd < 15_000L) return false;
                lastAdd = now;
                return CleanerService.enableViaSecure(c);
            }
            if (lastToggle != 0 && now - lastToggle < 90_000L) return false;
            if (lastAdd == 0) { lastAdd = now; return false; }       // baru terlihat: beri waktu sistem mengikat dulu
            if (now - lastAdd < 90_000L) return false;
            lastToggle = now; lastAdd = now;
            removeEntry(c);
            return CleanerService.enableViaSecure(c);
        } catch (Throwable e) { return false; }
    }

    private static void removeEntry(Context c) {
        try {
            String me = component(c).flattenToString();
            String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) return;
            StringBuilder sb = new StringBuilder();
            for (String x : cur.split(":")) {
                if (x.isEmpty() || x.equalsIgnoreCase(me) || x.equalsIgnoreCase(component(c).flattenToShortString())) continue;
                if (sb.length() > 0) sb.append(':');
                sb.append(x);
            }
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, sb.toString());
        } catch (Throwable ignored) { }
    }

    /** Pantau perubahan daftar aksesibilitas; bila IMZYY Cleaner hilang, pasang lagi (debounce 600 ms). */
    static void watch(final Context app) {
        if (observer != null) return;
        try {
            observer = new ContentObserver(H) {
                @Override public void onChange(boolean selfChange) {
                    H.removeCallbacks(fix(app));
                    H.postDelayed(fix(app), 600);
                }
            };
            app.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer);
        } catch (Throwable e) { observer = null; }
    }

    static void unwatch(Context app) {
        try { if (observer != null) app.getContentResolver().unregisterContentObserver(observer); } catch (Throwable ignored) { }
        observer = null;
    }

    private static Runnable fixer;
    private static Runnable fix(final Context app) {
        if (fixer == null) fixer = new Runnable() {
            @Override public void run() {
                if (!wanted(app)) return;
                if (!CleanerService.enabled() && !listed(app)) ensure(app);
            }
        };
        return fixer;
    }

    /** Cleaner terlepas (crash, dimatikan sistem): jadwalkan pasang ulang cepat bila pengguna memang menginginkannya. */
    static void lost(Context c) {
        try { if (wanted(c) && GpuEngine.canSecure(c)) GuardJob.revive(c, 2500L); } catch (Throwable ignored) { }
    }

    /** Pengguna pernah mengaktifkan Cleaner = ingin tetap aktif. Dicatat supaya guard tidak menyalakan yang tidak diminta. */
    static void markWanted(Context c, boolean on) { c.getSharedPreferences("turbo", Context.MODE_PRIVATE).edit().putBoolean("cleaner_wanted", on).apply(); }
    static boolean wanted(Context c) { return c.getSharedPreferences("turbo", Context.MODE_PRIVATE).getBoolean("cleaner_wanted", false); }

    /** Intent tercepat ke layanan ini (Android 14+: langsung ke halaman detail layanan). */
    static Intent settingsIntent(Context c) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        // Android 14 sampai 17 (dan seterusnya): halaman detail layanan langsung. Dipakai hanya bila firmware benar-benar punya halamannya,
        // kalau tidak jatuh ke daftar aksesibilitas biasa, jadi tidak ada versi Android yang tertutup atau gagal membuka.
        if (AndroidInfo.hasAccessibilityDetailPage()) {
            try {
                Intent d = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS"); // konstanta Settings.ACTION_ACCESSIBILITY_DETAIL_SETTINGS tidak ada di SDK publik
                d.putExtra(Intent.EXTRA_COMPONENT_NAME, component(c).flattenToString());
                if (d.resolveActivity(c.getPackageManager()) != null) i = d;
            } catch (Throwable ignored) { }
        }
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * Terbaca "setelan terbatas" (Android 13+, APK instal manual): aksesibilitas ditandai Tidak tersedia sampai pengguna
     * memilih "Izinkan setelan terbatas". Pembacaan ini best-effort; bila firmware tidak mengenalnya hasilnya false.
     */
    static boolean restricted(Context c) {
        if (!AndroidInfo.hasRestrictedSettings()) return false;
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int m = ops.unsafeCheckOpNoThrow("android:access_restricted_settings", android.os.Process.myUid(), c.getPackageName());
            return m == android.app.AppOpsManager.MODE_ERRORED || m == android.app.AppOpsManager.MODE_IGNORED;
        } catch (Throwable e) { return false; }
    }

    /** Layar panduan satu halaman untuk menyalakan Cleaner (langkah izinkan setelan terbatas, adb, baterai). */
    static Intent setupIntent(Context c) {
        return new Intent(c, AccessSetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Halaman Info Aplikasi: di sana ada menu titik tiga "Izinkan setelan terbatas" (Android 13+ untuk APK non-Play Store). */
    static Intent appInfoIntent(Context c) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Dipanggil watchdog tiap beberapa detik: jaga + beri tahu maksimal sekali per 10 menit bila tidak bisa jaga sendiri. */
    static String tick(Context c) {
        if (!wanted(c) || CleanerService.enabled()) return null;
        if (GpuEngine.canSecure(c)) return ensure(c) ? "IMZYY Cleaner dinyalakan ulang otomatis" : null;
        long now = System.currentTimeMillis();
        if (now - lastNotice < 600_000L) return null;
        lastNotice = now;
        return "IMZYY Cleaner mati • tap IZIN lalu aktifkan (adb sekali = permanen)";
    }
}
