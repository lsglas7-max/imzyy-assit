package com.imzyyknox.gameturbo;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Telemetri sistem best-effort tanpa root.
 * CPU utilization dibaca dari delta /proc/stat; GPU memakai counter busy kernel bila diekspos.
 * Jika firmware membatasi counter, UI wajib menandai nilai estimasi dengan '~'.
 */
final class SysStats {
    private SysStats() { }

    static final class Snap {
        int cores;
        long[] curKhz = new long[16], maxKhz = new long[16];
        long cpuCurMhz = -1, cpuMaxMhz = -1;
        long gpuCurMhz = -1, gpuMaxMhz = -1;
        float cpuLoad = 0f, gpuLoad = 0f;
        boolean cpuReal, gpuReal;                 // frekuensi clock berhasil dibaca
        boolean cpuUsageReal, gpuUsageReal;       // utilization asli, bukan rasio clock
        float tempC = Float.NaN;
        String tempSource = "N/A";
        float thermal = Float.NaN;
    }

    private static final String[] GPU_CUR = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/devices/platform/gpu/devfreq/gpufreq/cur_freq",
            "/sys/class/misc/mali0/device/clock",
            "/sys/module/pvrsrvkm/parameters/gpu_freq",
            "/sys/class/devfreq/gpufreq/cur_freq"
    };
    private static final String[] GPU_MAX = {
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/kernel/gpu/gpu_max_clock",
            "/sys/devices/platform/gpu/devfreq/gpufreq/max_freq",
            "/sys/class/devfreq/gpufreq/max_freq"
    };
    private static final String[] GPU_BUSY_PERCENT = {
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percent",
            "/sys/class/devfreq/gpufreq/busy_percent",
            "/sys/devices/platform/gpu/devfreq/gpufreq/busy_percent"
    };
    private static String gpuCurPath, gpuMaxPath, gpuBusyPath, gpuTotalPath, gpuBusyPercentPath;
    private static boolean gpuProbed;
    private static long prevCpuTotal = -1, prevCpuIdle = -1;
    private static long prevGpuBusy = -1, prevGpuTotal = -1;
    private static long lastThermalProbe;
    private static boolean thermalProbed;
    private static final ArrayList<String[]> THERMAL_SENSORS = new ArrayList<>();

    static String readLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path), 128)) {
            String s = r.readLine();
            return s == null ? null : s.trim();
        } catch (Throwable e) { return null; }
    }

    private static long num(String s) {
        if (s == null) return -1;
        try {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch >= '0' && ch <= '9') b.append(ch);
            }
            return b.length() == 0 ? -1 : Long.parseLong(b.toString());
        } catch (Throwable e) { return -1; }
    }

    private static long toMhz(long v) {
        if (v <= 0) return -1;
        if (v > 10_000_000L) return v / 1_000_000L;
        if (v > 10_000L) return v / 1000L;
        return v;
    }

    private static synchronized float readCpuUtilization() {
        String line = readLine("/proc/stat");
        if (line == null || !line.startsWith("cpu ")) return Float.NaN;
        try {
            StringTokenizer st = new StringTokenizer(line);
            st.nextToken();
            long total = 0, idle = 0;
            int index = 0;
            while (st.hasMoreTokens()) {
                long v = Long.parseLong(st.nextToken());
                // user/nice already include guest/guest_nice; count the first 8 Linux counters only.
                if (index < 8) total += v;
                if (index == 3 || index == 4) idle += v; // idle + iowait
                index++;
            }
            if (prevCpuTotal >= 0 && total > prevCpuTotal) {
                long dt = total - prevCpuTotal;
                long di = Math.max(0, idle - prevCpuIdle);
                prevCpuTotal = total; prevCpuIdle = idle;
                return Math.max(0f, Math.min(1f, (dt - di) / (float) dt));
            }
            prevCpuTotal = total; prevCpuIdle = idle;
        } catch (Throwable ignored) { }
        return Float.NaN;
    }

    private static void probeGpu() {
        if (gpuProbed) return;
        gpuProbed = true;
        for (String p : GPU_CUR) if (num(readLine(p)) > 0) { gpuCurPath = p; break; }
        for (String p : GPU_MAX) if (num(readLine(p)) > 0) { gpuMaxPath = p; break; }
        for (String p : GPU_BUSY_PERCENT) if (num(readLine(p)) >= 0) { gpuBusyPercentPath = p; break; }
        if (gpuCurPath == null || gpuBusyPercentPath == null) {
            File[] dirs = new File("/sys/class/devfreq").listFiles();
            if (dirs != null) for (File f : dirs) {
                String n = f.getName().toLowerCase(Locale.US);
                if (!n.contains("gpu") && !n.contains("mali") && !n.contains("kgsl")) continue;
                String base = f.getPath();
                if (gpuCurPath == null && num(readLine(base + "/cur_freq")) > 0) gpuCurPath = base + "/cur_freq";
                if (gpuMaxPath == null && num(readLine(base + "/max_freq")) > 0) gpuMaxPath = base + "/max_freq";
                if (num(readLine(base + "/busy_time")) >= 0 && num(readLine(base + "/total_time")) >= 0) {
                    gpuBusyPath = base + "/busy_time";
                    gpuTotalPath = base + "/total_time";
                }
            }
        }
        if (gpuBusyPercentPath == null && num(readLine("/sys/class/kgsl/kgsl-3d0/gpubusy")) >= 0) {
            gpuBusyPath = "/sys/class/kgsl/kgsl-3d0/gpubusy";
        }
    }

    private static synchronized float readGpuUtilization() {
        if (gpuBusyPercentPath != null) {
            long p = num(readLine(gpuBusyPercentPath));
            if (p >= 0 && p <= 100) return p / 100f;
        }
        if (gpuBusyPath != null && gpuTotalPath != null) {
            long busy = num(readLine(gpuBusyPath)), total = num(readLine(gpuTotalPath));
            if (busy >= 0 && total > 0) {
                if (prevGpuBusy >= 0 && total > prevGpuTotal) {
                    long db = Math.max(0, busy - prevGpuBusy), dt = total - prevGpuTotal;
                    prevGpuBusy = busy; prevGpuTotal = total;
                    return Math.max(0f, Math.min(1f, db / (float) dt));
                }
                prevGpuBusy = busy; prevGpuTotal = total;
            }
        } else if (gpuBusyPath != null) {
            String raw = readLine(gpuBusyPath);
            if (raw != null) {
                StringTokenizer st = new StringTokenizer(raw);
                if (st.countTokens() >= 2) {
                    try {
                        long busy = Long.parseLong(st.nextToken());
                        long total = Long.parseLong(st.nextToken());
                        if (prevGpuBusy >= 0 && total > prevGpuTotal) {
                            long db = Math.max(0, busy - prevGpuBusy), dt = total - prevGpuTotal;
                            prevGpuBusy = busy; prevGpuTotal = total;
                            return Math.max(0f, Math.min(1f, db / (float) dt));
                        }
                        prevGpuBusy = busy; prevGpuTotal = total;
                    } catch (Throwable ignored) { }
                }
            }
        }
        return Float.NaN;
    }

    private static synchronized void probeThermalSensors() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (thermalProbed && now - lastThermalProbe < 60_000L) return;
        thermalProbed = true;
        lastThermalProbe = now;
        THERMAL_SENSORS.clear();
        File root = new File("/sys/class/thermal");
        File[] zones = root.listFiles();
        if (zones == null) return;
        for (File z : zones) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            String type = readLine(z.getPath() + "/type");
            String path = z.getPath() + "/temp";
            if (type == null || !new File(path).canRead()) continue;
            String low = type.toLowerCase(Locale.US);
            if (low.contains("battery")) continue;
            if (low.contains("cpu") || low.contains("soc") || low.contains("gpu") || low.contains("ap")
                    || low.contains("tmu") || low.contains("therm") || low.contains("skin")) {
                THERMAL_SENSORS.add(new String[]{path, type});
            }
        }
    }

    private static float thermalValue(long raw) {
        if (raw <= 0) return Float.NaN;
        float v = raw >= 10_000 ? raw / 1000f : raw >= 1_000 ? raw / 100f : raw;
        return v >= -20f && v <= 125f ? v : Float.NaN;
    }

    private static void readTemperature(Context c, Snap s) {
        probeThermalSensors();
        float hottest = Float.NaN;
        String source = "";
        for (String[] sensor : THERMAL_SENSORS) {
            float value = thermalValue(num(readLine(sensor[0])));
            if (!Float.isNaN(value) && (Float.isNaN(hottest) || value > hottest)) {
                hottest = value;
                source = sensor[1].toUpperCase(Locale.US);
            }
        }
        if (!Float.isNaN(hottest)) {
            s.tempC = hottest; s.tempSource = source;
            return;
        }
        try {
            Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int raw = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (raw > 0) { s.tempC = raw / 10f; s.tempSource = "BATT"; }
        } catch (Throwable ignored) { }
    }

    static Snap read(Context c) {
        Snap s = new Snap();
        int n = Math.min(16, Runtime.getRuntime().availableProcessors());
        s.cores = n;
        long topCur = -1, topMax = -1; int ok = 0;
        for (int i = 0; i < n; i++) {
            long cur = num(readLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq"));
            long max = num(readLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq"));
            s.curKhz[i] = cur; s.maxKhz[i] = max;
            if (cur > 0) ok++;
            if (cur > topCur) topCur = cur;
            if (max > topMax) topMax = max;
        }
        if (ok > 0 && topMax > 0) {
            s.cpuReal = true;
            s.cpuCurMhz = topCur / 1000; s.cpuMaxMhz = topMax / 1000;
        }

        float cpu = readCpuUtilization();
        if (!Float.isNaN(cpu)) { s.cpuLoad = cpu; s.cpuUsageReal = true; }
        else if (s.cpuReal && s.cpuMaxMhz > 0) s.cpuLoad = Math.max(0f, Math.min(1f, s.cpuCurMhz / (float) s.cpuMaxMhz));
        else s.cpuLoad = 0.12f;

        probeGpu();
        long gc = gpuCurPath == null ? -1 : toMhz(num(readLine(gpuCurPath)));
        long gm = gpuMaxPath == null ? -1 : toMhz(num(readLine(gpuMaxPath)));
        if (gc > 0) {
            s.gpuReal = true; s.gpuCurMhz = gc; s.gpuMaxMhz = gm > 0 ? gm : -1;
        }
        float gpu = readGpuUtilization();
        if (!Float.isNaN(gpu)) { s.gpuLoad = gpu; s.gpuUsageReal = true; }
        else if (gc > 0 && gm > 0) s.gpuLoad = Math.max(0f, Math.min(1f, gc / (float) gm));
        else s.gpuLoad = 0.10f;

        s.thermal = thermal(c);
        readTemperature(c, s);
        return s;
    }

    /** Thermal headroom 0..1 (1 = mendekati batas panas), bila API ini didukung firmware. */
    static float thermal(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                float h = pm.getThermalHeadroom(0);
                return h;
            }
        } catch (Throwable ignored) { }
        return Float.NaN;
    }

    static String ghz(long mhz) {
        if (mhz <= 0) return "--";
        return mhz >= 1000 ? String.format(Locale.US, "%.2f GHz", mhz / 1000f) : mhz + " MHz";
    }
}
