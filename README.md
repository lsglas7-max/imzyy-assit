<<<<<<< HEAD
# ASISTEN GALAXY IMZZ — Galaxy Autopilot

Proyek Android native Kotlin untuk fitur AI Game & App Autopilot sesuai spesifikasi yang diberikan.

## 🐱 SKYLEN — maskot kucing putih (baru di v1.2.0)

Karakter AI berupa kucing putih yang tampil di layar utama, digambar penuh dengan Canvas (tanpa file gambar).

- **Bicara**: suara Android TextToSpeech lokal dengan pitch kucing, mulut bergerak sinkron dengan suara, plus balon ucapan yang mengetik huruf demi huruf. Semua ucapan asisten (buka aplikasi, status Autopilot, peringatan) sekarang diucapkan Skylen.
- **Ngobrol**: ketik `skylen, apa kabar?` atau kalimat obrolan biasa. Jawaban lewat API #2 lebih dulu, lalu API #1, dan kalau keduanya gagal Skylen tetap menjawab offline.
- **Gerak**: berkedip, bernapas, ekor goyang, kuping bergerak, mata mengikuti jari. 6 aksi: lompat, melambai, stretching, miring kepala, jalan, joget (spontan tiap 7–14 detik, atau tombol ✨ AKSI / perintah "skylen, joget").
- **Ekspresi**: normal, senang, jatuh cinta, mikir, siaga, kaget, ngantuk, sedih, marah. Otomatis mengikuti status Autopilot: PLANNING = mikir, RUNNING = siaga, PAUSED = ngantuk, WAITING = kaget, ERROR = sedih, EMERGENCY STOP = marah. Tidur sendiri kalau 2 menit tidak disentuh.
- **Gaya**: tombol 👕 GAYA ganti outfit (Polos, Pita, Topi Penyihir, Kacamata Cyber, Headphone, Syal, Mahkota, Helm Astronot). Pilihan tersimpan.
- **Interaksi**: usap kepala = mendengkur + hati melayang, ketuk badan = lompat + meow. Suara meow dan purr disintesis di memori (tanpa file audio dan tanpa internet).

File: `mascot/CatMascotView.kt` (gambar + animasi), `mascot/CatBrain.kt` (ngobrol + fallback offline), `mascot/MeowSynth.kt` (suara).

## Dua API AI (sudah terisi bawaan)

Bawaan build: API #1 = `https://api.kyio.web.id/api/v2/ai/gpt-5?q=` dan API #2 = `https://api.kyio.web.id/api/v2/ai/gpt-6-luna?q=`. Keduanya endpoint **teks** (GET `?q=`): tidak menerima gambar, jadi Autopilot mengirim teks layar hasil OCR beserta koordinat tiap teks, lalu AI membalas satu aksi JSON (tap/swipe/dll). Game atau video tanpa teks di layar tidak bisa dibaca dengan cara ini; untuk itu perlu API yang menerima gambar.


- **API #1** (`galaxyAiApiUrl` / `GALAXY_AI_API_URL`): utama untuk Autopilot (vision).
- **API #2** (`galaxyAiApiUrl2` / `GALAXY_AI_API_URL_2`): cadangan Autopilot (pindah otomatis kalau API #1 gagal) dan dipakai **lebih dulu** untuk ngobrol dengan Skylen, supaya jatah API #1 tidak habis oleh obrolan. Boleh dikosongkan.
- GitHub Actions: buat dua repository variable `GALAXY_AI_API_URL` dan `GALAXY_AI_API_URL_2`.
- Format request/response ada di `docs/AI_API_CONTRACT.md` (termasuk mode `chat` baru).

## Fitur yang sudah diimplementasikan

