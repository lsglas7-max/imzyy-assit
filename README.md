# IMZYY XL Launcher v6.0.0 — Android Native Edition

IMZYY XL is an Android-only launcher interface for Minecraft Java Edition. The launcher UI is written in Kotlin and rendered with native Android views. This project no longer contains a website, Vercel frontend, HTML/CSS/JavaScript interface, or WebView-based launcher screen.

The final application is designed to be **one APK**: the IMZYY dashboard and the Amethyst Minecraft Java engine are merged into the same Android application package. The source ZIP is the integration project, not a prebuilt APK. The large Amethyst source tree, native submodules, and official JRE bootstrap asset are downloaded during the build workflow.

## What changed in v6.0.0

**Design**
- New flat graphite theme with a single mint accent. No gradients, no emoji, no template-style glow.
- Rebuilt home screen: hero card with Play button and pulsing engine-status dot, quick-action tiles, live memory meter, and grouped settings-style lists.
- Every sub-page (versions, account, controller, files, performance, device monitor, engine check) shares the new components, a back chip in the header, and sentence-case buttons.

**Animation**
- Staggered rise-in for every block on page open (pre-draw driven, no flicker).
- Springy press feedback on tiles, buttons and chips; ripple clipped to rounded corners.
- Memory meter eases to its value and refreshes every 2.5 s while the home screen is visible.

**Optimization**
- Engine inspection is computed once and cached.
- Disk work (game-directory lookup, cache size, cache clearing) moved off the main thread, so screens open instantly.
- Live refresh stops when the app is paused.
- Hardware back returns to the home screen; edge-to-edge insets are handled.
- Build workflow now has a layout check that fails early with a clear message if `scripts/` or `android/` was not uploaded.

Everything from v5.3.x is kept: single APK, Amethyst engine, encrypted `.imzyy` backup/restore, mod/resource/shader import, safe cache cleanup. There is still no website or WebView.

## Integration architecture

The build script clones `AngelAuraMC/Amethyst-Android` at the configured `v3_openjdk` ref with its recursive Git submodules, then overlays the IMZYY Kotlin code into the upstream `app_pojavlauncher` application module.

The startup chain remains:

1. Amethyst `PojavApplication` initializes engine state.
2. Amethyst `TestStorageActivity` performs its storage and asset bootstrap.
3. IMZYY `MainActivity` displays the native dashboard and local account/backup screens.
4. Amethyst `LauncherActivity` opens inside the same package for installation, profiles, version management, sign-in, runtime and renderer settings.
5. Amethyst's game activity owns the game surface, JVM, native rendering, audio and native input pipeline.

The original Amethyst bootstrap path is retained. The resulting APK is not a separate dashboard that launches a second launcher APK.

## Included features

- Single-APK integration with the real Amethyst Java runtime and native engine.
- Native IMZYY dashboard, local account, encrypted `.imzyy` backup and restore.
- Mod (`.jar`), resource-pack (`.zip`) and shader-pack (`.zip`) import into the active profile when the game folder is available.
- Optional manual Storage Access Framework folder for file-library operations.
- Engine readiness checks, device information, memory snapshot, controller preview, and launcher temporary-cache cleanup.
- Java/runtime, renderer, Minecraft versions, profiles and actual launch delegated to Amethyst.

## Build with GitHub Actions

1. Create a GitHub repository and upload the contents of this project ZIP to its root.
2. Push to `main` or `master`.
3. Open **Actions → Build IMZYY XL with embedded Amethyst Engine → Run workflow**.
4. Download the `imzyy-xl-amethyst-engine-arm64-debug` artifact. It contains the APK and `BUILD-INFO.txt` with the upstream commit used.

The workflow uses Temurin JDK 21, Gradle 9.6.1, Android command-line tools 22.0, Android SDK platform `platforms;android-37.0`, and NDK `27.3.13750724`. It also downloads the official `jre8-pojav` build artifact before compilation. ARM64 is selected by default to keep the APK smaller for modern ARM64 phones.

## Build locally

Requirements: Git, Python 3, JDK 21, Gradle 9.6.1 (or the upstream wrapper), Android command-line tools, Android SDK platform `37.0`, NDK `27.3.13750724`, and network access to GitHub/Maven/Google repositories.

```bash
# Fetch Amethyst and its native submodules
bash scripts/build-amethyst.sh fetch

# Download the official `jre8-pojav` artifact from
# AngelAuraMC/angelauramc-openjdk-build (branch `buildjre8`)
# and extract it into:
# .build/Amethyst-Android/app_pojavlauncher/src/main/assets/components/jre/

# Overlay IMZYY and build the integrated APK
bash scripts/build-amethyst.sh build
```

The build validates that the APK contains ARM64 native libraries and the Amethyst JRE bootstrap files. Output:

```text
output/IMZYY-XL-Amethyst-Engine-debug.apk
```

Run the offline integration test before a full build:

```bash
python3 scripts/test-integration.py
```

Build options:

```bash
# Another upstream ref, after checking compatibility
AMETHYST_REF=v3_openjdk bash scripts/build-amethyst.sh

# All supported upstream ABIs, producing a larger APK
IMZYY_ABI_FILTER=all bash scripts/build-amethyst.sh
```

## Performance and compatibility notes

- The default APK is ARM64-only. Use `IMZYY_ABI_FILTER=all` for broader ABI coverage.
- Actual performance depends on the device GPU/driver, Android build, Minecraft version, Java runtime, renderer, mods, render distance and thermal state.
- Use Amethyst's renderer and game-profile settings for real Minecraft tuning. IMZYY does not set unsupported GPU flags or claim to force additional FPS.
- The cache action clears only the app's temporary cache directory. It is not a RAM booster and does not clear other apps' data.
- Mod/pack import checks file extension and destination, not the safety or compatibility of file contents. Use files from sources you trust and match them to the selected Minecraft version/mod loader.
- This authoring environment does not have the Android SDK/Gradle build toolchain or the full Amethyst source tree, so it cannot produce or hardware-test the final APK here. Use the provided Actions workflow to compile the actual APK.

## License and upstream notices

Amethyst is licensed under LGPL-3.0. Read `THIRD_PARTY_NOTICES.md`, retain required notices/source disclosures, and verify redistribution rights for every upstream component and asset before publishing an APK. This integration project does not grant rights to redistribute assets or libraries whose terms prohibit it.

Upstream engine: https://github.com/AngelAuraMC/Amethyst-Android

## Commit & push from Termux

Use the helper script to review changes, commit, and push the current branch without putting a GitHub token in shell history:

```bash
pkg update -y && pkg install git gh -y
cd ~/imzyy
# Authenticate once using the GitHub CLI's interactive flow:
gh auth login
# Then run:
bash scripts/termux-git-action.sh
```

The helper prompts for a commit message, stages changes, commits, and pushes the active branch. Review `git status` first and don't commit secrets, signing keys, or local build files.
