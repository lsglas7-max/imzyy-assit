package com.imzyyknox.gameturbo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

/** Holds the user-approved MediaProjection under the required foreground-service lifecycle. */
public final class ProjectionSessionService extends Service {
    static final String ACTION_START = "com.imzyyknox.gameturbo.PROJECTION_SESSION_START";
    static final String ACTION_STOP = "com.imzyyknox.gameturbo.PROJECTION_SESSION_STOP";
    private static final String CHANNEL = "live_projection_session";
    private static final int NOTIFICATION_ID = 8310;
    private final LocalBinder binder = new LocalBinder();
    private MediaProjection projection;
    public final class LocalBinder extends Binder { ProjectionSessionService getService() { return ProjectionSessionService.this; } }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onCreate() { super.onCreate(); NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Live audio mixer",NotificationManager.IMPORTANCE_LOW)); }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || ACTION_STOP.equals(intent.getAction())) { stopSession(); stopSelf(); return START_NOT_STICKY; }
        if(ACTION_START.equals(intent.getAction()) && projection==null) {
            try {
                Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
                Notification n=b.setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("IMZYY KNOX • Live Mixer").setContentText("Sesi audio internal aktif").setOngoing(true).build();
                if(Build.VERSION.SDK_INT>=30) startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                else if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                else startForeground(NOTIFICATION_ID,n);
                int result=intent.getIntExtra(InternalAudioCaptureService.EXTRA_RESULT_CODE,0);
                Intent data=getData(intent);
                MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
                projection=manager.getMediaProjection(result,data);
                if(projection==null) throw new IllegalStateException("MediaProjection tidak tersedia");
                projection.registerCallback(new MediaProjection.Callback(){ @Override public void onStop(){ stopSession(); stopSelf(); } },null);
            } catch(Throwable e) { stopSession(); stopSelf(); }
        }
        return START_NOT_STICKY;
    }
    private Intent getData(Intent i) { if(Build.VERSION.SDK_INT>=33) return i.getParcelableExtra(InternalAudioCaptureService.EXTRA_RESULT_DATA,Intent.class); //noinspection deprecation
        return i.getParcelableExtra(InternalAudioCaptureService.EXTRA_RESULT_DATA); }
    MediaProjection getProjection() { return projection; }
    private void stopSession() { MediaProjection old=projection; projection=null; if(old!=null){ try{old.stop();}catch(Exception ignored){} } stopForeground(STOP_FOREGROUND_REMOVE); }
    @Override public void onDestroy(){ stopSession(); super.onDestroy(); }
}