- **Smart App & Game Scanner**: membaca aplikasi yang punya launcher activity dari PackageManager, mengambil label/package/activity, menandai game dengan metadata/kata petunjuk, menampilkan ikon, serta menyimpan katalog ke SQLite lokal.
- **Package resolver**: pencarian nama aplikasi, alias DDS/UBG, pencocokan label dan package, dan dialog pemilihan jika ada beberapa hasil.
- **App launcher**: membuka aplikasi dengan launcher Intent aktual dari PackageManager. Status sukses hanya berarti Android menerima permintaan pembukaan; aplikasi foreground tidak diklaim sudah terverifikasi.
- **Voice & chat command**: pengenalan suara bahasa Indonesia via tombol push-to-talk (RecognizerIntent) dan respons suara menggunakan Android TextToSpeech lokal tanpa layanan TTS cloud. Always-on hotword belum diaktifkan.
- **Screen capture**: MediaProjection dengan persetujuan sistem Android, dijalankan melalui foreground service, dan memiliki notifikasi STOP.
- **Screen understanding**: OCR lokal ML Kit Text Recognition, screenshot terbaru disimpan sementara di cache aplikasi, lalu dikirim ke endpoint AI API milik pengguna hanya setelah pengguna menekan mulai dan menyetujui dialog privasi.
- **Gesture control**: AccessibilityService dengan tap, swipe, dan input teks pada kolom editable yang sedang fokus (bukan password). Izin Accessibility harus diaktifkan manual oleh pengguna.
- **Autopilot loop**: OBSERVE → UNDERSTAND → PLAN → ACT → VERIFY → REPLAN, status yang ditampilkan, batas 10 menit dan 120 aksi, pause/resume, AI Assist, Manual, dan Emergency Stop.
- **Safety fallback**: berhenti dan meminta pengguna mengambil alih saat OCR mendeteksi sinyal autentikasi atau tindakan sensitif; tidak ada bypass anti-cheat, pembelian, pembayaran, penghapusan, pengiriman pesan, atau autentikasi otomatis. Emergency Stop membatalkan rencana/aksi berikutnya; gesture yang sudah diserahkan ke Android mungkin menyelesaikan durasi gesture pendek yang sedang berjalan.
- **Game memory**: profil durasi tap/swipe dan sensitivitas disimpan lokal per package aplikasi yang aktif.
- **Package updates**: receiver untuk package install/remove/change memicu scan ulang dan memperbarui database.

## Cara membuka dan build

1. Ekstrak folder `GalaxyAutopilot`.
2. Buka folder tersebut sebagai proyek di Android Studio versi yang mendukung Android Gradle Plugin 8.7.3.
3. Gunakan JDK 17 dan Android SDK Platform 35.
4. Jalankan **Sync Project with Gradle Files**.
5. Pilih perangkat Android (minimum Android 8.0 / API 26), lalu Build > Build APK(s).

> Versi 1.2.0 belum dikompilasi di lingkungan pembuatnya (tidak ada Android SDK/Gradle). Kalau build GitHub Actions error, kirim log-nya, nanti diperbaiki.

Konfigurasi menggunakan Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, compile/target SDK 35, dan min SDK 26.

> APK belum disertakan. Build belum dapat diverifikasi di lingkungan ini karena Gradle Wrapper gagal mengunduh distribusi Gradle dari `services.gradle.org` (koneksi jaringan tidak tersedia). Buka project di Android Studio/JDK 17 untuk menjalankan build lokal.

## Konfigurasi endpoint AI

Project ini tidak menyertakan URL backend pihak ketiga. Masukkan URL API yang memang sudah lu miliki saat build:

- Lokal: `./gradlew assembleDebug -PgalaxyAiApiUrl="https://domain-api-lu/path"`
- Environment: set `GALAXY_AI_API_URL` sebelum menjalankan Gradle.
- GitHub Actions: buat repository variable bernama `GALAXY_AI_API_URL`; workflow akan meneruskannya saat build.

Endpoint harus menerima request JSON sesuai kontrak di bagian Privasi dan keamanan API dan mengembalikan satu planned action JSON. Endpoint diakses tanpa header provider key dari aplikasi Android.

## Setup pertama kali di Android

1. Instal APK hasil build.
2. Buka **ASISTEN GALAXY IMZZ** lalu tekan **SCAN APPS**.
3. Tekan **ACCESSIBILITY** dan aktifkan **Galaxy Autopilot Control** pada pengaturan Accessibility Android.
4. Tekan **SCREEN SHARE**, lalu setujui dialog rekam/cast layar Android. Notifikasi foreground service akan muncul selama capture aktif.
5. Saat build, masukkan URL API yang sudah lu punya melalui Gradle property `galaxyAiApiUrl` atau environment variable `GALAXY_AI_API_URL`. Aplikasi tidak meminta API key.
6. Berikan perintah seperti `Buka Roblox`. Setelah Roblox dibuka, ucapkan nama game seperti `Drag Drive Simulator` atau `Untitled Boxing Game`; Autopilot akan memakai OCR/vision untuk mencoba navigasi UI yang terlihat.
7. Gunakan **STOP**, **MANUAL**, atau notifikasi **STOP** untuk menghentikan aksi. Jangan tinggalkan sesi Autopilot tanpa pengawasan.

