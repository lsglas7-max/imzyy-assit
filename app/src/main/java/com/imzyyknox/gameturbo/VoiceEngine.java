package com.imzyyknox.gameturbo;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.AudioPlaybackCaptureConfiguration;
import android.os.Build;
import android.os.Process;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mesin voice changer. Dua mode:
 * - LIVE: mikrofon -> efek real-time -> keluaran audio (earphone / Bluetooth / speaker). Jeda sekitar 30 ms.
 * - REKAM: rekam lalu putar ulang dengan efek (durasi tetap, bukan sekadar mempercepat/memperlambat).
 * Efek memakai pitch shifter delay-line dua kepala (durasi tidak berubah), ring modulator, dan echo.
 * Android tidak mengizinkan aplikasi biasa menggantikan input mikrofon aplikasi lain (game / telepon).
 */
final class VoiceEngine {
    static final int RATE = 22050;       // rekam & putar ulang
    static final int LIVE_RATE = 44100;  // live: 44100 Hz dijamin didukung semua perangkat

    static final String[] FX_NAME = {"NORMAL", "FEMALE", "MALE", "CHILD", "ROBOT", "MONSTER", "ALIEN", "ECHO", "TELEPON", "RADIO", "ANONIM", "DEEP"};
    private static final float[] FX_PITCH = {1f, 1.26f, .79f, 1.5f, 1f, .63f, 1.18f, 1f, 1f, 1f, .84f, .68f};
    private static final float[] FX_RING = {0f, 0f, 0f, 0f, 50f, 0f, 95f, 0f, 0f, 0f, 0f, 0f};
    private static final boolean[] FX_ECHO = {false, false, false, false, false, true, false, true, false, false, false, false};
    // Penyaring pita (Hz, 0 = mati) dan distorsi lembut, untuk efek TELEPON / RADIO / ANONIM.
    private static final float[] FX_HP = {0, 0, 0, 0, 0, 0, 0, 0, 300f, 420f, 180f, 0};
    private static final float[] FX_LP = {0, 0, 0, 0, 0, 0, 0, 0, 3400f, 2600f, 4800f, 0};
    private static final float[] FX_DRIVE = {0, 0, 0, 0, 0, 0, 0, 0, 0, 3f, 0, 0};

    /** Pengolah efek per sampel. Satu instance dipakai satu aliran audio. */
    static final class Fx {
        private final int rate, dl, n;
        private final float[] buf, echo;
        private int w, ei;
        private float d1;
        private double ph;
        volatile float pitch = 1f, ringHz = 0f, drive = 0f;
        volatile boolean echoOn = false;
        /** Gerbang bising pada masukan: mic yang diam tidak ikut membawa desis ke game. */
        volatile boolean gate = true;
        private volatile float aHp = 0f, aLp = 0f;
        private float lpS, hpS, env, gateG = 1f;
        private static float coef(float hz, int rate) { return hz <= 0f ? 0f : 1f - (float) Math.exp(-2.0 * Math.PI * hz / rate); }

        Fx(int rate) {
            this.rate = rate;
            this.dl = Math.round(rate * 0.045f);   // jendela crossfade ~45 ms
            this.n = dl * 4 + 8;
            this.buf = new float[n];
            this.echo = new float[Math.round(rate * 0.22f) + 1];
        }

        /** fx di luar 0..7 berarti pitch bebas (custom). */
        void set(int fx, float custom) {
            if (fx < 0 || fx >= FX_NAME.length) { pitch = custom; ringHz = 0f; echoOn = false; aHp = 0f; aLp = 0f; drive = 0f; }
            else {
                pitch = FX_PITCH[fx]; ringHz = FX_RING[fx]; echoOn = FX_ECHO[fx];
                aHp = coef(FX_HP[fx], rate); aLp = coef(FX_LP[fx], rate); drive = FX_DRIVE[fx];
            }
        }

        private float read(float delay) {
            float pos = w - delay;
            if (pos < 0) pos += n;
            int i0 = (int) pos;
            if (i0 >= n) i0 = n - 1;
            float fr = pos - i0;
            int i1 = i0 + 1;
            if (i1 >= n) i1 = 0;
            return buf[i0] * (1f - fr) + buf[i1] * fr;
        }

