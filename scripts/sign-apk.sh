#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Signs an unsigned APK with apksigner. Used by the Release workflow; the Build
# workflow runs it with a throwaway key made in the job, so it is tested on every run.
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/sign-apk.sh [-h] <keystore.p12> <alias> <unsigned.apk> <signed.apk>

  The keystore password comes from the environment variable SOFTRING_KEYSTORE_PASSWORD
  (never from the command line). Needs zipalign and apksigner from the Android SDK
  build-tools ($ANDROID_HOME). Signature schemes: v2 and v3 (v1 is not needed from
  Android 7, minSdk is 26).
USAGE
}

if [[ ${1:-} == -h ]]; then
  usage
  exit 0
fi
if [[ $# -ne 4 ]]; then
  usage >&2
  exit 2
fi
keystore=$1
alias=$2
unsigned=$3
signed=$4
: "${SOFTRING_KEYSTORE_PASSWORD:?SOFTRING_KEYSTORE_PASSWORD is not set}"
: "${ANDROID_HOME:?ANDROID_HOME is not set}"

tools=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
if [[ -z $tools || ! -x $tools/apksigner || ! -x $tools/zipalign ]]; then
  echo "::error::apksigner or zipalign not found in $ANDROID_HOME/build-tools" >&2
  exit 1
fi

# apksigner keeps the alignment that the Android build already gave the APK
if ! "$tools/zipalign" -c 4 "$unsigned" >/dev/null; then
  echo "::error::$unsigned is not aligned" >&2
  exit 1
fi
mkdir -p "$(dirname "$signed")"
"$tools/apksigner" sign \
  --ks "$keystore" --ks-type PKCS12 --ks-key-alias "$alias" \
  --ks-pass env:SOFTRING_KEYSTORE_PASSWORD --key-pass env:SOFTRING_KEYSTORE_PASSWORD \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --v4-signing-enabled false \
  --out "$signed" "$unsigned"
"$tools/apksigner" verify "$signed"
echo "Signed: $signed"
