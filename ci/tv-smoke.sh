#!/usr/bin/env bash
# Installs the release APK on the running Android TV emulator, starts it in demo mode and
# checks that it is still alive after a while (no crash). Leaves a screenshot and logcat.
set -u
APK="$1"
PKG=io.github.hazymorning.klangbild
mkdir -p smoke

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
adb logcat -c || true

adb install -r "$APK"
adb shell am start -n "$PKG/.MainActivity" --ez demo true
sleep 25
adb exec-out screencap -p > smoke/demo-1.png
# a key press shows exact times; then let the demo run into the next track
adb shell input keyevent KEYCODE_DPAD_UP
sleep 3
adb exec-out screencap -p > smoke/demo-2-exact-time.png
adb shell input keyevent KEYCODE_DPAD_RIGHT
sleep 8
adb exec-out screencap -p > smoke/demo-3-next-track.png

# new extras while running must not restart the app
adb shell am start -n "$PKG/.MainActivity" --ez demo true --ez setup false
sleep 3

PID="$(adb shell pidof "$PKG" | tr -d '\r')"
adb logcat -d > smoke/logcat.txt
adb shell dumpsys gfxinfo "$PKG" > smoke/gfxinfo.txt || true

if grep -q "FATAL EXCEPTION" smoke/logcat.txt; then
  echo "App ist abgestürzt:"
  grep -A 30 "FATAL EXCEPTION" smoke/logcat.txt | head -60
  exit 1
fi
if [ -z "$PID" ]; then
  echo "App läuft nicht mehr."
  exit 1
fi
echo "Smoke-Test bestanden (PID $PID)."
