# 도커로 빌드하기

안드로이드 스튜디오나 SDK 설치 없이, 도커만 있으면 이 프로젝트를 빌드할 수 있습니다.

> **이 컨테이너가 하는 것 / 못 하는 것**
>
> | | |
> |---|---|
> | ✅ APK · AAB 생성 | 스토어 업로드용 산출물 |
> | ✅ 단위 테스트 실행 | 30개 |
> | ❌ 에뮬레이터 실행 | 컨테이너에 GPU 가 없습니다 |
> | ❌ 실기기 설치·테스트 | 컨테이너에 USB 가 없습니다 |
>
> 화면을 눈으로 확인하는 작업은 여전히 안드로이드 스튜디오나 무무 플레이어에서 하셔야 합니다.

---

## 1. 도커 설치 (처음 한 번)

1. <https://www.docker.com/products/docker-desktop/> 에서 **Docker Desktop for Windows** 내려받기
2. 설치 중 **"Use WSL 2 instead of Hyper-V"** 체크 (기본값)
3. 설치 후 재부팅
4. 작업 표시줄에 고래 아이콘이 뜨고 초록불이면 준비 완료

확인:
```bash
docker --version
```

## 2. 키 파일 준비

프로젝트 루트(`PicView/`)에 **`.env`** 파일을 만듭니다.

```env
TOUR_API_KEY=관광공사_디코딩_키
NAVER_CLIENT_ID=네이버_지도_클라이언트_ID
NAVER_CLIENT_SECRET=네이버_지도_시크릿
KAKAO_NATIVE_APP_KEY=카카오_네이티브_키
GEMINI_API_KEY=제미나이_키
```

- `.env` 는 `.gitignore` 에 있어 커밋되지 않습니다.
- **키를 이미지에 굽지 않습니다.** 실행할 때 환경변수로만 들어갑니다.
  이미지에 넣으면 레이어에 남아서, 이미지를 받은 사람이 전부 꺼내 볼 수 있습니다.
- 키가 없어도 빌드는 됩니다. 해당 기능만 폴백 동작합니다
  (예: 제미나이 키가 없으면 코스 설명이 템플릿 문구로 나옵니다).

## 3. 이미지 만들기 (처음 한 번, 5~15분)

```bash
docker compose -f docker/docker-compose.yml build
```

안드로이드 SDK 를 받느라 시간이 걸립니다. 한 번 만들면 다시 안 만들어도 됩니다.

## 4. 빌드

```bash
# 릴리즈 AAB (스토어 업로드용)
docker compose -f docker/docker-compose.yml run --rm build

# 단위 테스트
docker compose -f docker/docker-compose.yml run --rm build ./gradlew testDebugUnitTest

# 디버그 APK
docker compose -f docker/docker-compose.yml run --rm build ./gradlew assembleDebug
```

결과물은 프로젝트 루트의 **`build-output/`** 폴더에 나옵니다.

첫 빌드는 의존성(네이버 지도 SDK 등)을 받느라 몇 분 걸리고,
두 번째부터는 캐시가 남아 빨라집니다.

---

## 서명에 대해

`keystore.properties` 와 키스토어 파일은 **저장소에 없습니다**(유출 방지).
따라서 컨테이너에서 나온 릴리즈 빌드는 **서명되지 않습니다.**

- 심사·리뷰용으로 "소스에서 빌드가 되는지" 확인하는 용도로는 그대로 충분합니다.
- 스토어에 올릴 서명된 AAB 가 필요하면 키스토어를 가진 사람이 만들어야 합니다.
  `docker-compose.yml` 의 `volumes` 에 아래 두 줄을 추가하면 컨테이너에서도 서명됩니다.

  ```yaml
      - ../keystore:/src/keystore:ro
      - ../keystore.properties:/src/keystore.properties:ro
  ```

---

## 다른 사람에게 전달하는 방법

### 방법 A — 이 폴더째 전달 (권장)

받는 사람이 저장소를 클론하고 위 1~4번을 따라 하면 됩니다.
전달할 것은 **소스 저장소 주소 + `.env` 값**뿐입니다.

- 장점: 전달물이 가볍고, 받는 쪽이 최신 소스로 빌드합니다.
- 공모전 심사처럼 "빌드 재현이 되는가"를 보는 경우 이쪽이 맞습니다.

### 방법 B — Docker Hub 에 올려서 전달

```bash
# 1. 로그인
docker login

# 2. 내 계정 이름표를 붙임 (아이디를 본인 것으로)
docker tag picview-build:2.0 <도커허브아이디>/picview-build:2.0

# 3. 올리기 (이미지가 3~4GB 라 시간이 걸립니다)
docker push <도커허브아이디>/picview-build:2.0
```

받는 사람:
```bash
docker pull <도커허브아이디>/picview-build:2.0
```

- 장점: SDK 다운로드 없이 바로 씀
- 단점: 용량이 큼. 공개 저장소에 올리면 누구나 받을 수 있으니
  **키가 이미지에 없는지** 반드시 확인하세요(이 구성은 안 들어갑니다).

### 방법 C — 파일로 전달 (인터넷 없이)

```bash
docker save picview-build:2.0 | gzip > picview-build.tar.gz
```

받는 사람:
```bash
docker load < picview-build.tar.gz
```

3~4GB 파일이 나옵니다. USB 등으로 건네야 할 때만 쓰세요.

---

## 자주 나는 문제

| 증상 | 원인 / 해결 |
|---|---|
| `docker: command not found` | Docker Desktop 이 실행 중인지 확인 (고래 아이콘 초록불) |
| `SDK location not found` | 컨테이너가 자체 SDK 를 씁니다. 호스트 `local.properties` 와 무관하니 이 메시지가 뜨면 이미지를 다시 만들어 보세요 |
| 첫 빌드가 아주 느림 | 정상입니다. 의존성을 받는 중이고, 두 번째부터 빨라집니다 |
| `build-output/` 이 비어 있음 | 빌드가 실패한 경우입니다. 로그 마지막 부분을 확인하세요 |
| 디스크 부족 | 이미지 + 캐시로 6GB 정도 씁니다. `docker system prune` 로 정리 |
