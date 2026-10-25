#!/usr/bin/env bash
# 一键构建 iPerf-TV (iperf-droid) debug APK
#
# 用法:
#   ./build.sh                 # 构建 debug
#   ./build.sh assembleRelease # 构建 release
#
# 前置条件:
#   - JDK 17        (设置 JAVA_HOME，或让脚本自动探测)
#   - Android SDK   (设置 ANDROID_HOME，或让脚本自动探测常见安装位置)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1) 定位 JDK 17
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
  echo "[!] 未找到 JDK 17，请设置 JAVA_HOME 后重试" >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "[*] JAVA_HOME=$JAVA_HOME"

# 2) 定位 Android SDK
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
  echo "[!] 未找到 Android SDK，请设置 ANDROID_HOME 后重试" >&2
  exit 1
fi
echo "[*] ANDROID_HOME=$ANDROID_HOME"

# 3) 构建
if [ -x "./gradlew" ]; then
  GRADLE="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
else
  echo "[!] 未找到 gradle，请安装 Gradle 8.7+ 或生成 wrapper: gradle wrapper --gradle-version 8.7" >&2
  exit 1
fi

"$GRADLE" assembleDebug "$@"

APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo ""
  echo "[✓] 构建完成: $APK"
  echo "    安装命令: adb install -r \"$APK\""
else
  echo "[!] 未找到 APK" >&2
  exit 1
fi
