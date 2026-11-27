# Third-party notices

## FlameLauncher for Android

- Project: https://github.com/FlameLaunchers/FlameLauncher-Android
- Source ref: `main` (the exact resolved commit is recorded in `output/BUILD-INFO.txt` for each build)
- License: AGPL-3.0
- Use: Android application, JVM/game launch pipeline, renderer bridge, native input and audio components, version/loader/modpack installation.
- The build script retains the complete upstream checkout (including LICENSE and NOTICE) and emits a corresponding patched source archive for each successful build.

Upstream's README identifies these major components and licenses: Terracotta (AGPL-3.0), PojavLauncher core (GPL-3.0), ZalithLauncher 2 (GPL-3.0), EasyTier (LGPL-3.0), libadrenotools (BSD-2-Clause), patched LWJGL (BSD-3-Clause), and OpenJDK Temurin (GPL-2.0 with Classpath Exception). Consult the exact upstream `NOTICE` and license files in the source archive for the complete dependency list and required notices.

The IMZYY branding patch does not remove upstream copyright notices or license files. When distributing a combined app, comply with AGPL-3.0 source-availability requirements and all third-party license terms. Do not redistribute custom Vulkan drivers unless their separate terms allow it.

Minecraft is a trademark of Mojang AB / Microsoft. IMZYY XL Launcher is an independent project and is not affiliated with or endorsed by Mojang or Microsoft.
