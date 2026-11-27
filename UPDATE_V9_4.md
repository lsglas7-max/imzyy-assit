# Update v9.4 (versionCode 32): hub dijaga permanen berlapis + diagnosa kenapa mati

Keluhan: floating window mati tiap beberapa menit lalu hidup lagi, berulang.

## Lapisan penjaga baru
1. **Alarm penjaga (KeepAlive)**: alarm tepat waktu (USE_EXACT_ALARM) tiap 40 detik saat layar menyala, 5 menit saat layar mati.
   Alarm disimpan sistem, bukan proses aplikasi, jadi tetap hidup walau proses hub dibunuh, lalu menghidupkan hub lagi.
   Start foreground-service dari alarm tepat waktu diizinkan Android 12+. Rantai ikut aktif lagi setelah reboot.
   Berhenti hanya bila tombol X (dua ketukan) ditekan atau toggle ANTI HILANG dimatikan.
2. Sebelumnya (v9.2/v9.3) tetap ada: service foreground, watchdog overlay 1,2 detik, GuardJob periodik + job pemulihan, crash hook.
3. Notifikasi hub tidak punya tombol STOP lagi (mudah tersenggol dan mematikan hub); diganti BUKA. Notifikasi `ongoing`.

## Diagnosa (supaya tahu penyebab aslinya)
- Menu TOOLS > PERMANEN & KESEHATAN: status nyata tiap penghalang (tanpa batasan baterai, alarm tepat waktu, latar belakang
  tidak dibatasi, prioritas siaga, notifikasi) lengkap dengan tombol perbaiki, plus tombol APLIKASI TIDUR (Samsung).
- RIWAYAT MATI: 5 kematian proses terakhir dengan penyebab dari sistem (RAM habis, crash, dibunuh sistem/OEM, dihentikan
  pengguna, dibekukan, dst.) dan isi crash terakhir bila ada. Android 11+.
- Tiap hub hidup lagi setelah mati, penyebab kematian terbarunya otomatis dicatat di log dan status panel.

## Batas yang jujur
- Aplikasi biasa tidak bisa 100% permanen: "Paksa berhenti" dari pengguna menghapus semua alarm, dan firmware Samsung
  (Aplikasi tidur, Device care, Game Booster) bisa membunuh paket. Karena itu papan kesehatan menunjukkan penghalang mana yang aktif.
- Belum di-build di sini (tidak ada jaringan untuk Gradle). Build lewat GitHub Actions seperti biasa.
