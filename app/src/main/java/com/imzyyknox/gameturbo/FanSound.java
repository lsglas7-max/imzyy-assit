package com.imzyyknox.gameturbo;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/**
 * Suara kipas spin-up: "Bum... bum... stu-tu-tu-tu-wiiiii".
 * Dibuat sendiri lewat sintesis (tanpa file audio, tanpa izin). Tidak meminta audio focus
 * sehingga suara game tidak terputus; volume kecil dan bisa dimatikan lewat preferensi "fan_sound".
 */
final class FanSound {
    private FanSound() { }

    private static final int SR = 22050;
    private static short[] cache;
    private static AudioTrack cur;
    private static final Handler H = new Handler(Looper.getMainLooper());

    private static synchronized short[] build() {
        if (cache != null) return cache;
        final double dur = 2.3;
        int n = (int) (SR * dur);
        float[] x = new float[n];
        double kp1 = 0, kp2 = 0, wp = 0, lp = 0;
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR, s = 0;
            // BUM 1 dan BUM 2: sapuan sinus 135 -> 45 Hz dengan peluruhan cepat
            if (t < 0.5) {
                double f = 45 + 90 * Math.exp(-t * 26);
                kp1 += 2 * Math.PI * f / SR;
                s += Math.sin(kp1) * Math.exp(-t * 11) * 0.95;
            }
            double t2 = t - 0.30;
            if (t2 >= 0 && t2 < 0.5) {
                double f = 42 + 80 * Math.exp(-t2 * 26);
                kp2 += 2 * Math.PI * f / SR;
                s += Math.sin(kp2) * Math.exp(-t2 * 11) * 0.8;
            }
            // STU-TU-TU... lalu menyatu jadi desing turbin yang naik
            double w0 = 0.62;
            if (t >= w0) {
                double u = Math.min(1.0, (t - w0) / (dur - w0));
                double f = 70 + 520 * Math.pow(u, 1.6);
                wp += 2 * Math.PI * f / SR;
                double tone = Math.sin(wp) + 0.5 * Math.sin(2 * wp) + 0.28 * Math.sin(3 * wp);
                lp += 0.18 * (rnd.nextDouble() * 2 - 1 - lp);           // noise tapis rendah
                double rate = 7 + 12 * u;                              // laju chop naik
                double gate = 0.5 + 0.5 * Math.sin(2 * Math.PI * rate * t);
                gate = Math.pow(gate, 1.6);
                double depth = Math.max(0.0, 1.0 - 1.25 * u);          // makin lama makin halus
                double mod = (1 - depth) + depth * gate;
                double env = Math.min(1.0, (t - w0) / 0.05) * Math.min(1.0, (dur - t) / 0.35);
                s += (tone * 0.30 + lp * 0.9) * mod * env * (0.55 + 0.45 * u);
            }
            x[i] = (float) s;
        }
        float peak = 0.001f;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) (Math.tanh(x[i] / peak * 1.25) * 0.85 * Short.MAX_VALUE);
        cache = out;
        return out;
    }

    /** Putar sekali di thread terpisah. Aman dipanggil berulang; yang lama dihentikan dulu. */
    static void play(final float volume) {
        new Thread(() -> {
            try {
                final short[] pcm = build();
                final AudioTrack t = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC).build();
                t.write(pcm, 0, pcm.length);
                t.setVolume(Math.max(0f, Math.min(1f, volume)));
                synchronized (FanSound.class) { stop(); cur = t; }
                t.play();
                H.postDelayed(() -> { synchronized (FanSound.class) { if (cur == t) { stop(); } else { try { t.release(); } catch (Throwable ignored) { } } } }, (long) (pcm.length * 1000L / SR) + 400);
            } catch (Throwable e) {
                android.util.Log.w("IMZYY-Fan", "fan sound failed", e);
            }
        }, "fan-sound").start();
    }

    static synchronized void stop() {
        try { if (cur != null) { cur.stop(); cur.release(); } } catch (Throwable ignored) { }
        cur = null;
    }
    // ======================================================================================
    // MESIN: suara mesin V8 sintetis yang berputar terus selama dinyalakan (stream real-time).
    // Putaran (RPM) mengikuti beban CPU/GPU: makin berat game, makin meraung.
    // Tanpa file audio dan tanpa izin; tidak meminta audio focus, jadi suara game tidak terputus.
    // ======================================================================================
    private static volatile boolean engRun;
    private static volatile int engGen;
    private static volatile float engLoad = 0f, engVol = 0.5f;
    private static Thread engThread;

    static boolean engineOn() { return engRun; }
    static void setEngineLoad(float l) { engLoad = Math.max(0f, Math.min(1f, l)); }
    static void setEngineVolume(float v) { engVol = Math.max(0f, Math.min(1f, v)); }

    static synchronized void startEngine(float volume) {
        engVol = Math.max(0f, Math.min(1f, volume));
        if (engRun && engThread != null && engThread.isAlive()) return;
        engRun = true;
        final int g = ++engGen;
        engThread = new Thread(() -> engineLoop(g), "fan-engine");
        engThread.start();
    }

    static synchronized void stopEngine() { engRun = false; }

    private static void engineLoop(final int g) {
        AudioTrack t = null;
        try {
            int min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            final int chunk = 1024;
            t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(min, chunk * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            t.play();
            final short[] buf = new short[chunk];
            final java.util.Random rnd = new java.util.Random(11);
            final double dt = 1.0 / SR;
            double rpm = 0, ph = 0, phCrank = 0, phStart = 0, lpN = 0, lpOut = 0, gain = 0, load = 0, tt = 0;
            while (true) {
                final boolean want = engRun && g == engGen;
                if (!want && gain <= 0.0005) break;
                final double vol = engVol;
                for (int i = 0; i < chunk; i++) {
                    tt += dt;
                    // beban dihaluskan supaya RPM naik cepat dan turun pelan seperti mesin sungguhan
                    double lt = engLoad;
                    load += (lt - load) * (lt > load ? 3.0 : 1.2) * dt;
                    double target, crank = 0;
                    if (tt < 0.85) { target = 230; crank = 1; }                 // starter: "ngik-ngik-ngik"
                    else if (tt < 1.30) target = 1750;                           // mesin nyala, sentakan gas
                    else target = 850 + 5950 * load;                             // idle -> RPM tinggi
                    rpm += (target - rpm) * (target > rpm ? 7.0 : 2.4) * dt;
                    double fFire = rpm / 60.0 * 4.0;                             // V8: 4 letusan per putaran
                    ph += 2 * Math.PI * fFire * dt;
                    phCrank += 2 * Math.PI * (rpm / 120.0) * dt;                 // denyut siklus 4-tak
                    double ln = Math.min(1.0, Math.max(0.0, (rpm - 850) / 5950.0));
                    double tone = Math.sin(ph) + 0.62 * Math.sin(2 * ph + 0.6) + 0.46 * Math.sin(3 * ph + 1.1)
                            + 0.30 * Math.sin(4 * ph) + 0.18 * Math.sin(6 * ph + 0.4)
                            + 0.55 * Math.sin(0.5 * ph);                          // sub-harmonik = karakter V8 "brubrubru"
                    double lump = 1.0 + (0.38 - 0.22 * ln) * Math.sin(phCrank);    // idle lebih bergelombang
                    lpN += 0.22 * ((rnd.nextDouble() * 2 - 1) - lpN);
                    double noise = lpN * (0.10 + 0.55 * ln);                       // desis intake/knalpot
                    double x = (tone * 0.34 * lump + noise) * (0.45 + 0.55 * ln);
                    if (crank > 0) {                                               // sentakan starter
                        phStart += 2 * Math.PI * 4.5 * dt;
                        double chug = Math.pow(0.5 + 0.5 * Math.sin(phStart * 2), 2.0);
                        x = x * (0.35 + 0.65 * chug) + Math.sin(2 * Math.PI * 330 * tt) * 0.06;
                    }
                    x = Math.tanh(x * (1.5 + 1.8 * ln));                           // saturasi = raungan
                    double a = 0.07 + 0.50 * ln;                                   // low-pass: teredam saat idle
                    lpOut += a * (x - lpOut);
                    gain += ((want ? 1.0 : 0.0) - gain) * (want ? 6.0 : 14.0) * dt;
                    buf[i] = (short) (lpOut * gain * vol * 0.9 * Short.MAX_VALUE);
                }
                if (t.write(buf, 0, chunk) < 0) break;
            }
        } catch (Throwable e) {
            android.util.Log.w("IMZYY-Fan", "engine sound failed", e);
        } finally {
            synchronized (FanSound.class) { if (g == engGen) engRun = false; }
            try { if (t != null) { t.stop(); t.release(); } } catch (Throwable ignored) { }
        }
    }
}
