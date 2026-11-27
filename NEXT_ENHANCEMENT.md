# Game Turbo: FPS slider & Smart RAM update

## FPS slider
- Slider now snaps to refresh modes supported by the device at its current resolution (instead of arbitrary 10–120 values).
- If global refresh keys are blocked by Samsung/Android, it still records the requested mode and applies the window preference where supported (best effort).
- UI states clearly that this does not force a game's internal FPS.
- Brevent presence is checked only as installation status; the app does not falsely claim that Brevent's background server/ADB authorization is active.

## Smart RAM
- Protects the foreground app, saved game, launcher, keyboard, this app, user-protected packages, and system apps.
- Candidate list now excludes apps used within the last 12 minutes, checks a 90-minute history, and prioritizes longest-idle apps.
- Cleanup policy is pressure-based: skip cleanup at >=28% available RAM; limit to 2 candidates at 18–28%, 5 at 10–18%, and the selected cap below 10% available RAM.
- Android 14+ restrictions still apply: standard app process-kill requests may be limited. Force Stop via Accessibility remains an explicit opt-in.

## Elevated/Brevent
Brevent is not a universal public API that lets a third-party app unlock every Game Turbo feature. This update only checks whether the Brevent package is installed and labels elevated access as unverified. A real elevated backend needs a supported, explicit authorization mechanism (e.g. Shizuku integration and user approval) and per-feature capability checks; no hidden bypass or fake “fully unlocked” state is included.

## Build verification
Gradle could not be run here because the wrapper needs to download Gradle and network access was unavailable. Source changes are packaged, but the APK is not build-tested.
