# IMZYY KNOX Game Turbo v10.0

## Developer
- Pembuat Game Turbo Samsung
- Credi By Imzyy
- WhatsApp: https://wa.me/6281373070063

## Audio Studio v8.6
- LIVE microphone monitor with voice FX.
- LIVE mixer: microphone + permitted Android internal playback capture (Android 10+ / MediaProjection consent).
- Switch voice FX presets during LIVE without restarting the processing thread.
- Internal capture is optional and depends on the source app's playback-capture policy.
- Output is local monitoring to the device audio route/headset. It is not a virtual microphone injected into a game.

## Virtual microphone limitation
Stock Android's public SDK does not expose a general API for an ordinary app to replace another app's microphone input. Real in-game virtual-mic routing requires a separately engineered, device/OS-specific privileged backend such as a compatible rooted audio module or custom ROM/audio HAL integration. See `VIRTUAL_MIC_STATUS.md`.

## v10.0 — Live Monitor, Fan UI, dan Network

- Home diringkas dengan pintasan `Monitor live` dan `Jendela mengambang`.
- Monitor overlay menampilkan VSYNC/frame callback monitor, CPU, GPU, suhu, dan refresh rate layar; panel bisa digeser dan posisi tersimpan. Ketuk panel untuk berganti antara mode angka dan mode kipas.
- CPU usage dihitung dari delta `/proc/stat`; GPU usage memakai busy counter kernel bila tersedia. Nilai dengan awalan `~` adalah estimasi clock-ratio saat counter beban tidak diekspos oleh firmware.
- Suhu mencoba thermal zone SoC/CPU/GPU yang diekspos kernel, lalu fallback ke sensor baterai. Sumber suhu ditampilkan agar tidak disalahartikan sebagai suhu CPU bila hanya sensor baterai yang tersedia.
- Kipas visual diperbesar dan animasinya memakai akselerasi, perubahan kecepatan, serta pulse glow. RPM fisik hanya ditampilkan bila sensor sungguhan tersedia; animasi bukan kipas fisik.
- Network check mengikat pengukuran TCP ke network aktif, memvalidasi koneksi Internet, memberi ringkasan Wi-Fi/seluler, dan menguji DNS-over-TLS dengan handshake TLS. Ini bukan pengubah prioritas paket atau ping internal server game.
- Jendela aplikasi menggunakan freeform launch bounds. Keberhasilannya tetap bergantung dukungan firmware Android/OEM dan opsi pengembang perangkat.

### Batas pengukuran Android biasa

FPS game lain tidak selalu tersedia untuk aplikasi biasa. Angka VSYNC/frame callback di monitor adalah cadence overlay/display, bukan jaminan FPS render game. FPS render game membutuhkan API/integrasi dari game atau akses sistem yang tidak tersedia pada semua perangkat. Begitu juga busy counter GPU dan sensor thermal: bila firmware tidak mengeksposnya, aplikasi menandai nilai clock-ratio sebagai estimasi dan memakai fallback suhu baterai.

## Build APK
Run `./gradlew assembleDebug` with JDK 17 and a compatible Android SDK, or use the GitHub Actions workflow `Build IMZYY KNOX Game Turbo APK`.

## v8.8 — Floating Window Stability

- Floating hub starts as a `SPECIAL_USE` foreground service on Android 14+ instead of requesting microphone foreground access merely because microphone permission was previously granted.
- Microphone foreground access is promoted only when the user starts recording or LIVE voice features.
- Removed the delayed `AlarmManager` restart from `onTaskRemoved`; Android background-start restrictions could otherwise cause a foreground-service restart loop on newer versions.
- The floating hub remains configured with `stopWithTask=false` and `START_STICKY` for normal lifecycle recovery.

Note: Android/OEM battery management can still stop background services; users may need to allow unrestricted battery use for the app.


## v8.8 Floating Window stability
- Keeps the launcher activity foreground until the overlay attaches instead of immediately backgrounding it.
- Rebuilds a detached bubble view rather than silently retrying a possibly-invalid WindowManager view.
- Logs overlay attach and foreground-promotion failures for diagnosis.
- Restores a previously open panel after a detach when overlay permission is still granted.


## v8.9

### IMZYY Cleaner tetap hidup
- `AccessibilityGuard`: memantau daftar aksesibilitas sistem dan menyalakan ulang Cleaner seketika bila hilang (butuh izin adb sekali, permanen):
  `adb shell pm grant com.imzyyknox.gameturbo android.permission.WRITE_SECURE_SETTINGS`
- `BootReceiver`: Cleaner dan hub dinyalakan lagi setelah reboot atau update app.
- Tanpa izin adb: notifikasi "IMZYY Cleaner mati" yang langsung membuka halaman detail layanan (Android 14+).
- Android 13+ APK non-Play Store: Info Aplikasi > titik tiga > "Izinkan setelan terbatas", lalu atur baterai ke "Tanpa batasan" dan matikan "Pause app activity if unused".
- Layanan tidak memproses event sama sekali, hanya bekerja saat diminta, jadi tidak ada kode yang bisa crash di jalur event.

### Izin sekali, fitur tinggal toggle
- Toggle yang butuh izin membuka halamannya sekali; begitu izin diberikan, fitur menyala otomatis (Battery Balance, Touch, Bright, DND, Cleaner). Izin yang sudah ada tidak diminta lagi.

### RAM dan cache Android 14 sampai 17
- `RamManager`: killBackgroundProcesses hanya berlaku untuk proses sendiri sejak Android 14, jadi jalurnya Force Stop lewat Cleaner untuk app yang dipakai 45 menit terakhir (bukan app depan, launcher, keyboard, sistem, atau yang sudah berhenti), lalu kembali ke game. Jumlah maksimal diatur di TOOLS (4/8/12).
- `CacheEngine`: dua putaran pelepasan cache sistem, ditambah sapu cache eksternal dan `.thumbnails`. Pemindaian cache semua app berjalan paralel.

