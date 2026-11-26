package com.imzyyknox.gameturbo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Network optimizer best-effort untuk aplikasi tanpa root:
 * - pengukuran TCP connect latency ke beberapa endpoint melalui jaringan aktif;
 * - Wi-Fi low-latency lock saat pengguna mengaktifkannya;
 * - pemilihan Private DNS memakai TLS handshake DNS-over-TLS, bukan sekadar tes port terbuka;
 * - status validated/metered dan info transport jaringan.
 * Aplikasi biasa tidak dapat mengatur prioritas paket atau memaksa ping game lain tanpa hak sistem/VPN.
 */
final class NetEngine {
    private NetEngine() { }

    static final String[] DNS_NAME = {"Cloudflare", "Google", "Quad9"};
    static final String[] DNS_HOST = {"one.one.one.one", "dns.google", "dns.quad9.net"};
    private static final String[] PROBE_HOST = {"1.1.1.1", "8.8.8.8", "9.9.9.9"};

    static final class Quality {
        int avg = -1, best = -1, jitter = 0, loss = 100;
        String rating = "TIDAK ADA INTERNET";
        String link = "";
        String tip = "";
        boolean validated, metered;
        String transport = "";
    }

    /** TCP connect latency dalam ms. Ini bukan ICMP ping atau latency internal server game. */
    private static int tcp(String host, int port, int timeoutMs, Network network) {
        Socket s = new Socket();
        try {
            if (network != null && Build.VERSION.SDK_INT >= 23) network.bindSocket(s);
            long t0 = SystemClock.elapsedRealtime();
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return (int) (SystemClock.elapsedRealtime() - t0);
        } catch (Throwable e) {
            return -1;
        } finally {
            try { s.close(); } catch (Throwable ignored) { }
        }
    }

