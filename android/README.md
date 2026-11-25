# Android native source overlay

This directory contains the IMZYY Kotlin dashboard and integration sources copied into Amethyst's `app_pojavlauncher` module during the build. The UI is native Android; there is no web frontend or WebView screen.

Build the single integrated APK from the repository root with:

```bash
bash scripts/build-amethyst.sh
```

See the root `README.md` for the GitHub Actions build, SDK requirements, JRE bootstrap asset, and output location.
