# Update v9.3 (versionCode 31): auto start mesin, mulai ulang mesin, toggle anti hilang

Menu baru "AUTO START & ANTI HILANG" ada di tab TOOLS dan di tab FAN.

- AUTO START MESIN (default nyala): tiap hub hidup (reboot, restart sistem, pemulihan penjaga) mesin yang tadi aktif
  (suara mesin, Fan Optimizer, Wi-Fi low-latency) menyala lagi sendiri dan dicatat di log. Dimatikan = mesin tidak dilanjutkan
  setelah hub restart (toggle mesinnya ikut kembali ke mati supaya tampilan jujur).
- ANTI HILANG (default nyala): watchdog overlay dipercepat 2,5 s -> 1,2 s; selain memasang ulang bubble/panel yang terlepas,
  kini juga menampilkan bubble yang tersembunyi tanpa sebab, membawanya kembali ke layar bila keluar batas, dan memulihkan
  panel yang macet transparan. Penjaga GuardJob hanya menghidupkan hub lagi bila toggle ini nyala.
- MULAI ULANG MESIN: tombol sekali ketuk, tanpa menutup hub: suara mesin, optimizer, Wi-Fi lock, pembaca CPU/GPU, sampler FPS,
  dan cek overlay dimulai ulang.
- Crash hook: bila aplikasi crash, job pemulihan dijadwalkan sebelum proses mati (tetap tunduk pada ANTI HILANG dan tombol X).

Batas: sama seperti v9.2, "Paksa berhenti" dari pengguna atau pencabutan izin overlay tidak bisa dilawan aplikasi biasa,
dan start foreground dari background di Android 12+ bisa ditolak sistem (best effort). Belum di-build di sini.
