#!/usr/bin/env bash
set -euo pipefail
mkdir -p build670-device
package=tr.borsatakip.v5.diagnostic.v668.delayed
collect_evidence() {
  result=$?
  trap - EXIT
  set +e
  adb logcat -d > build670-device/logcat.txt
  adb shell dumpsys window > build670-device/window.txt
  adb shell dumpsys activity activities > build670-device/activities.txt
  adb shell dumpsys power > build670-device/power.txt
  adb exec-out screencap -p > build670-device/final-screen.png
  adb exec-out run-as "$package" tar -cf - files/build670-screens > build670-device/screens.tar
  exit "$result"
}
trap collect_evidence EXIT
app_apk=$(find device-input -name '*.apk' ! -name '*androidTest*' -print -quit)
test_apk=$(find device-input -name '*androidTest*.apk' -print -quit)
test -n "$app_apk"
test -n "$test_apk"
adb wait-for-device
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install -r "$app_apk"
adb install -r "$test_apk"
adb shell am force-stop "$package"
adb shell run-as "$package" mkdir -p files/build670-screens
adb shell dumpsys window > build670-device/window-before.txt
classes='tr.borsatakip.v5.ui.MarketTouchRuntimeTest#stocksHeaderButtonsOpenRealDestinationsWithTouch,tr.borsatakip.v5.ui.MarketTouchRuntimeTest#stocksTouchFiltersSearchSortFavoriteAndDetail,tr.borsatakip.v5.ui.MarketTouchRuntimeTest#stocksUnavailableFiltersGiveVisibleReasonsWithoutChangingRows,tr.borsatakip.v5.ui.MarketTouchRuntimeTest#stocksEmptyScanAndBottomNavigationReceiveTouch,tr.borsatakip.v5.ui.Build670ChartRuntimeTest'
adb logcat -c
adb shell am instrument -w -r -e class "$classes" "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee build670-device/instrumentation.txt
python3 ci/build670-evidence.py device
