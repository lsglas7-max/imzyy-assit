package com.imzyyknox.gameturbo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Real, non-root refresh-rate control. Games cannot be force-capped by a normal app; the display rate can. */
public class FpsEngine {
    static Display disp(Context c) { return ((DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY); }

    /** Status transparan: Brevent terpasang bukan bukti server/ADB elevated sudah aktif. */
    static String elevatedAccessHint(Context c) {
        boolean brevent = false;
        try { c.getPackageManager().getPackageInfo("me.piebridge.brevent", 0); brevent = true; } catch (Throwable ignored) { }
        boolean write = Settings.System.canWrite(c);
        if (GpuEngine.canSecure(c)) return "Mode elevated aktif • WRITE_SECURE_SETTINGS terverifikasi" + (write ? " + ubah setelan sistem" : "");
        if (write && brevent) return "Mode standar aktif • Brevent terpasang (server belum diverifikasi)";
        if (write) return "Mode standar aktif • izin ubah sistem tersedia";
        return brevent ? "Brevent terpasang • izin ADB/servis belum diverifikasi" : "Mode standar • tanpa Brevent/ADB (best effort)";
    }

    /** Supported modes at the current resolution (avoids resolution switching). */
    static List<Display.Mode> modes(Context c) {
        Display d = disp(c); Display.Mode cur = d.getMode(); List<Display.Mode> out = new ArrayList<>();
        for (Display.Mode m : d.getSupportedModes())
            if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()) out.add(m);
        Collections.sort(out, (a, b) -> Float.compare(a.getRefreshRate(), b.getRefreshRate()));
        return out;
    }
    static List<Integer> rates(Context c) {
        TreeSet<Integer> s = new TreeSet<>();
        for (Display.Mode m : modes(c)) s.add(Math.round(m.getRefreshRate()));
        return new ArrayList<>(s);
    }
    static float current(Context c) { return disp(c).getRefreshRate(); }

    /** target 0 = MAX. Picks the lowest supported rate >= target, else the highest. */
    static int pick(Context c, int target) {
        List<Integer> r = rates(c);
        if (r.isEmpty()) return Math.round(current(c));
        if (target == 0) return r.get(r.size() - 1);
        for (int x : r) if (x >= target) return x;
        return r.get(r.size() - 1);
    }
    static void prefer(WindowManager.LayoutParams p, Context c, int hz) {
        for (Display.Mode m : modes(c)) if (Math.round(m.getRefreshRate()) == hz) { p.preferredDisplayModeId = m.getModeId(); break; }
        p.preferredRefreshRate = hz;
    }
    /** Alasan terakhir penolakan (gabungan semua jalur yang dicoba), untuk ditampilkan di status. */
    static volatile String lastError = "";
    /** true bila firmware sudah menolak key refresh di Settings.System dengan IllegalArgumentException: jalur itu tidak dicoba berulang. */
    private static volatile boolean sysIae;

    /** Teks penolakan yang ramah dipakai di layar: nama exception tidak pernah ditampilkan ke pengguna. */
    static String friendly() {
        String e = lastError == null ? "" : lastError;
        if (e.isEmpty()) return "kunci global ditolak sistem";
        if (e.contains("IAE") || e.contains("IllegalArgument"))
            return "firmware menolak kunci lewat aplikasi, jalankan perintah Brevent Imzyy sekali untuk membukanya";
        if (e.contains("Security") || e.contains("izin belum aktif")) return "izin sistem belum diberikan";
        return e;
    }

