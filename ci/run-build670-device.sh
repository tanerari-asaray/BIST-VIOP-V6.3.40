#!/usr/bin/env bash
set -euo pipefail
mkdir -p build670-device
app_apk=$(find device-input -name '*.apk' ! -name '*androidTest*' -print -quit)
test_apk=$(find device-input -name '*androidTest*.apk' -print -quit)
test -n "$app_apk"
test -n "$test_apk"
adb install -r "$app_apk"
adb install -r "$test_apk"
package=tr.borsatakip.v5.diagnostic.v668.delayed
classes='tr.borsatakip.v5.ui.MarketTouchRuntimeTest#stocksHeaderButtonsOpenRealDestinationsWithTouch,tr.borsatakip.v5.ui.MarketTouchRuntimeTest#stocksTouchFiltersSearchSortFavoriteAndDetail,tr.borsatakip.v5.ui.MarketTouchRuntimeTest#stocksUnavailableFiltersGiveVisibleReasonsWithoutChangingRows,tr.borsatakip.v5.ui.MarketTouchRuntimeTest#stocksEmptyScanAndBottomNavigationReceiveTouch,tr.borsatakip.v5.ui.Build670ChartRuntimeTest'
adb logcat -c
adb shell am instrument -w -r -e class "$classes" "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee build670-device/instrumentation.txt
adb logcat -d > build670-device/logcat.txt
adb pull /data/local/tmp/build660-screens build670-device/ || true
adb exec-out run-as "$package" tar -cf - files/build670-screens > build670-device/chart-fixtures.tar || true
python3 ci/build670-evidence.py device
