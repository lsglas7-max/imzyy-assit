# Audio routing status — v8.6

## Implemented in v8.6
- LIVE monitor mode for microphone-only input.
- LIVE mixer mode combines microphone PCM with Android playback-capture PCM where the source app allows capture.
- Mixed signal is processed through the selected Voice FX and played to the device output/headset.
- FX presets can be switched while the LIVE engine is running without restarting its audio thread.
- User-approved MediaProjection is held by a dedicated foreground service for Android's projection lifecycle requirements.
- Switching between microphone-only and mixed input restarts only when the input topology changes.

## Important platform limitation
The LIVE mixer output is local monitoring/playback. It is **not** registered as a virtual microphone and does not replace another app/game's `AudioRecord` input. Public Android SDK APIs do not let an ordinary third-party app inject arbitrary PCM into another app's microphone stream. Actual in-game virtual-mic support still requires a separately engineered, device/OS-specific privileged backend (for example, compatible rooted audio-routing module or custom ROM/audio HAL), with explicit device-owner authorization and testing.

## Capture constraints
- Internal playback capture requires Android 10+ and user consent through MediaProjection.
- A game/media app may opt out of playback capture; protected or disallowed streams will not be present in the mix.
- The mixer uses microphone and captured playback as two sources, applies a conservative blend, then applies the selected effect.
- Use headphones to avoid acoustic feedback. Bluetooth route latency and device-specific audio policy can vary.
- No bypass of game privacy/capture restrictions is attempted.

## Not implemented
- System-wide virtual input device.
- Injection/replacement of another app's microphone stream.
- Guaranteed capture of every game's audio.
