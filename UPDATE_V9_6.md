# Update v9.6 (versionCode 34)

## Hub lebih sulit mati
- Aplikasi di-swipe dari Recents: `onTaskRemoved` memasang alarm tepat waktu 1,5 detik (boleh start foreground service dari background). Bila Samsung ikut membunuh proses, hub hidup lagi sekitar 1,5 detik kemudian. Job pemulihan 4 detik tetap ada sebagai lapis kedua.
- Detak alarm penjaga dirapatkan: layar menyala 25 detik (sebelumnya 40), layar mati 3 menit (sebelumnya 5).
- IMZYY Cleaner (Aksesibilitas) yang diikat ulang sistem setelah proses mati kini ikut menghidupkan hub, jalur pemulihan yang tidak bergantung alarm. Aktifkan JAGA CLEANER TETAP AKTIF.
- Tetap berlaku dari v9.2 sampai v9.4: foreground service, watchdog overlay, GuardJob periodik, crash hook, boot receiver.
- Batas yang tidak bisa dilawan aplikasi biasa: Paksa berhenti dari pengguna atau Brevent (menghapus semua alarm/job) dan uninstall.

## Voice changer
- Pilih MIC (mic HP, headset kabel, USB, Bluetooth) dan OUT (speaker, headset, USB, Bluetooth) dengan satu ketukan; langsung berlaku saat LIVE berjalan. Perangkat yang tercabut kembali ke AUTO dengan pesan.
- Mic Bluetooth membuka jalur komunikasi (SCO); butuh izin BLUETOOTH_CONNECT (pm grant ada di BREVENT_PM_GUIDE.md).
- Efek baru: TELEPON, RADIO, ANONIM, DEEP (total 12).
- NOISE GATE pada masukan dan pembatas lembut pada keluaran (suara game tidak pecah).
- Batas jujur: suara hasil efek keluar ke perangkat OUT. Menggantikan mic game secara langsung tetap tidak bisa tanpa root atau perangkat audio eksternal yang menyambungkan keluaran ke masukan.

Belum di-build di sini (tidak ada Android SDK/jaringan). Build lewat GitHub Actions seperti biasa.
