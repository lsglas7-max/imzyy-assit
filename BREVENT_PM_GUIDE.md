# Brevent / PM permissions guide

Package: `com.imzyyknox.gameturbo`

The in-app **SALIN PM GRANT (IZIN RUNTIME)** button copies these example commands:

```sh
pm grant com.imzyyknox.gameturbo android.permission.POST_NOTIFICATIONS
pm grant com.imzyyknox.gameturbo android.permission.RECORD_AUDIO
pm grant com.imzyyknox.gameturbo android.permission.WRITE_SECURE_SETTINGS
pm grant com.imzyyknox.gameturbo android.permission.BLUETOOTH_CONNECT
pm list permissions -g
```

Run only in a Brevent shell/ADB environment you control. POST_NOTIFICATIONS and RECORD_AUDIO are runtime permissions. WRITE_SECURE_SETTINGS is protected; the PM grant may fail on Samsung/Android builds, and if it fails it cannot be treated as granted. Android/device policy may reject any command or require Settings. These commands do not grant root, unrestricted app-op access, or global FPS control. Never treat command success alone as proof; the app should verify each permission using Android APIs.

Settings-only or restricted access should be opened through the OS UI: overlay (`SYSTEM_ALERT_WINDOW`), Modify system settings (`WRITE_SETTINGS`), Usage access, Notification policy/DND, and Accessibility. `WRITE_SECURE_SETTINGS` is protected and cannot be promised by `pm grant` on every firmware.

## Physical fan telemetry

This build probes common `hwmon`/fan sysfs RPM sensor paths. If a readable hardware sensor exists, RPM is read and the fan animation follows measured RPM. On typical Samsung phones with no physical fan/RPM sensor, it displays `RPM N/A` and the fan graphic stops; it does **not** invent RPM. Battery temperature is real battery telemetry where available; it is not a direct CPU/GPU temperature.