### Tab FAN
- Dua kipas besar CPU dan GPU dengan putaran sesuai beban, gauge, bar per inti, dan frekuensi live bila kernel mengizinkan. Bila diblokir firmware, ditandai EST.
- Suara kipas "bum bum stututu" disintesis di kode (tanpa file audio) dan diputar saat hub dibuka.

### Floating window
- Mode TOMBOL atau SIDEBAR, ukuran kecil/sedang/besar, menempel ke tepi, mengikuti rotasi layar.
- Zona aman gestur: jarak dari tepi back-gesture, di luar status bar dan area home-gesture, plus `setSystemGestureExclusionRects`.


## v9.0

### Perbaikan bug
- Tombol OPTIMASI GAME tidak lagi membuka halaman "Ubah pengaturan sistem" berulang-ulang. Penyebab: bila sistem menolak kunci refresh rate padahal izin sudah aktif, kode salah menganggap izin belum ada. Sekarang halaman izin hanya dibuka bila izin memang belum aktif; bila sistem menolak, fitur memakai mode refresh per jendela dan status menampilkan alasannya.
- `AccessibilityGuard`: memakai string action `android.settings.ACCESSIBILITY_DETAILS_SETTINGS` karena konstanta `Settings.ACTION_ACCESSIBILITY_DETAIL_SETTINGS` tidak ada di SDK publik (penyebab build gagal).

### Tab FAN
- SUARA MESIN: suara mesin V8 disintesis real-time di kode (bukan rekaman, tanpa file audio). Berputar terus selama dinyalakan, juga saat panel ditutup. Urutan: starter, mesin menyala, idle, lalu RPM naik-turun mengikuti beban CPU/GPU. Volume PELAN/SEDANG/KERAS.
- FAN OPTIMIZER: saat dinyalakan langsung optimasi sekali (GAME SAJA = optimasi game, SEMUA APP = optimalkan ponsel), lalu otomatis trim RAM bila beban >72% atau RAM bebas <18% (jeda minimal 2,5 menit). Mode SEMUA APP juga membersihkan cache semua aplikasi tiap 10 menit.
- TES MESIN memutar mesin 4,5 detik; BUM BUM memutar suara spin-up lama; SUARA SAAT BUKA tetap ada.

### Floating panel
- Menu bawah jadi satu baris rapi berukuran sama, bisa digeser, dengan urutan logis: HOME, FPS, GPU, NET, FAN, CACHE, JENDELA, TOOLS, STYLE, VOICE. Menu aktif otomatis tergulir ke tengah.


## v9.1

### Touch boost berfungsi (berlapis, semua nyata)
Sebelumnya boost mati total bila key sentuhan Samsung tidak ada dan izin adb belum diberikan. Sekarang ada empat lapis, tiap lapis dibaca ulang setelah ditulis dan dilaporkan di panel:
1. Key "Touch sensitivity" One UI (dicari sendiri; hanya key bernilai 0/1 yang disentuh).
2. Refresh rate dikunci ke maksimum layar (sampling sentuh Samsung mengikuti laju layar). Cuma butuh "Ubah setelan sistem", jadi ini lapis yang jalan tanpa adb.
3. Sentuh-tahan dipercepat (izin adb sekali).
4. Animasi sistem 0.5x (izin adb sekali).
Semua nilai asli disimpan dan dikembalikan saat dimatikan.

### Aksesibilitas "tidak tersedia"
Itu pembatasan Android 13+ untuk APK instal manual ("setelan terbatas"), bukan kerusakan aplikasi. Layar baru `AccessSetupActivity` menuntun langkahnya (Aksesibilitas, Info Aplikasi > titik tiga > Izinkan setelan terbatas, nyalakan lagi), memantau status langsung dan menutup sendiri begitu Cleaner aktif. Juga dibuka dari ikon pengaturan layanan di halaman Aksesibilitas. Jalur permanen: izin adb `WRITE_SECURE_SETTINGS` sekali.

### Menu berbaris ala Game Space (gaya One UI)
- Panel mengambang: menu utama berupa daftar baris (ikon, judul, keterangan, saklar atau panah) dalam grup Boost cepat, Saklar, Performa, Aplikasi, Alat. Ketuk panah di header untuk kembali ke menu. Navigasi 10 tab di bawah dihapus.
- Aplikasi utama: bagian Game Booster dan Alat juga berupa daftar berbaris.
- Voice Studio dan Pusat cache sebelumnya tidak terhubung ke menu mana pun; sekarang bisa dibuka dari panel dan aplikasi.
- Saklar "Layar tetap menyala" di aplikasi utama langsung berlaku di panel yang sedang berjalan.

## Latest update: physical fan telemetry + PM helper
- Mini fan no longer fabricates RPM. It probes available hardware fan sensor paths and only displays/spins from measured RPM; absent sensor is shown as unavailable.
- FPS page adds a copy-PM-grant button for applicable runtime permissions (notification and microphone), with notes that it is not universal elevated access.
- See `BREVENT_PM_GUIDE.md` for limits and instructions.

## UI refresh (floating panel)
- Dashboard now has four gaming profiles: Prioritas Ketahanan Battery, Standart, Balanced, and ImzzBoosted.
- Profile accent colors and fan animation response follow the selected profile; thermal warning colors override profile accents.
- Main panel quick actions use compact icon tiles and short labels. Cache-cleaning progress is shown inside the floating panel and fades back to SIAP (no Android notification toast).
- Battery page is simplified to a battery/temperature monitor, four profiles, and core toggles.
