#!/usr/bin/env bash
# Frame-timing check for the launcher: moves focus around and returns from an app, then prints jank stats.
# usage: bash bench.sh <ip:5555>
ADB="$LOCALAPPDATA/Microsoft/WinGet/Packages/Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe/platform-tools/adb.exe"
"$ADB" -s "$1" shell '
dumpsys dreams | grep -q "mCurrentDreamName=Comp" && input keyevent KEYCODE_DPAD_CENTER && sleep 2
am start -a android.settings.SETTINGS >/dev/null 2>&1; sleep 3; input keyevent KEYCODE_HOME; sleep 3
dumpsys gfxinfo tv.harry.home reset >/dev/null
for k in RIGHT RIGHT RIGHT RIGHT DOWN LEFT LEFT LEFT LEFT UP; do input keyevent KEYCODE_DPAD_$k; sleep 0.4; done
am start -a android.settings.SETTINGS >/dev/null 2>&1; sleep 3; input keyevent KEYCODE_HOME; sleep 3
dumpsys gfxinfo tv.harry.home | grep -E "Total frames|Janky|percentile|Slow|Missed"
dumpsys meminfo tv.harry.home | grep -E "TOTAL:|Native Heap:"
dumpsys window windows | grep mCurrentFocus'
