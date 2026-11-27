# Skylen / Asisten Galaxy IMZZ 1.8.0

This is a rebuilt Android project based on the supplied `Skylen-v1.7.0-patch.zip` source. The uploaded ZIP contained only seven app-module files and omitted the Gradle root, several referenced packages, resources, and service implementations. This package restores a complete Gradle project and separates Android actions from free-form AI chat.

## Included

- A command router that opens installed launcher apps by their visible app label/package name.
- `download / install / cari aplikasi <nama>` opens the Google Play search result for the requested app. Android/Play Store remains responsible for consent and installation.
- One-shot Indonesian speech recognition and Android Text-to-Speech.
- A user-started, draggable floating Skylen cat. Tap it to open the assistant; drag it to reposition it. Position is saved locally.
- Android `ROLE_ASSISTANT`, `VoiceInteractionService`, session, and recognition-service metadata. The visible assistant uses Android `SpeechRecognizer`; a separate fallback recognition endpoint is declared for platform compatibility. If Android/OEM maps the long-press power gesture to the selected digital assistant, it launches Skylen's assistant surface.
- Manual accessibility gestures (`tap x y`, `swipe x1 y1 x2 y2`) only after the user explicitly enables the service and gives the coordinate command. The helper does not inspect screen content or run an autonomous game loop.
- Offline cat replies plus optional remote text chat using the two Gradle-configured HTTPS endpoints. Chat prompts are sent to that configured API; screenshots and device files are not sent.

## Build

Open this directory in Android Studio with JDK 17 and Android SDK Platform 35 installed, or run `gradle assembleDebug` with Gradle 8.7+ available. The app module uses Android Gradle Plugin 8.6.1 and Kotlin 2.0.21.

GitHub Actions workflow: `.github/workflows/build-apk.yml`.

Optional API endpoint configuration:

```properties
galaxyAiApiUrl=https://your-https-chat-endpoint/?q=
galaxyAiApiUrl2=https://your-https-fallback-endpoint/?q=
```

or environment variables `GALAXY_AI_API_URL` and `GALAXY_AI_API_URL_2`.

## First-run setup

1. Install and open Skylen once.
2. Grant microphone permission when prompted.
3. To show the floating cat, use **TAMPILKAN KUCING** and approve **Display over other apps**.
4. To use the power-button assistant gesture, use **TOMBOL POWER** and select Skylen as the device's digital assistant. Samsung Side Key settings may also need to be adjusted; the app cannot intercept or override the physical power key itself.
5. Enable **Skylen Manual Gesture Helper** only when you need the explicit coordinate-based tap/swipe commands.

## Important limitations

- No ordinary app can force-capture the power key. Power-button invocation is controlled by Android and the device maker; the user must select the assistant and the device must route its long-press gesture to that assistant.
- Opening an app is supported. App installation is intentionally handed to Google Play / Android's installer and is never silently forced.
- There is no universal, reliable way to play every game with expert skill. Game state, controls, anti-cheat and game-specific mechanics differ. This build does not claim to be a pro autonomous gamer; it only provides explicitly requested manual gesture commands.
- Accessibility automation is restricted by Google Play policy. This implementation intentionally avoids autonomous screen-reading/action loops.
