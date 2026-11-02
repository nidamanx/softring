#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Store screenshots on a running emulator (Android 15 image with "google_apis",
# rootable): the app switched on with every permission granted, with a clean status
# bar, in the language the emulator was started with.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/store-screenshots.sh [-h] <locale> [apk]

  locale  fastlane language folder, also the emulator language (e.g. it-IT)
  apk     APK to install (default: app/build/outputs/apk/debug/app-debug.apk)

Writes fastlane/metadata/android/<locale>/images/phoneScreenshots/1.png ... 4.png
under screenshots/: main screen in the morning (light theme), main screen during
the night, Quick Settings tile and About page (dark theme).
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

locale="$1"
apk="${2:-app/build/outputs/apk/debug/app-debug.apk}"
# Debug builds have their own package (".dev"), so they can be installed next to the
# released app; class names keep the namespace
package="io.github.nidamanx.softring.dev"
namespace="io.github.nidamanx.softring"
out="screenshots/fastlane/metadata/android/$locale/images/phoneScreenshots"
mkdir -p "$out"
trap 'adb logcat -d > "screenshots/$locale-logcat.txt" 2>/dev/null || true' EXIT
trap 'echo "::error::failed at line $LINENO ($locale): $BASH_COMMAND"' ERR

wait_for_system() {
  for _ in $(seq 60); do
    adb shell cmd activity get-current-user >/dev/null 2>&1 && return 0
    sleep 5
  done
  echo "::error::the system did not start ($locale)"
  exit 1
}

# Switch adbd to root or back: adbd restarts, the connection may drop for a moment
adb_mode() {  # root | unroot
  local attempt
  for attempt in 1 2 3 4 5; do
    adb "$1" >/dev/null 2>&1 && break
    (( attempt < 5 )) || { echo "::error::adb $1 failed ($locale)"; exit 1; }
    sleep 5
  done
  sleep 3
  adb wait-for-device
  wait_for_system
}

wait_for_system
# The emulator option -change-locale switches the language after boot by restarting
# the framework, while this script is already running: wait for it, do not restart
# anything here ("it-IT" appears as "-it-rIT-" in the configuration)
config_token="-${locale%%-*}-r${locale##*-}-"
for _ in $(seq 60); do
  [[ $(adb shell am get-config 2>/dev/null | tr -d '\r') == *"$config_token"* ]] && break
  sleep 5
done
if [[ $(adb shell am get-config | tr -d '\r') != *"$config_token"* ]]; then
  echo "::error::the emulator language is not $locale"
  adb shell am get-config | head -n 3
  exit 1
fi
# The framework restart may still be settling
sleep 20
wait_for_system
sleep 10
adb shell wm dismiss-keyguard || true
adb shell settings put global hide_error_dialogs 1 || true
close_system_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null || true
}

# Normal shell (as in the smoke test): run-as from a root shell runs into SELinux
# denials
adb install -r "$apk"
adb shell am force-stop "$package"
adb shell cmd notification allow_dnd "$package"
adb shell appops set "$package" SCHEDULE_EXACT_ALARM allow
# Switched on, default hours (22:00-07:00); the tile is added below, so the app
# stops offering it. The app has never run: its settings folder does not exist yet
prefs_dir="/data/user_de/0/$package/shared_prefs"
printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<map>' \
  '    <boolean name="enabled" value="true" />' '    <boolean name="always" value="false" />' \
  '    <boolean name="tile_added" value="true" />' '</map>' \
  | adb shell "run-as $package sh -c 'mkdir -p $prefs_dir && cat > $prefs_dir/softring.xml'"
# Daytime volumes the sliders refer to ("1 / 7 · now 5")
adb shell cmd audio set-volume 5 5 || true
adb shell cmd audio set-volume 2 5 || true

adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0

# Times chosen by the owner: digit sum 1 in the morning, 9 in the evening, as shown
# in each language (English shows the evening in 12-hour format: 10:35)
day_time=1045
if [[ $locale == en-US ]]; then night_time=2235; else night_time=2223; fi

# Our tile, added as in the smoke test (Android 15 put it first). Not reordered:
# while System UI runs it overwrites sysui_qs_tiles at once
adb shell cmd statusbar add-tile "$package/$namespace.SoftRingTileService"
sleep 2
adb shell cmd statusbar collapse || true

# Wi-Fi and Bluetooth off (owner's choice): the Quick Settings tiles say "Off"
adb shell svc wifi disable || true
adb shell svc bluetooth disable || true
# The real status bar, not demo mode: on this image every demo "network" command
# added one more SIM or Wi-Fi icon. Battery full and unplugged, no notification icons
adb shell dumpsys battery unplug
adb shell dumpsys battery set level 100
adb shell cmd statusbar send-disable-flag notification-icons
# The real clock, set at hh:mm:00 (root only for this, then the normal shell again).
# Set again right before each screenshot, so the minute has not changed yet
set_time() {  # hhmm
  shown_time="$1"
  adb_mode root
  adb shell date "$(date +%m%d)$1$(date +%Y).00" >/dev/null
  adb_mode unroot
  sleep 2
}

