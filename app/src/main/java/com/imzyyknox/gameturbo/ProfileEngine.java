package com.imzyyknox.gameturbo;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.content.ContentResolver;
import java.util.List;

/** Applies only Android controls exposed to ordinary applications. */
final class ProfileEngine {
    private ProfileEngine() {}
    static boolean batteryBalance(Context c, boolean enabled) {
        if (!Settings.System.canWrite(c)) return false;
        SharedPreferences p = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
        ContentResolver cr = c.getContentResolver();
        try {
            if (enabled) {
                if (!p.contains("battery_saved_brightness")) p.edit()
                    .putInt("battery_saved_brightness", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
                    .putInt("battery_saved_brightness_mode", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)).apply();
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 105);
                return FpsEngine.apply(c, FpsEngine.pick(c, 60));
            } else {
                if (p.contains("battery_saved_brightness")) { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, p.getInt("battery_saved_brightness", 128));
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt("battery_saved_brightness_mode", 0));
                    p.edit().remove("battery_saved_brightness").remove("battery_saved_brightness_mode").apply(); }
                FpsEngine.reset(c); return true;
            }
        } catch (Exception e) { return false; }
    }
    static String applyGraphics(Context c, int mode) {
        List<Integer> rates = FpsEngine.rates(c); if (rates.isEmpty()) return "Mode refresh rate tidak tersedia.";
        int target;
        switch (mode) {
            case 1: target = rates.get(rates.size()-1); break;
            case 2: target = FpsEngine.pick(c, 60); break;
            case 3: target = rates.get(rates.size()-1); break;
            case 4: target = rates.get(0); break;
            default: int thermal = android.os.Build.VERSION.SDK_INT >= 29 ? ((android.os.PowerManager)c.getSystemService(Context.POWER_SERVICE)).getCurrentThermalStatus() : 0; target = thermal >= 3 ? rates.get(0) : FpsEngine.pick(c, 60);
        }
        if (!FpsEngine.apply(c, target)) return "Izin Ubah setelan sistem diperlukan untuk menerapkan refresh rate.";
        return "Refresh rate layar diterapkan: " + target + " Hz. Kualitas tekstur/shader tetap diatur dari dalam game.";
    }
}
