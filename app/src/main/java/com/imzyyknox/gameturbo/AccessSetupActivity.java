package com.imzyyknox.gameturbo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Panduan satu layar untuk menyalakan IMZYY Cleaner (Aksesibilitas).
 *
 * Kalau di Setelan tertulis "Tidak tersedia" / "Demi keamanan, setelan ini tidak tersedia", itu bukan kerusakan aplikasi:
 * Android 13+ memblokir aksesibilitas untuk APK hasil instal manual sampai pengguna memberi "Izinkan setelan terbatas".
 * Layar ini menuntun langkahnya, memantau status secara langsung, dan menutup diri begitu Cleaner aktif.
 * Jalur permanen tanpa langkah manual: izin adb WRITE_SECURE_SETTINGS sekali.
 */
public class AccessSetupActivity extends Activity {
    private static final int BG = 0xFF000000, CARD = 0xFF1C1C1F, CARD2 = 0xFF28282C, BLUE = 0xFF3E91FF, TXT = 0xFFFFFFFF,
            SUB = 0xFFA6A6AD, OK = 0xFF3DDC84, WARN = 0xFFFFB84D;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView state, restrictedLine;
    private boolean closed;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed) return;
            paint();
            if (CleanerService.enabled()) {
                closed = true;
                Toast.makeText(AccessSetupActivity.this, "IMZYY Cleaner aktif", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(AccessSetupActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                return;
            }
            ui.postDelayed(this, 1000);
        }
    };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView txt(String s, int sz, int c, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(c);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private LinearLayout.LayoutParams mt(int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); return p; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG); sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(40), dp(18), dp(28));

        root.addView(txt("Aktifkan IMZYY Cleaner", 26, TXT, true));
        root.addView(txt("Dipakai untuk Force Stop dan Clear Cache per aplikasi", 13, SUB, false), mt(4));

        LinearLayout stateCard = card();
        state = txt("", 16, TXT, true);
        stateCard.addView(state);
        restrictedLine = txt("", 12, SUB, false);
        stateCard.addView(restrictedLine, mt(6));
        root.addView(stateCard, mt(18));

        root.addView(step("1", "Coba nyalakan dulu", "Buka Aksesibilitas, pilih IMZYY Cleaner, lalu geser ke Aktif. Kalau berhasil, selesai. Kalau muncul \"Setelan dibatasi\" atau \"Tidak tersedia\", lanjut ke langkah 2.",
                "Buka Aksesibilitas", this::openAccessibility), mt(14));
        root.addView(step("2", "Izinkan setelan terbatas", "Android 13+ memblokir aksesibilitas untuk APK yang dipasang manual. Buka Info Aplikasi, tap titik tiga di kanan atas, pilih \"Izinkan setelan terbatas\", lalu konfirmasi. Menu titik tiga ini baru muncul setelah langkah 1 dicoba sekali.",
                "Buka Info Aplikasi", () -> go(AccessibilityGuard.appInfoIntent(this))), mt(10));
        root.addView(step("3", "Nyalakan lagi", "Kembali ke Aksesibilitas, pilih IMZYY Cleaner, geser ke Aktif. Halaman ini tertutup sendiri begitu Cleaner aktif.",
                "Buka Aksesibilitas", this::openAccessibility), mt(10));
        root.addView(step("+", "Otomatis & permanen (opsional)", "Sekali saja lewat debugging nirkabel: jalankan perintah adb di bawah. Sesudah itu Cleaner menyala sendiri, tidak perlu langkah 1 sampai 3, dan hidup lagi otomatis setelah reboot atau update.",
                "Salin perintah adb", this::copyAdb), mt(10));
        root.addView(step("+", "Jangan sampai dimatikan sistem", "Samsung bisa mematikan layanan di latar. Pilih \"Tanpa batasan\" untuk baterai, dan matikan \"Pause app activity if unused\" di Info Aplikasi.",
                "Atur baterai", this::battery), mt(10));

        TextView back = txt("Kembali", 15, TXT, true);
        back.setGravity(Gravity.CENTER); back.setPadding(0, dp(15), 0, dp(15));
        back.setBackground(bg(CARD2, 28));
        back.setOnClickListener(v -> finish());
        root.addView(back, mt(18));

        sv.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);
        AccessibilityGuard.markWanted(this, true);
        // Jalur otomatis bila izin adb sudah ada.
        if (!CleanerService.enabled() && GpuEngine.canSecure(this)) CleanerService.enableViaSecure(this);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(bg(CARD, 26));
        return c;
    }

    private LinearLayout step(String num, String title, String body, String btnLabel, final Runnable action) {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = txt(num, 13, TXT, true);
        n.setGravity(Gravity.CENTER);
        n.setBackground(bg(BLUE, 20));
        head.addView(n, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView t = txt(title, 15, TXT, true);
        t.setPadding(dp(12), 0, 0, 0);
        head.addView(t);
        c.addView(head);
        c.addView(txt(body, 12, SUB, false), mt(8));
        TextView btn = txt(btnLabel, 14, TXT, true);
        btn.setGravity(Gravity.CENTER); btn.setPadding(0, dp(12), 0, dp(12));
        btn.setBackground(bg(CARD2, 18));
        btn.setOnClickListener(v -> action.run());
        c.addView(btn, mt(12));
        return c;
    }

    private void paint() {
        boolean on = CleanerService.enabled();
        state.setText(on ? "Status: AKTIF" : "Status: belum aktif");
        state.setTextColor(on ? OK : WARN);
        if (on) restrictedLine.setText("Cleaner siap dipakai.");
        else if (AccessibilityGuard.restricted(this)) restrictedLine.setText("Terdeteksi: setelan terbatas masih memblokir. Lakukan langkah 2.");
        else if (Build.VERSION.SDK_INT >= 33) restrictedLine.setText("Android 13+: bila tombol abu-abu atau tertulis tidak tersedia, lakukan langkah 2.");
        else restrictedLine.setText("Buka Aksesibilitas lalu aktifkan IMZYY Cleaner.");
    }

    private void go(Intent i) {
        try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "Tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show(); }
    }

    private void openAccessibility() {
        AccessibilityGuard.markWanted(this, true);
        if (GpuEngine.canSecure(this) && CleanerService.enableViaSecure(this)) { Toast.makeText(this, "Diaktifkan lewat izin adb", Toast.LENGTH_SHORT).show(); return; }
        // Samsung: langsung ke daftar "Aplikasi terinstal"; kalau tidak ada, pakai halaman biasa.
        try { startActivity(new Intent("com.samsung.accessibility.installed_service").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
        catch (Exception ignored) { }
        go(AccessibilityGuard.settingsIntent(this));
    }

    private void copyAdb() {
        try {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("adb",
                    "adb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS"));
            Toast.makeText(this, "Perintah disalin. Jalankan lewat debugging nirkabel / komputer.", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Gagal menyalin", Toast.LENGTH_SHORT).show(); }
    }

    private void battery() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) { go(AccessibilityGuard.appInfoIntent(this)); return; }
            go(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) { go(AccessibilityGuard.appInfoIntent(this)); }
    }

    @Override protected void onResume() { super.onResume(); closed = false; ui.removeCallbacks(poll); ui.post(poll); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(poll); }
}
