#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Fails if a key or other secret is in the repository, even encrypted: release keys
# live only in CI secrets. keystore/debug.p12 is the public debug key, allowed.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/check-secrets.sh [-h] [--staged]

  --staged  check the files staged for commit (pre-commit hook) instead of all
            tracked files (Build workflow)

Run from the repository root. Refuses key files by name (.jks, .keystore, .p12,
.pfx, .enc, .key, .pem) and by content (PEM/OpenSSH/PGP private keys, files
encrypted with "openssl enc").
EOF
}
staged=0
case "${1:-}" in
  -h) usage; exit 0 ;;
  --staged) staged=1 ;;
  "") ;;
  *) usage >&2; exit 2 ;;
esac

allowed='keystore/debug.p12'
if (( staged )); then
  files=$(git diff --cached --name-only --diff-filter=ACMR)
else
  files=$(git ls-files)
fi

content() {  # file content as it will be committed
  if (( staged )); then git show ":$1"; else cat -- "$1"; fi
}

fail=0
refuse() {
  echo "::error file=$1::$2: keys and secrets never go in the repository, not even encrypted"
  fail=1
}
while IFS= read -r f; do
  [[ -n $f && $f != "$allowed" ]] || continue
  if [[ ${f,,} =~ \.(jks|keystore|p12|pfx|enc|key|pem)$ ]]; then
    refuse "$f" "key file name"
    continue
  fi
  # "Salted__" is the header of every "openssl enc" file with a salt
  if [[ $(content "$f" | head -c 8 | tr -d '\0') == Salted__ ]]; then
    refuse "$f" "file encrypted with openssl enc"
  # No grep -q: it would stop reading early, and with pipefail the writer's SIGPIPE
  # would turn a match into a failure
  elif content "$f" | grep -aE -- '-----BEGIN ([A-Z0-9]+ )*PRIVATE KEY( BLOCK)?-----' >/dev/null; then
    refuse "$f" "private key"
  fi
done <<< "$files"
exit "$fail"
