# IMZYY KNOX Game Turbo v10.0

- Simplified the home dashboard and added direct shortcuts to the live monitor and floating app windows.
- Added a draggable, position-persistent overlay with VSYNC/frame-cadence, CPU, GPU, temperature, and display refresh metrics.
- CPU usage now comes from `/proc/stat`; GPU busy counters are used when exposed by the device kernel. Estimated values are marked with `~`.
- Temperature reads exposed thermal zones first and falls back to battery temperature with the source identified.
- Enlarged the animated fan visuals and added smoother acceleration and pulsing glow.
- Updated network diagnostics with active-network-bound TCP probes, validation, link information, and DNS-over-TLS handshake tests.
- Added a freeform-app-window shortcut and clarified the Android/OEM support requirement.

**Measurement note:** Android's public SDK cannot guarantee access to another game's render FPS. The displayed VSYNC/frame-callback rate describes the monitor/display callback cadence, not the game's internal rendered FPS.
