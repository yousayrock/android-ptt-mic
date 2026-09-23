# Handoff: Android PTT Mic (2026-09-24)

## Goal

Build `yousayrock/android-ptt-mic`, a small GPL-3.0 AndroidMic-based PTT microphone app. XS17 (also referred to as X19 Pro) sends its microphone to a PC; holding the phone's volume-up side key transmits, releasing it immediately mutes. USB is preferred, Wi-Fi/TCP is also requested. Prioritize low latency for in-car use.

## Confirmed so far

- Repository: `https://github.com/yousayrock/android-ptt-mic`, branch `main`.
- Existing commits include initial design, PTT implementation, power-key limitation, and device probe results (`a059bac`, `dc8c564`, `31ca3da`, `c82c58d`).
- XS17 physical volume-up was observed in the foreground key probe as `KEYCODE_VOLUME_UP` (24), scan code 115, down/up. Accidental volume-down was `KEYCODE_VOLUME_DOWN` (25), scan code 114. Power key is intercepted by Android; use volume-up for V1.
- Screen-off PTT while an active stream is not yet verified. USB accessory handshake and audio delivery are not verified.
- PC-side Codex reported AndroidMic 2.2.9 x64 receiver GUI running, VB-CABLE active, output set to CABLE Input, and TCP port 54345 listening. Audio handshake, actual format negotiation, microphone delivery, and release-to-mute are still unverified. The PC had saved 48 kHz / i16 / Stereo; do not claim mono conversion is verified.
- Coordinate with the PC-side task in [GitHub Issue #1](https://github.com/yousayrock/android-ptt-mic/issues/1). Latest phone-side coordination comment: `5799272537`.
- User says the phone is currently waiting and is not USB-connected. Use wireless debugging/ADB, then test Wi-Fi/TCP first while PC receiver is listening.
- Wireless ADB mDNS discovery returned an `_adb-tls-connect._tcp` service, but `adb connect` from this Codex environment failed with Windows socket error 10013, including an escalated attempt. ADB device list was empty. Pair/connect from a normal Windows PowerShell outside Codex if needed; use `adb mdns services`, `adb pair <ip>:<pairing-port>` only if unpaired, then `adb connect <ip>:<connect-port>`. Pairing and connect ports differ. Never post pairing codes publicly.

## Current uncommitted work

The worktree has edits to simplify V1 and a new visual reference. Inspect `git status` and `git diff` before continuing. Current intended changes:

- Lock the stream to 48 kHz / signed PCM16 / mono and microphone source.
- Remove inherited sample-rate, channel, sample-format, and audio-source settings.
- Keep only USB and Wi-Fi transport choices; retain OFF / STANDARD / CAR processing modes.
- Remove manual mute/unmute controls so only the held PTT key opens the mic.
- Add a small Compose pixel-pulse indicator active only while transmitting.
- Add mockup assets `docs/assets/ptt-home-mockup.png` and `docs/assets/ptt-pixel-pulse-4frame.png`, documented in `docs/UI_MOCKUP.md`.
- Simplify strings and update `docs/PTT_SPEC.md`, `docs/ROADMAP.md`, and README.

The above has **not been built or committed yet**. A build using the Gradle 9.1 cache failed with `Unable to establish loopback connection`. The wrapper requests Gradle 9.6.1 and cannot download it under the current network restrictions. Resolve Gradle/network access, then build before installing/testing.

`Android/work/jtmp` is a scratch temp directory created during the failed build; do not commit it.

## Immediate next steps

1. Review the current diff, compile issues, and `git status`; keep scratch files out of Git.
2. Remove any accidental/manual unmute path completely; ensure PTT press/release updates UI state and notification safely.
3. Build debug APK, install over wireless ADB, and verify app opens.
4. Ask user to select Wi-Fi/TCP and connect while PC receiver listens on 54345; coordinate start timing in Issue #1. Verify connection, PC audio, volume-up press audio, release silence, then screen-off behavior.
5. Test USB accessory after Wi-Fi path if device/cable becomes available.
6. Commit and push the V1 simplification only after build checks pass. Keep GUI/transport/audio claims limited to observed evidence.

## Design scope

V1 should stay small: connection, PTT key test, OFF / STANDARD / CAR processing mode, fixed audio format, and clear transmitting status. Avoid restoring upstream advanced audio configuration. Car-noise research should compare processing benefit against added latency and degradation; AEC/RNNoise are investigation items, not required V1 dependencies.

The requested “loop skill” was not available in this Codex environment; GitHub Issue #1 is being used to coordinate with the PC-side Codex.

