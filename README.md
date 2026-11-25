# IMZYY XL Launcher — Premium Android ARM64 Edition (v3.1)

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

## v3.1: mouse controls, mod manager, Voxy option + texture optimizer

- **Black / white theme** across the upstream screens (palette patch) with an adjusted contrast pass so white-on-accent button text becomes black.
- **IMZYY Hub** is the new launcher entry screen (animated grid backdrop, staggered reveals, spring press feedback). **PLAY** opens the upstream launcher activity, which is untouched and still in the same APK.
  - **BOOST**: one-tap `options.txt` profiles (Hemat FPS / Seimbang / Kualitas, original backed up) and a performance mod pack (Sodium or Embeddium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, Entity Culling, Dynamic FPS) resolved live from Modrinth for the chosen Minecraft version + loader, with required dependencies, SHA-hash verification and a clean uninstall.
  - **MOUSE**: per-instance mouse sensitivity, scroll sensitivity, invert-Y, raw input and left/right button swap. Values are saved in that instance's `options.txt`; restart Minecraft after applying. Hardware mouse/keyboard input itself is provided by the upstream engine.
  - **MODS**: search Modrinth by Minecraft version/loader, import local `.jar` files through Android's file picker, enable/disable mods by reversible `.jar.disabled` rename, and remove mods from the selected instance.
  - **BOOST / Render distance**: vanilla render distance is configurable from 2–32 chunks and simulation distance from 2–12 chunks. This is not an infinite-distance switch; distant terrain requires a compatible LOD mod and remains limited by hardware.
  - **Voxy (optional)**: selected in the performance-mod catalogue. The installer resolves an exact Modrinth build for the chosen game version. It adds Mod Menu so Voxy's own in-game configuration can be opened when supported. Voxy is Fabric-focused (Quilt can attempt the Fabric build), requires Fabric API + Sodium and a GPU reporting OpenGL 4.6. Forge/NeoForge are not supported by the official Voxy build. A build missing for the selected Minecraft version is skipped rather than downloaded for another version.
  - **Texture optimizer**: losslessly recompresses PNG IDAT data in directory resource packs and `.zip` / `.mcpack` packs, without changing decoded pixel data or ancillary PNG chunks. Originals are backed up under `.imzyy-backups/textures`; only files/archives that become smaller are replaced. This can reduce pack storage size, but does not reduce decoded GPU texture memory or promise additional FPS. Files over the safety limits are skipped.
  - **IMPORT**: `.mrpack` (Modrinth format v1) importer: hash-verified parallel downloads from allow-listed hosts, zip-slip-safe overrides/client-overrides extraction. Create the matching instance (version + loader) in the launcher first, then pick its folder.
  - **GitHub card**: repo/releases buttons and a "latest release" check (needs `IMZYY_GITHUB_REPO`, set automatically by CI).
- Sodium/Embeddium need a suitable OpenGL 3.2+ path (Zink, Krypton or MobileGlues); they may fail on GL4ES. Switch the renderer in the existing renderer settings.
- **Voxy is not a universal Android performance switch.** Its published requirements include OpenGL 4.6, Fabric API and Sodium. Many Android GPU/renderer stacks expose OpenGL ES rather than desktop OpenGL 4.6, so Voxy may refuse to activate or fail to load on a phone. The launcher checks version/loader availability but cannot certify GPU compatibility or guarantee lag-free / unlimited render distance.
- The custom pointer/input event layer inside `MinecraftActivity` is upstream code and is not patched in this overlay. The new MOUSE page configures Minecraft's existing `options.txt` controls; in-game pointer behavior still depends on the engine's input implementation.

## GitHub

```bash
gh auth login
bash scripts/github-setup.sh imzyy-xl-launcher public --release v3.1.0
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
