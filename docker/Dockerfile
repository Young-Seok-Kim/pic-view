# ===========================================================
#  정읍 시선 — Android 빌드 환경
#
#  이 이미지는 "빌드"만 합니다. 에뮬레이터·실기기 테스트는 못 합니다
#  (컨테이너에 USB·GPU 가 없습니다). 단위 테스트와 APK/AAB 생성은 됩니다.
#
#  소스는 이미지에 굽지 않고 실행할 때 마운트합니다.
#  코드를 고칠 때마다 이미지를 다시 만들 필요가 없습니다.
# ===========================================================
FROM eclipse-temurin:17-jdk-jammy

# 프로젝트 기준: JDK 17 / Gradle 8.11.1 / AGP 8.10.1 / compileSdk 36
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive \
    # Gradle 데몬은 컨테이너에서 메모리만 잡아먹고 이득이 없습니다.
    GRADLE_OPTS="-Dorg.gradle.daemon=false"

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        unzip \
        git \
        rsync \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ---- Android 커맨드라인 도구 ----
# 버전을 고정합니다. latest 로 두면 나중에 이미지를 다시 만들 때
# 도구가 바뀌어 빌드 결과가 달라질 수 있습니다.
ARG CMDLINE_TOOLS=11076708
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# ---- SDK 구성 요소 ----
# app/build.gradle.kts 의 compileSdk 와 맞춰야 합니다. 바꾸면 여기도 같이 바꾸세요.
RUN yes | sdkmanager --licenses > /dev/null 2>&1 \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;36.0.0" \
    > /dev/null

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

# 기본 동작: 릴리즈 AAB 생성.
# 다른 작업은 실행할 때 덧붙이면 됩니다.
#   docker compose -f docker/docker-compose.yml run --rm build ./gradlew testDebugUnitTest
CMD ["./gradlew", "bundleRelease", "--no-daemon"]
