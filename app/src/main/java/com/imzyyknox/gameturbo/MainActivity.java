package com.imzyyknox.gameturbo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Game Turbo v8.0 - tampilan ala Game Launcher / Gaming Hub Samsung:
 * latar hitam AMOLED, kartu abu gelap membulat besar, aksen biru One UI, daftar game dengan ikon asli.
 */
public class MainActivity extends Activity {
    private static final int BG = 0xFF000000, CARD = 0xFF1C1C1F, CARD2 = 0xFF28282C, BLUE = 0xFF3E91FF, TXT = 0xFFFFFFFF,
            SUB = 0xFFA6A6AD, OK = 0xFF3DDC84, WARN = 0xFFFFB84D, BAD = 0xFFFF5C5C;
    private static final int[] TARGETS = {30, 60, 90, 0, -1};
    private static final String[] TARGET_NAMES = {"30", "60", "90", "MAX", "AUTO"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final FpsEngine.Meter meter = new FpsEngine.Meter();
    private final ArrayList<TextView> fpsChips = new ArrayList<>();
    private SharedPreferences sp;
    private TextView tRam, tSto, tTemp, tBat, optTitle, optSub, liveHz, cleanerSub;
    private IconView optIcon;
    private LinearLayout gameBox, permBox, hubSwitchHolder;
    private final Runnable tick = new Runnable() { @Override public void run() { refresh(); ui.postDelayed(this, 2000); } };

    // ---------------------------------------------------------------- helpers
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable bg(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView txt(String s, int sz, int c, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sz); t.setTextColor(c);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    private LinearLayout col() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); return r; }
    private LinearLayout.LayoutParams mt(int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); return p; }
    private LinearLayout.LayoutParams w1(boolean first) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); if (!first) p.leftMargin = dp(8); return p; }

    private LinearLayout card() {
        LinearLayout c = col();
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(bg(CARD, 26, 0));
        return c;
    }

    private TextView section(String s) {
        TextView t = txt(s, 13, SUB, true);
        t.setPadding(dp(8), dp(16), 0, dp(6));
        return t;
    }

    private IconView icon(int type, int size, int color) {
        IconView v = new IconView(this).set(type, color, false);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return v;
    }

    /** Izin "Ubah setelan sistem" diminta otomatis hanya sekali; sesudahnya pakai baris izin atau REFRESH. */
    private void askWrite() {
        if (Perm.first(this, "write")) {
            Toast.makeText(this, "Izinkan 'Ubah setelan sistem' (sekali saja), lalu coba lagi", Toast.LENGTH_LONG).show();
            go(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())));
        } else Toast.makeText(this, "Izin 'Ubah setelan sistem' belum aktif • aktifkan di daftar izin", Toast.LENGTH_LONG).show();
    }

    private void go(Intent i) {
        try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "Tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show(); }
    }

    // ---------------------------------------------------------------- build
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("turbo", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED && !sp.getBoolean("notification_prompted", false)) {
            sp.edit().putBoolean("notification_prompted", true).apply();
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG); sv.setFillViewport(true); sv.setVerticalScrollBarEnabled(false);
        sv.setOnApplyWindowInsetsListener((v, ins) -> { v.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom()); return ins; });
        LinearLayout root = col();
        root.setPadding(dp(14), dp(4), dp(14), dp(22));

        // header ala One UI: judul besar rata kiri
        LinearLayout head = row();
        head.setPadding(dp(6), dp(22), dp(6), dp(4));
        LinearLayout titles = col();
        TextView h1 = txt("Game Turbo", 26, TXT, true);
        TextView h2 = txt("IMZYY  •  Game Booster", 11, SUB, false);
        titles.addView(h1); titles.addView(h2);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        IconView logo = new IconView(this).set(IconView.LOGO, 0xFFFFFFFF, true);
        head.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head);

        buildOptimizeCard(root);
        buildStats(root);
        buildFloatingActions(root);

        root.addView(section("Game"));
        gameBox = col();
        root.addView(gameBox);

        root.addView(section("Game Booster"));
        buildBooster(root);

        root.addView(section("Pusat izin"));
        permBox = col();
        permBox.setBackground(bg(CARD, 26, 0));
        permBox.setPadding(dp(6), dp(6), dp(6), dp(6));
        root.addView(permBox);

        buildDeveloperCard(root);

        TextView foot = txt("IMZYY Game Turbo  •  v10.0", 10, 0xFF6E6E75, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(26), 0, 0);
        root.addView(foot);

        sv.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);
        renderGames(); renderPerms();
    }

    /** Kartu kredit developer dengan pintasan WhatsApp resmi. */
    private void buildDeveloperCard(LinearLayout root) {
        root.addView(section("Tentang & Developer"));
        LinearLayout c = card();
        c.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout top = row();
        LinearLayout info = col();
        info.addView(txt("Pembuat Game Turbo Samsung", 15, TXT, true));
        info.addView(txt("Credi By Imzyy", 13, SUB, false), mt(4));
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        TextView mark = txt("☎", 25, 0xFFFFFFFF, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(bg(0xFF25D366, 30, 0));
        top.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        c.addView(top);

        TextView contact = txt("Hubungi Developer via WhatsApp  →", 13, TXT, true);
        contact.setGravity(Gravity.CENTER);
        contact.setPadding(dp(14), dp(14), dp(14), dp(14));
        contact.setBackground(bg(0xFF176B45, 18, 0));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(14);
        c.addView(contact, cp);
        View.OnClickListener openWhatsApp = v -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/6281373070063"));
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, "WhatsApp atau browser tidak tersedia", Toast.LENGTH_SHORT).show();
            }
        };
        contact.setOnClickListener(openWhatsApp);
        mark.setOnClickListener(openWhatsApp);
        c.setOnClickListener(openWhatsApp);
        root.addView(c, mt(4));
    }

    private void buildOptimizeCard(LinearLayout root) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(15));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF17223A, 0xFF1C1C1F});
        g.setCornerRadius(dp(22));
        c.setBackground(g);
        android.widget.FrameLayout ring = new android.widget.FrameLayout(this);
        GradientDrawable rg = new GradientDrawable(); rg.setShape(GradientDrawable.OVAL); rg.setColor(0xFF0E1830); rg.setStroke(dp(3), BLUE);
        ring.setBackground(rg);
        optIcon = new IconView(this).set(IconView.CHECK, BLUE, false);
        ring.addView(optIcon, new android.widget.FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        c.addView(ring, new LinearLayout.LayoutParams(dp(72), dp(72)));
        optTitle = txt("", 18, TXT, true); optTitle.setGravity(Gravity.CENTER);
        c.addView(optTitle, mt(10));
        optSub = txt("", 11, SUB, false); optSub.setGravity(Gravity.CENTER);
        c.addView(optSub, mt(3));
        TextView btn = txt("Optimalkan sekarang", 15, TXT, true);
        btn.setGravity(Gravity.CENTER); btn.setPadding(dp(20), dp(11), dp(20), dp(11));
        btn.setBackground(bg(BLUE, 20, 0));
        btn.setOnClickListener(v -> optimize());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.topMargin = dp(12);
        c.addView(btn, bp);
        TextView hint = txt("Bersihkan memori dan cache yang dapat dilepas sistem", 10, 0xFF7C7C84, false);
        hint.setGravity(Gravity.CENTER);
        c.addView(hint, mt(10));
        root.addView(c, mt(5));
    }

    private void buildStats(LinearLayout root) {
        LinearLayout r = row();
        tRam = stat(r, "RAM", true); tSto = stat(r, "Penyimpanan", false);
        root.addView(r, mt(8));
        LinearLayout r2 = row();
        tTemp = stat(r2, "Suhu baterai", true); tBat = stat(r2, "Baterai", false);
        root.addView(r2, mt(6));
    }

    private TextView stat(LinearLayout r, String label, boolean first) {
        LinearLayout c = col();
        c.setPadding(dp(13), dp(10), dp(13), dp(10));
        c.setBackground(bg(CARD, 22, 0));
        c.addView(txt(label, 12, SUB, false));
        TextView v = txt("--", 19, TXT, true);
        c.addView(v, mt(2));
        r.addView(c, w1(first));
        return v;
    }

    /** Dua pintasan utama agar monitor dan jendela aplikasi tidak tersembunyi di dalam menu panjang. */
    private void buildFloatingActions(LinearLayout root) {
        root.addView(section("Monitor & jendela"));
        LinearLayout r = row();
        r.addView(actionTile(IconView.FPS, "Monitor live", "FPS • CPU • GPU • suhu", this::startMonitor), w1(true));
        r.addView(actionTile(IconView.FLOAT, "Jendela mengambang", "Buka aplikasi dalam freeform", () -> startHub(true, 4)), w1(false));
        root.addView(r, mt(2));
    }

    private View actionTile(int ic, String title, String subtitle, Runnable action) {
        LinearLayout c = col();
        c.setPadding(dp(12), dp(11), dp(10), dp(11));
        c.setBackground(bg(CARD, 18, 0));
        LinearLayout top = row();
        FrameLayout emblem = new FrameLayout(this);
        emblem.setBackground(bg(CARD2, 12, 0));
        emblem.addView(new IconView(this).set(ic, BLUE, true), new android.widget.FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        top.addView(emblem, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView arrow = txt("›", 21, SUB, false); arrow.setGravity(Gravity.CENTER);
        top.addView(arrow, new LinearLayout.LayoutParams(0, dp(34), 1f));
        c.addView(top);
        TextView name = txt(title, 12, TXT, true);
        c.addView(name, mt(7));
        TextView sub = txt(subtitle, 9, SUB, false); sub.setMaxLines(2);
        c.addView(sub, mt(2));
        c.setOnClickListener(v -> action.run());
        return c;
    }

    private void startMonitor() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan Tampil di atas aplikasi untuk monitor", Toast.LENGTH_LONG).show();
            go(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        sp.edit().putBoolean("review_panel", true).apply();
        Intent i = new Intent(this, FloatingService.class).putExtra("show_monitor", true);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "Monitor live aktif • tahan dan geser untuk memindahkan", Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- games (Gaming Hub style)
    private ArrayList<String> gamePkgs() {
        ArrayList<String> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        String legacy = sp.getString("saved_game_package", "");
        String raw = sp.getString("game_pkgs", "");
        if (!legacy.isEmpty() && !raw.contains(legacy)) raw = legacy + (raw.isEmpty() ? "" : "," + raw);
        for (String x : raw.split(",")) if (!x.isEmpty() && !out.contains(x) && pm.getLaunchIntentForPackage(x) != null) out.add(x);
        return out;
    }

    private void saveGames(List<String> l) { sp.edit().putString("game_pkgs", android.text.TextUtils.join(",", l)).putString("saved_game_package", l.isEmpty() ? "" : l.get(0)).apply(); }

    private void renderGames() {
        if (gameBox == null) return;
        gameBox.removeAllViews();
        final ArrayList<String> games = gamePkgs();
        ArrayList<String> cells = new ArrayList<>(games);
        cells.add("+");
        LinearLayout card = card();
        card.setPadding(dp(10), dp(14), dp(10), dp(10));
        for (int i = 0; i < cells.size(); i += 4) {
            LinearLayout r = row();
            for (int j = 0; j < 4; j++) {
                int idx = i + j;
                if (idx < cells.size()) r.addView(gameCell(cells.get(idx)), new LinearLayout.LayoutParams(0, -2, 1));
                else r.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            }
            card.addView(r, mt(i == 0 ? 0 : 10));
        }
        if (games.isEmpty()) {
            TextView t = txt("Tambahkan game dengan tombol + lalu tap untuk bermain dengan Game Booster.", 12, SUB, false);
            t.setPadding(dp(8), dp(10), dp(8), dp(2));
            card.addView(t);
        }
        gameBox.addView(card);
    }

    private View gameCell(final String pkg) {
        LinearLayout c = col();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(2), dp(4), dp(2), dp(4));
        if (pkg.equals("+")) {
            android.widget.FrameLayout f = new android.widget.FrameLayout(this);
            f.setBackground(bg(CARD2, 16, 0));
            f.addView(new IconView(this).set(IconView.PLUS, BLUE, false), new android.widget.FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
            c.addView(f, new LinearLayout.LayoutParams(dp(56), dp(56)));
            TextView l = txt("Tambah", 11, SUB, false); l.setGravity(Gravity.CENTER); l.setPadding(0, dp(6), 0, 0);
            c.addView(l);
            c.setOnClickListener(v -> chooseGame());
            return c;
        }
        PackageManager pm = getPackageManager();
        ImageView iv = new ImageView(this);
        try { iv.setImageDrawable(pm.getApplicationIcon(pkg)); } catch (Exception ignored) { }
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(15)); }
        });
        c.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(56)));
        String label = pkg;
        try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); } catch (Exception ignored) { }
        TextView l = txt(label, 11, TXT, false); l.setGravity(Gravity.CENTER); l.setSingleLine(true); l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l.setPadding(dp(2), dp(6), dp(2), 0);
        c.addView(l);
        final String fl = label;
        c.setOnClickListener(v -> playGame(pkg, fl));
        c.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(fl).setItems(new String[]{"Hapus dari Game", "Buka di jendela mengambang"}, (d, w) -> {
                if (w == 0) { ArrayList<String> g = gamePkgs(); g.remove(pkg); saveGames(g); renderGames(); }
                else { ArrayList<String> f = new ArrayList<>(); String raw = sp.getString("float_pkgs", ""); for (String x : raw.split(",")) if (!x.isEmpty()) f.add(x); if (!f.contains(pkg)) f.add(pkg); sp.edit().putString("float_pkgs", android.text.TextUtils.join(",", f)).apply(); Toast.makeText(this, "Ditambahkan ke tab JENDELA panel mengambang", Toast.LENGTH_SHORT).show(); }
            }).show();
            return true;
        });
        return c;
    }

    private void chooseGame() {
        final PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Collections.sort(apps, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.loadLabel(pm).toString(), b.loadLabel(pm).toString()));
        final ArrayList<String> labels = new ArrayList<>(), pkgs = new ArrayList<>();
        HashSet<String> have = new HashSet<>(gamePkgs());
        for (ResolveInfo ri : apps) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || pkgs.contains(p) || have.contains(p)) continue;
            labels.add(ri.loadLabel(pm).toString()); pkgs.add(p);
        }
        if (labels.isEmpty()) { Toast.makeText(this, "Tidak ada aplikasi lain untuk ditambahkan", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Tambah game").setItems(labels.toArray(new String[0]), (d, which) -> {
            ArrayList<String> g = gamePkgs(); g.add(pkgs.get(which)); saveGames(g); renderGames();
        }).setNegativeButton("Batal", null).show();
    }

    /** Tap game: boost RAM, kunci refresh rate sesuai pilihan, nyalakan panel mengambang, lalu buka game. */
    private void playGame(final String pkg, final String label) {
        if (sp.getBoolean("thermal_guard", false)) {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            float temp = battery == null ? 0 : battery.getIntExtra("temperature", 0) / 10f;
            if (temp >= 44f) { Toast.makeText(this, "Ditahan Penjaga suhu: " + temp + "°C", Toast.LENGTH_LONG).show(); return; }
        }
        Toast.makeText(this, "Menyiapkan " + label + "…", Toast.LENGTH_SHORT).show();
        int t = sp.getInt("fps", -1);
        if (t >= 0) FpsEngine.apply(this, FpsEngine.pick(this, t));
        if (Settings.canDrawOverlays(this) && !FloatingService.running) startForegroundService(new Intent(this, FloatingService.class));
        new Thread(() -> {
            Boost.run(this, 0);
            ui.postDelayed(() -> {
                Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                if (li == null) { Toast.makeText(this, "Game tidak ditemukan", Toast.LENGTH_SHORT).show(); return; }
                startActivity(li);
            }, 300);
        }).start();
    }

    // ---------------------------------------------------------------- booster: daftar berbaris ala Game Space, gaya One UI
    private LinearLayout group() {
        LinearLayout g = col();
        g.setBackground(bg(CARD, 26, 0));
        g.setPadding(dp(4), dp(4), dp(4), dp(4));
        return g;
    }

    /** Satu baris: judul, keterangan, dan tampilan kanan (switch / panah). Return TextView keterangan agar bisa diperbarui. */
    private TextView listRow(LinearLayout g, String title, String sub, View trailing, final Runnable click) {
        if (g.getChildCount() > 0) {
            View d = new View(this);
            d.setBackgroundColor(0x14FFFFFF);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, 1);
            dl.leftMargin = dp(14); dl.rightMargin = dp(14);
            g.addView(d, dl);
        }
        LinearLayout r = row();
        r.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout t = col();
        t.addView(txt(title, 15, TXT, true));
        TextView sb = txt(sub, 12, SUB, false);
        t.addView(sb);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (trailing != null) r.addView(trailing);
        if (click != null) r.setOnClickListener(v -> click.run());
        g.addView(r);
        return sb;
    }

    private TextView chev() { return txt("›", 24, SUB, false); }

    /** Baris dengan switch; keadaan akhir dibaca dari penyimpanan sehingga switch kembali sendiri bila aksi gagal. */
    private void switchRow(LinearLayout g, String title, String sub, final String key, final java.util.function.Consumer<Boolean> act) {
        final Switch s = new Switch(this); tint(s);
        s.setChecked(sp.getBoolean(key, false));
        final boolean[] guard = new boolean[1];
        s.setOnCheckedChangeListener((v, on) -> {
            if (guard[0]) return;
            if (act != null) act.accept(on); else sp.edit().putBoolean(key, on).apply();
            boolean actual = sp.getBoolean(key, false);
            if (actual != on) { guard[0] = true; s.setChecked(actual); guard[0] = false; }
        });
        listRow(g, title, sub, s, () -> s.toggle());
    }

    private void buildBooster(LinearLayout root) {
        LinearLayout g = group();
        hubSwitchHolder = col();
        g.addView(hubSwitchHolder);
        renderHubSwitch();
        switchRow(g, "Touch sensitivity boost", "Layar maks, key sentuh Samsung, respons cepat", "touch_profile", on -> {
            TouchEngine.Result res = TouchEngine.apply(this, on, sp.getInt("touch_hold", 1));
            if (res.needWrite && !Settings.System.canWrite(this)) askWrite();
            sp.edit().putBoolean("touch_profile", on ? res.ok : !res.ok).apply();
            Toast.makeText(this, res.msg, Toast.LENGTH_LONG).show();
        });
        switchRow(g, "Penjaga suhu", "Menahan boost saat ponsel terlalu panas", "thermal_guard", null);
        switchRow(g, "Layar tetap menyala", "Layar tidak mati saat panel aktif", "awake", on -> {
            sp.edit().putBoolean("awake", on).apply();
            FloatingService.reapplyAwake();
        });
        root.addView(g);

        root.addView(section("Frame rate"));
        LinearLayout fr = group();
        LinearLayout inner = col();
        inner.setPadding(dp(14), dp(12), dp(14), dp(14));
        LinearLayout hz = row();
        liveHz = txt("-- Hz", 12, BLUE, true);
        hz.addView(txt("Terkunci saat ini  ", 12, SUB, false)); hz.addView(liveHz);
        inner.addView(hz);
        LinearLayout chips = row();
        fpsChips.clear();
        for (int i = 0; i < TARGETS.length; i++) {
            final int tgt = TARGETS[i];
            TextView t = txt(TARGET_NAMES[i], 13, TXT, true);
            t.setGravity(Gravity.CENTER); t.setPadding(0, dp(11), 0, dp(11));
            t.setOnClickListener(v -> setFps(tgt));
            fpsChips.add(t); chips.addView(t, w1(i == 0));
        }
        inner.addView(chips, mt(10));
        fr.addView(inner);
        root.addView(fr);
        paintFps();

        root.addView(section("Alat"));
        LinearLayout tools = group();
        listRow(tools, "Voice changer", "Ubah suara real-time di panel", chev(), () -> startHub(true, 6));
        listRow(tools, "GPU & optimasi game", "Preset performa dan info GPU", chev(), () -> startHub(true, 7));
        listRow(tools, "Network game", "TCP latency, DNS TLS, Wi-Fi low-latency", chev(), () -> startHub(true, 8));
        listRow(tools, "Kipas & suara mesin", "CPU dan GPU live", chev(), () -> startHub(true, 9));
        listRow(tools, "Clear cache semua app", "Langsung, tanpa buka aplikasi satu-satu", chev(), () -> {
            Toast.makeText(this, "Membersihkan cache…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                final CacheEngine.Result r = CacheEngine.reclaim(this);
                ui.post(() -> Toast.makeText(this, r.ok ? (r.freed > 0 ? "Cache dilepas • bebas +" + OptimizeEngine.size(r.freed) : r.msg) : r.msg, Toast.LENGTH_LONG).show());
            }).start();
        });
        cleanerSub = listRow(tools, "IMZYY Cleaner", cleanerText(), chev(), () -> { AccessibilityGuard.markWanted(this, true); go(AccessibilityGuard.setupIntent(this)); });
        root.addView(tools);
    }

    private String cleanerText() {
        if (CleanerService.enabled()) return "Aktif • Force Stop dan Clear Cache siap";
        return AndroidInfo.hasRestrictedSettings() ? "Belum aktif • ketuk untuk panduan (setelan terbatas)" : "Belum aktif • ketuk untuk mengaktifkan";
    }

    private void tint(Switch s) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{0xFFFFFFFF, 0xFFB5B5BC}));
        s.setTrackTintList(new ColorStateList(st, new int[]{BLUE, 0xFF3A3A40}));
    }

    private void renderHubSwitch() {
        if (hubSwitchHolder == null) return;
        hubSwitchHolder.removeAllViews();
        Switch s = new Switch(this); tint(s);
        s.setChecked(FloatingService.running);
        s.setOnCheckedChangeListener((v, on) -> {
            if (on) startHub(false); else stopService(new Intent(this, FloatingService.class));
        });
        listRow(hubSwitchHolder, "Panel Game Booster", "Jendela mengambang dengan menu berbaris", s, () -> s.toggle());
    }

    private void startHub(boolean openPanel) { startHub(openPanel, 11); }

    private void startHub(boolean openPanel, int tab) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Tampil di atas aplikasi lain', lalu coba lagi", Toast.LENGTH_LONG).show();
            go(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            renderHubSwitch();
            return;
        }
        startForegroundService(new Intent(this, FloatingService.class).putExtra("open", openPanel).putExtra("tab", tab));
        Toast.makeText(this, openPanel ? "Membuka panel…" : "Panel aktif. Bubble akan muncul di layar", Toast.LENGTH_LONG).show();
        // Keep the activity visible until the overlay service has attached its window.
        // Some OEM task managers aggressively reclaim a just-backgrounded process.
    }

    // ---------------------------------------------------------------- fps
    private void paintFps() {
        int sel = sp.getInt("fps", -1);
        for (int i = 0; i < fpsChips.size(); i++) {
            boolean on = TARGETS[i] == sel || (TARGETS[i] == -1 && sel < 0);
            TextView t = fpsChips.get(i);
            t.setTextColor(on ? TXT : SUB);
            t.setBackground(bg(on ? BLUE : CARD2, 16, 0));
        }
    }

    private void setFps(int target) {
        if (target == -1) { FpsEngine.reset(this); sp.edit().remove("fps").apply(); paintFps(); Toast.makeText(this, "Refresh rate otomatis", Toast.LENGTH_SHORT).show(); return; }
        int hz = FpsEngine.pick(this, target);
        if (!FpsEngine.apply(this, hz)) {
            if (!Settings.System.canWrite(this) && !GpuEngine.canSecure(this)) { askWrite(); return; }
            // Izin sudah aktif tapi sistem menolak kunci refresh global: jangan minta izin lagi.
            Toast.makeText(this, "Kunci refresh global: " + FpsEngine.friendly(), Toast.LENGTH_LONG).show();
            return;
        }
        sp.edit().putInt("fps", target).apply();
        paintFps();
        Toast.makeText(this, "Menerapkan " + hz + " Hz", Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- optimize (langsung di dalam IMZYY)
    private volatile boolean optimizing;

    private void optimize() {
        if (optimizing) { Toast.makeText(this, "Sedang mengoptimalkan…", Toast.LENGTH_SHORT).show(); return; }
        optimizing = true;
        optTitle.setText("Mengoptimalkan…"); optSub.setText("RAM dan cache dibersihkan langsung");
        new Thread(() -> {
            final OptimizeEngine.Report rep = OptimizeEngine.run(this, line -> ui.post(() -> optSub.setText(line)));
            ui.post(() -> { optimizing = false; Toast.makeText(this, rep.summary, Toast.LENGTH_LONG).show(); refresh(); });
        }).start();
    }

    // ---------------------------------------------------------------- permission center
    private boolean hasUsage() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    // ---------------------------------------------------------------- wizard izin: sekali jalan, masing-masing sekali
    private static final String[] WIZ = {"overlay", "write", "usage", "allfiles", "cleaner", "dnd", "battery", "mic", "notif"};

    private boolean wizGranted(String k) {
        switch (k) {
            case "overlay": return Settings.canDrawOverlays(this);
            case "write": return Settings.System.canWrite(this);
            case "usage": return hasUsage();
            case "allfiles": return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
            case "cleaner": return CleanerService.enabled();
            case "dnd": return ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted();
            case "battery": return ((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
            case "mic": return checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED;
            case "notif": return Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
            default: return true;
        }
    }

    private void wizLaunch(String k) {
        Uri pkg = Uri.parse("package:" + getPackageName());
        switch (k) {
            case "overlay": go(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)); break;
            case "write": go(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)); break;
            case "usage": go(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); break;
            case "allfiles": go(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)); break;
            case "cleaner":
                if (CleanerService.enableViaSecure(this)) ui.postDelayed(this::wizardNext, 900);
                else { AccessibilityGuard.markWanted(this, true); go(AccessibilityGuard.setupIntent(this)); }
                break;
            case "dnd": go(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); break;
            case "battery": go(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)); break;
            case "mic": requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 2); break;
            case "notif": requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1); break;
            default: break;
        }
    }

    private void wizardStart() {
        sp.edit().putBoolean("wiz_active", true).apply();
        wizardNext();
    }

    /** REFRESH: lupakan catatan "sudah diminta" lalu cek ulang status. */
    private void wizardRefresh() {
        Perm.resetAll(this);
        sp.edit().putBoolean("wiz_active", false).apply();
        renderPerms();
        Toast.makeText(this, "Izin dicek ulang", Toast.LENGTH_SHORT).show();
    }

    /** Minta izin berikutnya yang belum aktif dan belum pernah diminta. Dipanggil lagi tiap kembali dari Pengaturan. */
    private void wizardNext() {
        if (!sp.getBoolean("wiz_active", false)) return;
        for (String k : WIZ) {
            if (wizGranted(k) || sp.getBoolean("wiz_" + k, false)) continue;
            sp.edit().putBoolean("wiz_" + k, true).apply();
            wizLaunch(k);
            return;
        }
        sp.edit().putBoolean("wiz_active", false).apply();
        renderPerms();
        Toast.makeText(this, "Semua izin sudah diminta sekali. Tidak akan diminta lagi.", Toast.LENGTH_LONG).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        renderPerms();
        ui.postDelayed(this::wizardNext, 300);
    }

    private void renderPerms() {
        if (permBox == null) return;
        permBox.removeAllViews();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean all = true;
        for (String k : WIZ) if (!wizGranted(k)) { all = false; break; }
        perm("Aktifkan semua izin sekaligus", "Diminta berurutan, masing-masing hanya SEKALI", all, this::wizardStart);
        perm("Refresh izin", "Cek ulang status, izin yang kurang bisa diminta sekali lagi", false, this::wizardRefresh);
        perm("Tampil di atas aplikasi lain", "Panel mengambang", Settings.canDrawOverlays(this), () -> go(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        perm("Ubah setelan sistem", "Kunci refresh rate layar", Settings.System.canWrite(this), () -> go(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))));
        perm("Akses penggunaan (pemindaian)", "Daftar aplikasi aktif dan ukuran cache", hasUsage(), () -> go(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        perm("IMZYY Cleaner (Aksesibilitas)", "Clear cache dan force stop per aplikasi", CleanerService.enabled(), () -> { AccessibilityGuard.markWanted(this, true); go(AccessibilityGuard.setupIntent(this)); });
        perm("Jangan ganggu", "DND saat bermain", nm.isNotificationPolicyAccessGranted(), () -> go(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        perm("Akses semua file", "Bersihkan cache tuntas (dialog sistem)", wizGranted("allfiles"), () -> wizLaunch("allfiles"));
        perm("Mikrofon", "Voice changer", checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED, () -> requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 2));
        perm("Tanpa batasan baterai", "Panel tidak mudah dimatikan sistem", pm.isIgnoringBatteryOptimizations(getPackageName()), () -> go(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
    }

    private void perm(String title, String sub, boolean granted, Runnable a) {
        LinearLayout r = row();
        r.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout t = col();
        t.addView(txt(title, 14, TXT, true)); t.addView(txt(sub, 11, SUB, false));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView st = txt(granted ? "Aktif" : "Aktifkan", 12, granted ? OK : BLUE, true);
        r.addView(st);
        if (!granted) r.setOnClickListener(v -> a.run());
        permBox.addView(r);
    }

    // ---------------------------------------------------------------- lifecycle / refresh
    @Override protected void onResume() {
        super.onResume();
        if (AccessibilityGuard.wanted(this)) { AccessibilityGuard.ensure(this); GuardJob.schedule(this); }
        meter.start(liveHz);
        renderPerms(); renderGames(); renderHubSwitch(); paintFps();
        if (cleanerSub != null) cleanerSub.setText(cleanerText());
        ui.postDelayed(this::wizardNext, 500);
        ui.post(tick);
    }

    @Override protected void onPause() { super.onPause(); meter.stop(); ui.removeCallbacks(tick); }

    private void refresh() {
        android.app.ActivityManager.MemoryInfo mi = Boost.mem(this);
        int used = (int) (100 - mi.availMem * 100 / Math.max(1, mi.totalMem));
        tRam.setText(used + "%"); tRam.setTextColor(used > 85 ? BAD : used > 70 ? WARN : TXT);
        StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
        tSto.setText(String.format(Locale.US, "%d GB bebas", sf.getAvailableBytes() >> 30));
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            float t = b.getIntExtra("temperature", 0) / 10f;
            tBat.setText(b.getIntExtra("level", 0) * 100 / Math.max(1, b.getIntExtra("scale", 100)) + "%");
            tTemp.setText(t + "°C"); tTemp.setTextColor(t >= 42 ? BAD : t >= 38 ? WARN : TXT);
        }
        liveHz.setText(Math.round(FpsEngine.current(this)) + " Hz");
        long last = sp.getLong("last_opt", 0);
        if (CleanerService.pending() > 0 || optimizing) return;
        if (last == 0) {
            optTitle.setText("Ponsel belum dioptimalkan"); optSub.setText("Tap untuk membersihkan RAM, cache, dan proses latar");
            optIcon.setColor(WARN, false);
        } else {
            long m = Math.max(0, (System.currentTimeMillis() - last) / 60000);
            optTitle.setText("Ponsel sudah dioptimalkan");
            String info = sp.getString("last_opt_info", "");
            optSub.setText((m < 1 ? "Baru saja" : m + " menit yang lalu") + (info.isEmpty() ? "" : " • " + info));
            optIcon.setColor(BLUE, false);
        }
    }
}
