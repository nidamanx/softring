# keystore/

- `certificate-sha256.txt`: the public fingerprint of the release signing
  certificate. Users compare it with the installed app; the Release workflow
  refuses an APK signed with any other key.
- `debug.p12`: the **debug** key (PKCS12, alias `androiddebugkey`, password
  `android`), public on purpose so every debug build has the same signature and
  installs over the previous one. It only signs test APKs; releases are signed
  with the release key, so a debug APK can never update a released app.

The release key itself is never stored in the repository, not even encrypted:
it lives only in the repository secrets and in an offline backup
(docs/MAINTAINING.md). Never replace it: a new key means users cannot update
and must uninstall and reinstall the app.