# Granted again right before the screenshots: in a test run the exact alarm
# permission was gone by then, showing the warning card
grant() {
  adb shell cmd notification allow_dnd "$package"
  adb shell appops set "$package" SCHEDULE_EXACT_ALARM allow
}
grant

size="$(adb shell wm size | sed -n 's/.*size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)"
read -r width height <<<"$size"
density="$(adb shell wm density | sed -n 's/.*density: \([0-9]*\).*/\1/p' | head -n 1)"
min_density=$(( density * 85 / 100 ))

bounds_of() {  # view id; prints "x1 y1 x2 y2" (visible part), empty if not on screen
  adb shell uiautomator dump /sdcard/softring-ui.xml >/dev/null
  adb shell cat /sdcard/softring-ui.xml | tr '>' '\n' | grep -F ":id/$1\"" \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -n 1 || true
}
tap_id() {  # view id
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<<"$(bounds_of "$1")"
  [[ -n ${y2:-} ]] || { echo "::error::$1 not found on screen ($locale)"; exit 1; }
  adb shell input tap "$(((x1 + x2) / 2))" "$(((y1 + y2) / 2))"
}
on_screen() {  # view id
  [[ -n $(bounds_of "$1") ]]
}
shot() {  # number, "panel" to keep Quick Settings open
  set_time "$shown_time"
  # Closing system dialogs would also close Quick Settings
  [[ ${2:-} == panel ]] || close_system_dialogs
  sleep 1
  adb exec-out screencap -p > "$out/$1.png"
}
start_app() {
  adb shell am start -S -W -n "$package/$namespace.MainActivity" >/dev/null
  sleep 5
  on_screen switch_enabled || { echo "::error::main screen not shown ($locale)"; exit 1; }
}
# The whole main screen in one picture: make the display a little smaller (as the
# "Display size" setting does) until the last note is clear of the navigation bar.
# Visible bounds are clipped to the screen, so a cut note ends at the bottom edge
fit_main_screen() {
  local y2
  while :; do
    read -r _ _ _ y2 <<<"$(bounds_of note_preview)"
    (( ${y2:-height} <= height - 100 )) && return 0
    if (( density - 10 < min_density )); then
      echo "::warning::main screen does not fit at density $density ($locale)"
      return 0
    fi
    density=$(( density - 10 ))
    adb shell wm density "$density"
    sleep 3
    start_app
  done
}

# 1. Morning, light theme: waiting for the night
adb shell cmd uimode night no
set_time "$day_time"
start_app
if on_screen card_exact; then
  echo "Exact alarm permission missing: granting it again"
  grant
  start_app
  on_screen card_exact && { echo "::error::exact alarm permission not granted ($locale)"; exit 1; }
fi
fit_main_screen
shot 1

# 2. Evening, dark theme: the night has started (the 22:00 alarm fires right after
# the clock change), volumes lowered until 07:00
set_time "$night_time"
sleep 5
adb shell cmd uimode night yes
sleep 2
start_app
fit_main_screen
shot 2

# 3. Quick Settings with the tile ("Until 07:00"); add-tile placed it first in a
# test run, but swipe through the pages in case it is further on
adb shell cmd statusbar expand-settings
sleep 3
for _ in 1 2 3 4 5; do
  adb shell uiautomator dump /sdcard/softring-ui.xml >/dev/null || true
  [[ $(adb shell cat /sdcard/softring-ui.xml 2>/dev/null) == *'text="SoftRing"'* ]] && break
  adb shell input swipe "$((width * 4 / 5))" "$((height / 3))" "$((width / 5))" "$((height / 3))" 300
  sleep 2
done
[[ $(adb shell cat /sdcard/softring-ui.xml 2>/dev/null) == *'text="SoftRing"'* ]] \
  || echo "::warning::SoftRing tile not found in Quick Settings ($locale)"
shot 3 panel
adb shell cmd statusbar collapse
sleep 1

# 4. About page, dark theme
start_app
tap_id btn_info
for attempt in $(seq 10); do
  on_screen btn_back && break
  (( attempt == 5 )) && tap_id btn_info
  sleep 1
done
on_screen btn_back || { echo "::error::About page not shown ($locale)"; exit 1; }
shot 4
adb shell cmd uimode night no
adb shell wm density reset

if adb logcat -d -b crash | grep -F "$package"; then
  echo "::error::$package crashed ($locale)"
  exit 1
fi
echo "Density used: $density"
ls -l "$out"
