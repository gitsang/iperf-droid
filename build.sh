#!/usr/bin/env bash
# One-shot build of the iperf-droid debug APK
#
# Usage:
#   ./build.sh                 # build debug
#   ./build.sh assembleRelease # build release
#
# Prerequisites:
#   - JDK 17        (set JAVA_HOME, or let the script auto-detect it)
#   - Android SDK   (set ANDROID_HOME, or let the script auto-detect common locations)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1) Locate JDK 17
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  for cand in \
    "/c/Program Files/Android/Android Studio/jbr" \
    "/c/Program Files/Java/jdk-17"* \
    "/c/Program Files/Eclipse Adoptium/jdk-17"* \
    "/usr/lib/jvm/java-17"*; do
    if [ -x "$cand/bin/java" ]; then JAVA_HOME="$cand"; break; fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] && command -v java >/dev/null 2>&1; then
  JAVA_BIN="$(command -v java)"
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "[!] JDK 17 not found; set JAVA_HOME and retry" >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "[*] JAVA_HOME=$JAVA_HOME"

# 2) Locate the Android SDK
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  for cand in \
    "$HOME/AppData/Local/Android/Sdk" \
    "$HOME/Library/Android/sdk" \
    "$HOME/Android/Sdk" \
    "/usr/lib/android-sdk"; do
    if [ -d "$cand" ]; then ANDROID_HOME="$cand"; break; fi
  done
fi
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ -z "$ANDROID_HOME" ] || [ ! -d "$ANDROID_HOME" ]; then
  echo "[!] Android SDK not found; set ANDROID_HOME and retry" >&2
  exit 1
fi
echo "[*] ANDROID_HOME=$ANDROID_HOME"

# 3) Build
if [ -x "./gradlew" ]; then
  GRADLE="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
else
  echo "[!] gradle not found; install Gradle 8.7+ or generate the wrapper: gradle wrapper --gradle-version 8.7" >&2
  exit 1
fi

"$GRADLE" assembleDebug "$@"

APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo ""
  echo "[✓] Build finished: $APK"
  echo "    Install with: adb install -r \"$APK\""
else
  echo "[!] APK not found" >&2
  exit 1
fi
