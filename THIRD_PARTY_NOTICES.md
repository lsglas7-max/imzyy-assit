# Third-party notices

## Amethyst Android engine

- Project: https://github.com/AngelAuraMC/Amethyst-Android
- Default source ref: `v3_openjdk`
- License stated by upstream: GNU Lesser General Public License v3.0 (LGPL-3.0)
- The exact Git commit used by a CI build is written to `output/BUILD-INFO.txt`.
- The integration is applied as an overlay to the upstream `app_pojavlauncher` application module. It retains upstream engine source, license files, copyright notices and recursive native submodules.

The IMZYY dashboard is added to the same Android application package. The upstream `PojavApplication`, `TestStorageActivity`, `LauncherActivity`, game `MainActivity`, JVM/renderer components and native input pipeline remain in the build. The storage bootstrap entry activity is patched only to open the IMZYY dashboard after the original storage and asset initialization calls.

## Other upstream components

Amethyst's README identifies dependencies/components with independent licenses and restrictions, including PojavLauncher components, AndroidX/support libraries, GL4ES, MobileGlues, ANGLE, OpenJDK, LWJGL/LWJGLX, Mesa, OpenAL-Soft, SDL, sdl2-compat, bytehook, libepoxy and others. The exact license state must be checked in the fetched upstream commit and recursive submodules used by the build.

In particular, upstream lists some parts as unknown license or All Rights Reserved/ARR. This project does not grant permission to redistribute components for which the rightsholder has not granted redistribution rights. Before publishing an APK, retain notices and provide corresponding source/modifications as required by each applicable license, and verify that all bundled components may be redistributed in that form.
