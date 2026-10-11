#!/usr/bin/env bash
# Signs an APK with the original key, the one every release up to 6.0.0 is signed with. That key
# was committed to this repository, so it protects nothing; the release job uses it only for
# 6.0.0 and only while the new key's secrets are not set (tools/rotate-signing-key.sh sets them).
# It signs the way ci/rotation-check.sh signs its "old" copy (v2 and v3), then checks that the
# certificate is ORIGINAL_CERT_SHA256.
#
#   ORIGINAL_CERT_SHA256=... bash ci/sign-original.sh app.apk
#
# Needs the commit that still held the key (a full clone, or it is fetched). The APK is signed in place.
set -euo pipefail
APK="$1"
: "${ORIGINAL_CERT_SHA256:?ORIGINAL_CERT_SHA256 must hold the SHA-256 digest of the original certificate}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APKSIGNER="$("$HERE/apksigner-path.sh")"
OLD_COMMIT=514feafd0ff414035e88ba66700a47e9a392de1a
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git cat-file -e "$OLD_COMMIT" 2> /dev/null || git fetch --quiet origin "$OLD_COMMIT"
git show "$OLD_COMMIT:keystore/klangbild-release.jks" > "$WORK/original.jks"
ORIGINAL_PASS="$(git show "$OLD_COMMIT:app/build.gradle.kts" | sed -n 's/^ *storePassword = "\(.*\)"$/\1/p')"
export ORIGINAL_PASS

"$APKSIGNER" sign \
  --ks "$WORK/original.jks" --ks-key-alias klangbild --ks-pass env:ORIGINAL_PASS \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out "$WORK/signed.apk" "$APK"
"$APKSIGNER" verify --verbose --print-certs "$WORK/signed.apk" | tee "$WORK/verify.txt"
# Every scheme's signer must be the original certificate, and there must be one.
certs="$(sed -n 's/^.*[Ss]igner.* certificate SHA-256 digest: //p' "$WORK/verify.txt" | sort -u)"
if [ "$certs" != "$ORIGINAL_CERT_SHA256" ]; then
  echo "The APK is not signed with the original key $ORIGINAL_CERT_SHA256." >&2
  exit 1
fi
mv "$WORK/signed.apk" "$APK"
