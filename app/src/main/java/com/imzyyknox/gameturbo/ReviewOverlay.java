package com.imzyyknox.gameturbo;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Monitor live overlay: FPS/vsync callback, CPU + GPU utilization, dan suhu.
 * Bisa digeser dan diketuk untuk berganti mode ringkas / kipas. Angka estimasi diberi '~'.
 */
final class ReviewOverlay {
    private static final int TXT = 0xFFF5F5F7, MUTE = 0xFF9A9AA2, BG = 0xE60A0A0C;
    private final Context ctx;
    private final WindowManager wm;
    private final android.content.SharedPreferences sp;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private LinearLayout root, metricsRow, fanRow;
    private WindowManager.LayoutParams lp;
    private TextView fValue, cpuValue, gpuValue, tempValue, infoLine, hintLine;
    private TextView vFps, vCpu, vGpu;
    private FanView fFps, fCpu, fGpu;
    private boolean shown, reading, sampling, suppressed;
    private int frames;
    private long frameStart;
    private volatile int fps = -1;
    private volatile SysStats.Snap snap;

    ReviewOverlay(Context c, WindowManager w, android.content.SharedPreferences prefs) {
        ctx = c; wm = w; sp = prefs;
    }

    boolean isShown() { return shown; }
    private float d() { return ctx.getResources().getDisplayMetrics().density; }
    private int dp(float v) { return Math.round(v * d()); }
    private int screenW() {
        if (Build.VERSION.SDK_INT >= 30) { try { return wm.getCurrentWindowMetrics().getBounds().width(); } catch (Throwable ignored) { } }
        return ctx.getResources().getDisplayMetrics().widthPixels;
    }
    private int screenH() {
        if (Build.VERSION.SDK_INT >= 30) { try { return wm.getCurrentWindowMetrics().getBounds().height(); } catch (Throwable ignored) { } }
        return ctx.getResources().getDisplayMetrics().heightPixels;
    }
    private boolean land() { return screenW() > screenH(); }
    private int mode() { return sp.getInt("review_mode", 0); } // 0 = angka, 1 = kipas

    boolean show() {
        if (shown && root != null && root.isAttachedToWindow()) return true;
        if (!Settings.canDrawOverlays(ctx)) return false;
        try {
            build();
            wm.addView(root, lp);
            shown = true;
            applyVisibility();
            startSampling();
            ui.removeCallbacks(tick);
            ui.post(tick);
            return true;
        } catch (Throwable e) {
            android.util.Log.e("IMZYY-Review", "Gagal memasang monitor mengambang", e);
            shown = false; root = null;
            return false;
        }
    }

    void hide() {
        shown = false;
        ui.removeCallbacks(tick);
        stopSampling();
        try { if (root != null && root.isAttachedToWindow()) wm.removeViewImmediate(root); } catch (Throwable ignored) { }
        root = null;
    }

    void destroy() { hide(); exec.shutdownNow(); }
    void setSuppressed(boolean s) { suppressed = s; applyVisibility(); }
    private void applyVisibility() { if (root != null) root.setVisibility(suppressed ? View.GONE : View.VISIBLE); }

    void ensure() {
        if (!shown) return;
        if (root == null || !root.isAttachedToWindow()) { shown = false; show(); }
    }

