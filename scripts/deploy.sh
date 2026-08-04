#!/usr/bin/env bash
# ===========================================================
#  빌드 → 에뮬레이터 설치 → 실행 → 스크린샷
#
#  안드로이드 스튜디오 없이 개발할 때 쓰는 한 방 스크립트입니다.
#  코드를 고친 뒤 이것만 돌리면 화면까지 확인할 수 있습니다.
#
#  사용법 (Git Bash):
#      ./scripts/deploy.sh                 디버그 빌드 → 설치 → 스크린샷
#      ./scripts/deploy.sh --release       릴리즈(R8) 빌드로 설치
#      ./scripts/deploy.sh --no-shot       스크린샷 생략
#
#  환경변수로 바꿀 수 있는 것:
#      EMULATOR   기본 127.0.0.1:7555 (무무 플레이어 12)
#                 기본 AVD 는 보통 emulator-5554
#      DELAY      실행 후 스크린샷까지 대기 초. 기본 10
# ===========================================================
set -euo pipefail

cd "$(dirname "$0")/.."

EMULATOR="${EMULATOR:-127.0.0.1:7555}"
DELAY="${DELAY:-10}"
APP_ID="com.youngs.picview"
ACTIVITY=".MainActivity"

VARIANT=debug
TAKE_SHOT=1
for arg in "$@"; do
    case "$arg" in
        --release) VARIANT=release ;;
        --no-shot) TAKE_SHOT=0 ;;
        *) echo "모르는 옵션: $arg"; exit 1 ;;
    esac
done

# ---- SDK 경로 확인 ----
# 안드로이드 스튜디오 없이 클론하면 local.properties 가 없습니다.
# (스튜디오는 프로젝트를 열 때 이 파일을 자동으로 만들어 줍니다)
# 없으면 ANDROID_HOME 을 보고 대신 만들어 줍니다.
if [ ! -f local.properties ]; then
    if [ -z "${ANDROID_HOME:-}" ]; then
        echo "★ local.properties 도 ANDROID_HOME 도 없습니다."
        echo "  Android SDK 를 설치하고 환경변수를 설정하세요:"
        echo "    ANDROID_HOME=C:\\Android\\Sdk"
        echo "  자세한 내용은 README.md 의 '방법 B' 를 보세요."
        exit 1
    fi
    echo "▶ local.properties 생성 (ANDROID_HOME 기준)"
    # 윈도우 경로의 역슬래시·콜론은 properties 형식에 맞게 이스케이프해야 합니다.
    printf 'sdk.dir=%s\n' \
        "$(printf '%s' "$ANDROID_HOME" | sed 's/\\/\\\\/g; s/:/\\:/g')" \
        > local.properties
fi

# ---- adb 찾기 ----
if command -v adb >/dev/null 2>&1; then
    ADB=adb
elif [ -x "${ANDROID_HOME:-}/platform-tools/adb.exe" ]; then
    ADB="${ANDROID_HOME}/platform-tools/adb.exe"
elif [ -x "${ANDROID_HOME:-}/platform-tools/adb" ]; then
    ADB="${ANDROID_HOME}/platform-tools/adb"
elif [ -x "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" ]; then
    ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
else
    echo "★ adb 를 찾지 못했습니다. ANDROID_HOME 을 설정하세요."
    exit 1
fi

# ---- 1. 빌드 ----
if [ "$VARIANT" = release ]; then
    echo "▶ 릴리즈 빌드 (R8 난독화 포함)"
    ./gradlew assembleRelease --console=plain
    APK=app/build/outputs/apk/release/app-release.apk
else
    echo "▶ 디버그 빌드"
    ./gradlew assembleDebug --console=plain
    APK=app/build/outputs/apk/debug/app-debug.apk
fi

# ---- 2. 에뮬레이터 연결 ----
# 무무 플레이어는 TCP 로 붙습니다. 실기기(USB)는 connect 가 필요 없어 실패해도 넘어갑니다.
"$ADB" connect "$EMULATOR" >/dev/null 2>&1 || true

if ! "$ADB" -s "$EMULATOR" get-state >/dev/null 2>&1; then
    echo
    echo "★ 에뮬레이터에 연결하지 못했습니다: $EMULATOR"
    echo "  - 무무 플레이어가 실행 중인지 확인하세요."
    echo "  - 포트가 다르면: EMULATOR=127.0.0.1:5555 ./scripts/deploy.sh"
    echo "  - 연결된 기기 목록:"
    "$ADB" devices
    exit 1
fi

# ---- 3. 설치 · 실행 ----
echo "▶ 설치: $EMULATOR"
"$ADB" -s "$EMULATOR" install -r "$APK"

"$ADB" -s "$EMULATOR" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$ADB" -s "$EMULATOR" shell am force-stop "$APP_ID" || true
"$ADB" -s "$EMULATOR" logcat -c || true
"$ADB" -s "$EMULATOR" shell am start -n "${APP_ID}/${ACTIVITY}" >/dev/null

# ---- 4. 스크린샷 ----
if [ "$TAKE_SHOT" = 1 ]; then
    echo "▶ ${DELAY}초 대기 후 스크린샷"
    sleep "$DELAY"
    mkdir -p build-output
    "$ADB" -s "$EMULATOR" exec-out screencap -p > build-output/screen.png
    echo "  → build-output/screen.png"
fi

# ---- 5. 크래시 확인 ----
CRASH=$("$ADB" -s "$EMULATOR" logcat -d -s AndroidRuntime:E 2>/dev/null | tail -20)
if [ -n "$CRASH" ]; then
    echo
    echo "★ 크래시 로그:"
    echo "$CRASH"
    exit 1
fi

echo
echo "완료. 에뮬레이터에서 확인하세요."
