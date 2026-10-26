#!/usr/bin/env bash
# The emulator action executes script lines separately. Keep exit codes and cleanup
# inside one shell so failed tests still export their evidence.
set -uo pipefail
mkdir -p ../delivery/screenshots ../delivery/emulator
# A cold-boot Quickstep ANR can cover every test window before our APK is installed.
# Clear only that emulator launcher, then require a genuinely focused system window.
# App ANRs and Espresso focus/touch failures remain visible and fail the job.
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent 224
adb shell wm dismiss-keyguard
adb logcat -d -v threadtime > ../delivery/emulator/boot-logcat.txt 2>&1
adb shell am force-stop com.android.launcher3
focus_ready=false
for attempt in $(seq 1 30); do
    adb shell dumpsys window > ../delivery/emulator/preflight-window.txt
    if python3 -c 'import pathlib,re,sys; s=pathlib.Path("../delivery/emulator/preflight-window.txt").read_text(); focus=re.findall(r"(?m)^\s*mCurrentFocus=(.+)$",s); line=focus[-1] if focus else ""; sys.exit(0 if "Window{" in line and "Application Not Responding" not in line and "Application Error" not in line else 1)'; then
        focus_ready=true
        break
    fi
    sleep 0.5
done
if [ "$focus_ready" != true ]; then
    echo "Emulator preflight failed: no focused interactive window"
    adb logcat -d -v threadtime > ../delivery/emulator/preflight-failure-logcat.txt 2>&1
    adb shell dumpsys activity activities > ../delivery/emulator/preflight-failure-activities.txt 2>&1
    adb exec-out screencap -p > ../delivery/emulator/preflight-failure.png
    exit 1
fi
adb logcat -c
adb shell setprop debug.hwui.use_buffer_age false || true
adb shell setprop debug.hwui.use_partial_updates false || true
./gradlew --no-daemon --stacktrace :app:connectedDiagnosticAndroidTest > ../delivery/emulator/connected-test.log 2>&1
test_exit=$?
cat ../delivery/emulator/connected-test.log
adb logcat -d -v threadtime > ../delivery/emulator/logcat.txt 2>&1 || true
adb shell dumpsys activity activities > ../delivery/emulator/activities.txt 2>&1 || true
adb shell dumpsys window windows > ../delivery/emulator/windows.txt 2>&1 || true
adb pull /data/local/tmp/build660-screens/. ../delivery/screenshots/ || true
if [ "$test_exit" -eq 0 ]; then
    python3 ../ci-verify-screenshots.py
    screenshot_exit=$?
    if [ "$screenshot_exit" -ne 0 ]; then exit "$screenshot_exit"; fi
fi
exit "$test_exit"