## Privasi dan keamanan API

- Screenshot dan OCR dikirim ke URL AI API yang dikonfigurasi. Endpoint harus menerima JSON berisi `goal`, `ocrText`, `previousActions`, `screenshotMimeType`, `screenshotBase64`, dan `safety`.
- API mengembalikan JSON aksi langsung (`type`, `x`, `y`, `x2`, `y2`, `text`, `waitMs`, `reason`, `speech`) atau objek `{ "action": { ... } }`. Proxy yang mengembalikan format Gemini `candidates[].content.parts[].text` juga didukung.
- Tidak ada API key provider yang ditanam di APK atau diminta lewat UI. Letakkan autentikasi/provider key di server API milik lu, bukan di aplikasi Android.
- Voice feedback menggunakan Android TextToSpeech lokal.
- App scanner hanya meminta visibilitas launcher melalui `<queries>`; proyek tidak meminta `QUERY_ALL_PACKAGES`.
- Screen capture memerlukan persetujuan sistem. Accessibility harus diaktifkan pengguna secara manual.
- Model AI pada endpoint dapat bersifat probabilistik. Koordinat UI bisa keliru, OCR dapat salah baca, dan tiap game memiliki layout/kontrol berbeda. Karena itu fitur tetap dibatasi, memerlukan persetujuan sesi, dan menyediakan Emergency Stop.

## Struktur modul

- `apps/`: scanner, package resolver, app launcher, deteksi game, navigasi game.
- `screen/`: MediaProjection manager/service dan OCR processor.
- `accessibility/`: service dan gesture controller.
- `autopilot/`: AI API client, planner, state machine, screen safety, action executor.
- `storage/`: SQLite app catalog dan memory control profile.
- `voice/`: Android TextToSpeech lokal.

## Batasan yang perlu diketahui

Autopilot belum bisa menjamin semua game/aplikasi dapat dikendalikan. Aplikasi yang tidak menyediakan UI accessibility, game dengan UI yang berubah-ubah, dialog izin, CAPTCHA, proteksi anti-cheat, atau layout yang tidak terbaca dapat memerlukan tindakan manual. Fitur pencarian Roblox dilakukan melalui UI yang terlihat, bukan API internal Roblox dan bukan hardcoded daftar game. Verifikasi keberhasilan masuk ke game bergantung pada perubahan layar/OCR setelah aksi.
=======
# IMZYY XL Launcher — Premium Android ARM64 Edition (v3)

IMZYY XL Launcher is a single Android application for **Minecraft: Java Edition**. The project embeds the complete upstream JVM/native launch stack and Minecraft game activity in the same APK. Users install and open **IMZYY XL Launcher only**; there is no second launcher APK to install or hand off to. The game activity runs as an isolated process inside the same Android application for stability, not as another launcher app.

## What is inside

The selected open-source engine currently documents:

- Minecraft Java versions from Alpha/Beta through current releases.
- Vanilla, Fabric, Forge, NeoForge and Quilt.
- Mod and modpack discovery/installation through CurseForge and Modrinth, including dependencies.
- Version-matched OpenJDK runtimes selected automatically.
- Zink, Krypton, GL4ES and MobileGlues rendering paths, with hardware-dependent fallback behavior.
- Touch controls, editable on-screen buttons, hardware keyboard, mouse and gamepad support.
- ARM64 Android builds.

OptiFine is not listed as a first-class integrated loader by this upstream. Do not assume it is guaranteed; use the upstream-supported loader and mod paths.

## IMZYY XL Lite

Lite is a build variant of the same project (`IMZYY_VARIANT=lite`, workflow `android-lite.yml`).

