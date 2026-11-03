# keystore/

- `certificate-sha256.txt`: the public fingerprint of the release signing
  certificate. Users compare it with the installed app; the Release workflow
  refuses an APK signed with any other key.
- `debug-certificate-sha256.txt`: the public fingerprint of the debug signing
  certificate (test builds, "SoftRing-D"). The Build workflow of the private
  development repository refuses a debug APK signed with any other key.

The keys themselves are never stored in the repository, not even encrypted:
they live only in the repository secrets and in an offline backup
(docs/MAINTAINING.md); the Build workflow fails if a key file appears
(`scripts/check-secrets.sh`). Never replace the release key: a new key means users
cannot update and must uninstall and reinstall the app.
