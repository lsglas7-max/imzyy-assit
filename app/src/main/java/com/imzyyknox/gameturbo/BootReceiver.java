package com.imzyyknox.gameturbo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/** Setelah reboot atau update aplikasi: nyalakan lagi IMZYY Cleaner (bila izin adb ada) dan hidupkan hub bila tadi aktif. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try {
            String a = i == null ? null : i.getAction();
            if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
            GuardJob.schedule(c);   // penjaga hub selalu dijadwalkan, tidak bergantung pada aksesibilitas
            if (KeepAlive.wanted(c)) KeepAlive.arm(c, 30_000L);   // detak alarm: hub hidup lagi walau prosesnya dibunuh
            if (AccessibilityGuard.wanted(c)) AccessibilityGuard.ensure(c);
            SharedPreferences sp = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
            if (sp.getBoolean("hub_autostart", true) && sp.getBoolean("hub_was_on", false) && Settings.canDrawOverlays(c)) {
                // SPECIAL_USE foreground service boleh dimulai dari BOOT_COMPLETED.
                c.startForegroundService(new Intent(c, FloatingService.class));
            }
        } catch (Throwable e) {
            android.util.Log.e("IMZYY-Boot", "boot restore failed", e);
        }
    }
}
