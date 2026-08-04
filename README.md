# 📸 정읍 시선 (PicView)

> **"지금 정읍에서, 어디를 어떻게 담을까"**
> 실시간 기상·천문 데이터로 촬영 적기를 계산하는 지역 특화 출사(出寫) 안내 앱

『2026 관광데이터 활용 공모전』 출품작 · Android Native

---

## 이 앱이 하는 일

일반 관광 앱은 **"어디를 갈까"** 까지 답합니다.
정읍 시선은 **"지금 어디를, 언제, 어떻게 담을까"** 까지 답합니다.

| 기능 | 설명 |
|---|---|
| **포토스코어** | 기상청·천문연 데이터를 결합해 지금 사진이 잘 나오는 순서로 정렬. 7개 항목으로 근거를 공개합니다 |
| **빛 스케줄 코스** | 일출·일몰 시각과 촬영지의 방위를 맞춰 동선을 짭니다. 골든아워 슬롯을 먼저 예약하고 한낮엔 실내로 피합니다 |
| **촬영 정보** | 장소마다 최적 시간대·방위·추천 구도 |
| **스마트 프레임 가이드** | CameraX 위에 삼분할·대칭축·중앙원 오버레이. 밝은 하늘에서도 선이 묻히지 않게 이중 렌더링 |
| **오디오 가이드** | 장소 개요를 TTS 로 낭독. 속도 조절, 큰 글씨 모드에서는 0.85배속 |
| **출사 기록 · 일기** | 다녀온 곳이 하루 단위로 모이고, 방문 기록으로 일기 초안을 생성 |
| **미션 · 배지** | 골든아워 촬영, 유산 순례 등 6종. 방문 기록으로 자동 판정 |
| **사계절 캘린더** | 내장산 단풍·구절초 개화 등 절정 시기를 D-day 로 안내 |
| **큰 글씨 모드** | 탭 5개 → 3개, 글자·버튼 확대. 시니어 접근성 |

---

## 빌드

### 방법 A — 안드로이드 스튜디오

```bash
git clone https://github.com/Young-Seok-Kim/pic-view.git
```

프로젝트를 열면 `local.properties` 가 자동 생성됩니다. 그대로 Run 하면 됩니다.

**API 키 없이도 빌드·실행됩니다.** 키가 없는 기능은 자동으로 폴백합니다
(예: 제미나이 키가 없으면 코스 설명이 템플릿 문구로 나옵니다).

실제 데이터를 보려면 `local.properties` 에 아래를 추가하세요.

```properties
TOUR_API_KEY=한국관광공사_디코딩_키
NAVER_CLIENT_ID=네이버_지도_클라이언트_ID
GEMINI_API_KEY=제미나이_키
```

> `local.properties` 는 `.gitignore` 에 있어 커밋되지 않습니다.
> 키가 없으면 `local.defaults.properties` 의 `UNSET` 이 대신 들어갑니다.

### 방법 B — 안드로이드 스튜디오 없이 (커맨드라인)

스튜디오는 에디터일 뿐입니다. **빌드·설치·확인에 필요한 건 JDK + SDK + adb 뿐**입니다.

**처음 한 번 설치**

