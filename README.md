# 📸 Pic View (정읍시선)
> **실시간 기상·천문 데이터 융합 기반의 로컬 출사 가이드 및 스마트 프레임 어시스턴트 플랫폼**

본 프로젝트는 『2026 관광데이터 활용 공모전』 출품작으로, 전라북도 정읍의 관광 자원을 사계절 내내 활성화하기 위해 공공데이터를 융합 연산하는 Android Native 애플리케이션입니다.

---

## 🌟 Key Features (핵심 기능)

* **실시간 '시선' 추천 엔진 (Photo-Score)**
  * 기상청 초단기예보(하늘 상태, 강수) 및 한국천문연구원 API(일출·일몰) 데이터를 안드로이드 단에서 실시간 결합 연산.
  * 일출/일몰 전후 30분(매직아워) 연산 시 가중치를 부여하고, 우천 시에는 실내/문화시설 스팟(무성서원 등)으로 추천 큐레이션을 다이내믹 시프트하여 "지금 이 순간 최적의 스팟"을 정렬합니다.
* **스마트 프레임 가이드 (CameraX Overlay)**
  * Android CameraX API를 활용하여 SurfaceView 위에 Canvas를 레이어로 오버레이.
  * 내장산, 구절초 지방정원 등 정읍 명소별 최적의 구도 가이드라인(3분할, 대칭, 프레임 내 프레임 등)을 화면에 제시하여 누구나 전문가 수준의 사진 촬영을 하도록 지원합니다.
* **글로벌 다국어 익스텐션 (Localization)**
  * 외래 관광객 유입 트렌드에 대응하여, 시스템 `strings.xml` 기반의 리소스 다국어(영어/중국어/일어) 처리를 인프라로 구축하여 글로벌 확장성을 확보했습니다.

---

## 🛠️ Tech Stacks (Android Native)

* **Language & OS**: Kotlin / Minimum SDK 26 (Oreo) 이상 타겟팅
* **UI Framework**: Android Architecture Components (XML / ViewBinding / Material Design)
* **Asynchronous / Networking**: Retrofit2 + Coroutine 기반의 REST API 비동기 파싱
* **Database & BaaS**: Google Firebase Firestore (사용자 즐겨찾기 및 커뮤니티 데이터 동기화)
* **Maps SDK**: Naver Map Android SDK (위치 기반 도보/차량 경로 시각화 및 마커 다이내믹 렌더링)
* **Image Loading**: Glide Library (고화질 이미지 로딩 최적화 및 메모리 관리)
