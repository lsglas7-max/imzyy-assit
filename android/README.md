# Android integration

The final distributable is one APK named `IMZYY-XL-Launcher-arm64-debug.apk`. The main dashboard, login flow, version/profile management, renderer configuration, native JVM and Minecraft activity are packaged as one Android application. `MinecraftActivity` can run in an isolated Android process for memory/process separation, but it is still an activity in this same APK and not a separately installed launcher.

The build fetches FlameLauncher-Android at `IMZYY_ENGINE_REF`, overlays IMZYY branding, typography and device-capped performance profiles, then builds `:app:assembleDebug`.

Do not rename the Android application ID until Microsoft OAuth callback registration has been updated and verified.
