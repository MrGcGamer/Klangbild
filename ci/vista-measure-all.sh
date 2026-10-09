#!/usr/bin/env bash
# Runs the Fensterblick measurements chosen in the workflow (see ci/vista-measure.sh).
#   bash ci/vista-measure-all.sh klangbild.apk <prototyp|kurz|15 Minuten>
set -u
APK="$1"
MODE="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
case "$MODE" in
  prototyp)
    bash "$HERE/vista-measure.sh" "$APK" 90 proto-steady --es fensterblick_proto an
    bash "$HERE/vista-measure.sh" "$APK" 90 proto-timelapse --es fensterblick_proto zeitraffer
    ;;
  kurz)
    bash "$HERE/vista-measure.sh" "$APK" 120 fensterblick --es fensterblick an
    bash "$HERE/vista-measure.sh" "$APK" 120 zeitraffer --es fensterblick zeitraffer
    ;;
  "15 Minuten")
    bash "$HERE/vista-measure.sh" "$APK" 900 fensterblick-15min --es fensterblick an
    bash "$HERE/vista-measure.sh" "$APK" 120 zeitraffer --es fensterblick zeitraffer
    ;;
esac
cat measure/*/summary.txt
