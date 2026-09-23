# Handoff: Android PTT Mic (2026-09-24)

## Goal

Use the XS17 phone's volume-up side key as push-to-talk, sending phone microphone audio to a Windows PC for OBS and ChatGPT voice input. OBS broadcasting is managed by the user; do not start a stream as part of setup.

## Verified device and PC setup

- Phone: XS17, Android reports model A25. AndroidMic 2.2.9 debug is installed.
- Transport: Wi-Fi/TCP on the same trusted LAN. AndroidMic on the PC listens on port `54345`; at the last check the phone had an established TCP connection to the PC and the Android screen reported `Microphone has started to record` and `Streaming Mode: WIFI`.
- Audio format: 48 kHz, signed PCM16 (`i16`), mono.
- PC playback endpoint: `CABLE Input (VB-Audio Virtual Cable)`.
- Applications should select `CABLE Output (VB-Audio Virtual Cable)` as their microphone input.
- The OBS scene collection has an input capture source configured for CABLE Output. The user confirmed that PTT audio reaches OBS. The exact live scene should be checked in OBS if switching collections or profiles.
- The user enabled Android's `PTT side button input` accessibility service and confirmed the volume UI no longer appears when using the side key. The service only filters volume-up while a stream is active; when disconnected, volume behavior remains normal.
- The user manages YouTube/OBS broadcasting. No stream was started during this setup.

## Reconnect procedure

1. Connect the phone and PC to the same trusted Wi-Fi network.
2. Start AndroidMic on the PC in TCP/Wi-Fi mode. Select `CABLE Input` as its output device and listen on the PC's current Wi-Fi IPv4 address, port `54345`.
3. On Android, select Wi-Fi mode and enter that PC IPv4 address and port. Tap Connect and confirm the screen reports Wi-Fi streaming and microphone recording.
4. Keep `PTT side button input` enabled in Android Accessibility settings. Press and hold the phone's volume-up side key while speaking; releasing the key should close the PTT gate.
5. In OBS and ChatGPT, select `CABLE Output` as the microphone. Verify OBS's audio meter before the user starts their own broadcast.

TCP transport is unauthenticated and unencrypted. Keep it on a trusted LAN; do not expose the receiver port through router port forwarding.

## Remaining verification

- The user has confirmed audio reaches OBS. Re-test press/release muting after reconnects and when the phone screen is off.
- Select CABLE Output in ChatGPT's microphone input and verify an actual voice conversation; this selection was not confirmed during setup.
- USB accessory transport was not used: the phone app reported no USB accessory. Wi-Fi/TCP is the working path.

## Repository state

- This repository is `yousayrock/android-ptt-mic`, branch `main`.
- The V1 audio profile and PTT controls are already present in the current main commit. This handoff records the operational PC/phone setup and verification status; local Windows/OBS configuration files are outside the repository.
- XS17 reports the intended side key as `KEYCODE_VOLUME_UP` (24), scan code 115. The power key is intercepted by Android; volume-up is the supported V1 PTT key.
