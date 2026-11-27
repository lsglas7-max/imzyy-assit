package com.imzyyknox.gameturbo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageManager;

/**
 * Activity transparan: memicu dialog resmi Android "bersihkan cache semua aplikasi" (butuh Akses semua file),
 * lalu IMZYY Cleaner menekan tombol konfirmasinya otomatis. Tidak ada halaman Info Aplikasi yang dibuka.
 * Hasilnya dilaporkan balik ke floating window.
 */
public class CacheClearActivity extends Activity {
    private static final int REQ = 7;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            startActivityForResult(new Intent(StorageManager.ACTION_CLEAR_APP_CACHE), REQ);
            if (CleanerService.enabled()) CleanerService.watchSystemDialog(10000);
        } catch (Exception e) {
            done(-2);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        done(resultCode);
    }

    private void done(int code) {
        FloatingService.cacheDialogResult(code);
        finish();
        overridePendingTransition(0, 0);
    }
}
