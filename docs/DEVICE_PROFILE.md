# Device Profile: XS17

Status: initial input discovery, 2026-09-24.

| Field | Observation |
| --- | --- |
| User device name | XS17 |
| Android-reported model | A25 |
| Manufacturer | iBRIT |
| Android version | 12 |
| Input device | `/dev/input/event0`, `mtk-kpd` |
| Tested side key | `KEY_VOLUMEUP`, down and up events |

The raw key was observed with `adb shell getevent -lt` while the user pressed and released the intended side button. App-level `KeyEvent` code, screen-off delivery, and sustained PTT audio have not yet been verified.

The current implementation filters volume-up through an explicitly enabled AccessibilityService only while a stream is active. With streaming inactive, the service leaves volume behavior unchanged. Confirm this policy and screen-off behavior on the target device before treating the profile as validated.
