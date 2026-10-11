#!/usr/bin/env bash
# Checks signing key rotation on the running emulator, with throwaway keys made here. A copy of
# the APK signed with the "old" key the way earlier releases were signed is installed; the same
# APK signed with the "new" key and the lineage (exactly as ci/sign-release.sh signs a release)
# must install over it with adb install -r and keep the app's data; after that a copy signed
# with the old key alone must be refused.
#
#   bash ci/rotation-check.sh klangbild.apk
set -euo pipefail
APK="$1"
PKG=io.github.hazymorning.klangbild
HERE="$(cd "$(dirname "$0")" && pwd)"
APKSIGNER="$("$HERE/apksigner-path.sh")"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

export OLD_PASS="old-$RANDOM-$RANDOM" NEW_PASS="new-$RANDOM-$RANDOM"
for key in old new; do
  pass_var="$(echo "$key" | tr a-z A-Z)_PASS"
  keytool -genkeypair -keystore "$WORK/$key.p12" -storetype PKCS12 -storepass "${!pass_var}" \
    -alias klangbild -keyalg RSA -keysize 4096 -validity 1000 -dname "CN=Rotation check $key" > /dev/null
done
bash "$HERE/make-lineage.sh" "$WORK/old.p12" "$WORK/new.p12" "$WORK/lineage"

# Earlier releases: v2 and v3 signatures with the old key.
"$APKSIGNER" sign --ks "$WORK/old.p12" --ks-key-alias klangbild --ks-pass env:OLD_PASS --out "$WORK/old.apk" "$APK"
cp "$APK" "$WORK/new.apk"
ORIGINAL_CERT_SHA256="$(keytool -list -v -keystore "$WORK/old.p12" -storepass "$OLD_PASS" -alias klangbild |
  sed -n 's/^.*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f')" \
  RELEASE_KEYSTORE_PASSWORD="$NEW_PASS" bash "$HERE/sign-release.sh" "$WORK/new.apk" "$WORK/new.p12" "$WORK/lineage"

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
echo "API level $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
adb uninstall "$PKG" > /dev/null 2>&1 || true

first_install() { adb shell dumpsys package "$PKG" | sed -n 's/^ *firstInstallTime=//p' | head -1 | tr -d '\r'; }

adb install "$WORK/old.apk"
before="$(first_install)"
if ! adb install -r "$WORK/new.apk"; then
  echo "An APK signed with the new key and the lineage did not install over the old key's copy." >&2
  exit 1
fi
after="$(first_install)"
if [ -z "$before" ] || [ "$before" != "$after" ]; then
  echo "The update did not keep the installed app (first install $before, now $after)." >&2
  exit 1
fi
if out="$(adb install -r "$WORK/old.apk" 2>&1)"; then
  echo "The old key could still sign an update after the rotation." >&2
  exit 1
fi
echo "$out"
if ! echo "$out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
  echo "The old key's copy was refused for another reason than its signature." >&2
  exit 1
fi
adb uninstall "$PKG" > /dev/null
echo "Key rotation works: the new key updates the old install, the old key is refused."
