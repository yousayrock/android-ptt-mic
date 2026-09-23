# Architecture

## Goal

`X19 Pro -> USB -> PC` shall appear to the PC as a normal microphone input. Audio is transmitted only while the X19 Pro side button is held. Releasing the button must mute immediately.

## Components

```text
XS17 side button (KEY_VOLUMEUP)
        |
        v
Accessibility key filter -> PTT state machine ---> Audio gate (hard mute)
        |                       |                     |
        |                       +--> short vibration |
        v                                             v
Foreground microphone service -> processing chain -> AndroidMic USB transport
                                                        |
                                                        v
                                         AndroidMic PC receiver / virtual mic
```

- **PTT input adapter**: an explicitly enabled AccessibilityService captures volume-up only during an active stream; otherwise the key continues controlling system volume. XS17 screen-off operation still needs device validation.
- **PTT state machine**: transitions are `IDLE`, `PRESSED`, `RECONNECTING`, and `ERROR`. `ACTION_UP`, focus loss, service stop, USB loss, and watchdog timeout all force `IDLE`.
- **Audio gate**: closed means zeroed PCM frames; it must not rely only on UI state. Open/close timestamps are logged for latency measurement.
- **Foreground service**: owns microphone capture, processing, USB connection, reconnect backoff, and notification lifecycle.
- **USB transport**: reuse AndroidMic's USB serial/ADB transport initially; USB is preferred over network paths.

## Audio contract

V1 target format is 48,000 Hz, signed 16-bit PCM, mono. The transport must preserve frame order and provide bounded buffering. Default design target is 20 ms frames; the final value is measured on the target phone and PC.

## Reliability

USB disconnect triggers an immediate gate close and reconnect loop with bounded exponential backoff. Reconnect must not reopen the microphone until a valid USB stream is established and the PTT key is still held. Boot auto-start is optional and must be disabled by default until tested against the X19 Pro firmware.

## Licensing boundary

This repository retains the upstream GPL-3.0 `LICENSE`. Modified AndroidMic code remains GPL-3.0, upstream copyright notices are preserved, and releases will provide corresponding source. New code will carry a project copyright notice and a GPL-3.0-or-later/only declaration consistent with the selected upstream terms.
