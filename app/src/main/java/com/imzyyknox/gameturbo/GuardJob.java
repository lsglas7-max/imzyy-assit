package com.imzyyknox.gameturbo;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * Penjaga berkala yang hidup di luar panel. Dua tugas, keduanya TANPA butuh akses aksesibilitas:
 * 1) hidupkan lagi jendela mengambang (FloatingService) bila sistem/OEM mematikannya dan pengguna belum menutupnya sendiri;
 * 2) bila izin adb WRITE_SECURE_SETTINGS ada, pasang ulang IMZYY Cleaner (Aksesibilitas).
 * Jadwal periodik (15 menit) bertahan walau aplikasi di-kill dan setelah reboot. Job satu kali ({@link #revive}) dipakai
 * service/Cleaner sendiri saat terputus supaya pemulihan terjadi dalam hitungan detik.
 */
public class GuardJob extends JobService {
    private static final int ID = 4242, ID_REVIVE = 4243;

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null || js.getPendingJob(ID) != null) return;
            js.schedule(new JobInfo.Builder(ID, new ComponentName(c, GuardJob.class))
                    .setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
        } catch (Throwable ignored) { }
    }

    /** Job satu kali: pulihkan hub/Cleaner setelah delayMs. Diabaikan bila pengguna sudah menutup hub sendiri. */
    static void revive(Context c, long delayMs) {
        try {
            if (!(wantsHub(c) && antiVanish(c)) && !AccessibilityGuard.wanted(c)) return;
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            long d = Math.max(0L, delayMs);
            js.schedule(new JobInfo.Builder(ID_REVIVE, new ComponentName(c, GuardJob.class))
                    .setMinimumLatency(d).setOverrideDeadline(d + 30_000L).build());
        } catch (Throwable ignored) { }
    }

    /** Pengguna menutup hub dengan sengaja: batalkan pemulihan yang menunggu. */
    static void cancelRevive(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) js.cancel(ID_REVIVE);
        } catch (Throwable ignored) { }
    }

    /** Toggle ANTI HILANG (default nyala): bila dimatikan, penjaga tidak menyalakan hub lagi. */
    static boolean antiVanish(Context c) {
        return c.getSharedPreferences("turbo", Context.MODE_PRIVATE).getBoolean("hub_antivanish", true);
    }

    private static boolean wantsHub(Context c) {
        return c.getSharedPreferences("turbo", Context.MODE_PRIVATE).getBoolean("hub_was_on", false);
    }

    /** Hub diminta hidup tetapi service tidak berjalan: nyalakan lagi (best effort, batas background-start Android berlaku). */
    static void reviveHub(Context c) {
        try {
            if (!wantsHub(c) || !antiVanish(c) || FloatingService.alive()) return;
            if (!Settings.canDrawOverlays(c)) return;
            c.startForegroundService(new Intent(c, FloatingService.class));
        } catch (Throwable e) {
            android.util.Log.w("IMZYY-Guard", "hub revive blocked: " + e);
        }
    }

    @Override public boolean onStartJob(JobParameters p) {
        try { if (AccessibilityGuard.wanted(this)) AccessibilityGuard.ensure(this); } catch (Throwable ignored) { }
        reviveHub(this);
        return false;
    }

    @Override public boolean onStopJob(JobParameters p) { return false; }
}
