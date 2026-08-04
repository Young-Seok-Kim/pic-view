#!/usr/bin/env bash
# ===========================================================
#  빌드 컨테이너 진입점
#
#  호스트 소스(/src)를 컨테이너 작업 폴더(/workspace)로 복사한 뒤 빌드합니다.
#  직접 마운트해서 쓰지 않는 이유:
#    - local.properties 의 sdk.dir 이 윈도우 경로("C:\...")라 리눅스에서 깨집니다.
#      컨테이너용으로 다시 써야 하는데, 마운트된 호스트 파일을 덮어쓰면
#      안드로이드 스튜디오 쪽 설정이 망가집니다.
#    - 컨테이너가 호스트의 build/ 를 건드리지 않게 됩니다.
# ===========================================================
set -euo pipefail

SRC=/src
WORK=/workspace
OUT=/out

echo "[1/3] 소스 복사"
mkdir -p "$WORK"
rsync -a --delete \
    --exclude '.git/' \
    --exclude 'build/' \
    --exclude '.gradle/' \
    --exclude '.idea/' \
    --exclude '.kotlin/' \
    "$SRC/" "$WORK/"

echo "[2/3] 컨테이너용 local.properties 생성"
# 키는 이미지가 아니라 실행 시 환경변수로 들어옵니다.
# 값이 없으면 UNSET 이 들어가고, 앱은 해당 기능만 폴백 동작합니다.
cat > "$WORK/local.properties" <<EOF
sdk.dir=${ANDROID_HOME}
TOUR_API_KEY=${TOUR_API_KEY:-UNSET}
NAVER_CLIENT_ID=${NAVER_CLIENT_ID:-UNSET}
NAVER_CLIENT_SECRET=${NAVER_CLIENT_SECRET:-UNSET}
KAKAO_NATIVE_APP_KEY=${KAKAO_NATIVE_APP_KEY:-UNSET}
GEMINI_API_KEY=${GEMINI_API_KEY:-UNSET}
EOF

cd "$WORK"
chmod +x gradlew

echo "[3/3] 빌드: $*"
"$@"

# 산출물을 호스트로 꺼냅니다(/out 이 마운트돼 있을 때만).
if [ -d "$OUT" ]; then
    find app/build/outputs \( -name '*.aab' -o -name '*.apk' \) -exec cp {} "$OUT/" \; 2>/dev/null || true
    echo "산출물 → /out"
    ls -la "$OUT" 2>/dev/null || true
fi
