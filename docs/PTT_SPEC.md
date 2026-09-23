# PTT Specification

## V1 behavior

| Event | Required behavior |
| --- | --- |
| Side button down | Enter `PRESSED`, open the audio gate, start USB transmission, issue one short vibration |
| Side button repeat | Keep transmitting; do not retrigger vibration |
| Side button up | Enter `IDLE`, close the gate immediately, and send muted PCM frames if the transport requires continuous framing |
| Display off | Preserve button handling and the foreground service |
| USB disconnect | Close the gate immediately; retry connection automatically |
| Service stop / crash recovery | Never leave the gate open; restart into `IDLE` |

## KeyEvent investigation

The first implementation milestone is a diagnostic screen and persistent log containing:

- key code and human-readable name;
- `ACTION_DOWN`, `ACTION_UP`, repeat count, scan code, device ID, source, flags, and event time;
- behavior with screen on, screen locked, and screen off;
- behavior when the app is foreground, background, and the service is active.

On XS17, `adb shell getevent -lt` recorded the tested side button as `/dev/input/event0` (`mtk-kpd`), `KEY_VOLUMEUP` down/up. Android therefore maps the intended button to volume up, not a media key. The app uses an explicitly user-enabled AccessibilityService key filter to turn only volume-up into PTT while a stream is active. When no stream is active, it passes the key through as normal volume control.

The power button is not selected for V1. Android's WindowManager policy handles `KEYCODE_POWER` as a system key before normal app dispatch, so an ordinary app cannot rely on Activity or AccessibilityService callbacks to implement press-and-hold PTT with it. The power button's raw kernel event was not confirmed on this device; this is a platform-level design constraint, not a claim that a device-specific low-level mapping was measured. See the [Android 12 power-key policy](https://android.googlesource.com/platform/frameworks/base/%2B/refs/tags/android-vts-12.0_r11/services/core/java/com/android/server/policy/PhoneWindowManager.java).

The diagnostic screen records foreground `dispatchKeyEvent` events and saves them to the app's private `key-event-probe.log`. Foreground physical-key testing on XS17 confirmed `KEYCODE_VOLUME_UP` (24, scanCode 115) DOWN/UP; an accidental volume-down press was recorded as `KEYCODE_VOLUME_DOWN` (25, scanCode 114) DOWN/UP. A MediaSession remains available for devices whose PTT input is routed as a media key. Screen-off volume-key capture uses the accessibility key filter; the service is enabled on this device, but capture while a stream is active and display-off behavior still need validation.

## Implementation status

- Probe Activity and persistent event log: implemented, built, and installed on XS17.
- XS17 kernel input mapping: observed as `mtk-kpd / KEY_VOLUMEUP` down/up.
- AccessibilityService capture of volume up during streaming: implemented and enabled on XS17, but behavior during an active stream and with display off remains unvalidated.
- Start muted, stream zeroed PCM while muted, unmute and short vibration on press, remute on release: implemented; physical button/app path and audio gate still need end-to-end validation.
- Foreground capture and AndroidMic USB Accessory transport: inherited from upstream.
- USB reconnect logic: implemented but not validated with a running PC USB Accessory receiver. Boot auto-start and hard enforcement of 48 kHz/i16/mono remain pending. Screen-off side-key behavior requires enabling the accessibility service and testing on-device.

## Safety and latency

The gate close path is higher priority than UI updates. Measure `ACTION_UP -> first muted frame` and `ACTION_DOWN -> first unmuted frame` separately. Acceptance targets are documented in test results rather than assumed before the target hardware is available.
