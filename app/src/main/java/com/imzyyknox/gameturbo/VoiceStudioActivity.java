package com.imzyyknox.gameturbo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.ByteArrayOutputStream;

/** Offline voice effects studio. It does not replace the microphone input of other apps. */
public class VoiceStudioActivity extends Activity {
    private static final int RATE = 22050, REQUEST_MIC = 81, REQUEST_INTERNAL_AUDIO = 82;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private TextView status;
    private AudioRecord recorder;
    private Thread recordThread;
    private boolean statusReceiverRegistered;
    private final VoiceEngine liveEngine = new VoiceEngine();
    private boolean pendingInternalMix;
    private boolean pendingLiveMic;
    private boolean pendingInternalOnly;
    private int selectedFx = 0;
    private boolean waitingForProjectionService;
    private final ServiceConnection projectionConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            ProjectionSessionService service = ((ProjectionSessionService.LocalBinder) binder).getService();
            MediaProjection projection = service.getProjection();
            String error = pendingInternalOnly
                    ? liveEngine.startLiveInternalOnly(VoiceStudioActivity.this, projection, selectedFx, 1f)
                    : liveEngine.startLiveWithInternal(VoiceStudioActivity.this, projection, selectedFx, 1f);
            boolean internalOnly = pendingInternalOnly;
            pendingInternalOnly = false;
            status.setText(error == null ? (internalOnly ? "LIVE aktif • internal audio only" : "LIVE MIX aktif • mikrofon + audio internal") + " • FX " + VoiceEngine.FX_NAME[selectedFx] : error);
            if (error != null) stopProjectionSession();
        }
        @Override public void onServiceDisconnected(ComponentName name) { status.setText("Sesi internal audio terputus"); }
    };
    private final BroadcastReceiver captureReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String s = intent.getStringExtra(InternalAudioCaptureService.EXTRA_STATUS);
            if ("SAVED".equals(s)) status.setText("Audio internal tersimpan: " + intent.getStringExtra(InternalAudioCaptureService.EXTRA_FILE));
            else if ("CAPTURING".equals(s)) status.setText("Menangkap audio internal game…");
            else if ("NO_AUDIO".equals(s)) status.setText("Tidak ada audio yang diizinkan untuk ditangkap. Coba game lain atau cek izin capture-nya.");
            else if (s != null && s.startsWith("ERROR:")) status.setText(s);
        }
    };

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable bg(int a, int b) { GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a,b}); d.setCornerRadius(dp(16)); d.setStroke(dp(1), 0x6653E9FF); return d; }
    private TextView text(String s, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t; }
    private LinearLayout.LayoutParams top(int n) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(n); return p; }
    private void action(LinearLayout parent, String label, Runnable r) { TextView b=text(label,13,0xFFDAF8FF,true); b.setGravity(Gravity.CENTER); b.setPadding(dp(12),dp(14),dp(12),dp(14)); b.setBackground(bg(0xFF12344B,0xFF101A35)); b.setOnClickListener(v->r.run()); parent.addView(b,top(9)); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setBackgroundColor(0xFF07111D);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(18),dp(22),dp(18),dp(24));
        TextView title=text("IMZYY VOICE STUDIO",22,Color.WHITE,true); root.addView(title);
        root.addView(text("VOICE FX • LIVE MIXER + OFFLINE STUDIO",10,0xFF53E9FF,true),top(4));
        TextView note=text("Rekam suara, lalu dengarkan dengan efek Female, Male, Robot, atau Child. Audio diproses di perangkat. Efek ini tidak mengganti input mikrofon pada game, panggilan, atau aplikasi lain.",12,0xFFB8C8D8,false); note.setLineSpacing(dp(3),1f); root.addView(note,top(16));
        status=text("Status: belum ada rekaman",12,0xFF55E7A9,true); root.addView(status,top(18));
        action(root,"START RECORDING",this::requestMic);
        TextView internalTitle=text("INTERNAL GAME AUDIO",11,0xFF53E9FF,true); root.addView(internalTitle,top(22));
        TextView internalNote=text("Tangkap audio playback game yang mengizinkan capture Android 10+. Android akan menampilkan dialog Screen Capture. Fitur ini merekam audio game, bukan mengambil alih mikrofon game.",12,0xFFB8C8D8,false); internalNote.setLineSpacing(dp(3),1f); root.addView(internalNote,top(6));
        action(root,"START INTERNAL AUDIO CAPTURE",this::requestInternalCapture);
        action(root,"STOP INTERNAL AUDIO CAPTURE",this::stopInternalCapture);
        TextView virtualTitle=text("VIRTUAL MICROPHONE • DEVICE SUPPORT",11,0xFF53E9FF,true); root.addView(virtualTitle,top(22));
        TextView virtualNote=text("Voice FX engine sudah dapat dipakai sebagai pemroses audio lokal. Namun Android standar tidak menyediakan API bagi aplikasi biasa untuk memasukkan hasilnya sebagai mikrofon aplikasi game lain. Penggantian input game memerlukan backend sistem/ROM atau modul root yang kompatibel; status ini tidak akan ditampilkan aktif sebelum backend nyata terpasang.",12,0xFFB8C8D8,false); virtualNote.setLineSpacing(dp(3),1f); root.addView(virtualNote,top(6));
        TextView liveTitle=text("LIVE AUDIO MIXER",11,0xFF53E9FF,true); root.addView(liveTitle,top(22));
        TextView liveNote=text("Monitor LIVE dengan efek suara. Pilih mikrofon saja, audio internal saja, atau campuran mikrofon + audio game yang mengizinkan playback capture. Efek dapat diganti saat LIVE tanpa menghentikan mesin audio. Output mixer ini dipantau di perangkat/earphone; bukan input mikrofon virtual untuk game.",12,0xFFB8C8D8,false); liveNote.setLineSpacing(dp(3),1f); root.addView(liveNote,top(6));
        action(root,"LIVE • MICROPHONE ONLY",this::startLiveMic);
        action(root,"LIVE • INTERNAL GAME AUDIO ONLY",this::requestInternalOnly);
        action(root,"LIVE • MIC + INTERNAL GAME AUDIO",this::requestInternalMix);
        action(root,"FX: NORMAL",()->changeLiveFx(0));
        action(root,"FX: FEMALE",()->changeLiveFx(1));
        action(root,"FX: MALE",()->changeLiveFx(2));
        action(root,"FX: CHILD",()->changeLiveFx(3));
        action(root,"FX: ROBOT",()->changeLiveFx(4));
        action(root,"FX: MONSTER",()->changeLiveFx(5));
        action(root,"FX: ALIEN",()->changeLiveFx(6));
        action(root,"FX: ECHO",()->changeLiveFx(7));
        action(root,"STOP LIVE MIXER",()->{ liveEngine.stopLive(); stopProjectionSession(); status.setText("LIVE mixer dihentikan"); });
        action(root,"STOP RECORDING",this::stopRecording);
        TextView sub=text("PLAYBACK VOICE PRESETS",11,0xFF53E9FF,true); root.addView(sub,top(22));
        action(root,"FEMALE VOICE",()->play(1.23f,false));
        action(root,"MALE VOICE",()->play(0.86f,false));
        action(root,"ROBOT VOICE",()->play(1.0f,true));
        action(root,"CHILD VOICE",()->play(1.38f,false));
        action(root,"STOP PLAYBACK",()->{ try { if (track!=null) { track.pause(); track.flush(); track.release(); track=null; } status.setText("Playback dihentikan"); } catch(Exception ignored){} });
        action(root,"BACK TO GAME TURBO",()->finish());
        ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll);
        IntentFilter filter = new IntentFilter(InternalAudioCaptureService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(captureReceiver, filter);
        statusReceiverRegistered = true;
    }
    private AudioTrack track;
    private void startLiveMic() {
        if (waitingForProjectionService) stopProjectionSession();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingInternalMix = false; pendingLiveMic = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        String error = liveEngine.startLive(selectedFx, 1f);
        status.setText(error == null ? "LIVE aktif • sumber mikrofon • FX " + VoiceEngine.FX_NAME[selectedFx] : error);
    }
    private void requestInternalOnly() {
        if (Build.VERSION.SDK_INT < 29) { status.setText("Internal audio membutuhkan Android 10+"); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingInternalOnly = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        pendingInternalOnly = true;
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_INTERNAL_AUDIO);
    }
    private void requestInternalMix() {
        if (Build.VERSION.SDK_INT < 29) { status.setText("LIVE mix internal membutuhkan Android 10+"); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingInternalMix = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        pendingInternalMix = true;
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_INTERNAL_AUDIO);
    }
    private void changeLiveFx(int fx) {
        selectedFx = fx;
        if (liveEngine.isLive()) liveEngine.setLive(fx, 1f);
        status.setText("FX dipilih: " + VoiceEngine.FX_NAME[fx] + (liveEngine.isLive() ? " • diterapkan saat LIVE" : " • berlaku saat LIVE dimulai"));
    }
    private void stopProjectionSession() {
        try { if (waitingForProjectionService) { unbindService(projectionConnection); waitingForProjectionService = false; } } catch (Exception ignored) { }
        stopService(new Intent(this, ProjectionSessionService.class));
    }
    private void requestInternalCapture() {
        if (Build.VERSION.SDK_INT < 29) { status.setText("Internal audio capture membutuhkan Android 10 atau lebih baru"); return; }
        MediaProjectionManager manager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_INTERNAL_AUDIO);
    }
    private void stopInternalCapture() {
        Intent i = new Intent(this, InternalAudioCaptureService.class).setAction(InternalAudioCaptureService.ACTION_STOP);
        startService(i);
        status.setText("Menghentikan internal audio capture…");
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INTERNAL_AUDIO) {
            if (resultCode != RESULT_OK || data == null) { pendingInternalMix = false; pendingInternalOnly = false; status.setText("Izin Screen Capture dibatalkan"); return; }
            if (pendingInternalMix || pendingInternalOnly) {
                pendingInternalMix = false;
                try {
                    Intent session = new Intent(this, ProjectionSessionService.class).setAction(ProjectionSessionService.ACTION_START)
                            .putExtra(InternalAudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                            .putExtra(InternalAudioCaptureService.EXTRA_RESULT_DATA, data);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(session); else startService(session);
                    waitingForProjectionService = true;
                    bindService(new Intent(this, ProjectionSessionService.class), projectionConnection, Context.BIND_AUTO_CREATE);
                } catch (Throwable e) { status.setText("LIVE mix gagal: " + e.getMessage()); }
            } else {
                Intent service = new Intent(this, InternalAudioCaptureService.class).setAction(InternalAudioCaptureService.ACTION_START)
                        .putExtra(InternalAudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                        .putExtra(InternalAudioCaptureService.EXTRA_RESULT_DATA, data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
                status.setText("Memulai internal audio capture…");
            }
        }
    }
    private void requestMic() {
        pendingInternalMix = false; pendingInternalOnly = false; pendingLiveMic = false;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC); return; }
        startRecording();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) { super.onRequestPermissionsResult(r,p,g); if(r==REQUEST_MIC && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) { if (pendingInternalMix) requestInternalMix(); else if (pendingInternalOnly) requestInternalOnly(); else if (pendingLiveMic) { pendingLiveMic = false; startLiveMic(); } else startRecording(); } else if (r==REQUEST_MIC) status.setText("Izin mikrofon tidak diberikan"); }
    private void startRecording() {
        if(recording.get()) return;
        try {
            int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min, RATE));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Microphone unavailable");
            pcm.reset(); recording.set(true); recorder.startRecording();
            recordThread=new Thread(()->{ short[] buf=new short[2048]; while(recording.get()) { int n=recorder.read(buf,0,buf.length); if(n>0) synchronized(pcm){ for(int i=0;i<n;i++){ pcm.write(buf[i]&255); pcm.write((buf[i]>>8)&255); } } } }); recordThread.start();
            status.setText("Merekam... tekan STOP RECORDING setelah selesai");
        } catch(Exception e) { recording.set(false); status.setText("Gagal membuka mikrofon: "+e.getMessage()); }
    }
    private void stopRecording() {
        if(!recording.get()) { status.setText(pcm.size()>0?"Rekaman siap diproses":"Belum ada rekaman"); return; }
        recording.set(false);
        try { if(recorder!=null){recorder.stop();recorder.release();recorder=null;} } catch(Exception ignored){}
        status.setText("Rekaman siap • "+(pcm.size()/2/RATE)+" detik");
    }
    private void play(float pitch, boolean robot) {
        if(recording.get()) stopRecording();
        byte[] raw; synchronized(pcm){raw=pcm.toByteArray();}
        if(raw.length<2048){status.setText("Rekam suara terlebih dahulu");return;}
        try {
            int count=raw.length/2; short[] in=new short[count];
            for(int i=0;i<count;i++) in[i]=(short)((raw[i*2]&255)|(raw[i*2+1]<<8));
            int outCount=Math.max(1,(int)(count/pitch)); short[] out=new short[outCount];
            for(int i=0;i<outCount;i++) {
                float src=i*pitch; int a=Math.min(count-1,(int)src), z=Math.min(count-1,a+1); float frac=src-a;
                float sample=in[a]*(1-frac)+in[z]*frac;
                if(robot) sample*=0.25f+0.75f*Math.sin(2*Math.PI*42*i/RATE);
                out[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,sample));
            }
            if(track!=null){try{track.stop();track.release();}catch(Exception ignored){}}
            int bytes=out.length*2; track=new AudioTrack(android.media.AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,bytes,AudioTrack.MODE_STATIC);
            track.write(out,0,out.length); track.play(); status.setText(robot?"Memutar efek Robot":"Memutar efek suara • pitch "+pitch);
        } catch(Exception e){status.setText("Playback gagal: "+e.getMessage());}
    }
    @Override protected void onDestroy(){ liveEngine.release(); stopProjectionSession(); if(statusReceiverRegistered){try{unregisterReceiver(captureReceiver);}catch(Exception ignored){} statusReceiverRegistered=false;} stopRecording(); if(track!=null){try{track.stop();track.release();}catch(Exception ignored){} track=null;} super.onDestroy(); }
}