        float process(float x) {
            if (gate) {
                float a = Math.abs(x);
                env = a > env ? a : env * 0.9995f;
                float tgt = env > 0.008f ? 1f : 0f;
                gateG += (tgt - gateG) * (tgt > gateG ? 0.02f : 0.0008f);
                x *= gateG;
            }
            buf[w] = x;
            float p = pitch, out;
            if (Math.abs(p - 1f) < 0.005f) {
                out = x;
            } else {
                float d2 = d1 + dl * 0.5f;
                if (d2 >= dl) d2 -= dl;
                float s = (float) Math.sin(Math.PI * d1 / dl);
                float g1 = s * s, g2 = 1f - g1;
                out = g1 * read(d1) + g2 * read(d2);
                d1 += 1f - p;
                if (d1 >= dl) d1 -= dl; else if (d1 < 0) d1 += dl;
            }
            w++;
            if (w >= n) w = 0;
            float rh = ringHz;
            if (rh > 0f) {
                ph += 2.0 * Math.PI * rh / rate;
                if (ph > 2.0 * Math.PI) ph -= 2.0 * Math.PI;
                out *= 0.3f + 0.7f * (float) Math.sin(ph);
            }
            float hp = aHp, lp = aLp;
            if (hp > 0f) { hpS += hp * (out - hpS); out -= hpS; }          // buang bass: HP satu kutub
            if (lp > 0f) { lpS += lp * (out - lpS); out = lpS; }           // buang treble: LP satu kutub
            if (drive > 0f) out = (float) Math.tanh(out * drive) / (float) Math.tanh(drive);
            if (echoOn) {
                float e = echo[ei];
                float y = out + e * 0.45f;
                echo[ei] = out + e * 0.38f;
                ei++;
                if (ei >= echo.length) ei = 0;
                out = y;
            }
            // Pembatas lembut di atas 0,8 supaya suara game tidak pecah.
            float aa = Math.abs(out);
            if (aa > 0.8f) out = Math.signum(out) * (0.8f + 0.2f * (float) Math.tanh((aa - 0.8f) / 0.2f));
            return out;
        }
    }

    private static short toShort(float y) {
        if (y > 1f) y = 1f; else if (y < -1f) y = -1f;
        return (short) (y * 32767f);
    }

    // ------------------------------------------------------------ rekam & putar ulang
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private AudioRecord recorder;
    private AudioTrack track;

    boolean isRecording() { return recording.get(); }
    boolean hasAudio() { synchronized (pcm) { return pcm.size() >= 2048; } }
    int seconds() { synchronized (pcm) { return pcm.size() / 2 / RATE; } }

