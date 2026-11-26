package com.imzyyknox.gameturbo;

import android.os.Build;

/**
 * Deteksi versi Android yang sedang dijalankan (16, 17, dan versi di bawahnya sampai API 26).
 * Fitur memeriksa kemampuan nyata (API level), bukan memaksa "Android 14" saja, jadi tidak ada
 * jalur yang hanya berlaku di satu versi dan tidak ada pesan "Android 14+" yang salah di Android 17.
 */
final class AndroidInfo {
    private AndroidInfo() { }

    static int sdk() { return Build.VERSION.SDK_INT; }

    static boolean atLeast(int api) { return Build.VERSION.SDK_INT >= api; }

    /** Nomor utama Android dari sistem (contoh "17"); bila tidak terbaca, dipetakan dari API level. */
    static String release() {
        String r = Build.VERSION.RELEASE;
        if (r != null && !r.trim().isEmpty() && Character.isDigit(r.trim().charAt(0))) return r.trim();
        int s = sdk();
        if (s >= 37) return "17";
        switch (s) {
            case 36: return "16";
            case 35: return "15";
            case 34: return "14";
            case 33: return "13";
            case 32: case 31: return "12";
            case 30: return "11";
            case 29: return "10";
            case 28: return "9";
            case 27: return "8.1";
            default: return "8";
        }
    }

    /** Contoh: "Android 17 (API 37)". */
    static String label() { return "Android " + release() + " (API " + sdk() + ")"; }

    /** Halaman detail layanan aksesibilitas langsung (Android 14 ke atas, termasuk 15, 16, dan 17). */
    static boolean hasAccessibilityDetailPage() { return atLeast(34); }

    /** App lain tidak bisa dihentikan lewat killBackgroundProcesses (Android 14 ke atas); Force Stop lewat Cleaner jadi jalurnya. */
    static boolean needsCleanerForStopping() { return atLeast(34); }

    /** Setelan terbatas untuk APK instal manual (Android 13 ke atas). */
    static boolean hasRestrictedSettings() { return atLeast(33); }

    /** Ringkasan kemampuan untuk ditampilkan di status. */
    static String summary() {
        StringBuilder sb = new StringBuilder(label());
        sb.append(" • aksesibilitas ").append(atLeast(26) ? "didukung" : "tidak didukung");
        if (hasRestrictedSettings()) sb.append(" • perlu izin setelan terbatas");
        return sb.toString();
    }
}
