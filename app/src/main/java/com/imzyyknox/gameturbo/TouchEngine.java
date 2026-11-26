package com.imzyyknox.gameturbo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Touch Boost v2 - berlapis, setiap lapis nyata dan dilaporkan apa adanya.
 *
 * LAPIS 1  Key "Touch sensitivity" One UI (dicari sendiri di tabel Settings System/Secure/Global).
 *          Butuh "Ubah setelan sistem" (key System) atau izin adb (key Secure/Global).
 * LAPIS 2  Refresh rate dikunci ke maksimum layar. Sampling sentuh layar Samsung mengikuti laju layar,
 *          jadi 120 Hz memberi jeda sentuh lebih pendek. Cuma butuh "Ubah setelan sistem". Ini lapis yang
 *          selalu bisa jalan tanpa adb.
 * LAPIS 3  Sentuh &amp; tahan dipercepat (long_press_timeout). Butuh izin adb sekali.
 * LAPIS 4  Animasi sistem dikurangi (0.5x) supaya respons UI terasa instan. Butuh izin adb sekali.
 *
 * Semua nilai asli disimpan dan dikembalikan saat Touch Boost dimatikan.
 * Boost dianggap berhasil bila minimal satu lapis benar-benar tertulis dan terbaca ulang.
 */
final class TouchEngine {
    private TouchEngine() { }

    static final int SYSTEM = 0, SECURE = 1, GLOBAL = 2;
    private static final String[] SCOPE_NAME = {"System", "Secure", "Global"};
    /** Sentuh &amp; tahan (ms): NORMAL, CEPAT, MAX. */
    private static final int[] HOLD_MS = {400, 250, 150};
    private static final String LONG_PRESS = "long_press_timeout";
    static final String[] HOLD_LABEL = {"NORMAL", "CEPAT", "MAX"};

    static final class Result {
        boolean ok;          // minimal satu lapis benar-benar tertulis
        boolean needWrite;   // tidak ada yang bisa ditulis karena "Ubah setelan sistem" belum aktif
        boolean needSecure;  // lapis tambahan tersedia bila izin adb diberikan
        int layers;          // jumlah lapis aktif
        String msg = "";
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("turbo", Context.MODE_PRIVATE); }

    private static Uri uri(int scope) {
        return scope == SYSTEM ? Settings.System.CONTENT_URI : scope == SECURE ? Settings.Secure.CONTENT_URI : Settings.Global.CONTENT_URI;
    }

    static boolean canWrite(Context c, int scope) {
        return scope == SYSTEM ? Settings.System.canWrite(c) : GpuEngine.canSecure(c);
    }

    /** Apakah ada yang bisa ditulis sekarang tanpa membuka layar izin apa pun. */
    static boolean canApply(Context c) { return Settings.System.canWrite(c) || GpuEngine.canSecure(c); }

    // ------------------------------------------------------------------ scan key

    private static boolean looksLikeTouchKey(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        boolean hit = (n.contains("touch") && (n.contains("sensitiv") || n.contains("boost") || n.contains("response")))
                || n.contains("glove") || n.contains("screen_protector") || n.contains("protector_mode");
        if (!hit) return false;
        String[] skip = {"sound", "vibrat", "explor", "hold", "press", "feedback", "keyboard", "hover", "stylus", "_pen", "pen_", "palm", "accessib", "wake", "lock"};
        for (String s : skip) if (n.contains(s)) return false;
        return true;
    }

    /** Semua key bernuansa "touch sensitivity" / "glove" di ponsel ini: {scope, name, value}. */
    static ArrayList<String[]> scan(Context c) {
        ArrayList<String[]> out = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();
        for (int s = SYSTEM; s <= GLOBAL; s++) {
            Cursor cur = null;
            try {
                cur = cr.query(uri(s), new String[]{"name", "value"}, null, null, null);
                if (cur == null) continue;
                while (cur.moveToNext()) {
                    String name = cur.getString(0);
                    if (name != null && looksLikeTouchKey(name)) out.add(new String[]{String.valueOf(s), name, String.valueOf(cur.getString(1))});
                }
            } catch (Throwable ignored) {
            } finally {
                if (cur != null) try { cur.close(); } catch (Throwable ignored) { }
            }
        }
        return out;
    }

