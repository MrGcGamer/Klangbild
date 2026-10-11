#!/usr/bin/env bash
# Prints the path of apksigner from the newest Android build tools installed (key rotation needs
# build tools 33 or later), looked for in ANDROID_HOME, ANDROID_SDK_ROOT, local.properties and
# where Android Studio puts the SDK.
set -euo pipefail
for SDK in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
  "$( [ -f local.properties ] && sed -n 's/^sdk\.dir=//p' local.properties || true)" \
  "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  [ -n "$SDK" ] && [ -d "$SDK/build-tools" ] || continue
  BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
  if [ -n "$BUILD_TOOLS" ] && [ -x "$BUILD_TOOLS/apksigner" ]; then
    echo "$BUILD_TOOLS/apksigner"
    exit 0
  fi
done
echo "No apksigner found: set ANDROID_HOME to the Android SDK." >&2
exit 1
