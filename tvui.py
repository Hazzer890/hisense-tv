"""Drive the TV's on-screen menus by visible text instead of blind key presses.

usage: python tvui.py texts              list the text on screen
       python tvui.py tap "Some label"   tap the item with that exact text
"""
import os, re, subprocess, sys

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages"
                         r"\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe")
TV = "192.168.0.215:5555"


def sh(cmd):
    return subprocess.run([ADB, "-s", TV, "shell", cmd], capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout


def nodes():
    xml = sh("uiautomator dump /sdcard/ui.xml >/dev/null; cat /sdcard/ui.xml")
    for m in re.finditer(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if m.group(1):
            x1, y1, x2, y2 = map(int, m.groups()[1:])
            yield m.group(1), (x1 + x2) // 2, (y1 + y2) // 2


if sys.argv[1] == "texts":
    print(" | ".join(t for t, _, _ in nodes()))
else:
    want = sys.argv[2]
    for text, x, y in nodes():
        if text == want:
            sh(f"input tap {x} {y}")
            print(f"tapped '{want}' at {x},{y}")
            break
    else:
        sys.exit(f"'{want}' is not on screen")
