#!/usr/bin/env bash
# Frame timing of the screensaver's fade-in, three runs. usage: bash bench-saver.sh <ip:5555>
# Prints each frame's total time in ms, in order, so slow frames can be placed: the first few
# belong to the window opening (screen still black), the rest are the visible fade.
ADB="$LOCALAPPDATA/Microsoft/WinGet/Packages/Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe/platform-tools/adb.exe"
sh() { "$ADB" -s "$T" shell "$1"; }
T="$1"
for i in 1 2 3; do
  sh 'dumpsys dreams | grep -q "mCurrentDreamName=Comp" && input keyevent KEYCODE_DPAD_CENTER && sleep 2
      dumpsys gfxinfo tv.harry.home reset >/dev/null
      am start -n com.android.systemui/.Somnambulator >/dev/null 2>&1; sleep 4'
  sh 'dumpsys gfxinfo tv.harry.home framestats' | tr -d '\r' | grep -E '^0,[0-9]' \
    | awk -F, '{printf "%d ", ($14-$2)/1000000}'; echo
done
sh 'input keyevent KEYCODE_DPAD_CENTER'