    /** Perintah shell (Brevent/ADB) untuk aktivasi lebih dalam: izin sekali + kunci refresh lewat shell, yang tidak terkena validasi aplikasi. */
    static String breventCommands(Context c, int hz) {
        String pkg = c.getPackageName();
        int h = hz <= 0 ? Math.round(current(c)) : hz;
        return "# Brevent Imzyy • " + AndroidInfo.label() + "\n"
                + "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS\n"
                + "pm grant " + pkg + " android.permission.POST_NOTIFICATIONS\n"
                + "pm grant " + pkg + " android.permission.RECORD_AUDIO\n"
                + "appops set " + pkg + " WRITE_SETTINGS allow\n"
                + "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow\n"
                + "settings put system peak_refresh_rate " + h + ".0\n"
                + "settings put system min_refresh_rate " + h + ".0\n"
                + "settings put secure refresh_rate_mode " + (h >= 90 ? 2 : 1);
    }

    private static String why(Throwable e) {
        String m = e.getMessage();
        if (m != null && m.length() > 48) m = m.substring(0, 48);
        return e.getClass().getSimpleName().replace("Exception", "") + (m == null ? "" : ":" + m);
    }

    /** Tulis satu key refresh di Settings.System. Coba Float, lalu Int, lalu String; tiap varian dibaca ulang. null = sukses. */
    private static String writeSystemKey(ContentResolver cr, String key, int hz) {
        String last = "ditolak";
        for (int variant = 0; variant < 3; variant++) {
            try {
                boolean ok = variant == 0 ? Settings.System.putFloat(cr, key, hz)
                        : variant == 1 ? Settings.System.putInt(cr, key, hz)
                        : Settings.System.putString(cr, key, hz + ".0");
                if (ok && Math.round(Settings.System.getFloat(cr, key, -1f)) == hz) return null;
                last = "tidak terverifikasi";
            } catch (IllegalArgumentException e) {
                last = "IAE";                 // firmware menolak nilai/key: coba varian berikutnya (pesan mentah tidak ditampilkan)
            } catch (SecurityException e) {
                return "Security " + why(e);  // izin memang tidak ada: varian lain pasti sama
            } catch (Throwable e) {
                last = why(e);
            }
        }
        return last;
    }

    /** Jalur 1: key AOSP peak/min_refresh_rate di Settings.System (butuh "Ubah setelan sistem"). null = sukses. */
    private static String applySystem(Context c, ContentResolver cr, SharedPreferences sp, int hz) {
        float oldPeak = -1f, oldMin = 0f;
        try {
            oldPeak = Settings.System.getFloat(cr, "peak_refresh_rate", -1f);
            oldMin = Settings.System.getFloat(cr, "min_refresh_rate", 0f);
            if (!sp.contains("pk0")) sp.edit().putFloat("pk0", oldPeak).putFloat("mn0", oldMin).apply();
        } catch (Throwable ignored) { }
        String e1 = writeSystemKey(cr, "peak_refresh_rate", hz);
        String e2 = e1 == null ? writeSystemKey(cr, "min_refresh_rate", hz) : null;
        if (e1 == null && e2 == null) return null;
        // Gagal sebagian: kembalikan supaya tidak ada setelan setengah jalan.
        try { Settings.System.putFloat(cr, "peak_refresh_rate", oldPeak); Settings.System.putFloat(cr, "min_refresh_rate", oldMin); } catch (Throwable ignored) { }
        return "System[" + (e1 != null ? "peak " + e1 : "min " + e2) + "]";
    }

    /**
     * Jalur 2 (Samsung One UI): "Kehalusan gerakan" = Settings.Secure "refresh_rate_mode" (1 = standar 60 Hz, 2 = tinggi/adaptif).
     * Butuh WRITE_SECURE_SETTINGS (pm grant lewat Brevent/adb). Hanya ditulis bila key ini memang ada di firmware. null = sukses.
     */
    private static String applySamsungMode(Context c, ContentResolver cr, SharedPreferences sp, int hz) {
        try {
            String cur = Settings.Secure.getString(cr, "refresh_rate_mode");
            if (cur == null) return "Samsung[tidak ada key]";
            if (!sp.contains("rrm0")) sp.edit().putString("rrm0", cur).apply();
            int want = hz >= 90 ? 2 : 1;
            boolean ok = Settings.Secure.putString(cr, "refresh_rate_mode", String.valueOf(want));
            if (ok && String.valueOf(want).equals(Settings.Secure.getString(cr, "refresh_rate_mode"))) return null;
            return "Samsung[tidak terverifikasi]";
        } catch (Throwable e) { return "Samsung[" + why(e) + "]"; }
    }

