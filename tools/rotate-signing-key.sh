#!/usr/bin/env bash
# Moves Klangbild's releases to a new signing key. Run it once, in a clone of the repository,
# with gh logged in as an admin of the repository and the Android SDK installed (ANDROID_HOME):
#
#   bash tools/rotate-signing-key.sh
#
# Releases up to 6.0.0 were signed with a key that was committed to this repository, so anyone
# can use it. This script takes that key from the history, creates a new one and writes the
# lineage that lets installed copies accept the new key (ci/make-lineage.sh). It stores the new
# key, its password and the lineage as the GitHub Actions secrets the release job signs with
# (ci/sign-release.sh), then deletes every local copy: the new key exists only in the secrets.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
OLD_COMMIT=514feafd0ff414035e88ba66700a47e9a392de1a
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git cat-file -e "$OLD_COMMIT" 2> /dev/null || git fetch --quiet origin "$OLD_COMMIT"
git show "$OLD_COMMIT:keystore/klangbild-release.jks" > "$WORK/old.jks"
OLD_PASS="$(git show "$OLD_COMMIT:app/build.gradle.kts" | sed -n 's/^ *storePassword = "\(.*\)"$/\1/p')"
NEW_PASS="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
export OLD_PASS NEW_PASS

keytool -genkeypair -keystore "$WORK/new.p12" -storetype PKCS12 -storepass "$NEW_PASS" \
  -alias klangbild -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Klangbild, O=hazymorning" > /dev/null
bash ci/make-lineage.sh "$WORK/old.jks" "$WORK/new.p12" "$WORK/lineage"

base64 < "$WORK/new.p12" | tr -d '\n' | gh secret set RELEASE_KEYSTORE
printf '%s' "$NEW_PASS" | gh secret set RELEASE_KEYSTORE_PASSWORD
base64 < "$WORK/lineage" | tr -d '\n' | gh secret set RELEASE_LINEAGE
echo "The new signing key and its lineage are stored as repository secrets."
echo "The next version tag is signed with the new key."
