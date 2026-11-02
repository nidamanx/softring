#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Smoke test on a running emulator: install the debug APK, start the app, check it
# is alive, take a screenshot of the top and of the bottom of the page (also in the
# dark theme on Android 10+), check the volume changes made with the app closed
# (Quick Settings tile on Android 10+, a two-minute night), and fail on any crash or
# ANR (not responding) of the app.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/emulator-smoke.sh [-h] <name> [apk]

  name  label used in the screenshot file names (e.g. api35-phone)
  apk   debug APK to install (default: app/build/outputs/apk/debug/app-debug.apk)

Screenshots: screenshots/<name>-off.png (first launch, switched off), then switched on
screenshots/<name>-top.png and -bottom.png, the About page <name>-info-top.png and
-info-bottom.png, and on Android 10+ the same in the dark theme (<name>-dark-*.png).
Full log: screenshots/<name>-logcat.txt.
EOF
}

if [[ "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi
if [[ $# -lt 1 ]]; then
  usage >&2
  exit 2
fi

name="$1"
apk="${2:-app/build/outputs/apk/debug/app-debug.apk}"
# Debug builds have their own package (".dev"), so they can be installed next to the
# released app; class names keep the namespace
package="io.github.nidamanx.softring.dev"
namespace="io.github.nidamanx.softring"
mkdir -p screenshots
# Keep the full log even when the test stops early
trap 'adb logcat -d > "screenshots/$name-logcat.txt" 2>/dev/null || true' EXIT

# A slow emulator often shows "System UI / Launcher isn't responding" for other
# apps: hide those dialogs so they do not cover the screenshots. An ANR of this
# app is still detected from the log at the end.
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put global anr_show_background 0 || true
close_system_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null || true
}

# "Boot completed" comes before the system can start apps on recent images (API 37):
# wait until the activity manager answers
for _ in $(seq 30); do
  adb shell cmd activity get-current-user >/dev/null 2>&1 && break
  sleep 5
done
sleep 10
adb install -r "$apk"
# Android 8 sometimes refuses to clear the log right after boot: not essential
adb logcat -c || { sleep 2 && adb logcat -c; } || echo "logcat not cleared, continuing"

size="$(adb shell wm size | sed -n 's/.*size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)"
read -r width height <<<"$size"

# Tap a view of the app, found by its id in the uiautomator dump (works on any screen size)
tap_id() {  # view id
  local bounds x1 y1 x2 y2
  adb shell uiautomator dump /sdcard/softring-ui.xml >/dev/null
  bounds="$(adb shell cat /sdcard/softring-ui.xml | tr '>' '\n' | grep -F ":id/$1\"" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -n 1 || true)"
  [[ -n $bounds ]] || { echo "::error::$1 not found on screen ($name)"; exit 1; }
  read -r x1 y1 x2 y2 <<<"$bounds"
  adb shell input tap "$(((x1 + x2) / 2))" "$(((y1 + y2) / 2))"
}

# True if a view of the app with this id is on screen
on_screen() {  # view id
  local ui
  adb shell uiautomator dump /sdcard/softring-ui.xml >/dev/null
  # Read the whole dump first: with pipefail, grep -q closing the pipe early would fail
  ui="$(adb shell cat /sdcard/softring-ui.xml)"
  [[ $ui == *":id/$1\""* ]]
}

# Screenshot the top of the current page, scroll to the end with a few swipes up in
# the middle of the screen, screenshot the bottom
capture_page() {  # file name prefix
  close_system_dialogs
  adb exec-out screencap -p > "screenshots/$1-top.png"
  for _ in 1 2 3 4; do
    adb shell input swipe "$((width / 2))" "$((height * 3 / 4))" "$((width / 2))" "$((height / 4))" 300
  done
  sleep 2
  close_system_dialogs
  adb exec-out screencap -p > "screenshots/$1-bottom.png"
}

# Start the app (again, with -S), check it is alive, capture the main screen, then
# open the About page with the info button and capture it too
capture() {  # suffix for the file names
  adb shell am start -S -W -n "$package/$namespace.MainActivity"
  sleep 5
  adb shell pidof "$package"
  capture_page "$name$1"
  adb shell am start -S -W -n "$package/$namespace.MainActivity"
  sleep 5
  # Right after a cold start a tap may be lost: wait until the About page's back
  # button is on screen, tapping the info button again once if needed
  tap_id btn_info
  for attempt in $(seq 10); do
    on_screen btn_back && break
    (( attempt == 5 )) && tap_id btn_info
    sleep 1
  done
  capture_page "$name$1-info"
  adb shell input keyevent KEYCODE_BACK
}

# The app starts switched off: screenshot that, then turn it on by tapping the switch
# The first start may still hit a system that is not ready ("Broken pipe"): retry
for attempt in 1 2 3; do
  adb shell am start -W -n "$package/$namespace.MainActivity" && break
  (( attempt < 3 )) || exit 1
  sleep 20
done
sleep 5
close_system_dialogs
adb exec-out screencap -p > "screenshots/$name-off.png"
tap_id switch_enabled
sleep 2

capture ""
# Dark theme: "cmd uimode night" exists from Android 10 (API 29)
if (( $(adb shell getprop ro.build.version.sdk | tr -d '\r') >= 29 )); then
  adb shell cmd uimode night yes
  sleep 2
  capture "-dark"
  adb shell cmd uimode night no
fi

# Volume changes made with the app closed: the Quick Settings tile (Android 10+) and
# a short night of two minutes (the alarm path). Android 17+ ignores volume changes
# from an app with no visible activity or foreground service, so these fail there
# unless VolumeService is used. Settings are written directly so the result does not
# depend on the time of day.
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
volume_failed=0
skip_volume=0
stream_name() {  # stream number: 2 = ring, 5 = notification
  if [[ $1 == 2 ]]; then echo STREAM_RING; else echo STREAM_NOTIFICATION; fi
}
volume_of() {  # stream number
  local level
  # Printed on stdout on recent versions, only to the log on older ones (API 30)
  level="$(adb shell cmd media_session volume --stream "$1" --get 2>&1 | sed -n 's/.*volume is \([0-9]*\).*/\1/p' | head -n 1)"
  if [[ -z $level ]]; then
    # "- STREAM_RING:" block: "streamVolume:5" (older versions), or the first
    # level in "Current: 2 (speaker): 5, ..." / "Current: 40000000 (default): 5"
    level="$(adb shell dumpsys audio | tr -d '\r' | awk -v s="- $(stream_name "$1"):" '
      $0 == s { found = 1; next }
      found && /streamVolume:/ { sub(/.*streamVolume:/, ""); print $1 + 0; exit }
      found && /Current:/ { if (match($0, /\): [0-9]+/)) print substr($0, RSTART + 3, RLENGTH - 3); exit }')"
  fi
  echo "$level"
}
set_volume() {  # stream number, level; fails if no shell command could set it
  # "cmd audio set-volume" on recent versions; on API 35 "cmd media_session volume
  # --set" is refused by an app-op check inside AudioService
  local command
  for command in "cmd audio set-volume $1 $2" "cmd media_session volume --stream $1 --set $2" \
    "media volume --stream $1 --set $2"; do
    adb shell "$command" >/dev/null 2>&1 || true
    [[ $(volume_of "$1") == "$2" ]] && return 0
  done
  return 1
}
expect_volumes() {  # label, expected ring, expected notification
  local ring notification
  # Up to 20 s: a cold start of the app on a slow emulator can take a while
  for _ in $(seq 10); do
    ring="$(volume_of 2)"
    notification="$(volume_of 5)"
    [[ $ring == "$2" && $notification == "$3" ]] && break
    sleep 2
  done
  echo "volume test ($1): ring=$ring notification=$notification, expected $2/$3"
  if [[ $ring != "$2" || $notification != "$3" ]]; then
    echo "::error::volume test failed on $name: $1 (ring=$ring notification=$notification, expected $2/$3)"
    volume_failed=1
    # Tells a tap or alarm that never reached the app (state unchanged) from a
    # volume change that Android ignored (state changed, volume not)
    echo "App state:"
    adb shell "run-as $package cat /data/user_de/0/$package/shared_prefs/softring.xml" || true
    adb shell dumpsys alarm | grep -F -B1 -A2 "$package" | grep -v -i "cancel" | head -n 12 || true
  fi
}
reset_app() {  # extra preference lines
  adb shell am force-stop "$package"
  # Android 15 sends the boot broadcasts again after a force-stop: let them settle
  sleep 3
  printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<map>' \
    '    <boolean name="dnd_card_dismissed" value="true" />' "$@" '</map>' \
    | adb shell "run-as $package sh -c 'cat > /data/user_de/0/$package/shared_prefs/softring.xml'"
  # The boot broadcasts above may have started the app with the old settings in memory:
  # stop it again so it reads the new file
  adb shell am force-stop "$package"
  sleep 3
  if ! { set_volume 5 5 && set_volume 2 5; }; then
    echo "::error::volume test setup failed on $name: cannot set the volume from the shell"
    volume_failed=1
  fi
}
# "click-tile" with the panel closed is often lost, especially after a force-stop:
# open Quick Settings first, as a person would
tap_tile() {
  adb shell cmd statusbar expand-settings
  sleep 2
  adb shell cmd statusbar click-tile "$package/$namespace.SoftRingTileService"
  sleep 1
  adb shell cmd statusbar collapse
}
tile_test() {  # label, "closed" or "foreground"
  reset_app '    <boolean name="enabled" value="false" />' '    <boolean name="always" value="true" />'
  if [[ $2 == foreground ]]; then
    adb shell am start -W -n "$package/$namespace.MainActivity"
  else
    adb shell input keyevent KEYCODE_HOME
  fi
  sleep 3
  expect_volumes "tile, $1, before" 5 5
  tap_tile
  expect_volumes "tile, $1, on" 1 1
  tap_tile
  expect_volumes "tile, $1, off" 5 5
}
second_of_day() {  # on the emulator clock
  local h m sec
  IFS=: read -r h m sec <<<"$(adb shell date +%H:%M:%S | tr -d '\r')"
  echo $(( 10#$h * 3600 + 10#$m * 60 + 10#$sec ))
}
wait_until() {  # second of day on the emulator clock
  sleep $(( ($1 - $(second_of_day) + 86400) % 86400 ))
}
night_test() {
  local now night_start night_end
  now="$(second_of_day)"
  # Start at least one minute from now, on a whole minute; end two minutes later
  night_start=$(( (now / 60 * 60 + 120) % 86400 ))
  night_end=$(( (night_start + 120) % 86400 ))
  reset_app '    <boolean name="enabled" value="true" />' '    <boolean name="always" value="false" />' \
    "    <int name=\"start_second_of_day\" value=\"$night_start\" />" \
    "    <int name=\"end_second_of_day\" value=\"$night_end\" />"
  if (( sdk >= 31 )); then
    adb shell appops set "$package" SCHEDULE_EXACT_ALARM allow
  fi
  # Opening the app schedules the alarm (without touching the volume), then it goes away
  adb shell am start -W -n "$package/$namespace.MainActivity"
  sleep 3
  adb shell input keyevent KEYCODE_HOME
  adb shell am kill "$package" || true
  adb shell dumpsys alarm | grep -F -A3 "$package" | head -n 8 || true
  expect_volumes "night, before" 5 5
  wait_until $(( night_start + 30 ))
  expect_volumes "night, started" 1 1
  wait_until $(( night_end + 30 ))
  expect_volumes "night, ended" 5 5
}
# Can the shell read and set the volume here? Not on Android 8, and a slow emulator
# may not answer at once
volume_ok=0
for _ in 1 2 3; do
  current="$(volume_of 2)"
  if [[ -n $current ]] && set_volume 2 "$(( current == 4 ? 3 : 4 ))"; then
    volume_ok=1
    break
  fi
  sleep 5
done
if (( ! volume_ok )); then
  echo "::warning::volume test skipped on $name: cannot read or set the volume from the shell (ring read: '${current:-}')"
  adb shell cmd media_session volume --stream 2 --get 2>&1 | head -n 5 || true
  adb shell dumpsys audio | grep -F -A8 -- "- STREAM_RING:" | head -n 10 || true
  skip_volume=1
elif (( sdk >= 29 )); then
  adb shell cmd statusbar add-tile "$package/$namespace.SoftRingTileService"
  sleep 2
  tile_test "app closed" closed
  tile_test "app in foreground" foreground
  adb shell cmd statusbar collapse || true
fi
# Informational, never fails: what happens to the volume while Do Not Disturb
# (priority only, the default policy lets starred contacts call) is active
dnd_test() {
  reset_app '    <boolean name="enabled" value="false" />' '    <boolean name="always" value="true" />'
  adb shell cmd notification allow_dnd "$package" || true
  adb shell am start -W -n "$package/$namespace.MainActivity"
  adb shell cmd notification set_dnd priority || true
  sleep 3
  echo "DND scenario, DND on: ring=$(volume_of 2) notification=$(volume_of 5)"
  tap_tile
  sleep 5
  echo "DND scenario, tile on: ring=$(volume_of 2) notification=$(volume_of 5)"
  tap_tile
  sleep 5
  echo "DND scenario, tile off: ring=$(volume_of 2) notification=$(volume_of 5)"
  adb shell cmd notification set_dnd off || true
  sleep 3
  echo "DND scenario, DND off: ring=$(volume_of 2) notification=$(volume_of 5)"
  adb shell dumpsys audio | grep -iE "ringer mode|muted streams|zen" | head -n 12 || true
}

if (( ! skip_volume )); then
  night_test
  if (( sdk >= 29 )); then
    dnd_test
  fi
fi
# The app's own diary (debug builds, when DebugLog.ENABLED): one line per event and per volume change
echo "SoftRing log:"
adb logcat -d -s SoftRing:I | tail -n 120 || true
adb shell dumpsys audio | grep -i -A3 "hardening" || true

adb shell dumpsys package "$package" | grep -i "permission"
if adb logcat -d -b crash | grep -F "$package"; then
  echo "::error::$package crashed on $name"
  exit 1
fi
if adb logcat -d -b main -b system | grep -F "ANR in $package"; then
  echo "::error::$package was not responding (ANR) on $name"
  exit 1
fi
if (( volume_failed )); then
  adb logcat -d | grep -iE "softring|AudioService|AS\.|volume" | tail -n 80 || true
  exit 1
fi
