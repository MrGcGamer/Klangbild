#!/usr/bin/env bash
# Writes the signing certificate lineage that moves Klangbild from an old signing key to a new
# one (APK Signature Scheme v3 key rotation). The old key keeps only the capability an update
# needs: installed copies accept APKs signed with the new key and keep their data. It cannot
# sign an update for a copy that has seen the new key (no rollback), cannot share a user id and
# gets no signature permissions.
#
#   OLD_PASS=... NEW_PASS=... bash ci/make-lineage.sh old.jks new.p12 lineage.bin
#
# Both keystores hold their key under the alias "klangbild".
set -euo pipefail
OLD_KS="$1"
NEW_KS="$2"
OUT="$3"
: "${OLD_PASS:?OLD_PASS must hold the password of the old keystore}"
: "${NEW_PASS:?NEW_PASS must hold the password of the new keystore}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APKSIGNER="$("$HERE/apksigner-path.sh")"

"$APKSIGNER" rotate --out "$OUT" \
  --old-signer --ks "$OLD_KS" --ks-key-alias klangbild --ks-pass env:OLD_PASS \
  --set-installed-data true --set-shared-uid false --set-permission false --set-rollback false --set-auth false \
  --new-signer --ks "$NEW_KS" --ks-key-alias klangbild --ks-pass env:NEW_PASS