    /** Mengembalikan null kalau berhasil, atau pesan error. */
    String start() {
        if (recording.get()) return null;
        if (live.get()) stopLive();
        try {
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) { recorder.release(); recorder = null; return "Mikrofon tidak tersedia"; }
            synchronized (pcm) { pcm.reset(); }
            recording.set(true);
            final AudioRecord r = recorder;
            r.startRecording();
            new Thread(() -> {
                short[] buf = new short[2048];
                while (recording.get()) {
                    int n = r.read(buf, 0, buf.length);
                    if (n > 0) synchronized (pcm) { for (int i = 0; i < n; i++) { pcm.write(buf[i] & 255); pcm.write((buf[i] >> 8) & 255); } }
                }
            }).start();
            return null;
        } catch (Exception e) { recording.set(false); return "Gagal membuka mikrofon: " + e.getMessage(); }
    }

    void stop() {
        recording.set(false);
        try { if (recorder != null) { recorder.stop(); recorder.release(); } } catch (Exception ignored) { }
        recorder = null;
    }

    void stopPlay() {
        try { if (track != null) { track.pause(); track.flush(); track.release(); } } catch (Exception ignored) { }
        track = null;
    }

    /** Putar rekaman dengan efek fxIdx (0..7, selain itu pakai pitch custom). Null bila berhasil, atau pesan error. */
    String play(int fxIdx, float customPitch) {
        if (recording.get()) stop();
        byte[] raw; synchronized (pcm) { raw = pcm.toByteArray(); }
        if (raw.length < 2048) return "Rekam suara dulu";
        try {
            int count = raw.length / 2;
            Fx f = new Fx(RATE);
            f.set(fxIdx, customPitch);
            int extra = f.echoOn ? RATE / 2 : 0;
            short[] out = new short[count + extra];
            for (int i = 0; i < out.length; i++) {
                float x = 0f;
                if (i < count) x = (short) ((raw[i * 2] & 255) | (raw[i * 2 + 1] << 8)) / 32768f;
                out[i] = toShort(f.process(x));
            }
            stopPlay();
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, out.length * 2, AudioTrack.MODE_STATIC);
            track.write(out, 0, out.length);
            track.play();
            return null;
        } catch (Exception e) { return "Playback gagal: " + e.getMessage(); }
    }

    // ------------------------------------------------------------ LIVE
    private final AtomicBoolean live = new AtomicBoolean(false);
    private volatile Fx liveFx;
    private volatile float liveGain = 1.0f;
    private Thread liveThread;
    private AudioRecord liveRec;
    private AudioTrack liveTrack;
    private AudioRecord liveInternal;
    private MediaProjection liveProjection;
    private volatile float liveInputMicGain = -1f;

    boolean isLive() { return live.get(); }
    /** Catatan routing terakhir (mis. mic eksternal tidak terdeteksi); null bila mulus. */
    volatile String routeNote;

    // ------------------------------------------------------------ audio eksternal: pilih masukan / keluaran
    private volatile int inId = 0, outId = 0;     // 0 = otomatis
    private volatile boolean gateOn = true;
    private Context appCtx;
    private boolean commRouted;

    /** Dipanggil sebelum startLive: masukan mic, keluaran, dan gerbang bising. id 0 = otomatis. */
    void configure(Context c, int inputId, int outputId, boolean gate) {
        appCtx = c.getApplicationContext(); inId = inputId; outId = outputId; gateOn = gate;
        Fx f = liveFx; if (f != null) f.gate = gate;
        if (live.get()) applyRoutes();
    }

    private static boolean isUsable(AudioDeviceInfo d, boolean input) {
        int t = d.getType();
        if (input) return t == 15 || t == 3 || t == 11 || t == 22 || t == 7 || t == 26;       // MIC HP, headset kabel, USB, Bluetooth SCO/BLE
        return t == 2 || t == 1 || t == 3 || t == 4 || t == 11 || t == 22 || t == 7 || t == 8 || t == 26 || t == 27 || t == 23;
    }

    static java.util.List<AudioDeviceInfo> devices(Context c, boolean input) {
        java.util.ArrayList<AudioDeviceInfo> out = new java.util.ArrayList<>();
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo d : am.getDevices(input ? AudioManager.GET_DEVICES_INPUTS : AudioManager.GET_DEVICES_OUTPUTS))
                if (isUsable(d, input)) out.add(d);
        } catch (Throwable ignored) { }
        return out;
    }

    static String label(AudioDeviceInfo d) {
        String k;
        switch (d.getType()) {
            case 15: k = "MIC HP"; break;
            case 2: k = "SPEAKER"; break;
            case 1: k = "EARPIECE"; break;
            case 3: k = "HEADSET"; break;
            case 4: k = "HEADPHONE"; break;
            case 11: case 22: k = "USB"; break;
            case 7: k = "BLUETOOTH"; break;
            case 8: k = "BT A2DP"; break;
            case 26: case 27: k = "BLE"; break;
            default: k = "EXTERNAL";
        }
        CharSequence n = d.getProductName();
        return n == null || n.length() == 0 ? k : k + " • " + n;
    }

    static AudioDeviceInfo find(Context c, boolean input, int id) {
        if (id == 0) return null;
        for (AudioDeviceInfo d : devices(c, input)) if (d.getId() == id) return d;
        return null;
    }

    /** Terapkan pilihan masukan/keluaran ke aliran LIVE yang sedang jalan (atau yang akan dibuat). Return pesan bila ada masalah. */
    String applyRoutes() {
        String msg = null;
        Context c = appCtx;
        if (c == null) return null;
        try {
            AudioDeviceInfo in = find(c, true, inId);
            AudioDeviceInfo out = find(c, false, outId);
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            // Headset Bluetooth baru dipakai sebagai mic bila jalur komunikasi (SCO) dibuka.
            if (in != null && (in.getType() == 7 || in.getType() == 26)) {
                try {
                    if (Build.VERSION.SDK_INT >= 31) am.setCommunicationDevice(in);
                    else { am.startBluetoothSco(); am.setBluetoothScoOn(true); }
                    commRouted = true;
                } catch (SecurityException e) { msg = "Bluetooth butuh izin Perangkat sekitar (BLUETOOTH_CONNECT)"; }
            } else releaseComm(am);
            AudioRecord r = liveRec; AudioTrack t = liveTrack;
            if (r != null) { boolean ok = r.setPreferredDevice(in); if (in != null && !ok && msg == null) msg = "Mic eksternal tidak diterima sistem, dipakai mic HP"; }
            if (t != null) { boolean ok = t.setPreferredDevice(out); if (out != null && !ok && msg == null) msg = "Keluaran pilihan tidak diterima sistem"; }
            if (in == null && inId != 0 && msg == null) msg = "Mic eksternal tidak terdeteksi, dipakai mic HP";
        } catch (Throwable e) { msg = "Routing audio gagal: " + e.getClass().getSimpleName(); }
        return msg;
    }

    private void releaseComm(AudioManager am) {
        if (!commRouted) return;
        commRouted = false;
        try {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
            else { am.setBluetoothScoOn(false); am.stopBluetoothSco(); }
        } catch (Throwable ignored) { }
    }

    /** Ganti efek saat LIVE sedang jalan (langsung terdengar). */
    void setLive(int fxIdx, float customPitch) {
        Fx f = liveFx;
        if (f != null) f.set(fxIdx, customPitch);
    }

    void setGain(float g) { liveGain = Math.max(0.2f, Math.min(2.5f, g)); }

    /** true bila ada earphone kabel/Bluetooth/USB. Tanpa itu, LIVE lewat speaker bisa berdengung. */
    static boolean hasHeadset(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET || t == AudioDeviceInfo.TYPE_USB_DEVICE) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** Mulai LIVE. Mengembalikan null bila berhasil, atau pesan error. */
    String startLive(int fxIdx, float customPitch) {
        return startLiveInternal(null, null, fxIdx, customPitch, 1f);
    }

    /** LIVE mixer: microphone + permitted Android playback capture, monitored locally. */
    String startLiveWithInternal(Context context, MediaProjection projection, int fxIdx, float customPitch) {
        if (Build.VERSION.SDK_INT < 29) return "Mix audio internal membutuhkan Android 10+";
        if (context == null || projection == null) return "Izin internal audio belum tersedia";
        return startLiveInternal(context, projection, fxIdx, customPitch, 0.70f);
    }

    String startLiveInternalOnly(Context context, MediaProjection projection, int fxIdx, float customPitch) {
        if (Build.VERSION.SDK_INT < 29) return "Internal audio membutuhkan Android 10+";
        if (context == null || projection == null) return "Izin internal audio belum tersedia";
        return startLiveInternal(context, projection, fxIdx, customPitch, 0f);
    }

    private String startLiveInternal(Context context, MediaProjection projection, int fxIdx, float customPitch, float micMixGain) {
        if (live.get()) {
            boolean sameSource = (projection == null && liveInternal == null) ||
                    (projection != null && liveInternal != null && Math.abs(liveInputMicGain - micMixGain) < 0.001f);
            if (sameSource) { setLive(fxIdx, customPitch); return null; }
            stopLive();
        }
        if (recording.get()) stop();
        stopPlay();
        try {
            // Lepas resource lama jika thread audio sebelumnya berhenti karena error.
            releaseLive();
            final int chunk = 512; // sekitar 11,6 ms per blok pada 44,1 kHz
            int rmin = AudioRecord.getMinBufferSize(LIVE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int rbuf = Math.max(rmin, chunk * 8);
            AudioRecord candidate = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    LIVE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, rbuf);
            if (candidate.getState() != AudioRecord.STATE_INITIALIZED) {
                try { candidate.release(); } catch (Exception ignored) { }
                candidate = new AudioRecord(MediaRecorder.AudioSource.MIC, LIVE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, rbuf);
            }
            final AudioRecord r = candidate;
            AudioRecord internalCandidate = null;
            if (projection != null) {
                AudioPlaybackCaptureConfiguration capture = new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
                AudioFormat internalFormat = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(LIVE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
                int imin = AudioRecord.getMinBufferSize(LIVE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                internalCandidate = new AudioRecord.Builder().setAudioFormat(internalFormat)
                        .setBufferSizeInBytes(Math.max(imin, chunk * 16)).setAudioPlaybackCaptureConfig(capture).build();
                if (internalCandidate.getState() != AudioRecord.STATE_INITIALIZED) {
                    internalCandidate.release(); r.release(); return "Internal audio capture tidak didukung";
                }
            }
            final AudioRecord ir = internalCandidate;
            if (r.getState() != AudioRecord.STATE_INITIALIZED) { r.release(); return "Mikrofon tidak tersedia (dipakai aplikasi lain?)"; }
            int tmin = AudioTrack.getMinBufferSize(LIVE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            final AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(LIVE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(tmin, chunk * 8))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build();
            final Fx f = new Fx(LIVE_RATE);
            f.set(fxIdx, customPitch);
            f.gate = gateOn;
            liveFx = f; liveRec = r; liveTrack = t; liveInternal = ir; liveProjection = projection; liveInputMicGain = micMixGain;
            routeNote = applyRoutes();
            live.set(true);
            r.startRecording();
            if (ir != null) ir.startRecording();
            t.play();
            liveThread = new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                short[] in = new short[chunk], out = new short[chunk], internalPcm = ir == null ? null : new short[chunk * 2];
                try {
                    while (live.get()) {
                        int got = r.read(in, 0, chunk, AudioRecord.READ_BLOCKING);
                        if (got < 0) break;
                        if (got == 0) continue;
                        int internalGot = 0;
                        if (ir != null) {
                            int read = ir.read(internalPcm, 0, internalPcm.length, AudioRecord.READ_NON_BLOCKING);
                            if (read > 0) internalGot = read / 2;
                        }
                        float g = liveGain;
                        for (int i = 0; i < got; i++) {
                            float mic = in[i] / 32768f;
                            float game = i < internalGot ? ((internalPcm[i * 2] + internalPcm[i * 2 + 1]) / 65536f) : 0f;
                            float mixed = Math.max(-1f, Math.min(1f, mic * micMixGain + game * 0.70f));
                            out[i] = toShort(f.process(mixed) * g);
                        }
                        int written = t.write(out, 0, got, AudioTrack.WRITE_BLOCKING);
                        if (written < 0) break;
                    }
                } catch (Throwable ignored) {
                } finally {
                    // Jangan biarkan UI menampilkan LIVE aktif ketika driver audio berhenti.
                    live.set(false);
                }
            }, "imzyy-live");
            liveThread.start();
            return null;
        } catch (Throwable e) {
            live.set(false);
            releaseLive();
            return "LIVE gagal: " + e.getMessage();
        }
    }

    void stopLive() {
        live.set(false);
        Thread th = liveThread;
        liveThread = null;
        if (th != null) { try { th.join(400); } catch (InterruptedException ignored) { } }
        releaseLive();
    }

    private void releaseLive() {
        liveFx = null;
        try { if (liveInternal != null) liveInternal.stop(); } catch (Exception ignored) { }
        try { if (liveInternal != null) liveInternal.release(); } catch (Exception ignored) { }
        liveInternal = null;
        liveInputMicGain = -1f;
        if (liveProjection != null) { try { liveProjection.stop(); } catch (Exception ignored) { } liveProjection = null; }
        try { if (liveRec != null) liveRec.stop(); } catch (Exception ignored) { }
        try { if (liveRec != null) liveRec.release(); } catch (Exception ignored) { }
        try { if (liveTrack != null) { liveTrack.pause(); liveTrack.flush(); } } catch (Exception ignored) { }
        try { if (liveTrack != null) liveTrack.release(); } catch (Exception ignored) { }
        liveRec = null;
        liveTrack = null;
        try { if (appCtx != null) releaseComm((AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE)); } catch (Throwable ignored) { }
    }

    void release() { stop(); stopPlay(); stopLive(); }
}
