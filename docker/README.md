# 도커로 빌드하기

안드로이드 SDK 설치 없이, 도커만 있으면 이 프로젝트를 빌드할 수 있습니다.

---

## 왜 빌드 전용인가

| | |
|---|---|
| ✅ APK · AAB 생성 | 스토어 업로드용 산출물 |
| ✅ 단위 테스트 30개 | |
| ❌ 에뮬레이터 실행 | 컨테이너에 GPU·중첩 가상화가 없습니다 |
| ❌ 에뮬레이터에 설치 | 무무 플레이어의 adb 포트는 `127.0.0.1` 에만 열려 있어 컨테이너에서 닿지 않습니다 |

**화면을 보며 개발하는 루프는 호스트에서 하세요.** → [`scripts/deploy.sh`](../scripts/deploy.sh)

이 컨테이너의 쓸모는 **"내 PC 설정 없이도 똑같이 빌드된다"** 는 재현성입니다.
남에게 환경을 넘기거나, 심사에서 소스 빌드를 보여줄 때 씁니다.

---

## 1. 도커 설치 (처음 한 번)

1. <https://www.docker.com/products/docker-desktop/> 에서 **Docker Desktop for Windows** 내려받기
2. 설치 중 **"Use WSL 2 instead of Hyper-V"** 체크 (기본값)
3. 설치 후 재부팅
4. 작업 표시줄에 고래 아이콘이 초록불이면 준비 완료

```bash
docker --version
```

## 2. 키 파일 준비

프로젝트 루트에서:

```bash
cp .env.example .env
```

값을 채웁니다. **비워도 빌드는 됩니다** — 키가 없는 기능만 폴백 동작합니다.

- `.env` 는 `.gitignore` 에 있어 커밋되지 않습니다.
- **키를 이미지에 굽지 않습니다.** 실행할 때 환경변수로만 들어갑니다.
  이미지에 넣으면 레이어에 남아, 이미지를 받은 사람이 `docker history` 로 전부 꺼내 볼 수 있습니다.

## 3. 이미지 만들기 (처음 한 번, 5~15분)

```bash
docker compose -f docker/docker-compose.yml build
```

안드로이드 SDK 를 받느라 시간이 걸립니다. 한 번 만들면 다시 안 만들어도 됩니다.

## 4. 빌드

```bash
# 스토어 업로드용 AAB
docker compose -f docker/docker-compose.yml run --rm aab

# 디버그 APK
docker compose -f docker/docker-compose.yml run --rm apk

# 단위 테스트
docker compose -f docker/docker-compose.yml run --rm test
```

결과물은 프로젝트 루트의 **`build-output/`** 에 나옵니다.

첫 빌드는 의존성(네이버 지도 SDK 등)을 받느라 몇 분 걸리고,
두 번째부터는 Gradle 캐시가 남아 빨라집니다.

---

## 서명에 대해

`keystore.properties` 와 키스토어 파일은 **저장소에 없습니다**(유출 방지).
따라서 컨테이너에서 나온 릴리즈 빌드는 **서명되지 않습니다.**

- "소스에서 빌드가 되는가" 를 확인하는 용도로는 그대로 충분합니다.
- 스토어에 올릴 서명된 AAB 는 키스토어를 가진 사람이 만들어야 합니다.
  `docker-compose.yml` 의 `volumes` 에 아래 두 줄을 추가하면 컨테이너에서도 서명됩니다.

  ```yaml
      - ../keystore:/src/keystore:ro
      - ../keystore.properties:/src/keystore.properties:ro
  ```

---

## 다른 사람에게 전달하는 방법

### 방법 A — 저장소 주소만 전달 (권장)

받는 사람이 클론하고 위 1~4번을 따라 하면 됩니다.
전달할 것은 **저장소 주소 + `.env` 값**뿐입니다.

- 전달물이 가볍고, 받는 쪽이 항상 최신 소스로 빌드합니다.
- 공모전 심사처럼 "빌드가 재현되는가" 를 보는 경우 이쪽이 맞습니다.

### 방법 B — Docker Hub 로 전달

```bash
docker login
docker tag picview-build:2.0 <도커허브아이디>/picview-build:2.0
docker push <도커허브아이디>/picview-build:2.0
```

받는 사람:
```bash
docker pull <도커허브아이디>/picview-build:2.0
```

이미지가 3~4GB 라 올리는 데 시간이 걸립니다.
공개 저장소에 올려도 **키는 이미지에 없습니다**(이 구성 기준).

### 방법 C — 파일로 전달 (인터넷 없이)

```bash
docker save picview-build:2.0 | gzip > picview-build.tar.gz   # 보내는 쪽
docker load < picview-build.tar.gz                            # 받는 쪽
```

3~4GB 파일이 나옵니다. USB 로 건네야 할 때만 쓰세요.

---

## 자주 나는 문제

| 증상 | 원인 / 해결 |
|---|---|
| `docker: command not found` | Docker Desktop 이 실행 중인지 확인 (고래 아이콘 초록불) |
| 첫 빌드가 아주 느림 | 정상입니다. 의존성을 받는 중이고 두 번째부터 빨라집니다 |
| `build-output/` 이 비어 있음 | 빌드 실패입니다. 로그 마지막 부분을 확인하세요 |
| `bad interpreter: ...^M` | `entrypoint.sh` 가 CRLF 로 받아진 경우. `.gitattributes` 가 막고 있으니 저장소를 다시 클론해 보세요 |
| 디스크 부족 | 이미지 + 캐시로 6GB 정도 씁니다. `docker system prune` 로 정리 |
