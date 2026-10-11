#!/usr/bin/env bash
# Runs the window view measurements chosen in the workflow (see ci/vista-measure.sh).
#   bash ci/vista-measure-all.sh klangbild.apk <short|15min>
set -u
APK="$1"
MODE="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
case "$MODE" in
  short)
    bash "$HERE/vista-measure.sh" "$APK" 120 window-view --es window_view on
    bash "$HERE/vista-measure.sh" "$APK" 120 timelapse --es window_view timelapse
    ;;
  15min)
    bash "$HERE/vista-measure.sh" "$APK" 900 window-view-15min --es window_view on
    bash "$HERE/vista-measure.sh" "$APK" 120 timelapse --es window_view timelapse
    ;;
  *)
    echo "Unknown measurement: $MODE" >&2
    exit 1
    ;;
esac
cat measure/*/summary.txt
