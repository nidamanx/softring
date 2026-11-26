# Maintaining SoftRing

Everything happens on GitHub: no local tools are required.

## Release key

The release signing key is never stored in the repository. The Release workflow
reads it from the repository secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD`.
`keystore/certificate-sha256.txt` holds the public fingerprint, also shown in the
"Verifying" section of both READMEs and on the author's website (ndmnet.eu, "Open
source tools" section, Italian and English pages): update it there too if the key
ever changes.

## Releasing a version

1. In `app/build.gradle.kts` increase `versionCode` (by 1) and set
   `versionName` (e.g. `1.1.0`).
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (English
   only, max 500 characters: stores show it for every language). In
   `CHANGELOG.md` move the `[Unreleased]` entries to `## [X.Y.Z] - YYYY-MM-DD`.
   Set the same version in the version badge of `README.md` and `README.it.md`,
   and `version` / `date-released` in `CITATION.cff`.
3. Commit, wait for the **Build** workflow to be green (its `version` job
   fails if any of the files above does not match `versionName`).
4. *Actions → Release → Run workflow*. It creates the tag `v<versionName>`,
   builds and signs the APK, checks the permissions and the certificate,
   creates the attestation and publishes the release with `SHA256SUMS`.
5. Fetch the tag created by the workflow into the local clone:
   `git fetch origin --tags`. Check the release page: the APK, `SHA256SUMS`
   and the signing certificate in the notes.

Obtainium users get the update automatically. F-Droid picks up the new tag
on its own (usually within a few days).

## Submitting to F-Droid (first time only)

1. In `fdroid/io.github.nidamanx.softring.yml` set `AllowedAPKSigningKeys`
   (content of `keystore/certificate-sha256.txt` in lowercase without colons)
   and, in `commit`, the full hash of the release commit: fdroiddata does not
   accept a tag there.
2. With a GitLab account, fork https://gitlab.com/fdroid/fdroiddata, add the
   file as `metadata/io.github.nidamanx.softring.yml` (web editor is fine) and
   open a merge request. F-Droid's CI checks the build and reproducibility;
   reviewers may ask for changes.

## Testing builds

- Every push builds a **debug APK** (*Build* run → *Artifacts*). Debug builds are a
  separate app, **SoftRing-D** (`io.github.nidamanx.softring.dev`), so they can be
  installed next to the released one. Switch on only one of the two at a time: both
  on would fight over the same volumes. In this repository debug builds are signed
  with a fixed debug key (`DEBUG_KEYSTORE_BASE64` and `DEBUG_KEYSTORE_PASSWORD`
  secrets), so each one installs over the previous one; the Build fails if the secrets
  are missing (except on pull requests, which may get no secrets) or the certificate
  differs from `keystore/debug-certificate-sha256.txt`. Forks and local builds use the
  SDK's own debug key. Losing the debug key only means generating a
  new one and reinstalling SoftRing-D once.
- Debug builds can keep a **diary** of the last volume events, shown at the
  bottom of the screen and in logcat (tag `SoftRing`). It is off: set `ENABLED`
  to `true` in `DebugLog.kt` to bring it back. Release builds never record
  anything.
- The **emulator test** runs only by hand (*Actions → Emulator smoke test → Run
  workflow*); the *Only this API level* field runs a single level (e.g. `30`).
  API 37 is experimental and runs only when asked for by name (`37.0`).
- **Dependabot** pull requests: merge them on GitHub once their Build is green, then
  bring the local copy up to date (`git pull --ff-only origin main`).
- The **icon** is generated: edit `art/generate_icon.py`, then run
  `python3 art/generate_icon.py` (needs `cairosvg` for the store PNG).
- The store **feature graphic** (1024×500, five languages, tagline taken from the
  app strings) is generated too: `python3 art/generate_feature_graphic.py` (needs
  `cairosvg` and the Roboto font). Run it again after changing the icon or a tagline.
- The README **install badges** "Coming soon to F-Droid" and "Download the APK" are
  generated: `python3 art/generate_badges.py` (same needs). The Obtainium badge is the
  official one, linked. Once the app is on F-Droid, replace the "coming soon" badge with
  the official "Get it on F-Droid" badge linking to the app page.

## Screenshots

Store screenshots are made by a workflow, in the five languages: *Actions →
Store screenshots → Run workflow* (manual only). It starts an Android 15 phone
emulator per language, switches the app on with every permission granted, with a
clean status bar, and takes four screenshots: main screen in the morning (light
theme), main screen during the night, Quick Settings tile and About page (dark
theme). The display is made a little smaller when needed, so the whole main
screen fits in one picture. The artifact `store-screenshots` is laid out like the
repository: copy its `fastlane/` folder over the one here, check the images and
commit them. Run it again when the screens change. To try a change to the script,
choose a single language (one job instead of five). The emulator test
screenshots are for checking the layout only.