    private static Network activeNetwork(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm == null ? null : cm.getActiveNetwork();
        } catch (Throwable e) { return null; }
    }

    private static boolean online(Context c, Network n, Quality q) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null || n == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null) return false;
            q.validated = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            q.metered = !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && q.validated;
        } catch (Throwable e) { return false; }
    }

    /** Blocking. Selalu panggil dari thread background. */
    static Quality measure(Context c) {
        Quality q = new Quality();
        Network network = activeNetwork(c);
        link(c, network, q);
        if (!online(c, network, q)) {
            if (!q.link.isEmpty() && !q.link.equals("Tidak terhubung")) q.tip = "Jaringan belum tervalidasi / captive portal";
            return q;
        }

        final int rounds = 5;
        int total = 0, okAll = 0, hostsOk = 0, best = Integer.MAX_VALUE;
        long avgSum = 0, jitSum = 0;
        for (String host : PROBE_HOST) {
            int ok = 0, prev = -1, fails = 0;
            long sum = 0, jitterSum = 0; int jitterCount = 0;
            for (int i = 0; i < rounds; i++) {
                total++;
                if (fails >= 2 && ok == 0) continue;
                int ms = tcp(host, 443, 1200, network);
                if (ms < 0) { fails++; continue; }
                ok++; sum += ms; best = Math.min(best, ms);
                if (prev >= 0) { jitterSum += Math.abs(ms - prev); jitterCount++; }
                prev = ms;
                try { Thread.sleep(55); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
            }
            if (ok > 0) {
                hostsOk++; okAll += ok; avgSum += sum / ok;
                jitSum += jitterCount == 0 ? 0 : jitterSum / jitterCount;
            }
        }
        if (hostsOk == 0) return q;
        q.avg = (int) (avgSum / hostsOk);
        q.best = best;
        q.jitter = (int) (jitSum / hostsOk);
        q.loss = Math.round(100f * (total - okAll) / Math.max(1, total));
        if (q.avg < 45 && q.jitter < 12 && q.loss == 0) q.rating = "SANGAT BAGUS";
        else if (q.avg < 90 && q.jitter < 25 && q.loss <= 5) q.rating = "BAGUS";
        else if (q.avg < 160 && q.loss <= 15) q.rating = "CUKUP";
        else q.rating = "BURUK";

        if (q.loss > 3) q.tip = "Ada timeout probe • cek sinyal atau kepadatan jaringan";
        else if (q.jitter > 25) q.tip = "Jitter tinggi • hentikan unduhan/streaming di latar";
        else if (q.metered) q.tip = "Data terukur aktif • pantau penggunaan kuota";
        return q;
    }

    /** Transport, validasi internet, metered, dan kualitas link dari sistem. */
    @SuppressWarnings("deprecation")
    static void link(Context c, Network n, Quality q) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = n == null || cm == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) { q.link = "Tidak terhubung"; return; }
            q.validated = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            q.metered = !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            String validation = q.validated ? "validated" : "belum tervalidasi";
            String meter = q.metered ? "metered" : "unmetered";
            int down = Math.max(0, nc.getLinkDownstreamBandwidthKbps() / 1000);
            int up = Math.max(0, nc.getLinkUpstreamBandwidthKbps() / 1000);

            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                q.transport = "Wi-Fi";
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
                int f = wi == null ? 0 : wi.getFrequency();
                int rssi = wi == null ? -127 : wi.getRssi();
                int speed = wi == null ? 0 : wi.getLinkSpeed();
                String band = f >= 5925 ? "6 GHz" : f >= 4900 ? "5 GHz" : f > 0 ? "2.4 GHz" : "Wi-Fi";
                q.link = band + " • " + rssi + " dBm • link " + speed + " Mbps • " + validation;
                if (rssi < -78) q.tip = "Sinyal Wi-Fi lemah • lebih dekat ke router";
                else if (f > 0 && f < 3000) q.tip = "Pita 2.4 GHz terdeteksi • 5 GHz dapat lebih lega bila sinyalnya kuat";
            } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                q.transport = "Seluler";
                q.link = "Data seluler • " + down + " Mbps turun / " + up + " Mbps naik (estimasi link) • " + validation;
            } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                q.transport = "Ethernet";
                q.link = "Ethernet • " + down + " Mbps turun / " + up + " Mbps naik • " + validation;
            } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                q.transport = "VPN";
                q.link = "VPN • " + validation;
            } else {
                q.transport = "Jaringan lain";
                q.link = q.transport + " • " + validation;
            }
            q.link += " • " + meter;
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && !"VPN".equals(q.transport)) q.link += " • VPN aktif";
        } catch (Throwable e) {
            q.link = "Info jaringan tidak terbaca";
        }
    }

    // ------------------------------------------------------------ Wi-Fi low-latency lock
    private static WifiManager.WifiLock lock;

    @SuppressWarnings("deprecation")
    static synchronized boolean lockOn(Context c) {
        try {
            if (lock == null) {
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm == null) return false;
                int mode = Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                lock = wm.createWifiLock(mode, "imzyy:netboost");
                lock.setReferenceCounted(false);
            }
            if (!lock.isHeld()) lock.acquire();
            return lock.isHeld();
        } catch (Throwable e) { return false; }
    }

    static synchronized void lockOff() {
        try { if (lock != null && lock.isHeld()) lock.release(); } catch (Throwable ignored) { }
    }
    static synchronized boolean lockHeld() { return lock != null && lock.isHeld(); }

    // ------------------------------------------------------------ DNS pribadi
    static boolean canSecure(Context c) { return GpuEngine.canSecure(c); }

    /** Mengukur TLS handshake ke endpoint DNS-over-TLS port 853 (bukan hanya menguji port TCP). */
    private static int dotHandshake(String host, int timeoutMs) {
        Socket tcp = new Socket();
        SSLSocket tls = null;
        try {
            long start = SystemClock.elapsedRealtime();
            tcp.connect(new InetSocketAddress(host, 853), timeoutMs);
            tcp.setSoTimeout(timeoutMs);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            tls = (SSLSocket) factory.createSocket(tcp, host, 853, true);
            tls.setSoTimeout(timeoutMs);
            SSLParameters params = tls.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(params);
            tls.startHandshake();
            return (int) (SystemClock.elapsedRealtime() - start);
        } catch (Throwable e) {
            return -1;
        } finally {
            try { if (tls != null) tls.close(); else tcp.close(); } catch (Throwable ignored) { }
        }
    }

    /** {indeks DNS_HOST, ms} tercepat berdasar median handshake TLS; null bila tidak bisa dijangkau. */
    static int[] fastestDns() {
        int bestI = -1, bestMs = Integer.MAX_VALUE;
        for (int i = 0; i < DNS_HOST.length; i++) {
            int[] samples = new int[3]; int ok = 0;
            for (int k = 0; k < samples.length; k++) {
                int ms = dotHandshake(DNS_HOST[i], 1500);
                if (ms >= 0) samples[ok++] = ms;
            }
            if (ok >= 2) {
                Arrays.sort(samples, 0, ok);
                int median = samples[ok / 2];
                if (median < bestMs) { bestMs = median; bestI = i; }
            }
        }
        return bestI < 0 ? null : new int[]{bestI, bestMs};
    }

    static boolean applyDns(Context c, String host) {
        try {
            ContentResolver cr = c.getContentResolver();
            SharedPreferences sp = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
            if (!sp.getBoolean("dns_applied", false)) {
                String pm = Settings.Global.getString(cr, "private_dns_mode");
                String ph = Settings.Global.getString(cr, "private_dns_specifier");
                sp.edit().putString("dns_prev_mode", pm == null ? "opportunistic" : pm)
                        .putString("dns_prev_host", ph == null ? "" : ph).apply();
            }
            Settings.Global.putString(cr, "private_dns_specifier", host);
            Settings.Global.putString(cr, "private_dns_mode", "hostname");
            sp.edit().putBoolean("dns_applied", true).putString("dns_host", host).apply();
            return true;
        } catch (Throwable e) { return false; }
    }

    static boolean restoreDns(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences("turbo", Context.MODE_PRIVATE);
            if (!sp.getBoolean("dns_applied", false)) return false;
            ContentResolver cr = c.getContentResolver();
            Settings.Global.putString(cr, "private_dns_specifier", sp.getString("dns_prev_host", ""));
            Settings.Global.putString(cr, "private_dns_mode", sp.getString("dns_prev_mode", "opportunistic"));
            sp.edit().putBoolean("dns_applied", false).apply();
            return true;
        } catch (Throwable e) { return false; }
    }
}