    /** Key terbaik yang bernilai 0/1: {scope, name, value} atau null. */
    static String[] bestKey(Context c) {
        String[] best = null; int bestScore = -1;
        for (String[] k : scan(c)) {
            if (!"0".equals(k[2]) && !"1".equals(k[2])) continue;   // nilai selain 0/1 = tingkat, bukan saklar; jangan ditebak
            String n = k[1].toLowerCase(Locale.ROOT);
            int score = 0;
            if (n.contains("touch") && n.contains("sensitiv")) score += 4;
            if (n.contains("enable")) score += 2;
            if (Integer.parseInt(k[0]) == SYSTEM) score += 1;
            if (score > bestScore) { bestScore = score; best = k; }
        }
        return best;
    }

    static String describe(String[] k) { return SCOPE_NAME[Integer.parseInt(k[0])] + "/" + k[1] + "=" + k[2]; }

    // ------------------------------------------------------------------ read / write

    private static int getInt(Context c, int scope, String key, int def) {
        ContentResolver cr = c.getContentResolver();
        try {
            return scope == SYSTEM ? Settings.System.getInt(cr, key, def) : scope == SECURE ? Settings.Secure.getInt(cr, key, def) : Settings.Global.getInt(cr, key, def);
        } catch (Throwable e) { return def; }
    }

    private static boolean putInt(Context c, int scope, String key, int v) {
        ContentResolver cr = c.getContentResolver();
        try {
            if (scope == SYSTEM) return Settings.System.canWrite(c) && Settings.System.putInt(cr, key, v);
            if (!GpuEngine.canSecure(c)) return false;
            return scope == SECURE ? Settings.Secure.putInt(cr, key, v) : Settings.Global.putInt(cr, key, v);
        } catch (Throwable e) { return false; }
    }

    /** Status singkat untuk ditampilkan di floating window. */
    static String status(Context c) {
        StringBuilder sb = new StringBuilder();
        String[] k = bestKey(c);
        sb.append(k == null ? "Key sentuhan Samsung: tidak ada" : "Key " + describe(k));
        sb.append(" • izin sistem ").append(Settings.System.canWrite(c) ? "OK" : "belum");
        sb.append(" • izin adb ").append(GpuEngine.canSecure(c) ? "OK" : "belum");
        return sb.toString();
    }

    // ------------------------------------------------------------------ apply

    /**
     * Nyalakan / matikan boost. Aman dipanggil dari UI thread (hanya baca/tulis Settings).
     * @param holdLevel 0 NORMAL, 1 CEPAT, 2 MAX untuk sentuh-tahan
     */
    static Result apply(Context c, boolean on, int holdLevel) {
        try { return on ? enable(c, holdLevel) : disable(c); }
        catch (IllegalArgumentException e) { Result r = new Result(); r.msg = "Key/settings tidak didukung firmware"; return r; }
        catch (SecurityException e) { Result r = new Result(); r.needWrite = true; r.msg = "Izin sistem membatasi Touch Boost"; return r; }
        catch (Throwable e) { Result r = new Result(); r.msg = "Touch best-effort gagal: " + e.getClass().getSimpleName(); return r; }
    }

