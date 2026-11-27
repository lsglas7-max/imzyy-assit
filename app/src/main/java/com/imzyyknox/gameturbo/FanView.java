package com.imzyyknox.gameturbo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import java.io.File;
import android.view.View;

/**
 * Kipas visual CPU / GPU / VSYNC. Kecepatan mengikuti data beban atau cadence monitor dengan inersia:
 * saat dibuka kipas "spin-up" dari diam, saat beban turun melambat pelan-pelan.
 * Palet monokrom perak/hitam seperti sisa UI; cincin gauge memanas putih -> kuning -> merah saat beban tinggi.
 */
final class FanView extends View {
    private static final int BLADES = 7;

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), tick = new Paint(Paint.ANTI_ALIAS_FLAG),
            bladeFill = new Paint(Paint.ANTI_ALIAS_FLAG), bladeEdge = new Paint(Paint.ANTI_ALIAS_FLAG),
            blur = new Paint(Paint.ANTI_ALIAS_FLAG), hub = new Paint(Paint.ANTI_ALIAS_FLAG),
            glow = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blade = new Path();
    private final RectF rf = new RectF();

    private float cx, cy, r;
    private float angle, omega;          // derajat, derajat/detik
    private float targetLoad, shownLoad; // 0..1
    private float temperatureC = Float.NaN;
    private int physicalRpm = -1;
    private int profileAccent = 0;
    private int profileMode = 1;
    private int liveFps = -1;
    private boolean fpsDrivenVisual;
    private String rpmSensorPath;
    private long lastSensorProbe, lastRpmSample;
    private int cachedRpm = -1;
    private String label = "CPU";
    private boolean running, estimate, compact;
    private long last;

    FanView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        tick.setStyle(Paint.Style.STROKE); tick.setStrokeCap(Paint.Cap.ROUND);
        bladeEdge.setStyle(Paint.Style.STROKE); bladeEdge.setStrokeWidth(1.2f); bladeEdge.setColor(0x66FFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txt.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setLabel(String s) { label = s; invalidate(); }
    void setProfileAccent(int color) { profileAccent = color; invalidate(); }
    void setProfileMode(int mode) { profileMode = Math.max(0, Math.min(3, mode)); invalidate(); }
    /** Mode kecil (header): label hub lebih besar, tanpa teks RPM/LOAD/FPS di dalam kipas. */
    void setCompact(boolean b) { compact = b; invalidate(); }
    void setLoad(float load, boolean estimated) { targetLoad = Math.max(0f, Math.min(1f, load)); estimate = estimated; }
    /** Actual fan RPM only if a readable hardware sensor exists. No RPM is fabricated. */
    void setTemperatureC(float c) { temperatureC = c; invalidate(); }
    /** Visual blade animation can follow overlay FPS, but it is NOT a physical fan RPM reading. */
    void setVisualFps(int fps) { liveFps = fps; fpsDrivenVisual = true; invalidate(); }
    int physicalRpm() { return physicalRpm; }

    private int readPhysicalRpm() {
        long now = SystemClock.elapsedRealtime();
        // Sensor RPM tidak perlu dibaca setiap frame animasi; polling 1 detik lebih ringan.
        if (now - lastRpmSample < 1000L) return cachedRpm;
        lastRpmSample = now;
        if (rpmSensorPath == null && now - lastSensorProbe > 10_000L) {
            lastSensorProbe = now;
            String[] paths = {
                "/sys/class/hwmon/hwmon0/fan1_input", "/sys/class/hwmon/hwmon1/fan1_input",
                "/sys/class/hwmon/hwmon2/fan1_input", "/sys/class/hwmon/hwmon3/fan1_input",
                "/sys/devices/platform/fan/fan_speed", "/sys/devices/platform/cooling_device/fan_rpm"
            };
            for (String path : paths) { if (new File(path).canRead()) { rpmSensorPath = path; break; } }
        }
        if (rpmSensorPath == null) { cachedRpm = -1; return cachedRpm; }
        try { String value = SysStats.readLine(rpmSensorPath); int rpm = Integer.parseInt(value); cachedRpm = rpm >= 0 && rpm < 100_000 ? rpm : -1; return cachedRpm; }
        catch (Throwable t) { rpmSensorPath = null; cachedRpm = -1; return cachedRpm; }
    }

    /** Mulai dari diam supaya terlihat spin-up bersamaan dengan suara. */
    void spinUp() { omega = 0; angle = 0; }

    @Override protected void onMeasure(int w, int h) {
        int s = MeasureSpec.getSize(w);
        if (s <= 0) s = (int) (140 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(s, s);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f; cy = h / 2f; r = Math.min(w, h) / 2f - 2f;
        float u = r / 100f;

        ring.setStrokeWidth(5f * u);
        ring.setShader(new SweepGradient(cx, cy, new int[]{0xFFFFFFFF, 0xFF5A5A62, 0xFFE8E8EE, 0xFF2A2A2E, 0xFFFFFFFF}, null));
        tick.setStrokeWidth(2.4f * u);
        face.setShader(new RadialGradient(cx, cy, r * .92f, new int[]{0xFF1B1B1F, 0xFF09090A, 0xFF000000}, new float[]{0f, .7f, 1f}, Shader.TileMode.CLAMP));
        bladeFill.setShader(new LinearGradient(cx, cy - r * .85f, cx, cy, new int[]{0xFFF4F4F6, 0xFF8E8E96, 0xFF303034}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        hub.setShader(new RadialGradient(cx - 5 * u, cy - 6 * u, 26 * u, new int[]{0xFFFFFFFF, 0xFF9A9AA2, 0xFF1C1C20}, new float[]{0f, .45f, 1f}, Shader.TileMode.CLAMP));
        glow.setStrokeWidth(9f * u);

        // satu bilah melengkung, titik awal di dekat hub, menjulur ke atas
        blade.reset();
        float r0 = 15f * u, r1 = 87f * u;
        blade.moveTo(cx - 6 * u, cy - r0);
        blade.cubicTo(cx - 38 * u, cy - r0 - 20 * u, cx - 34 * u, cy - r1 + 11 * u, cx - 5 * u, cy - r1);
        blade.cubicTo(cx + 18 * u, cy - r1 + 5 * u, cx + 28 * u, cy - r0 - 23 * u, cx + 8 * u, cy - r0);
        blade.close();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); running = true; last = 0; postOnAnimation(loop); }
    @Override protected void onDetachedFromWindow() { running = false; removeCallbacks(loop); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View v, int vis) {
        super.onVisibilityChanged(v, vis);
        boolean on = vis == VISIBLE && isAttachedToWindow();
        if (on && !running) { running = true; last = 0; postOnAnimation(loop); } else if (!on) running = false;
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            shownLoad += (targetLoad - shownLoad) * Math.min(1f, dt * 3f);
            // 120 deg/s saat idle sampai ~1500 deg/s saat beban penuh; batas supaya tidak terlihat "mundur" (efek roda)
            physicalRpm = readPhysicalRpm();
            // Semua kipas selalu berputar: RPM sensor bila ada, kalau tidak ikut FPS (kipas FPS) atau beban (CPU/GPU).
            // Putaran ini animasi visual; angka RPM fisik tetap N/A bila tidak ada sensor nyata.
            float want;
            if (physicalRpm > 0) want = physicalRpm * 6f;
            else if (fpsDrivenVisual && liveFps >= 0) want = 240f + Math.max(0, Math.min(180, liveFps)) * 5.2f;
            else want = 220f + shownLoad * 1580f;
            // Putaran visual mengikuti profil game: lebih responsif saat beban naik, melambat halus saat idle.
            want *= new float[]{0.78f, 1.0f, 1.18f, 1.42f}[profileMode];
            float rate = want > omega ? 4.4f : 2.0f;
            omega += (want - omega) * Math.min(1f, dt * rate);
            angle = (angle + omega * dt) % 360f;
            invalidate();
            postOnAnimation(this);
        }
    };

    private int heat(float l) {
        if (!Float.isNaN(temperatureC)) {
            if (temperatureC >= 44f) return 0xFFFF5252; // hot
            if (temperatureC >= 40f) return 0xFFFF9F43; // warm
            if (temperatureC >= 36f) return 0xFFFFD166; // elevated
            return 0xFF55E6A5; // cool/normal
        }
        if (profileAccent != 0) {
            if (l >= .82f) return 0xFFFF6B5E; // heat warning always overrides profile color
            if (l >= .65f) return 0xFFFFC45B;
            return profileAccent;
        }
        if (l < .55f) return 0xFFFFFFFF;
        if (l < .8f) return 0xFFFFD27A;
        return 0xFFFF6B5E;
    }

    @Override protected void onDraw(Canvas c) {
        if (r <= 0) return;
        float u = r / 100f;
        // dasar
        c.drawCircle(cx, cy, r, face);
        c.drawCircle(cx, cy, r - 2.5f * u, ring);

        // gauge: 48 tick, menyala sesuai beban
        int n = 48, lit = Math.round(shownLoad * n);
        int hc = heat(shownLoad);
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(135 + i * (270.0 / (n - 1)));
            float x0 = (float) (cx + Math.cos(a) * (r - 14 * u)), y0 = (float) (cy + Math.sin(a) * (r - 14 * u));
            float x1 = (float) (cx + Math.cos(a) * (r - 8 * u)), y1 = (float) (cy + Math.sin(a) * (r - 8 * u));
            tick.setColor(i < lit ? hc : 0x33FFFFFF);
            c.drawLine(x0, y0, x1, y1, tick);
        }

        // cahaya lembut di balik bilah saat beban tinggi
        if (shownLoad > .12f) {
            float pulse = .62f + .38f * (float) ((Math.sin(SystemClock.uptimeMillis() * .0045d) + 1d) * .5d);
            glow.setStrokeWidth((7f + 4f * pulse) * u);
            int alpha = (int) (35f + 82f * shownLoad * pulse);
            glow.setColor((hc & 0x00FFFFFF) | (alpha << 24));
            c.drawCircle(cx, cy, r * (.56f + .018f * pulse), glow);
        }

        // bilah
        c.save();
        c.rotate(angle, cx, cy);
        for (int i = 0; i < BLADES; i++) {
            c.drawPath(blade, bladeFill);
            c.drawPath(blade, bladeEdge);
            c.rotate(360f / BLADES, cx, cy);
        }
        c.restore();

        // motion blur: piringan tembus pandang makin tebal saat berputar cepat
        float spd = Math.min(1f, omega / 1750f);
        if (spd > .25f) {
            blur.setStyle(Paint.Style.FILL);
            blur.setColor(0x00FFFFFF | ((int) (46 * spd) << 24));
            c.drawCircle(cx, cy, r * .82f, blur);
        }

        // hub + label
        c.drawCircle(cx, cy, 17 * u, hub);
        txt.setColor(0xFF111113);
        if (compact) {
            txt.setTextSize(13f * u);
            c.drawText(label, cx, cy + 4.6f * u, txt);
            return;
        }
        txt.setTextSize(11f * u);
        c.drawText(label, cx, cy + 4f * u, txt);
        if (estimate) {
            txt.setColor(0x88FFFFFF); txt.setTextSize(7f * u);
            c.drawText("LOAD", cx, cy + r * .62f, txt);
        }
        txt.setColor(hc); txt.setTextSize(8f * u);
        c.drawText(physicalRpm >= 0 ? physicalRpm + " RPM" : "RPM N/A", cx, cy + r * .78f, txt);
        if (fpsDrivenVisual && liveFps >= 0 && r >= 26f) {
            txt.setColor(0xFFFFFFFF); txt.setTextSize(10f * u);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            c.drawText(liveFps + " FPS", cx, cy + r * .94f, txt);
        }
    }
}
