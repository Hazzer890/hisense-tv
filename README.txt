restore one with: adb shell cmd package install-existing <package>
launcher/            source for the custom home screen (tv.harry.home). Rebuild: bash launcher/build.sh, then adb install -r launcher/launcher.apk
edit the home tiles: PINNED list at the top of launcher/src/tv/harry/home/Home.java
go back to the stock home screen: adb shell pm enable com.google.android.tvlauncher ; adb uninstall tv.harry.home
reconnect adb in a later session: powershell -ExecutionPolicy Bypass -File connect.ps1 (needs the TV's MAC address in tv-mac.txt)
not in git: wallpaper photos, tv-mac.txt, the signing key (build.sh makes a new one) and built APKs. Add photos and run wallpaper.py before the first build.
change wallpapers: put .jpg files in launcher/wallpaper-src, run python launcher/wallpaper.py, then rebuild and reinstall
