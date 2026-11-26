# Update v9.7 (versionCode 35)

## Floating window landscape
- Kipas CPU/FPS/GPU di header otomatis diperkecil (sekitar 60%) saat layar miring dan kembali penuh saat tegak. Padding strip header ikut dirapatkan. Ukuran dihitung ulang tiap rotasi tanpa membangun ulang panel.
- Pesan status tidak lagi terpotong satu baris: 3 baris (2 di landscape), ketuk untuk membuka semuanya, tahan untuk menyalin.

## IllegalArgumentException
- Nama exception tidak pernah tampil lagi. Firmware yang menolak key refresh dibaca sebagai "firmware menolak kunci lewat aplikasi" dan aplikasi langsung memakai mode jendela. Setelah penolakan pertama jalur Settings.System tidak dicoba berulang kalau jalur secure tersedia.
- Tombol baru SALIN PERINTAH BREVENT IMZYY (tab FPS): menyalin izin sekali (WRITE_SECURE_SETTINGS, WRITE_SETTINGS, overlay) plus kunci refresh lewat shell Brevent/ADB. Tanpa Brevent tetap jalan lewat mode jendela, tanpa pesan error.

## Panel review
- Overlay terpisah: FPS, baterai, suhu baterai, CPU, GPU, tanpa membuka floating window. Dua tampilan: TEKS dan KIPAS (tiga kipas mini). Ketuk = ganti tampilan, geser = pindah, posisi tersimpan sebagai rasio layar. Nyalakan di tab TOOLS (PANEL REVIEW KIPAS). Opsi sembunyikan saat panel utama terbuka.
- Jujur soal data: FPS = laju frame overlay, suhu = sensor baterai, CPU/GPU bertanda ~ bila hanya estimasi.

## Android 8 sampai 17+
- AndroidInfo mendeteksi versi (contoh "Android 17 (API 37)"), tampil di tab INFO. Jalur aksesibilitas, Force Stop lewat Cleaner, dan setelan terbatas memakai kemampuan nyata, bukan syarat tunggal "Android 14". Halaman detail aksesibilitas dipakai bila ada, kalau tidak jatuh ke daftar biasa.

Belum di-build di sini (tidak ada Android SDK/jaringan). Build lewat GitHub Actions seperti biasa.
