package com.imzyyknox.gameturbo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Dipanggil alarm {@link KeepAlive}: pasang alarm berikutnya dulu (rantai tidak boleh putus), lalu hidupkan hub bila mati. */
public class KeepAliveReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try {
            if (!KeepAlive.wanted(c)) return;                 // pengguna menutup hub / ANTI HILANG mati: rantai berhenti
            KeepAlive.arm(c, KeepAlive.nextDelay(c));
            GuardJob.schedule(c);
            if (FloatingService.alive() || !Settings.canDrawOverlays(c)) return;
            c.startForegroundService(new Intent(c, FloatingService.class));
        } catch (Throwable e) {
            android.util.Log.w("IMZYY-KeepAlive", "revive blocked: " + e);
        }
    }
}