    void reflow() {
        if (!shown || root == null) return;
        sizeParts();
        placeFromPrefs();
        try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) { }
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private TextView label(String text) {
        TextView t = new TextView(ctx);
        t.setText(text); t.setTextColor(MUTE); t.setTextSize(8);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView value(String text) {
        TextView t = new TextView(ctx);
        t.setText(text); t.setTextColor(TXT); t.setTextSize(13);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        return t;
    }

    private LinearLayout metric(String name, TextView val) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(5), dp(3), dp(5), dp(3));
        c.addView(label(name));
        c.addView(val, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private void build() {
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(9), dp(7), dp(9), dp(7));
        root.setBackground(rounded(BG, 15, 0x55FFFFFF));
        root.setElevation(dp(10));
        root.setContentDescription("Monitor IMZYY. Geser untuk memindahkan. Ketuk untuk mengganti mode angka dan kipas.");

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("IMZYY  •  LIVE MONITOR");
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView live = label("● LIVE");
        live.setTextColor(0xFF8BE3B0);
        top.addView(live);
        root.addView(top);

        fValue = value("--"); cpuValue = value("--"); gpuValue = value("--"); tempValue = value("--°C");
        metricsRow = new LinearLayout(ctx);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        metricsRow.setGravity(Gravity.CENTER);
        metricsRow.setBackground(rounded(0x5518181B, 10, 0));
        metricsRow.addView(metric("FPS*", fValue), new LinearLayout.LayoutParams(0, -2, 1f));
        metricsRow.addView(metric("CPU", cpuValue), new LinearLayout.LayoutParams(0, -2, 1f));
        metricsRow.addView(metric("GPU", gpuValue), new LinearLayout.LayoutParams(0, -2, 1f));
        metricsRow.addView(metric("TEMP", tempValue), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(metricsRow, topMargin(5));

        fFps = fan("VSYNC"); fCpu = fan("CPU"); fGpu = fan("GPU");
        vFps = value("-- Hz"); vCpu = value("--%"); vGpu = value("--%");
        fanRow = new LinearLayout(ctx);
        fanRow.setOrientation(LinearLayout.HORIZONTAL);
        fanRow.setGravity(Gravity.CENTER);
        fanRow.addView(fanColumn(fFps, vFps), new LinearLayout.LayoutParams(0, -2, 1f));
        fanRow.addView(fanColumn(fCpu, vCpu), new LinearLayout.LayoutParams(0, -2, 1f));
        fanRow.addView(fanColumn(fGpu, vGpu), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(fanRow, topMargin(4));

        infoLine = label("TEMP --°C  •  DISPLAY -- Hz");
        infoLine.setTextColor(0xFFD7D7DC);
        root.addView(infoLine, topMargin(3));
        hintLine = label("FPS* = frame monitor overlay  •  geser untuk pindah / ketuk untuk mode kipas");
        hintLine.setTextSize(7);
        root.addView(hintLine, topMargin(2));

        lp = new WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        sizeParts(); placeFromPrefs();
        root.setOnTouchListener(drag);
    }

    private LinearLayout.LayoutParams topMargin(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        return p;
    }

    private FanView fan(String name) {
        FanView f = new FanView(ctx);
        f.setLabel(name); f.setCompact(true); f.spinUp();
        return f;
    }

    private LinearLayout fanColumn(FanView f, TextView val) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(2), 0, dp(2), 0);
        col.addView(f, new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(val, new LinearLayout.LayoutParams(-1, -2));
        return col;
    }

    private void sizeParts() {
        boolean l = land();
        int fanSize = l ? 34 : 44;
        for (FanView f : new FanView[]{fFps, fCpu, fGpu}) {
            if (f == null) continue;
            android.view.ViewGroup.LayoutParams p = f.getLayoutParams();
            if (p != null) { p.width = dp(fanSize); p.height = dp(fanSize); f.setLayoutParams(p); }
        }
        for (TextView t : new TextView[]{fValue, cpuValue, gpuValue, tempValue, vFps, vCpu, vGpu}) {
            if (t != null) t.setTextSize(l ? 11 : 13);
        }
        boolean fans = mode() == 1;
        if (metricsRow != null) metricsRow.setVisibility(fans ? View.GONE : View.VISIBLE);
        if (fanRow != null) fanRow.setVisibility(fans ? View.VISIBLE : View.GONE);
        if (hintLine != null) hintLine.setText(fans
                ? "FAN VISUAL  •  bukan kipas fisik  •  ketuk untuk mode angka"
                : "FPS* = frame monitor overlay  •  geser untuk pindah / ketuk untuk mode kipas");
    }

    private void placeFromPrefs() {
        int w = Math.max(1, root.getWidth()), h = Math.max(1, root.getHeight());
        int sw = screenW(), sh = screenH();
        int x = Math.round(sp.getFloat("review_xr", .02f) * sw);
        int y = Math.round(sp.getFloat("review_yr", .08f) * sh);
        lp.x = Math.max(0, Math.min(x, Math.max(0, sw - Math.max(w, dp(60)))));
        lp.y = Math.max(0, Math.min(y, Math.max(0, sh - Math.max(h, dp(30)))));
    }

    private final View.OnTouchListener drag = new View.OnTouchListener() {
        int sx, sy; float tx, ty; boolean moved;
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (lp == null || root == null) return false;
            int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = lp.x; sy = lp.y; tx = e.getRawX(); ty = e.getRawY(); moved = false; return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - tx, dy = e.getRawY() - ty;
                    if (!moved && Math.abs(dx) < slop && Math.abs(dy) < slop) return true;
                    moved = true;
                    int w = root.getWidth(), h = root.getHeight();
                    lp.x = Math.max(0, Math.min(sx + Math.round(dx), Math.max(0, screenW() - w)));
                    lp.y = Math.max(0, Math.min(sy + Math.round(dy), Math.max(0, screenH() - h)));
                    try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) { }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        sp.edit().putFloat("review_xr", lp.x / (float) Math.max(1, screenW()))
                                .putFloat("review_yr", lp.y / (float) Math.max(1, screenH())).apply();
                    } else {
                        sp.edit().putInt("review_mode", mode() == 1 ? 0 : 1).apply();
                        sizeParts(); render();
                        try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL: return true;
                default: return true;
            }
        }
    };

    private final Choreographer.FrameCallback frameCb = new Choreographer.FrameCallback() {
        @Override public void doFrame(long ns) {
            if (!sampling) return;
            if (frameStart == 0L) frameStart = ns;
            frames++;
            long elapsed = ns - frameStart;
            if (elapsed >= 1_000_000_000L) {
                fps = Math.round(frames * 1_000_000_000f / elapsed);
                frames = 0; frameStart = ns;
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void startSampling() {
        sampling = true; frames = 0; frameStart = 0L;
        Choreographer.getInstance().removeFrameCallback(frameCb);
        Choreographer.getInstance().postFrameCallback(frameCb);
    }
    private void stopSampling() { sampling = false; Choreographer.getInstance().removeFrameCallback(frameCb); }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!shown || root == null) return;
            if (!reading && !suppressed) {
                reading = true;
                try {
                    exec.execute(() -> {
                        try { snap = SysStats.read(ctx); } catch (Throwable e) { android.util.Log.w("IMZYY-Review", "Telemetry unavailable", e); }
                        reading = false;
                        ui.post(ReviewOverlay.this::render);
                    });
                } catch (Throwable e) { reading = false; }
            }
            ui.postDelayed(this, 1000);
        }
    };

    private String percent(float value, boolean real) {
        int n = Math.round(Math.max(0f, Math.min(1f, value)) * 100f);
        return (real ? "" : "~") + n + "%";
    }

    private void render() {
        if (!shown || root == null) return;
        SysStats.Snap s = snap;
        String f = fps >= 0 ? String.valueOf(fps) : "--";
        String cpu = s == null ? "--" : percent(s.cpuLoad, s.cpuUsageReal);
        String gpu = s == null ? "--" : percent(s.gpuLoad, s.gpuUsageReal);
        float temp = s == null ? Float.NaN : s.tempC;
        String source = s == null ? "N/A" : s.tempSource;
        String tp = Float.isNaN(temp) ? "--°C" : String.format(Locale.US, "%.1f°C", temp);
        int hz = Math.round(FpsEngine.current(ctx));

        fValue.setText(f); cpuValue.setText(cpu); gpuValue.setText(gpu); tempValue.setText(tp);
        vFps.setText(f + " Hz"); vCpu.setText(cpu); vGpu.setText(gpu);
        infoLine.setText("TEMP " + tp + " " + source + "  •  DISPLAY " + hz + " Hz");

        if (fFps != null) {
            float fpsLoad = hz > 0 && fps > 0 ? Math.max(0f, Math.min(1f, fps / (float) hz)) : 0.15f;
            fFps.setLoad(fpsLoad, false);
            if (fps >= 0) fFps.setVisualFps(fps);
            fFps.setTemperatureC(temp);
        }
        if (s != null) {
            fCpu.setLoad(s.cpuLoad, !s.cpuUsageReal);
            fGpu.setLoad(s.gpuLoad, !s.gpuUsageReal);
            fCpu.setTemperatureC(temp); fGpu.setTemperatureC(temp);
        }
    }
}