    private static Result enable(Context c, int holdLevel) {
        Result r = new Result();
        SharedPreferences p = prefs(c);
        ArrayList<String> parts = new ArrayList<>();
        boolean sysOk = Settings.System.canWrite(c), secOk = GpuEngine.canSecure(c);

        // LAPIS 1: key Samsung
        String[] k = bestKey(c);
        if (k != null) {
            int scope = Integer.parseInt(k[0]); String name = k[1];
            if (canWrite(c, scope)) {
                int cur = getInt(c, scope, name, -1);
                if (cur == 1) {
                    parts.add("Key Samsung sudah ON");
                    r.layers++;
                } else {
                    if (!p.contains("touch_key")) p.edit().putInt("touch_orig", cur < 0 ? 0 : cur).putInt("touch_scope", scope).putString("touch_key", name).apply();
                    boolean wrote = putInt(c, scope, name, 1);
                    if (wrote && getInt(c, scope, name, -1) == 1) { parts.add("Key Samsung ON"); r.layers++; }
                    else { p.edit().remove("touch_orig").remove("touch_scope").remove("touch_key").apply(); parts.add("Key Samsung ditolak sistem"); }
                }
            } else if (scope != SYSTEM) { r.needSecure = true; parts.add("Key Samsung butuh izin adb"); }
        } else {
            parts.add("Key Samsung tidak ada di firmware ini");
        }

        // LAPIS 2: refresh rate maksimum (sampling sentuh mengikuti laju layar)
        if (sysOk) {
            List<Integer> rates = FpsEngine.rates(c);
            if (!rates.isEmpty()) {
                int max = rates.get(rates.size() - 1);
                if (!p.contains("touch_prev_fps")) p.edit().putInt("touch_prev_fps", p.getInt("fps", -1)).apply();
                if (FpsEngine.apply(c, max)) {
                    p.edit().putInt("fps", 0).apply();
                    parts.add("layar " + max + " Hz"); r.layers++;
                } else parts.add("kunci refresh global: " + FpsEngine.friendly());
            }
        }

        // LAPIS 3 + 4: butuh izin adb
        if (secOk) {
            ContentResolver cr = c.getContentResolver();
            try {
                if (!p.contains("touch_orig_hold")) p.edit().putInt("touch_orig_hold", Settings.Secure.getInt(cr, LONG_PRESS, 400)).apply();
                int ms = HOLD_MS[Math.max(0, Math.min(holdLevel, HOLD_MS.length - 1))];
                if (Settings.Secure.putInt(cr, LONG_PRESS, ms)) { parts.add("tahan " + ms + " ms"); r.layers++; }
            } catch (Throwable e) { parts.add("sentuh-tahan ditolak"); }
            try {
                if (!p.contains("touch_orig_anim")) p.edit().putFloat("touch_orig_anim", Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)).apply();
                if (GpuEngine.setAnim(c, 0.5f)) { parts.add("animasi 0.5x"); r.layers++; }
            } catch (Throwable e) { parts.add("animasi ditolak"); }
        } else {
            r.needSecure = true;
            parts.add("tahan-cepat & animasi butuh izin adb sekali");
        }

        r.ok = r.layers > 0;
        r.needWrite = !r.ok && !sysOk;
        if (!r.ok && !sysOk && !secOk) parts.add(0, "Izinkan 'Ubah setelan sistem' agar Touch Boost jalan");
        r.msg = join(parts);
        return r;
    }

    private static Result disable(Context c) {
        Result r = new Result();
        SharedPreferences p = prefs(c);
        ArrayList<String> parts = new ArrayList<>();
        boolean failed = false;

        String name = p.getString("touch_key", null);
        if (name != null) {
            int scope = p.getInt("touch_scope", SYSTEM);
            if (putInt(c, scope, name, p.getInt("touch_orig", 0))) parts.add("Key Samsung dikembalikan");
            else { failed = true; parts.add("Key Samsung gagal dikembalikan"); }
            p.edit().remove("touch_orig").remove("touch_scope").remove("touch_key").apply();
        }
        if (p.contains("touch_prev_fps")) {
            int prev = p.getInt("touch_prev_fps", -1);
            p.edit().remove("touch_prev_fps").apply();
            if (Settings.System.canWrite(c)) {
                if (prev >= 0) FpsEngine.apply(c, FpsEngine.pick(c, prev)); else FpsEngine.reset(c);
                p.edit().putInt("fps", prev).apply();
                parts.add(prev >= 0 ? "refresh kembali ke pilihanmu" : "refresh otomatis");
            }
        }
        if (GpuEngine.canSecure(c)) {
            ContentResolver cr = c.getContentResolver();
            try {
                if (p.contains("touch_orig_hold")) { Settings.Secure.putInt(cr, LONG_PRESS, p.getInt("touch_orig_hold", 400)); p.edit().remove("touch_orig_hold").apply(); parts.add("tahan normal"); }
                if (p.contains("touch_orig_anim")) { GpuEngine.setAnim(c, p.getFloat("touch_orig_anim", 1f)); p.edit().remove("touch_orig_anim").apply(); parts.add("animasi normal"); }
            } catch (Throwable e) { failed = true; parts.add("sebagian gagal dipulihkan"); }
        }
        if (parts.isEmpty()) parts.add("Touch Boost sudah normal");
        r.ok = !failed;
        r.msg = join(parts);
        return r;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) { if (sb.length() > 0) sb.append(" • "); sb.append(s); }
        return sb.toString();
    }

    /** Re-apply tingkat sentuh-tahan saat pilihan diganti sementara boost aktif. */
    static void setHold(Context c, int level) {
        if (!GpuEngine.canSecure(c)) return;
        try {
            Settings.Secure.putInt(c.getContentResolver(), LONG_PRESS, HOLD_MS[Math.max(0, Math.min(level, HOLD_MS.length - 1))]);
        } catch (Throwable ignored) { }
    }
}
