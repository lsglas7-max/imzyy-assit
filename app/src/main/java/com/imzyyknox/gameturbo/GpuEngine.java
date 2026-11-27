package com.imzyyknox.gameturbo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.provider.Settings;

/** Info GPU asli (EGL) dan pengaturan yang memang bisa diubah aplikasi biasa. */
final class GpuEngine {
    private static String[] cached;

    /** {renderer, vendor, versi OpenGL ES}. Panggil dari thread background. */
    static synchronized String[] info() {
        if (cached != null) return cached;
        String[] out = {"GPU tidak terbaca", "-", "-"};
        EGLDisplay d = null; EGLContext ctx = null; EGLSurface sf = null;
        try {
            d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] v = new int[2];
            EGL14.eglInitialize(d, v, 0, v, 1);
            int[] attr = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
            EGLConfig[] cfg = new EGLConfig[1]; int[] n = new int[1];
            EGL14.eglChooseConfig(d, attr, 0, cfg, 0, 1, n, 0);
            ctx = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            sf = EGL14.eglCreatePbufferSurface(d, cfg[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            EGL14.eglMakeCurrent(d, sf, sf, ctx);
            out[0] = String.valueOf(GLES20.glGetString(GLES20.GL_RENDERER));
            out[1] = String.valueOf(GLES20.glGetString(GLES20.GL_VENDOR));
            out[2] = String.valueOf(GLES20.glGetString(GLES20.GL_VERSION));
        } catch (Throwable ignored) {
        } finally {
            try {
                if (d != null) {
                    EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (sf != null) EGL14.eglDestroySurface(d, sf);
                    if (ctx != null) EGL14.eglDestroyContext(d, ctx);
                }
            } catch (Throwable ignored) { }
        }
        cached = out;
        return out;
    }

    static boolean canSecure(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    /** Skala animasi sistem (window, transisi, animator). Butuh WRITE_SECURE_SETTINGS (sekali lewat adb). */
    static boolean setAnim(Context c, float scale) {
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, scale);
            Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, scale);
            Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, scale);
            return true;
        } catch (Throwable e) { return false; }
    }
}