    /**
     * Kunci refresh rate global. Dua jalur dicoba bergantian dan hasilnya diverifikasi:
     *  1) Settings.System peak/min_refresh_rate (butuh Ubah setelan sistem)
     *  2) Samsung refresh_rate_mode (butuh WRITE_SECURE_SETTINGS dari pm grant)
     * Sukses bila salah satu jalur terverifikasi. Tidak pernah melempar exception ke pemanggil.
     */
    static boolean apply(Context c, int hz) {
        lastError = "";
        boolean canSys = Settings.System.canWrite(c), canSec = GpuEngine.canSecure(c);
        if (!canSys && !canSec) { lastError = "izin belum aktif"; return false; }
        ContentResolver cr = c.getContentResolver();
        SharedPreferences sp = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
        StringBuilder err = new StringBuilder();
        boolean ok = false;
        // Setelah firmware menolak key refresh (IllegalArgument) dan jalur secure tersedia, jalur System tidak dicoba lagi.
        if (canSys && !(sysIae && canSec)) {
            String r = applySystem(c, cr, sp, hz);
            if (r == null) ok = true; else { err.append(r); if (r.contains("IAE")) sysIae = true; }
        }
        if (canSec) {
            String r = applySamsungMode(c, cr, sp, hz);
            if (r == null) ok = true;
            else if (!ok) { if (err.length() > 0) err.append(" • "); err.append(r); }
        }
        if (!ok) lastError = err.toString();
        return ok;
    }

    static void reset(Context c) {
        ContentResolver cr = c.getContentResolver();
        SharedPreferences sp = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
        List<Integer> r = rates(c);
        if (Settings.System.canWrite(c)) {
            try {
                Settings.System.putFloat(cr, "min_refresh_rate", 0f);
                Settings.System.putFloat(cr, "peak_refresh_rate", r.isEmpty() ? 60f : r.get(r.size() - 1));
            } catch (Throwable ignored) { }
        }
        if (GpuEngine.canSecure(c) && sp.contains("rrm0")) {
            try { Settings.Secure.putString(cr, "refresh_rate_mode", sp.getString("rrm0", "2")); } catch (Throwable ignored) { }
            sp.edit().remove("rrm0").apply();
        }
        sp.edit().putInt("fps", -1).apply();
    }
    /** Called ~1s after apply: reports what the display is really doing. */
    static String verify(Context c, int target, int hz) {
        int cur = Math.round(current(c));
        String note = (target > 0 && hz != target) ? (hz > target ? " (layar tidak punya " + target + " Hz, dipakai mode terendah)" : " (maks layar ini)") : "";
        if (cur == hz) return "Layar berjalan " + cur + " Hz" + note;
        return "Diminta " + hz + " Hz, sistem masih " + cur + " Hz. Set juga 'Kehalusan gerakan' di Display";
    }

    /** Measures the real vsync rate delivered to the screen (live Hz). */
    static class Meter implements Choreographer.FrameCallback {
        private int n; private long t0; private boolean on; private TextView out;
        void start(TextView o) { out = o; n = 0; t0 = 0; on = true; Choreographer.getInstance().removeFrameCallback(this); Choreographer.getInstance().postFrameCallback(this); }
        void stop() { on = false; Choreographer.getInstance().removeFrameCallback(this); }
        @Override public void doFrame(long ns) {
            if (!on) return;
            if (t0 == 0) t0 = ns;
            n++;
            long d = ns - t0;
            if (d >= 1000000000L) { if (out != null) out.setText(Math.round(n * 1e9f / d) + " Hz"); n = 0; t0 = ns; }
            Choreographer.getInstance().postFrameCallback(this);
        }
    }
}