- **Size**: only **Java 21** stays inside the APK. Java 8 / 17 / 25 (about 157 MB) are stripped from the APK, published by CI as assets of the `runtimes` GitHub release, and downloaded once, SHA-256 verified, when needed (HOME > Runtime Java, or automatically on game start when not on the UI thread). Estimated APK: roughly 105-120 MB; the build prints the real number and warns above 150 MB.
- **All Minecraft versions**: the launcher picks the Java version per Minecraft version. Old versions (<= 1.16) need Java 8, 1.18-1.20.4 need Java 17, 26.x needs Java 25: download these once in HOME before playing. If a runtime is missing the launcher falls back to the bundled Java 21, which works for most modern versions but not for old/Forge-heavy ones.
- **Mods**: IMPORT accepts `.mrpack`, a single `.jar`, or a `.zip` that contains mods (jars are found at any depth and flattened into `mods/`; nothing else is extracted).
- **Voxy** is an opt-in entry in the BOOST pack (Fabric + Sodium only, needs OpenGL 4.6; often unsupported on phones).
- **Controls** are unchanged: KONTROL tab + the upstream button editor.
- **Look**: animated shimmer title, pulsing PLAY button, drifting particles, monochrome palette.

## v3: monochrome UI + IMZYY Hub

- **Black / white theme** across the upstream screens (palette patch) with an adjusted contrast pass so white-on-accent button text becomes black.
- **IMZYY Hub** is the new launcher entry screen (animated grid backdrop, staggered reveals, spring press feedback). **PLAY** opens the upstream launcher activity, which is untouched and still in the same APK.
  - **BOOST**: one-tap `options.txt` profiles (Hemat FPS / Seimbang / Kualitas, original backed up) and a performance mod pack (Sodium or Embeddium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, Entity Culling, Dynamic FPS) resolved live from Modrinth for the chosen Minecraft version + loader, with required dependencies, SHA-hash verification and a clean uninstall.
  - **MODS**: search Modrinth filtered by version/loader and install into the instance.
  - **IMPORT**: `.mrpack` (Modrinth format v1) importer: hash-verified parallel downloads from allow-listed hosts, zip-slip-safe overrides/client-overrides extraction. Create the matching instance (version + loader) in the launcher first, then pick its folder.
  - **GitHub card**: repo/releases buttons and a "latest release" check (needs `IMZYY_GITHUB_REPO`, set automatically by CI).
- Sodium/Embeddium need an OpenGL 3.2+ path (Zink, Krypton, MobileGlues); they crash on GL4ES. Switch the renderer in the existing renderer settings.
- **KONTROL tab + in-game hooks** (patched into the real upstream input code, verified against its source):
  - camera sensitivity (replaces the fixed 1.5 constant), drag slop and long-press delay, all live-tunable;
  - **virtual mouse / touchpad mode** for menus and inventory: drag moves a visible cursor, tap = left click, long press or two-finger tap = right click (gameplay keeps normal camera control);
  - on-screen button colors converted to monochrome, and a shortcut to the upstream button-layout editor.
  - Settings travel via `filesDir/imzyy_controls.json` because the game runs in its own `:minecraft` process; they apply when the game (re)starts.
- Instances in the Hub come straight from the launcher's own instance list (Minecraft version and loader are read automatically), so mod install/FPS pack/.mrpack always match the selected instance.
- Local `.mrpack` import checks the pack's Minecraft version and loader against the selected instance and refuses mismatches.

## GitHub

```bash
gh auth login
bash scripts/github-setup.sh imzyy-xl-launcher public --release v3.0.0
```

Pushing to `main` builds the APK as an artifact; pushing a `v*` tag also publishes a GitHub Release with the APK, build info and corresponding source.

## Built-in performance profiles

The JVM/game settings page includes three working one-tap profiles. They change existing engine settings; they do not inject undocumented JVM arguments:

| Profile | Heap limit policy | Render scale | GC |
|---|---:|---:|---|
| FPS | 45% of detected heap ceiling, capped at 2048 MB | 75% | G1GC |
| Balanced | 58% of detected heap ceiling, capped at 3072 MB | 85% | G1GC |
| Visual | 68% of detected heap ceiling, capped at 4096 MB | 100% | G1GC |

The heap ceiling is derived from device RAM and the preset keeps memory headroom for Android, textures and native renderer allocations. These are starting points, not universal FPS guarantees. The best configuration depends on the phone's GPU/driver, RAM pressure, thermal state, Minecraft version, mods and renderer. Settings take effect on the next game launch. Renderer choices remain available in the existing renderer settings; the launcher does not force unsupported GPU flags.

### Renderer starting points

- **Zink**: try first on a modern Vulkan-capable GPU for Minecraft 1.17+ and shader-capable setups. The engine documents a device-dependent fallback path.
- **Krypton**: a broad GLES 3.x path when Zink is unstable or unavailable.
- **GL4ES**: compatibility path for older Minecraft versions and devices that cannot run Zink.
- **MobileGlues**: another OpenGL/GLES path exposed by the upstream engine; verify compatibility with the exact game version and device.

