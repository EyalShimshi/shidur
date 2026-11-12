#!/usr/bin/env bash
# Runs the release APK on an emulator before it's published. R8 removes code it thinks is unused,
# and code that is only reached by reflection breaks only when the app runs. Opens the app, plays a
# station from a shidur:// link, goes to the home screen, and fails if the app crashes or nothing
# plays. Crashes are shown with the original names, decoded with the build's mapping file.
# Usage: smoke-test.sh <apk> <mapping.txt>
set -u

apk=$1
mapping=$2
pkg=io.github.eyalshimshi.shidur
# On different servers, in case one is down or slow from GitHub's servers.
stations="glglz 103fm eco99 kan-bet"
retrace="$ANDROID_HOME/cmdline-tools/latest/bin/retrace"

# Fails the test; the details go to the log and to the run's Summary page.
fail() {
  local message=$1 details=${2:-}
  echo "::error::$message"
  {
    echo "## Smoke test: $message"
    if [ -n "$details" ]; then
      echo '```'
      cat "$details"
      echo '```'
    fi
  } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  if [ -n "$details" ]; then cat "$details"; fi
  exit 1
}

# Writes $1 with the original class and method names to decoded.txt.
decode() {
  "$retrace" "$mapping" "$1" > decoded.txt 2>&1 || cp "$1" decoded.txt
}

check_no_crash() {
  adb logcat -d -b crash > crash.txt
  if grep -q "Process: $pkg" crash.txt; then
    decode crash.txt
    fail "Shidur crashed $1" decoded.txt
  fi
}

# Writes the media session, the text on the screen and Shidur's own log to decoded.txt, with the
# original names, and a screenshot to smoke-test-screen.png.
collect_diagnostics() {
  local pid
  pid=$(adb shell pidof "$pkg" | tr -d '\r')
  {
    echo "--- Media session ---"
    adb shell dumpsys media_session | grep -A 30 "package=$pkg"
    echo "--- Text on the screen ---"
    timeout 30 adb shell uiautomator dump /sdcard/window.xml > /dev/null
    adb shell cat /sdcard/window.xml | grep -oE '(text|content-desc)="[^"]+"' | head -n 80
    echo "--- Shidur's log ---"
    if [ -n "$pid" ]; then adb logcat -d --pid="$pid" | tail -n 250; fi
  } > diagnostics.txt 2>&1
  adb exec-out screencap -p > smoke-test-screen.png 2> /dev/null || true
  decode diagnostics.txt
}

is_playing() {
  adb shell dumpsys media_session | grep -A 30 "package=$pkg" |
    grep -qE 'state=PlaybackState \{state=(PLAYING\(3\)|3),'
}

# Waits up to $1 seconds for the media session to report that it's playing.
wait_until_playing() {
  local deadline=$((SECONDS + $1))
  while [ "$SECONDS" -lt "$deadline" ]; do
    check_no_crash "$2"
    if is_playing; then return 0; fi
    sleep 3
  done
  return 1
}

adb install -r -g "$apk" || fail "The APK didn't install"
adb logcat -c || true

echo "Opening Shidur"
adb shell am start -W -n "$pkg/.MainActivity"
sleep 10
check_no_crash "while opening"
adb shell pidof "$pkg" > /dev/null || fail "Shidur isn't running after opening it"

played=""
for station in $stations; do
  echo "Playing $station"
  adb shell am start -W -a android.intent.action.VIEW -d "shidur://station/$station" \
    --ez play_station true -n "$pkg/.MainActivity"
  if wait_until_playing 45 "while starting $station"; then
    played=$station
    break
  fi
  echo "::warning::$station didn't start playing within 45 seconds"
done
if [ -z "$played" ]; then
  collect_diagnostics
  fail "No station started playing" decoded.txt
fi
echo "$played is playing"

echo "Going to the home screen"
adb shell input keyevent KEYCODE_HOME
sleep 10
if ! wait_until_playing 30 "in the background"; then
  collect_diagnostics
  fail "$played stopped playing when Shidur went to the background" decoded.txt
fi

echo "Opening a station page"
adb shell am start -W -a android.intent.action.VIEW -d "shidur://station/kan-88" -n "$pkg/.MainActivity"
sleep 5
check_no_crash "while opening a station page"

echo "Smoke test passed: Shidur opened, played $played and kept playing in the background."
echo "Smoke test passed: Shidur opened, played \`$played\` and kept playing in the background." >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
