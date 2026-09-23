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

The diagnostic must identify whether the side button is delivered as a normal `KeyEvent`, a vendor broadcast, or only through an accessibility/input device API. No key code is hard-coded until this test is completed on the X19 Pro.

## Safety and latency

The gate close path is higher priority than UI updates. Measure `ACTION_UP -> first muted frame` and `ACTION_DOWN -> first unmuted frame` separately. Acceptance targets are documented in test results rather than assumed before the target hardware is available.
