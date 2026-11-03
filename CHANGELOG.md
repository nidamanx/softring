# Changelog

All notable changes to SoftRing are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- The About page also explains the start-up permission (re-applying your hours after
  a restart).

## [1.0.0] - 2026-11-02

First public release.

### Added

- Hours with configurable start and end (default 22:00–07:00).
- Starts switched off: nothing changes until you turn it on.
- "Always" option: the volume stays lowered until you turn SoftRing off.
- Separate lowered volume for calls and for messages and notifications.
- Volume preview: releasing a slider plays the sound at that level (Android 9+).
- The night only lowers the volume: a daytime volume that is already lower is kept.
- Automatic restore of daytime volumes in the morning.
- Works after a reboot, also before the first unlock (direct boot).
- Quick Settings tile: shows whether SoftRing is on, and until when, and switches it,
  with the phone unlocked.
- On vibrate or silent the volume is left as it is, and the status says so.
- Also works while a mode (Do Not Disturb, Bedtime…) that lets calls ring is on.
- Settings are not included in backups or device-to-device transfers, to protect your
  privacy.
- Works on Android 17, which ignores volume changes from apps that are not open: the
  change runs in a foreground service that lasts less than a second.
- About page (ⓘ): how it works, what it never touches, battery, privacy,
  permissions, version and author.
- English, Italian, French, German and Spanish.
