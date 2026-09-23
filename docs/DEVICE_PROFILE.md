# Device Profile: XS17

Status: foreground key-event discovery, 2026-09-24; screen-off/streaming behavior pending.

| Field | Observation |
| --- | --- |
| User device name | XS17 |
| Android-reported model | A25 |
| Manufacturer | iBRIT |
| Android version | 12 |
| Input device | `/dev/input/event0`, `mtk-kpd` |
| Tested side key | `KEY_VOLUMEUP`, down and up events |
| Android foreground event | `KEYCODE_VOLUME_UP` (24), scanCode 115, DOWN/UP |
| Other volume key observed | `KEYCODE_VOLUME_DOWN` (25), scanCode 114, DOWN/UP (user reported accidental press) |

The raw key was observed with `adb shell getevent -lt` while the user pressed and released the intended side button. The foreground diagnostic Activity subsequently recorded physical volume-up and volume-down `KeyEvent` DOWN/UP events, confirming app-level delivery while the probe was in the foreground. Screen-off delivery and sustained PTT audio have not yet been verified.

The current implementation filters volume-up through an explicitly enabled AccessibilityService only while a stream is active. With streaming inactive, the service leaves volume behavior unchanged. Confirm this policy and screen-off behavior on the target device before treating the profile as validated.
