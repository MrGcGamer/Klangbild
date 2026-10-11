#!/usr/bin/env bash
# Prints the path of apksigner from the newest Android build tools installed (key rotation needs
# build tools 33 or later).
set -euo pipefail
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f local.properties ]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' local.properties)"
fi
if [ -z "$SDK" ]; then
  echo "Set ANDROID_HOME to the Android SDK." >&2
  exit 1
fi
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
if [ -z "$BUILD_TOOLS" ] || [ ! -x "$BUILD_TOOLS/apksigner" ]; then
  echo "No apksigner under $SDK/build-tools." >&2
  exit 1
fi
echo "$BUILD_TOOLS/apksigner"
