package com.imzyyknox.gameturbo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;

/** Activity transparan yang hanya meminta izin mikrofon sekali, lalu langsung menutup diri. */
public class MicPermissionActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { done(true); return; }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 81);
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        done(g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED);
    }
    private void done(boolean ok) { FloatingService.micResult(ok); finish(); overridePendingTransition(0, 0); }
}
