# Roadmap

## Phase 0 — design and baseline

- [x] Fork AndroidMic under `yousayrock/android-ptt-mic`.
- [x] Preserve upstream GPL-3.0 license and document source/distribution obligations.
- [x] Define PTT state machine, USB-first transport, audio contract, and car-noise test matrix.

## Phase 1 — X19 Pro input probe

- [x] Add diagnostic KeyEvent capture and local logs.
- [x] Add user-enabled AccessibilityService capture for XS17's volume-up key while streaming.
- [ ] Test display on/off, lock screen, background, and foreground service cases.
- [x] Record the confirmed kernel event source and key code in a device profile.

## Phase 2 — V1 PTT service

- [x] Reuse AndroidMic Foreground Service microphone capture; add PTT lifecycle integration.
- [x] Implement hard PTT gate, short press vibration, and immediate release mute.
- [x] Fix the V1 stream contract to 48 kHz / 16-bit / mono and default to USB.
- [x] Add USB detach/attach detection and handshake retry.
- [x] Add OFF / STANDARD / CAR processing modes.

## Phase 3 — vehicle validation

- [ ] Run repeatable engine, road, HVAC, and speaker-feedback recordings.
- [ ] Compare quality, CPU, battery, artifacts, and latency.
- [ ] Test boot auto-wait only after service and permission behavior is stable.

## Phase 4 — release

- [ ] Document X19 Pro firmware/build assumptions.
- [ ] Publish source and build instructions with GPL notices.
- [ ] Provide PC setup and troubleshooting for the AndroidMic virtual microphone path.
