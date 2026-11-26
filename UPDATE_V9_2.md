# Update v9.2 (versionCode 30): guard hub, kipas berputar semua, kipas CPU/GPU menyatu di header

## Guard jendela mengambang (tanpa akses aksesibilitas)
- Hub tidak lagi bergantung pada Aksesibilitas sama sekali. Penjaga berjalan lewat `GuardJob` (JobScheduler), bukan lewat Cleaner.
- `GuardJob` periodik (15 menit, bertahan setelah reboot) sekarang selalu dijadwalkan saat hub hidup dan saat boot,
  dan menyalakan lagi `FloatingService` bila hub diminta hidup tetapi service mati.
- Job pemulihan satu kali (`GuardJob.revive`) dijadwalkan saat: service dimatikan sistem (`onDestroy` tanpa penutupan oleh pengguna),
  aplikasi di-swipe dari recents (`onTaskRemoved`), gagal naik ke foreground, dan Cleaner terlepas.
- Layar menyala / dibuka kunci langsung memicu pemeriksaan panel & bubble (tidak menunggu siklus 2,5 detik).
- Tombol X sekarang butuh dua ketukan (3 detik) supaya tidak tertutup tanpa sengaja. Hanya X (atau aksi STOP) yang menutup permanen
  dan membatalkan semua pemulihan.
- Bila WRITE_SECURE_SETTINGS (adb) ada, Cleaner dipasang ulang dalam beberapa detik setelah terlepas.

## Kipas
- Semua kipas berputar terus: kipas FPS mengikuti FPS, kipas CPU dan GPU mengikuti beban masing-masing
  (sebelumnya kipas CPU/GPU diam karena tidak punya sumber animasi). RPM fisik tetap `N/A` bila tidak ada sensor nyata.
- Kipas CPU | FPS | GPU kini menyatu dalam satu strip di header (di bawah SIAP), tampil di semua halaman dan diperbarui terus
  selama panel terbuka. Halaman FAN tinggal inti CPU, suara mesin, dan Fan Optimizer.

## Batas yang jujur
- Android tidak mengizinkan aplikasi biasa menolak "Paksa berhenti", mencabut izin overlay, atau melawan pembunuhan proses
  oleh firmware tanpa batas. Penjaga ini membuat hub hidup lagi secepat yang diizinkan sistem; di Android 12+ start foreground
  dari background bisa ditolak, jadi pemulihan bersifat best effort.
- Agar paling awet di Samsung: matikan optimasi baterai untuk aplikasi ini dan keluarkan dari "Aplikasi tidur".
- Belum di-build di sini (tidak ada jaringan untuk mengunduh Gradle). Build lewat GitHub Actions seperti biasa.
