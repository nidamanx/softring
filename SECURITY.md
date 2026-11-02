# Security Policy

## Supported versions

Only the latest release receives fixes.

| Version | Supported |
| ------- | --------- |
| latest  | yes       |
| older   | no        |

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Use GitHub's private reporting instead: open the **Security** tab of this
repository and click **Report a vulnerability**. You will get a private
channel with the maintainer.

Useful details to include:

- app version (Settings → Apps → SoftRing) and where you installed it from
  (GitHub release, Obtainium, F-Droid);
- device model, Android version and, if not stock, the OS (for example
  GrapheneOS);
- steps to reproduce;
- the impact you expect (for example "a component reachable by other apps" or
  "a release APK signed with a different key").

## What is in scope

- Components that other apps can reach: receivers, activities or alarms that
  can be triggered from outside the app to change the volume.
- Any permission requested beyond the five listed in the README, and any
  network access.
- Data written outside the app's private storage, or readable by other apps.
- Changes to streams or settings the app must never touch: alarm and media
  volume, ringer mode (vibrate, silent).
- Release integrity: an APK whose signing certificate, `SHA256SUMS` or build
  provenance attestation does not match what the README says.

## What is out of scope

- Volume behavior decided by the system or the device maker, such as ringtone
  and notification volumes linked on some devices (see "Known limitations" in
  the README).
- Alarms delayed by Android when "Alarms & reminders" is not granted.
- APKs obtained from sources other than those listed in the README.
- Vulnerabilities of Android itself or of the device firmware.

## Disclosure

This is a personal project maintained on a best-effort basis. Reports are
acknowledged as soon as reasonably possible and fixes are released before any
public disclosure.
