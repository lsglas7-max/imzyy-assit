# Update v9.8 (versionCode 36)

## Handle swipe (mode hub baru)
- TOOLS > TAMPILAN HUB: pilih TOMBOL / SIDEBAR / SWIPE. SWIPE = handle tipis di pojok layar (hanya garis pegangan).
- Tarik ke arah dalam layar sampai panjang swipe tercapai, menu langsung terbuka (getar singkat). Handle ikut tertarik mengikuti jari.
- Tarik naik/turun = pindahkan handle di tepi (posisi tersimpan, menempel kiri/kanan).
- Cara membuka: SWIPE / KETUK, KETUK SAJA (semua menu langsung terbuka), atau SWIPE SAJA.
- Panjang swipe: PENDEK, SEDANG, PANJANG. Ukuran handle ikut pilihan KECIL/SEDANG/BESAR.
- Handle dikecualikan dari gestur back sistem (gesture exclusion), jadi swipe tidak bentrok dengan gesture HP. Batas Android: area pengecualian maksimal 200 dp per sisi.

## Clear cache per app
- Cache Control Center: tombol MANAGE (yang membuka halaman app) diganti CLEAR, langsung membersihkan lewat IMZYY Cleaner.
- Di floating window: bila Cleaner tidak menemukan tombol Clear cache di Setelan, app otomatis memakai pelepasan cache sistem (senyap), tidak lagi menyuruh bersihkan manual. Pesan gagal kini memuat teks tombol yang terlihat supaya mudah dilaporkan.
- Tombol baru SALIN PERINTAH CLEAR CACHE (BREVENT): `pm trim-caches` dan `pm clear --cache-only` untuk app dengan cache terbesar. Ini satu-satunya jalur yang benar-benar bersih per app tanpa membuka Setelan sama sekali.

Belum di-build di sini (tidak ada Android SDK/jaringan). Build lewat GitHub Actions seperti biasa.
