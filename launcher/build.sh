#!/usr/bin/env bash
# Builds launcher.apk with the bare SDK tools (no Gradle). Run from Git Bash: ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$USERPROFILE/Android/Sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-26/android.jar"
KS="$PWD/debug.keystore"

rm -rf out && mkdir -p out/classes out/dex

"$BT/aapt2.exe" link --manifest AndroidManifest.xml -A assets -I "$JAR" -o out/base.apk

javac -nowarn -encoding UTF-8 --release 8 -cp "$JAR" -d out/classes src/tv/harry/home/*.java
"$BT/d8.bat" --min-api 26 --lib "$JAR" --output out/dex $(find out/classes -name '*.class')

cp out/base.apk out/unaligned.apk
(cd out/dex && "$BT/aapt.exe" add ../unaligned.apk classes.dex >/dev/null)
"$BT/zipalign.exe" -f 4 out/unaligned.apk out/aligned.apk

# One fixed local debug key, so every rebuild installs over the last one.
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias home \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=home"
fi

"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android --ks-key-alias home \
  --out launcher.apk out/aligned.apk
"$BT/apksigner.bat" verify launcher.apk
ls -la launcher.apk
