package com.imzyyknox.gameturbo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures only playback streams that the source app allows Android to capture. */
public final class InternalAudioCaptureService extends Service {
    public static final String ACTION_START = "com.imzyyknox.gameturbo.INTERNAL_AUDIO_START";
    public static final String ACTION_STOP = "com.imzyyknox.gameturbo.INTERNAL_AUDIO_STOP";
    public static final String EXTRA_RESULT_CODE = "projection_result_code";
    public static final String EXTRA_RESULT_DATA = "projection_result_data";
    public static final String ACTION_STATUS = "com.imzyyknox.gameturbo.INTERNAL_AUDIO_STATUS";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_FILE = "file";
    private static final String CHANNEL = "internal_audio_capture";
    private static final int NOTIFICATION_ID = 8304;
    private static final int RATE = 44100;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private MediaProjection projection;
    private AudioRecord recorder;
    private Thread worker;
    private File output;

    @Override public void onCreate() { super.onCreate(); createChannel(); }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_STOP.equals(intent.getAction())) { stopCapture(); stopSelf(); return START_NOT_STICKY; }
        if (ACTION_START.equals(intent.getAction()) && !running.get()) {
            try {
                Notification n = notification("Menyiapkan internal audio capture…");
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                else startForeground(NOTIFICATION_ID, n);
                int result = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
                Intent data = getResultData(intent);
                if (data == null) throw new IllegalArgumentException("Izin Screen Capture tidak tersedia");
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = manager.getMediaProjection(result, data);
                if (projection == null) throw new IllegalStateException("MediaProjection gagal dibuat");
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() { stopCapture(); stopSelf(); }
                }, null);
                beginCapture();
            } catch (Throwable e) { broadcast("ERROR: " + e.getMessage(), null); stopSelf(); }
        }
        return START_NOT_STICKY;
    }

    private Intent getResultData(Intent source) {
        if (Build.VERSION.SDK_INT >= 33) return source.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        //noinspection deprecation
        return source.getParcelableExtra(EXTRA_RESULT_DATA);
    }

    private void beginCapture() throws Exception {
        if (Build.VERSION.SDK_INT < 29) throw new UnsupportedOperationException("Internal audio capture membutuhkan Android 10 atau lebih baru");
        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        recorder = new AudioRecord.Builder().setAudioFormat(format)
                .setBufferSizeInBytes(Math.max(min, RATE * 2)).setAudioPlaybackCaptureConfig(config).build();
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Audio capture tidak didukung perangkat");
        File musicDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
        if (musicDir == null) musicDir = getFilesDir();
        if (!musicDir.exists() && !musicDir.mkdirs()) throw new IllegalStateException("Folder penyimpanan audio gagal dibuat");
        output = new File(musicDir, "IMZYY-InternalAudio-" + System.currentTimeMillis() + ".wav");
        running.set(true);
        recorder.startRecording();
        updateNotification("Merekam audio internal yang diizinkan aplikasi sumber");
        worker = new Thread(this::captureLoop, "imzyy-internal-audio");
        worker.start();
        broadcast("CAPTURING", null);
    }

    private void captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        long dataBytes = 0;
        try (RandomAccessFile wav = new RandomAccessFile(output, "rw")) {
            wav.setLength(0); writeWavHeader(wav, 0);
            short[] buffer = new short[2048];
            while (running.get()) {
                int count = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (count < 0) throw new IllegalStateException("AudioRecord read error: " + count);
                if (count == 0) continue;
                for (int i = 0; i < count; i++) { short s = buffer[i]; wav.write(s & 255); wav.write((s >>> 8) & 255); }
                dataBytes += count * 2L;
            }
            wav.seek(0); writeWavHeader(wav, dataBytes);
        } catch (Throwable e) {
            if (running.get()) broadcast("ERROR: " + e.getMessage(), null);
        } finally {
            running.set(false);
            if (dataBytes > 0 && output != null) broadcast("SAVED", output.getAbsolutePath());
            else if (output != null) { // no eligible playback stream was captured
                // Keep no empty recording artifact.
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                broadcast("NO_AUDIO", null);
            }
            stopRecorder();
            if (projection != null) { try { projection.stop(); } catch (Exception ignored) { } projection = null; }
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private static void writeWavHeader(RandomAccessFile f, long dataLength) throws Exception {
        long total = dataLength + 36;
        f.writeBytes("RIFF"); writeLE(f, total, 4); f.writeBytes("WAVEfmt "); writeLE(f, 16, 4);
        writeLE(f, 1, 2); writeLE(f, 2, 2); writeLE(f, RATE, 4); writeLE(f, RATE * 4L, 4);
        writeLE(f, 4, 2); writeLE(f, 16, 2); f.writeBytes("data"); writeLE(f, dataLength, 4);
    }
    private static void writeLE(RandomAccessFile f, long v, int bytes) throws Exception { for (int i=0;i<bytes;i++) f.write((int)(v >>> (8*i)) & 255); }

    private void stopCapture() { running.set(false); stopRecorder(); }
    private void stopRecorder() { try { if (recorder != null) recorder.stop(); } catch (Exception ignored) { }
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) { } recorder = null; }
    private void createChannel() { if (Build.VERSION.SDK_INT >= 26) {
        NotificationChannel c = new NotificationChannel(CHANNEL, "Internal audio capture", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
    } }
    private Notification notification(String message) {
        Intent stop = new Intent(this, InternalAudioCaptureService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 8305, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("IMZYY KNOX • Audio Capture")
                .setContentText(message).setOngoing(true).addAction(android.R.drawable.ic_media_pause, "Stop", pi).build();
    }
    private void updateNotification(String text) { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification(text)); }
    private void broadcast(String status, String file) { Intent i = new Intent(ACTION_STATUS).setPackage(getPackageName()).putExtra(EXTRA_STATUS, status); if(file!=null)i.putExtra(EXTRA_FILE,file); sendBroadcast(i); }
    @Override public void onDestroy() { stopCapture(); if (projection != null) { try { projection.stop(); } catch(Exception ignored){} projection=null; } super.onDestroy(); }
}
