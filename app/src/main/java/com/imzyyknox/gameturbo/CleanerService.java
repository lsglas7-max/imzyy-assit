package com.imzyyknox.gameturbo;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Otomatisasi lewat Accessibility: membuka Info Aplikasi, menekan "Clear cache" / "Force stop"
 * seperti yang dilakukan pengguna, lalu lanjut ke aplikasi berikutnya tanpa menunggu lama.
 * Tidak ada tirai/layar penutup; progres dilaporkan ke log di floating window.
 */
public class CleanerService extends AccessibilityService {
    interface Listener { void on(String msg, boolean ok, boolean queueDone); }

    static final int CLEAR_CACHE = 1, FORCE_STOP = 2;
    static Listener listener;
    private static CleanerService inst;

    private static final String[] CLEAR = {"clear cache", "hapus cache", "bersihkan cache", "kosongkan cache", "hapus tembolok"};
    private static final String[] STORAGE = {"storage", "penyimpanan"};
    private static final String[] STORAGE_EXCL = {"clear", "hapus", "manage", "kelola", "data", "kosongkan", "bersihkan"};
    private static final String[] FORCE = {"force stop", "paksa berhenti", "paksa hentikan", "hentikan paksa", "force-stop"};
    private static final String[] CONFIRM_OK = {"ok", "oke", "yes", "ya"};
    private static final long POLL_MS = 110, TIMEOUT_MS = 5500;

    private static class Task {
        final String pkg, label; final int type;
        Task(String p, String l, int t) { pkg = p; label = l; type = t; }
    }

    private final ArrayDeque<Task> queue = new ArrayDeque<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private Task cur;
    private boolean busy;
    private int phase, depth, totalCount, doneCount, epoch;
    private long startedAt, phaseAt;

    static boolean enabled() { return inst != null; }

