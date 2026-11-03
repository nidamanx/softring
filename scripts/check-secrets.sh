#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Fails if a key, credential or other secret is in the repository, even encrypted:
# secrets live only in CI secrets and in the owner's password manager.
set -euo pipefail

# Files that look like secrets but are public on purpose (e.g. a public debug key).
# One repository path per entry; keep this list as short as possible.
ALLOWED=(
  keystore/debug.p12  # public debug key, on purpose (keystore/README.md)
)

usage() {
  cat <<'USAGE'
Usage: scripts/check-secrets.sh [-h] [--staged | --range <rev-list args>...]

  (no option)  all tracked files (CI)
  --staged     the files staged for commit (pre-commit hook)
  --range      every file added or changed by each commit selected by the git
               rev-list arguments, e.g. --range origin/main..HEAD (pre-push hook):
               also catches a secret that a later commit removed

Refuses secrets by file name (keystores, private keys, encrypted files, .env,
credential files) and by content (private keys, encrypted files, access tokens).
USAGE
}

mode=tree
case "${1:-}" in
  -h|--help) usage; exit 0 ;;
  --staged) mode=staged ;;
  --range) mode=range; shift; (( $# )) || { usage >&2; exit 2; } ;;
  "") ;;
  *) usage >&2; exit 2 ;;
esac

name_re='\.(jks|keystore|p12|pfx|enc|key|pem|gpg|age|kdbx|ppk)$|(^|/)(\.env(\.[^/]*)?|id_(rsa|dsa|ecdsa|ed25519)|\.netrc|\.pgpass|\.htpasswd|credentials(\.json)?|secrets?\.(json|ya?ml|toml|env))$'
name_ok_re='(^|/)\.env\.(example|sample|template)$'
# -{5} instead of five dashes, so this line does not match itself
content_re='-{5}BEGIN ([A-Z0-9]+ )*PRIVATE KEY( BLOCK)?-{5}|-{5}BEGIN PGP MESSAGE-{5}|ghp_[A-Za-z0-9]{36}|gh[ousr]_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{60,}|glpat-[A-Za-z0-9_-]{20}|AKIA[0-9A-Z]{16}|xox[abprs]-[A-Za-z0-9-]{10,}|AIza[0-9A-Za-z_-]{35}|sk-ant-[A-Za-z0-9_-]{20,}|sk-(proj-)?[A-Za-z0-9]{40,}'

fail=0
refuse() {  # where, reason
  echo "::error::$1: $2: keys and secrets never go in the repository, not even encrypted"
  fail=1
}

check() {  # label, path, object ("" = working tree file)
  local label=$1 f=$2 obj=$3 a
  for a in "${ALLOWED[@]}"; do [[ $f == "$a" ]] && return 0; done
  if [[ ${f,,} =~ $name_re && ! ${f,,} =~ $name_ok_re ]]; then
    refuse "$label" "secret file name"
    return 0
  fi
  if [[ -z $obj ]]; then
    [[ -f $f && ! -L $f ]] || return 0
  fi
  read_it() { if [[ -n $obj ]]; then git cat-file blob "$obj"; else cat -- "$f"; fi; }
  local head
  head=$(read_it | head -c 21 | tr -d '\0') || true
  # Headers of "openssl enc" (with salt) and of age
  if [[ $head == Salted__* || $head == age-encryption.org/v1 ]]; then
    refuse "$label" "encrypted file"
  # No grep -q: it stops reading early and, with pipefail, the writer's SIGPIPE
  # would turn a match into a failure
  elif read_it | grep -aE -- "$content_re" >/dev/null; then
    refuse "$label" "private key or access token"
  fi
}

case $mode in
  tree)
    while IFS= read -r f; do [[ -n $f ]] && check "$f" "$f" ""; done < <(git ls-files)
    ;;
  staged)
    while IFS= read -r f; do
      [[ -n $f ]] && check "$f" "$f" ":$f"
    done < <(git diff --cached --name-only --diff-filter=ACMR)
    ;;
  range)
    while IFS= read -r c; do
      while IFS= read -r f; do
        [[ -n $f ]] && check "${c:0:7}:$f" "$f" "$c:$f"
      done < <(git diff-tree --no-commit-id --name-only -r --root --diff-filter=ACMR "$c")
    done < <(git rev-list "$@")
    ;;
esac
exit "$fail"
