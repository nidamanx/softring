#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Fails if a file disagrees with versionName / versionCode in app/build.gradle.kts.
# Before the first release CHANGELOG.md has no released section and CITATION.cff no
# date-released. Used by the Build and Release workflows.
set -euo pipefail

if [[ "${1:-}" == "-h" ]]; then
  cat <<'EOF'
Usage: scripts/check-version.sh [-h] [--release]

  --release  also require a released section "## [versionName] - YYYY-MM-DD" in
             CHANGELOG.md (used by the Release workflow)

Run from the repository root. Checks the version badges of README.md and
README.it.md, the English fastlane changelog for versionCode, the
latest released section of CHANGELOG.md, version and date-released in CITATION.cff.
EOF
  exit 0
fi
release=0
[[ "${1:-}" == "--release" ]] && release=1

ver=$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
code=$(sed -n 's/^[[:space:]]*versionCode = \([0-9]*\)$/\1/p' app/build.gradle.kts)
echo "versionName=$ver versionCode=$code"
fail=0
expect() {  # file, what, found, wanted
  if [[ $3 != "$4" ]]; then
    echo "::error file=$1::$2 is '$3', expected '$4'"
    fail=1
  fi
}
for f in README.md README.it.md CITATION.cff CHANGELOG.md; do
  [[ -f $f ]] || { echo "::error file=$f::$f is missing"; fail=1; }
done
for f in README.md README.it.md; do
  [[ -f $f ]] || continue
  expect "$f" "version badge" "$(sed -n 's/.*badge\/version-\([^-]*\)-informational.*/\1/p' "$f")" "$ver"
done
# Changelogs are English only: stores fall back to en-US for other languages
f="fastlane/metadata/android/en-US/changelogs/$code.txt"
[[ -f $f ]] || { echo "::error file=$f::$f is missing (versionCode $code)"; fail=1; }
released=$(grep -m1 -E '^## \[[0-9]' CHANGELOG.md || true)
date=""
if [[ -n $released ]]; then
  date=$(sed -n 's/^## \[[^]]*\] - \([0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}\)$/\1/p' <<<"$released")
  expect CHANGELOG.md "latest release" "$released" "## [$ver] - ${date:-YYYY-MM-DD}"
elif (( release )); then
  echo "::error file=CHANGELOG.md::no released section: move [Unreleased] to ## [$ver] - YYYY-MM-DD"
  fail=1
fi
if [[ -f CITATION.cff ]]; then
  expect CITATION.cff "version" "$(sed -n 's/^version: "\(.*\)"$/\1/p' CITATION.cff)" "$ver"
  expect CITATION.cff "date-released" "$(sed -n 's/^date-released: "\(.*\)"$/\1/p' CITATION.cff)" "$date"
fi
exit "$fail"