    /**
     * Aktifkan IMZYY Cleaner langsung dari floating window, tanpa membuka layar Aksesibilitas.
     * Hanya bisa kalau izin WRITE_SECURE_SETTINGS sudah diberikan sekali lewat adb; kalau belum, return false.
     */
    static boolean enableViaSecure(Context c) {
        if (!GpuEngine.canSecure(c)) return false;
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            String me = new ComponentName(c, CleanerService.class).flattenToString();
            String cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            boolean has = false;
            if (cur != null) for (String x : cur.split(":")) if (x.equalsIgnoreCase(me)) has = true;
            if (!has) Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, cur == null || cur.isEmpty() ? me : cur + ":" + me);
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (Throwable e) { return false; }
    }
    static int pending() { return inst == null ? 0 : inst.queue.size() + (inst.cur != null ? 1 : 0); }
    static int total() { return inst == null ? 0 : inst.totalCount; }
    static int done() { return inst == null ? 0 : inst.doneCount; }
    static void cancel() { if (inst != null) inst.cancelAll(); }
    private static String returnPkg;
    /** Setelah antrean selesai, buka lagi aplikasi/game yang tadi di depan. */
    static void returnTo(String pkg) { returnPkg = (pkg == null || pkg.isEmpty()) ? null : pkg; }

    static void enqueue(final String pkg, final int type, final String label) {
        final CleanerService me = inst;
        if (me == null) return;
        // Aman dipanggil dari thread mana pun: antrean dan startActivity hanya di thread utama.
        if (Looper.myLooper() != Looper.getMainLooper()) { me.h.post(() -> enqueue(pkg, type, label)); return; }
        if (!inst.busy && inst.cur == null) { inst.totalCount = 0; inst.doneCount = 0; }
        inst.totalCount++;
        inst.queue.add(new Task(pkg, label == null ? pkg : label, type));
        if (!inst.busy) inst.next();
    }

    // ------------------------------------------------------------ konfirmasi dialog "bersihkan cache" bawaan sistem
    private static final String[] SYS_OK = {"clear", "clear cache", "clear all", "hapus", "hapus cache", "hapus semua", "bersihkan", "bersihkan cache", "kosongkan", "ok", "oke", "yes", "ya", "lanjutkan", "continue", "delete"};
    private static final String[] CACHE_WORDS = {"cache", "tembolok"};
    private boolean sysWatch;
    private long sysUntil;

    /**
     * Pantau dialog resmi Android "bersihkan cache" yang dipicu CacheClearActivity, lalu tekan tombol konfirmasinya.
     * Hanya menekan tombol di jendela aplikasi sistem yang memuat kata cache; tidak menyentuh jendela game.
     */
    static boolean watchSystemDialog(long ms) {
        if (inst == null) return false;
        inst.sysWatch = true;
        inst.sysUntil = SystemClock.elapsedRealtime() + ms;
        inst.h.removeCallbacks(inst.sysPoll);
        inst.h.postDelayed(inst.sysPoll, 150);
        return true;
    }

    private final Runnable sysPoll = new Runnable() {
        @Override public void run() {
            if (!sysWatch) return;
            if (SystemClock.elapsedRealtime() > sysUntil) { sysWatch = false; return; }
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                CharSequence pk = root == null ? null : root.getPackageName();
                if (pk != null && isSystemPkg(pk.toString()) && hasText(root, CACHE_WORDS)) {
                    AccessibilityNodeInfo ok = findClickable(root, SYS_OK, true, null);
                    if (ok != null && ok.isEnabled()) {
                        ok.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        sysWatch = false;
                        say("Dialog cache sistem dikonfirmasi otomatis", true, true);
                        return;
                    }
                }
            } catch (Exception ignored) { }
            h.postDelayed(this, 120);
        }
    };

    private boolean isSystemPkg(String pkg) {
        if (pkg.equals(getPackageName())) return false;
        try {
            return (getPackageManager().getApplicationInfo(pkg, 0).flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) { return false; }
    }

    private boolean hasText(AccessibilityNodeInfo n, String[] words) {
        if (n == null) return false;
        CharSequence t = n.getText(), d = n.getContentDescription();
        String s = ((t == null ? "" : t.toString()) + " " + (d == null ? "" : d.toString())).toLowerCase(Locale.ROOT);
        for (String w : words) if (s.contains(w)) return true;
        for (int i = 0; i < n.getChildCount(); i++) if (hasText(n.getChild(i), words)) return true;
        return false;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
        try {
            // Pengguna menyalakan Cleaner = ingin tetap aktif; AccessibilityGuard akan menjaganya.
            AccessibilityGuard.markWanted(this, true);
            GuardJob.schedule(this);
            keepAlive();
            // Sistem mengikat ulang layanan aksesibilitas yang aktif setelah proses mati: jalur pemulihan hub yang tidak bergantung alarm.
            if (KeepAlive.wanted(this)) { KeepAlive.arm(getApplicationContext(), 5_000L); GuardJob.reviveHub(getApplicationContext()); }
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
                setServiceInfo(info);
            }
        } catch (Throwable e) { android.util.Log.w("IMZYY-Cleaner", "serviceInfo tweak failed", e); }
    }
    /** Notifikasi senyap supaya proses tidak dianggap tidur oleh Samsung; dibungkus try karena tipe FGS bisa ditolak firmware. */
    private void keepAlive() {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new android.app.NotificationChannel("imzyy_cleaner", "IMZYY Cleaner", android.app.NotificationManager.IMPORTANCE_MIN));
            android.app.Notification n = new android.app.Notification.Builder(this, "imzyy_cleaner").setContentTitle("IMZYY Cleaner aktif")
                    .setContentText("Dijaga tetap hidup").setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true).build();
            if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(9, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(9, n);
        } catch (Throwable e) { android.util.Log.w("IMZYY-Cleaner", "keepAlive failed", e); }
    }
    @Override public boolean onUnbind(Intent intent) { inst = null; AccessibilityGuard.lost(getApplicationContext()); return super.onUnbind(intent); }
    @Override public void onDestroy() { inst = null; h.removeCallbacksAndMessages(null); AccessibilityGuard.lost(getApplicationContext()); super.onDestroy(); }
    // Tidak memproses event sama sekali: layanan ini hanya bekerja saat diminta, jadi tidak ada kode yang bisa crash di sini.
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    private void say(String m, boolean ok, boolean done) { if (listener != null) listener.on(m, ok, done); }

    private void next() {
        epoch++;
        cur = queue.poll();
        if (cur == null) { busy = false; return; }
        busy = true; phase = 0; depth = 1; startedAt = SystemClock.elapsedRealtime(); phaseAt = startedAt;
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + cur.pkg));
            // CLEAR_TASK: tiap aplikasi mulai dari halaman bersih, jadi tidak perlu tekan BACK di antara aplikasi.
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(i);
        } catch (Exception e) { finish("Gagal membuka Info Aplikasi: " + cur.label, false); return; }
        h.removeCallbacks(poll);
        h.postDelayed(poll, 160);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (cur == null) return;
            if (SystemClock.elapsedRealtime() - startedAt > TIMEOUT_MS) { finish("Dilewati (tombol tidak ditemukan): " + cur.label + seenTexts(), false); return; }
            try { step(); } catch (Exception e) { finish("Gagal: " + cur.label, false); return; }
            if (cur != null) h.postDelayed(this, POLL_MS);
        }
    };

    private void step() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        CharSequence pk = root.getPackageName();
        String pn = pk == null ? "" : pk.toString().toLowerCase(Locale.ROOT);
        boolean inSettings = pn.contains("settings");
        boolean confirmPhase = cur.type == FORCE_STOP && phase == 1;
        if (!inSettings && !confirmPhase) return;
        if (cur.type == FORCE_STOP) stepForceStop(root); else stepClear(root);
    }

    private void stepForceStop(AccessibilityNodeInfo root) {
        if (phase == 0) {
            AccessibilityNodeInfo n = findClickable(root, FORCE, false, null);
            if (n == null) return;
            if (!n.isEnabled()) { finish("Sudah tidak berjalan: " + cur.label, true); return; }
            n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            phase = 1; phaseAt = SystemClock.elapsedRealtime();
            return;
        }
        if (SystemClock.elapsedRealtime() - phaseAt < 180) return;
        AccessibilityNodeInfo ok = findClickable(root, CONFIRM_OK, true, null);
        if (ok == null) ok = findClickable(root, FORCE, false, null);
        if (ok == null) return;
        ok.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        finish("Force stop: " + cur.label, true);
    }

    private void stepClear(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo n = findClickable(root, CLEAR, false, null);
        if (n != null) {
            if (!n.isEnabled()) { finish("Tidak ada cache: " + cur.label, true); return; }
            n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            finish("Cache dibersihkan: " + cur.label, true);
            return;
        }
        if (phase == 0) {
            AccessibilityNodeInfo s = findClickable(root, STORAGE, false, STORAGE_EXCL);
            if (s != null) {
                s.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                phase = 1; depth = 2; phaseAt = SystemClock.elapsedRealtime();
            } else scrollAny(root);
        } else scrollAny(root);
    }

    /** Diagnosa saat tombol tidak ketemu: paket jendela dan beberapa teks tombol yang terlihat, supaya mudah dilaporkan. */
    private String seenTexts() {
        try {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            if (r == null) return " • jendela tidak terbaca";
            StringBuilder sb = new StringBuilder(" • jendela " + r.getPackageName() + " • terlihat: ");
            int[] n = {0};
            collect(r, sb, n);
            return sb.toString();
        } catch (Throwable e) { return ""; }
    }

    private void collect(AccessibilityNodeInfo x, StringBuilder sb, int[] n) {
        if (x == null || n[0] >= 8) return;
        CharSequence t = x.getText();
        if (t != null && t.length() > 0 && t.length() < 30 && (x.isClickable() || clickableUp(x) != null)) {
            if (n[0] > 0) sb.append(" | ");
            sb.append(t); n[0]++;
        }
        for (int i = 0; i < x.getChildCount() && n[0] < 8; i++) collect(x.getChild(i), sb, n);
    }

    private void scrollAny(AccessibilityNodeInfo n) {
        if (n == null) return;
        if (n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return;
        for (int i = 0; i < n.getChildCount(); i++) scrollAny(n.getChild(i));
    }

    private void finish(String msg, boolean ok) {
        final int backs = cur != null && cur.type == CLEAR_CACHE ? depth : 1;
        final boolean stop = cur != null && cur.type == FORCE_STOP;
        cur = null;
        doneCount++;
        h.removeCallbacks(poll);
        final boolean last = queue.isEmpty();
        say(msg, ok, last);
        if (!last) { h.postDelayed(this::next, stop ? 260 : 90); return; }
        busy = false;
        final int my = epoch;
        // Antrian habis: keluar dari Pengaturan, kembali ke layar sebelumnya.
        for (int k = 1; k <= backs; k++) h.postDelayed(() -> { if (my == epoch && cur == null) performGlobalAction(GLOBAL_ACTION_BACK); }, (stop ? 300L : 150L) * k);
        final String rp = returnPkg; returnPkg = null;
        if (rp != null) h.postDelayed(() -> { if (my == epoch && cur == null) launchPkg(rp); }, (stop ? 300L : 150L) * backs + 450L);
    }

    private void launchPkg(String p) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); startActivity(i); }
        } catch (Throwable ignored) { }
    }

    private void cancelAll() {
        final int backs = depth;
        queue.clear(); cur = null; busy = false; epoch++; returnPkg = null;
        h.removeCallbacksAndMessages(null);
        for (int k = 1; k <= backs; k++) h.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 150L * k);
        say("Pembersihan dibatalkan", false, true);
    }

    private static boolean matches(String raw, String[] keys, boolean exact, String[] excl) {
        if (raw == null) return false;
        String s = raw.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty() || s.length() > 60) return false;
        if (excl != null) for (String x : excl) if (s.contains(x)) return false;
        for (String k : keys) { if (exact ? s.equals(k) : s.contains(k)) return true; }
        return false;
    }

    private AccessibilityNodeInfo clickableUp(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo x = n;
        for (int i = 0; i < 6 && x != null; i++) {
            if (x.isClickable()) return x;
            x = x.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo findClickable(AccessibilityNodeInfo n, String[] keys, boolean exact, String[] excl) {
        if (n == null) return null;
        CharSequence t = n.getText(), d = n.getContentDescription();
        if (matches(t == null ? null : t.toString(), keys, exact, excl) || matches(d == null ? null : d.toString(), keys, exact, excl)) {
            AccessibilityNodeInfo c = clickableUp(n);
            if (c != null) return c;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findClickable(n.getChild(i), keys, exact, excl);
            if (r != null) return r;
        }
        return null;
    }
}
