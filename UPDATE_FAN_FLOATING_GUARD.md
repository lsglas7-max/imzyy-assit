# Update: Mini Fan, FPS display, typography, floating-window recovery

- Mini fan is now 60dp and sits immediately below `SIAP`, outside page content.
- The blade animation follows measured overlay FPS for visual responsiveness. This is a UI animation only; it is not advertised as hardware RPM.
- Physical RPM stays `RPM fisik N/A` unless a real readable RPM sensor exists.
- Live, previous, after, and delta FPS labels are enlarged and slightly heavier.
- Text helper uses bold/medium weights for improved readability.
- Floating panel's recovery watchdog checks more frequently (2.5 s) and retains `panel_open` intent after a temporary WindowManager detach. It rebuilds a detached panel rather than treating the detach as user-close.

## Limits

Android/Samsung may still close overlays when the user revokes overlay permission, force-stops the app, or firmware kills the foreground service. An ordinary app cannot override those actions. Reopen/restore overlay permission if needed.