1. **JDK 17** — [Temurin 17](https://adoptium.net/temurin/releases/?version=17) 설치
2. **Android 커맨드라인 도구** — [SDK 도구 다운로드](https://developer.android.com/studio#command-line-tools-only)
   압축을 풀고 `C:\Android\Sdk\cmdline-tools\latest\` 에 둡니다.
3. **환경변수** `ANDROID_HOME=C:\Android\Sdk`
4. **SDK 구성 요소**
   ```bash
   sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
   ```
5. **에뮬레이터** — [무무 플레이어](https://www.mumuplayer.com/kr/) 설치
   (안드로이드 스튜디오 AVD 대신 씁니다. adb 포트는 보통 `127.0.0.1:7555`)

**개발 루프**

코드를 고친 뒤 이 한 줄이면 빌드·설치·스크린샷까지 끝납니다.

```bash
./scripts/deploy.sh
```

```
▶ 디버그 빌드
▶ 설치: 127.0.0.1:7555
▶ 10초 대기 후 스크린샷
  → build-output/screen.png
완료.
```

| 옵션 | 설명 |
|---|---|
| `./scripts/deploy.sh --release` | R8 난독화가 적용된 릴리즈 빌드로 설치 (스토어에 올라갈 것과 동일) |
| `./scripts/deploy.sh --no-shot` | 스크린샷 생략 |
| `EMULATOR=127.0.0.1:5555 ./scripts/deploy.sh` | 다른 포트의 에뮬레이터 |

> Git Bash 에서 실행하세요. 크래시가 나면 로그를 찍고 종료 코드 1 로 끝납니다.

### 방법 C — 도커 (빌드 전용)

SDK 설치조차 없이, 재현 가능한 빌드만 필요할 때 씁니다.

```bash
cp .env.example .env          # 키 입력 (비워도 빌드됨)
docker compose -f docker/docker-compose.yml build      # 처음 1회, 5~15분
docker compose -f docker/docker-compose.yml run --rm aab
```

결과물은 `build-output/` 에 나옵니다. 자세한 내용은 **[docker/README.md](docker/README.md)**.

> **에뮬레이터 설치는 못 합니다.** 컨테이너에 GPU 가 없고, 무무의 adb 포트가
> `127.0.0.1` 에만 열려 있어 컨테이너에서 닿지 않습니다.
> 화면을 보며 개발하는 루프는 방법 B 를 쓰세요.

### 테스트

```bash
./gradlew testDebugUnitTest
```

빛 스케줄 계산, 코스 배치 규칙, 미션 판정, 계절 D-day 등 30개.

---

## 기술 스택

| 레이어 | 기술 |
|---|---|
| 언어 / 최소 SDK | Kotlin · minSdk 26 (Oreo) |
| UI | XML + ViewBinding + Material 3 |
| 아키텍처 | MVVM + Repository |
| 카메라 | CameraX (SurfaceView + Canvas 오버레이) |
| 지도 | Naver Maps Android SDK |
| 네트워크 | Retrofit2 + Coroutines |
| 로컬 저장 | Room (저장한 코스 · 방문 기록 · 일기) |
| 이미지 | Glide |
| 문구 생성 | Gemini API (실패 시 템플릿 폴백) |

**로그인이 없습니다.** 모든 기록이 단말에 남고, 통계는 익명 설치 ID로만 묶입니다.

---

## 프로젝트 구조

```
app/src/main/java/com/youngs/picview/
├── data/
│   ├── api/          Retrofit 서비스 (관광공사·기상청·천문연·Gemini)
│   ├── model/        API 응답 모델
│   ├── local/        Room 엔티티 · DAO
│   └── repository/   코스·일기 저장소
├── domain/
│   ├── light/        일출·일몰 → 빛 구간 계산
│   ├── course/       출사 코스 생성 엔진
│   ├── score/        포토스코어 7팩터
│   ├── spot/         정읍 촬영지 방위·구도 정보
│   ├── mission/      미션 판정
│   ├── season/       계절별 촬영 적기
│   └── diary/        출사 일기
├── ui/               화면 (탭 5개 + 큰 글씨 모드 3개)
└── util/             좌표·거리, TTS, 설정
```

---

## 데이터 출처

- **한국관광공사 국문 관광정보 서비스** — 촬영지 목록·사진·개요 (실시간 호출)
- **기상청 초단기실황** — 기온·강수
- **한국천문연구원 출몰시각** — 일출·일몰

> 관광 정보는 매번 API 로 받아옵니다. 로컬 DB 에 저장하는 건 사용자가 만든
> 기록(저장한 코스·방문 기록·일기)뿐입니다.
>
> 촬영 지식(방위·구도·절정 시기)은 API 가 제공하지 않아 직접 정리한 값입니다.
> 장소·좌표·사진은 전부 API 실시간 데이터입니다.

---

## 알려진 제약

- **이동 거리·시간은 추정치**입니다. 직선거리에 도로 우회 계수(1.35)를 적용한 값이며,
  실제 도로 경로가 아닙니다. 카카오 모빌리티 연동 시 교체 예정입니다.
- **정읍 등록 축제가 관광공사 API 에 거의 없습니다**(2026년 0건). 그래서 캘린더는
  축제가 아니라 피사체의 절정 시기를 기준으로 만들었습니다.
- 컨테이너 빌드 결과물은 **서명되지 않습니다**(키스토어 미포함).
