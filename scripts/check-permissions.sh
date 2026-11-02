#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Fails if the APK's permissions differ from config/allowed-permissions.txt.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/check-permissions.sh [-h] <apk> [allowed-list]

  apk           APK to check (needs aapt2 from the Android SDK build-tools)
  allowed-list  default: config/allowed-permissions.txt
EOF
}
if [[ "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi
if [[ $# -lt 1 ]]; then
  usage >&2
  exit 2
fi

apk="$1"
allowed="${2:-config/allowed-permissions.txt}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

aapt2="$(find "$sdk/build-tools" -mindepth 2 -maxdepth 2 -name aapt2 2>/dev/null | sort -V | tail -n 1 || true)"
if [[ -z "$aapt2" ]]; then
  echo "::error::aapt2 not found in $sdk/build-tools" >&2
  exit 1
fi

dump="$("$aapt2" dump permissions "$apk")"
actual="$(grep -E '^uses-permission' <<<"$dump" | sed -E "s/.*name='([^']+)'.*/\1/" | sort -u)"
expected="$(grep -vE '^[[:space:]]*(#|$)' "$allowed" | sort -u)"
declared="$(grep -E '^permission:' <<<"$dump" || true)"

echo "Requested permissions:"
while IFS= read -r perm; do echo "  $perm"; done <<<"$actual"

status=0
if [[ "$actual" != "$expected" ]]; then
  echo "::error::Permissions differ from $allowed"
  diff <(echo "$expected") <(echo "$actual") || true
  status=1
fi
if [[ -n "$declared" ]]; then
  echo "::error::The APK declares its own permissions, which is not expected:"
  echo "$declared"
  status=1
fi
exit "$status"
