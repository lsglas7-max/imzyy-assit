# Game Turbo — Fan RPM / Thermal / FPS permission pass

## Mini fan strip
- FanView animates with load, reports a *virtual/estimated RPM* (not a physical fan RPM; phones generally have no cooling fan).
- Mini strip reads Android battery temperature via ACTION_BATTERY_CHANGED. Color bands: green <36°C, yellow 36–40°C, orange 40–44°C, red >=44°C.
- Temperature is the battery sensor and is not guaranteed to equal SoC or surface temperature. Missing sensor displays --.
- FPS is measured from the overlay's Choreographer callbacks. It is NOT game-render FPS; this is labeled honestly.

## FPS permissions
- Without Brevent/elevated shell: display-mode slider uses supported modes and window preferred refresh rate (best effort).
- Brevent package presence is not treated as proof of its server or privileged permissions.
- No undocumented Brevent command, silent ADB grant, root bypass, or fake hard lock is used. Actual per-game FPS locking requires game/system privileges or an officially supported interface.
- UI clarifies standard vs elevated access. Users can open Brevent manually and grant access on their own device.