Do not stack multiple renderer drivers or copy random JVM flags from PC guides. Change one setting at a time and keep a working profile so regressions can be isolated.

## Account and game files

- The launcher application is free/open-source; Minecraft Java Edition still requires a valid Microsoft account with a Minecraft Java entitlement for official online play.
- Microsoft sign-in and game-file downloads are handled by the upstream engine. This project does not bypass authentication or distribute Minecraft game files.
- The upstream Android namespace/application ID are intentionally preserved for now because Microsoft OAuth callback registration may depend on that identity. The visible app branding and artifact name are IMZYY XL Launcher.

## Build with GitHub Actions

1. Upload the contents of this ZIP to the root of a GitHub repository.
2. Push to `main` or `master`, or run **Actions → Build IMZYY XL Launcher (ARM64)** manually.
3. Download the artifact `IMZYY-XL-Launcher-ARM64-debug`.
4. It contains the debug APK, build provenance and corresponding patched upstream source archive.

## Build locally

Requirements: Git, Python 3, JDK 17, Android SDK platform 36, Android build-tools 36.0.0, NDK 27.0.12077973, CMake 3.22.1 and network access.

```bash
python3 scripts/test-integration.py
bash scripts/build-imzyy.sh all
```

Output:

```text
output/IMZYY-XL-Launcher-arm64-debug.apk
output/BUILD-INFO.txt
output/IMZYY-XL-Launcher-corresponding-source.tar.gz
```

The build downloads FlameLauncher-Android, applies the IMZYY identity/UI/performance patch and compiles one ARM64 Android APK. The exact upstream commit is recorded in `BUILD-INFO.txt`. For reproducible releases, pin `IMZYY_ENGINE_REF` to a reviewed commit or tag before publishing.

## Validation and limitations

- Offline integration tests validate the patcher, single-app activity presence, branding, UI profiles, typography, icon wiring, ARM64 target and idempotency.
- The Android APK must still be built by GitHub Actions or on a machine with the Android SDK/NDK/Gradle toolchain. This environment cannot access GitHub or run the Android build, so no fresh APK or physical-device FPS result is claimed here.
- Install and test on an ARM64 phone after CI passes. Check first launch, Microsoft sign-in, one vanilla instance, one mod-loader instance, renderer switching, touch input and resume after returning from the game.

## Licensing

The selected FlameLauncher source tree declares **AGPL-3.0** for the combined project because it includes GPL/AGPL components. Keep its `LICENSE` and `NOTICE`, publish the corresponding source and modifications when distributing the app, and review all upstream notices. Do not redistribute custom Vulkan drivers unless their separate terms allow it. Minecraft is a Mojang/Microsoft trademark; IMZYY XL is not affiliated with Mojang or Microsoft.

Upstream engine: https://github.com/FlameLaunchers/FlameLauncher-Android

## In-game extras (v8)

- **Asset repair**: every PLAY verifies the instance's Minecraft assets and re-downloads missing/truncated files; Home has a manual "PERIKSA & PERBAIKI ASSET" button.
- **Pad**: tap PAD in-game, then tap a button to change its mode, hide/show it, or merge more keys into it (e.g. CTRL+C, pressed together). "+ TOMBOL" adds new buttons or combos, "DAFTAR / SEMBUNYI" hides buttons one by one or all at once. The same controls exist in Hub > KONTROL.
- **MODS (in-game)**: search Modrinth modpacks that match the running instance (Minecraft version + loader), install them with hash verification, then use RELOAD TEKSTUR (F3+T), RELOAD CHUNK (F3+A) or RESTART GAME. The modpack is staged in `.imzyy-pending` (the running JVM's mod jars are never touched) and applied by RESTART GAME, which relaunches the same game automatically; it is also applied on the next Hub PLAY. Reload only refreshes resources and rendering.
- The upstream "Release Notes" menu and its GitHub update popup are removed at patch time.
- **Anti lag masuk world** (BOOST tab): lowers render/simulation distance per device tier (never raises a value you already set lower) and cheapens chunk updates, particles, shadows and biome blend; re-applied on every PLAY.
>>>>>>> 113562a88c7a3cf5611046853739faf5ecfe4949
