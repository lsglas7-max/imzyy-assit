# Update v9.5 (versionCode 33)

## 1. Kunci refresh global ditolak (IllegalArgumentException)
- `FpsEngine.apply` sekarang punya dua jalur, hasilnya dibaca ulang, sukses bila salah satu terverifikasi:
  1. Settings.System `peak_refresh_rate` / `min_refresh_rate`. Tiap key dicoba varian Float, Int, lalu String; IAE tidak lagi menghentikan proses.
  2. Samsung `refresh_rate_mode` (Settings.Secure, 1 = standar, 2 = tinggi). Dipakai bila WRITE_SECURE_SETTINGS sudah di-grant (Brevent/adb) dan key ada di firmware.
- Bila `canWrite` mati tapi WRITE_SECURE_SETTINGS ada, aplikasi tidak lagi memaksa buka halaman "Ubah setelan sistem".
- Pesan gagal kini menyebut jalur dan penyebab (mis. `System[peak IAE ...] • Samsung[tidak ada key]`), supaya jelas jalur mana yang ditolak.
- `reset` ikut memulihkan nilai `refresh_rate_mode` awal.

## 2. Clear cache satu per satu di floating window
- Tap ikon sapu = bersihkan cache HANYA app itu. Cleaner menekan "Clear cache" di Setelan (bukan membuka app yang dibersihkan).
- Cleaner dinyalakan otomatis lewat WRITE_SECURE_SETTINGS bila belum aktif.
- Layar ditutup tirai sebentar (switch TIRAI SAAT PER APP), game dibuka lagi setelah selesai.
- Selesai: cache app dibaca ulang dari sistem dan angka di daftar langsung berubah (mis. 48 MB menjadi 0 MB ✓ bersih), total di atas ikut turun.
- Tahan ikon sapu = lepas cache semua app secara senyap (cara lama).

## 3. Sidebar ala Game Tools Samsung
- Mode SIDEBAR menempel paling pojok (switch NEMPEL PALING POJOK), sudut luar rata dengan tepi layar.
- Satu ketukan membuka panel berisi semua menu (switch KETUK = SEMUA MENU), meluncur dari sisi sidebar, sejajar dengan handle.
- Panel di mode sidebar lebih tinggi dan menempel di sisi yang sama; digeser ke sisi lain, sidebar ikut pindah sisi saat ditutup.
- Handle sedikit lebih besar supaya mudah diketuk; toleransi geser diperlebar agar ketukan tidak terbaca sebagai usap.

## Belum diuji di HP
Belum di-build di sini (tidak ada Android SDK/jaringan). Build lewat GitHub Actions seperti biasa.
