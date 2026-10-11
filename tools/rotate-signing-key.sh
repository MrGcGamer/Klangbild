#!/usr/bin/env bash
# Moves Klangbild's releases to a new signing key. Run it once, on macOS or Linux, with the
# GitHub CLI logged in as an admin of the repository (gh auth login) and Java 17 or later
# (Android Studio's own is found). Nothing else is needed, not even a clone:
#
#   curl -fsSL https://raw.githubusercontent.com/hazymorning/Klangbild/main/tools/rotate-signing-key.sh | bash
#
# Releases up to 6.0.0 are signed with a key that was committed to this repository, so anyone
# can use it. This script takes that key from the history, creates a new one and writes the
# lineage that lets installed copies accept the new key (ci/make-lineage.sh). It signs the
# latest release's APK with both to check them (ci/sign-release.sh), stores the new key, its
# password and the lineage as the GitHub Actions secrets the release job signs with, and deletes
# every local copy: the new key exists only in the secrets. It refuses to run a second time,
# since copies that already accepted one new key would refuse another.
set -euo pipefail
REPO=hazymorning/Klangbild
OLD_COMMIT=514feafd0ff414035e88ba66700a47e9a392de1a
ORIGINAL_CERT_SHA256=6a27430a5a3a6507f770002420f7972ba2a99320ed557e32dde734eceaf837d9
# apksigner from Android's build tools 35, used only when no Android SDK is installed. The tool
# runs on Java, so the Linux archive serves every system.
BUILD_TOOLS_URL=https://dl.google.com/android/repository/build-tools_r35_linux.zip
BUILD_TOOLS_SHA256=bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88

say() { printf '%s\n' "$*"; }
fail() { printf '%s\n' "$*" >&2; exit 1; }
java_works() { java -version > /dev/null 2>&1 && command -v keytool > /dev/null; }

# Everything runs from here, so that "curl ... | bash" has read the whole script before it starts.
main() {
  WORK="$(mktemp -d)"
  trap 'rm -rf "$WORK"' EXIT

  command -v gh > /dev/null || fail "Install the GitHub CLI first: https://cli.github.com"
  gh auth status > /dev/null 2>&1 || fail "Log in to GitHub first: gh auth login"
  local names
  names="$(gh secret list -R "$REPO")" || fail "Could not read the secrets of $REPO: an admin of it must run this."
  if printf '%s\n' "$names" | cut -f1 | grep -qx RELEASE_KEYSTORE; then
    fail "The new key is already in the secrets. Nothing to do."
  fi

  # Java: on the path, in JAVA_HOME or Android Studio's own.
  if ! java_works; then
    local dir
    for dir in "${JAVA_HOME:+$JAVA_HOME/bin}" \
      "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin" \
      "$HOME/android-studio/jbr/bin" "/opt/android-studio/jbr/bin" "/snap/android-studio/current/jbr/bin"; do
      if [ -n "$dir" ] && [ -x "$dir/java" ]; then
        PATH="$dir:$PATH"
        java_works && break
      fi
    done
  fi
  java_works || fail "Install Java 17 or later first (Android Studio brings one)."

  say "Fetching the repository ..."
  gh repo clone "$REPO" "$WORK/repo" -- --quiet --depth=1
  cd "$WORK/repo"

  if ! APKSIGNER="$(bash ci/apksigner-path.sh 2> /dev/null)"; then
    say "Fetching apksigner from Android's build tools ..."
    curl -fsSL -o "$WORK/build-tools.zip" "$BUILD_TOOLS_URL"
    local sum
    sum="$( (sha256sum "$WORK/build-tools.zip" 2> /dev/null || shasum -a 256 "$WORK/build-tools.zip") | cut -d' ' -f1)"
    [ "$sum" = "$BUILD_TOOLS_SHA256" ] || fail "The build tools download does not match its checksum."
    unzip -q "$WORK/build-tools.zip" 'android-15/apksigner' 'android-15/lib/apksigner.jar' -d "$WORK"
    chmod +x "$WORK/android-15/apksigner"
    APKSIGNER="$WORK/android-15/apksigner"
  fi
  export APKSIGNER

  say "Making the new key and its lineage ..."
  git fetch --quiet --depth=1 origin "$OLD_COMMIT"
  git show "$OLD_COMMIT:keystore/klangbild-release.jks" > "$WORK/old.jks"
  OLD_PASS="$(git show "$OLD_COMMIT:app/build.gradle.kts" | sed -n 's/^ *storePassword = "\(.*\)"$/\1/p')"
  NEW_PASS="$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
  export OLD_PASS NEW_PASS
  keytool -genkeypair -keystore "$WORK/new.p12" -storetype PKCS12 -storepass "$NEW_PASS" \
    -alias klangbild -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Klangbild, O=hazymorning" > /dev/null 2>&1
  bash ci/make-lineage.sh "$WORK/old.jks" "$WORK/new.p12" "$WORK/lineage"

  say "Checking them on the latest release ..."
  gh release download -R "$REPO" --pattern klangbild.apk --dir "$WORK"
  ORIGINAL_CERT_SHA256="$ORIGINAL_CERT_SHA256" RELEASE_KEYSTORE_PASSWORD="$NEW_PASS" \
    bash ci/sign-release.sh "$WORK/klangbild.apk" "$WORK/new.p12" "$WORK/lineage" > /dev/null

  say "Storing them as repository secrets ..."
  base64 < "$WORK/new.p12" | tr -d '\n' | gh secret set RELEASE_KEYSTORE -R "$REPO"
  printf '%s' "$NEW_PASS" | gh secret set RELEASE_KEYSTORE_PASSWORD -R "$REPO"
  base64 < "$WORK/lineage" | tr -d '\n' | gh secret set RELEASE_LINEAGE -R "$REPO"
  say "Done. The new key exists only in the repository's secrets; the next release is signed with it."
}

main "$@"
