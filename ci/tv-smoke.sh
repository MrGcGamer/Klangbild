#!/usr/bin/env bash
# Starts a stand-in Home Assistant on the host (ci/fake_ha.py), installs the release APK on
# the running Android TV emulator, points it at that server with ADB extras and walks through
# the main states: playing, exact time on a key press, the settings menu, a track change,
# new extras while running, the quick settings and the Fensterblick. Checks that the app is
# still alive and never crashed. Leaves screenshots, logcat and gfxinfo in smoke/.
set -u
APK="$1"
PKG=io.github.hazymorning.klangbild
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p smoke

python3 "$HERE/fake_ha.py" 8123 > smoke/fake_ha.log 2>&1 &
FAKE=$!
trap 'kill $FAKE 2>/dev/null || true' EXIT

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
adb logcat -c || true

adb install -r "$APK"
# The emulator reaches the host's loopback at 10.0.2.2 (a private address: cleartext allowed).
adb shell am start -n "$PKG/.MainActivity" \
  --es ha_url http://10.0.2.2:8123 --es token smoke --es entity media_player.wohnzimmer
sleep 25
adb exec-out screencap -p > smoke/1-playing.png
# The very first start takes the connection from its extras: it must not stay on the setup page.
if grep -q "subscribe_entities" smoke/fake_ha.log; then
  echo "Erster Start: verbunden."
else
  echo "Hinweis: Beim ersten Start hat sich Klangbild nicht verbunden (Einrichtungsseite):"
  adb logcat -d -s Klangbild:I | tail -20
fi
# a key press brings hidden parts (time, place, progress) back for a moment
adb shell input keyevent KEYCODE_DPAD_UP
sleep 2
adb exec-out screencap -p > smoke/2-peek.png
# the settings menu: open with a long press, move down, close with Back
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER
sleep 2
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_DOWN
sleep 2
adb exec-out screencap -p > smoke/3-menu.png
adb shell input keyevent KEYCODE_BACK
# the stand-in server starts the next track 40 s after the app subscribed
sleep 22
adb exec-out screencap -p > smoke/4-next-track.png

# new extras while running must not restart the app
adb shell am start -n "$PKG/.MainActivity" --es light dezent
sleep 4
adb exec-out screencap -p > smoke/5-light-subtle.png

# the quick settings rise from the bottom edge on down, up closes them
adb shell input keyevent KEYCODE_DPAD_DOWN
sleep 2
adb exec-out screencap -p > smoke/6-quick-settings.png
adb shell input keyevent KEYCODE_DPAD_UP
sleep 1

# the Fensterblick on request (its picture is baked first); asked for, it stays until it is
# left – Back brings the stage back
adb shell am start -n "$PKG/.MainActivity" --es fensterblick an
sleep 20
adb exec-out screencap -p > smoke/7-fensterblick.png
adb shell input keyevent KEYCODE_BACK
sleep 5
adb exec-out screencap -p > smoke/8-back-to-stage.png

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
if ! grep -q "subscribe_entities" smoke/fake_ha.log; then
  echo "Klangbild hat sich nicht mit dem Ersatz-Home-Assistant verbunden:"
  cat smoke/fake_ha.log
  exit 1
fi
if ! grep -q "get_config" smoke/fake_ha.log; then
  echo "Hinweis: Klangbild hat Home Assistant nicht nach seinem Standort gefragt."
fi
echo "Was die App protokolliert hat:"
grep " Klangbild: " smoke/logcat.txt | tail -20 || true
echo "Smoke-Test bestanden (PID $PID)."
