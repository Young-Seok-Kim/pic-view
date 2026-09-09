package com.youngs.picview.domain.guide

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.youngs.picview.R
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.SpotFacts
import com.youngs.picview.ui.guide.GuideOverlayView

/**
 * 시선 가이드의 촬영 구도 한 장.
 *
 * 웹 시안(GuidePage.tsx)의 guides 배열을 그대로 옮겼습니다. 데이터가
 * 코드에 있는 이유는 [com.youngs.picview.domain.spot.SpotFactsTable] 과
 * 같습니다 — 이것은 UI 문자열이 아니라 촬영 지식입니다.
 */
data class SiseonGuideItem(
    val id: String,
    val title: String,
    val english: String,
    @DrawableRes val imageRes: Int,
    val description: String,
    val direction: String,
    val tip: String,
    val tips: List<String>
)

/**
 * 지금 상황(빛)에 따른 조언. 웹 시안의 contextCards.
 *
 * 처음에는 문구 넷(label·title·value·hint·detail)만 들고 있었습니다. 그래서
 * 날씨 탭을 바꿔도 **말만 바뀌고 사진과 구도는 그대로**였고, 필터가 무엇을
 * 위해 있는지 알 수 없었습니다. 날씨마다 다음 다섯이 실제로 갈립니다.
 *
 *   ① 참고 사진과 그 라벨 ([photoRes] · [photoLabelEn] · [photoLabelKo])
 *   ② 그 사진에서 볼 것 한 줄 ([guidePoint])
 *   ③ 그 날씨에만 쓰는 촬영 기능 두셋 ([tools])
 *   ④ 촬영 후 스스로에게 묻는 복습 질문 ([frameQuestion])
 *   ⑤ 탭·라벨의 색 ([toneRes]) — 글을 읽지 않아도 구분되게
 *
 * 여기에 [guideId] 를 두어 날씨를 고르면 구도 캐러셀까지 그 날씨의 구도로
 * 옮겨 갑니다. 사진·구도·기능·질문이 한꺼번에 바뀌어야 "필터를 눌렀다"가
 * 화면에 남습니다.
 */
data class GuideContext(
    val id: String,
    val chipLabel: String,
    val label: String,
    val title: String,
    val value: String,
    val hint: String,
    val detail: String,
    /** 탭에 붙는 이모지. 색을 못 보는 상황에서도 구분되게 합니다. */
    val emoji: String,
    /** 이 날씨의 컨셉 한 마디 — "빛과 그림자". */
    val concept: String,
    @DrawableRes val photoRes: Int,
    val photoLabelEn: String,
    val photoLabelKo: String,
    /** 참고 사진에서 무엇을 볼지 한 줄. */
    val guidePoint: String,
    /** 이 날씨에서만 뜻이 있는 촬영 기능 두셋. */
    val tools: List<String>,
    /** MY FRAME 복습 질문 — 날씨마다 잘 찍었는지 재는 자가 다릅니다. */
    val frameQuestion: String,
    @ColorRes val toneRes: Int,
    @ColorRes val toneContainerRes: Int,
    /** 이 날씨가 부르는 구도. 탭을 바꾸면 캐러셀도 여기로 옮겨 갑니다. */
    val guideId: String
)

/** 구도별 한 걸음 더 — 웹 시안의 signalCards를 글로 옮긴 것. */
data class GuideSignal(
    val label: String,
    val title: String,
    val value: String,
    val hint: String,
    val detail: String
)

object SiseonGuide {

    /**
     * 구도 열 가지.
     *
     * 예전에는 열넷이었습니다. 그런데 참고 사진은 열 장뿐이라 넷은 남의
     * 사진을 빌려 썼고("레이어드"와 "프레임 인 프레임"이 같은 창틀 사진,
     * "대각선"이 단풍터널), 빌리지 않은 것도 사진과 이름이 어긋났습니다
     * ("클로즈업" 자리에 고택 전경).
     *
     * 구도를 가르치는 화면에서 사진이 그 구도가 아니면 가르치는 것이
     * 틀린 것이 됩니다. 그래서 **사진이 실제로 보여 주는 구도**를 기준으로
     * 다시 짰습니다 — 한 구도에 한 장, 빌려 쓰는 사진 없음.
     *
     * 뺀 넷(낮은 각도·클로즈업·대각선·시점 전환)은 맞는 정읍 실사진이
     * 생기면 그때 되살립니다. 없는 사진을 채우려고 다른 장소의 사진이나
     * 생성 이미지를 넣지는 않습니다.
     */
    val items: List<SiseonGuideItem> = listOf(
        SiseonGuideItem(
            "symmetry", "대칭", "SYMMETRY", R.drawable.guide_symmetry,
            "중앙축을 화면 가운데에 맞춰 좌우 균형을 만들어보세요.",
            "정면", "중앙선과 좌우 여백 맞추기",
            listOf("반복되는 창과 기둥 찾기", "수평선을 중앙축에 맞추기", "좌우 밝기 차이를 확인하기")
        ),
        SiseonGuideItem(
            "silhouette", "실루엣", "SILHOUETTE", R.drawable.guide_silhouette,
            "빛을 피사체 뒤에 두고 윤곽을 선명하게 남겨보세요.",
            "역광", "밝은 하늘 기준으로 노출 맞추기",
            listOf("사람의 옆모습이나 나뭇가지 활용", "피사체 형태를 단순하게 만들기", "하늘의 색을 먼저 관찰하기")
        ),
        SiseonGuideItem(
            "reflection", "반사", "REFLECTION", R.drawable.guide_reflection,
            "수면이나 젖은 바닥에 생기는 빛의 반사를 함께 담아보세요.",
            "측광", "반사면을 화면 아래 절반에 배치",
            listOf("물결이 잔잔할 때 촬영하기", "실제 장면과 반사 경계 맞추기", "반사된 빛의 색도 함께 기록하기")
        ),
        SiseonGuideItem(
            "leading", "리딩라인", "LEADING LINES", R.drawable.guide_leading,
            "길과 난간의 선을 활용해 시선을 한 곳으로 유도해보세요.",
            "서향", "선의 끝점에 빛이나 사람 배치",
            listOf("선의 시작점을 화면 아래에 두기", "소실점에 초점 맞추기", "반복되는 선을 여러 개 찾기")
        ),
        SiseonGuideItem(
            "frame", "프레임 인 프레임", "FRAME IN FRAME", R.drawable.guide_frame,
            "문·창·아치 안에 피사체를 넣어 장면 속 장면을 만들어보세요.",
            "순광", "자연스러운 테두리 먼저 찾기",
            listOf("안쪽 피사체에 초점 맞추기", "테두리를 화면 가장자리까지 채우기", "밝기 차이로 깊이 만들기")
        ),
        SiseonGuideItem(
            "top", "탑뷰", "TOP VIEW", R.drawable.guide_top,
            "위에서 바라보며 패턴과 사람의 배치를 새로운 시점으로 담아보세요.",
            "동향", "바닥의 반복 패턴 찾기",
            listOf("그림자의 방향 관찰하기", "사람과 패턴의 크기 대비 만들기", "수직선과 대각선을 함께 사용하기")
        ),
        // 사진: 내장산 능선 위로 하늘이 화면의 2/3를 비웁니다.
        // 예전에는 여기에 구절초 꽃밭(빈 곳이 없는 사진)이 붙어 있었습니다.
        SiseonGuideItem(
            "space", "여백", "NEGATIVE SPACE", R.drawable.guide_low_angle,
            "비워둔 공간으로 피사체의 고요함과 방향성을 강조해보세요.",
            "측광", "시선이 향하는 쪽을 비워두기",
            listOf("피사체를 한쪽 1/3에 배치하기", "빈 공간의 색을 단순하게 유지하기", "작은 인물로 장소의 규모 보여주기")
        ),
        SiseonGuideItem(
            "motion", "움직임", "MOTION", R.drawable.guide_motion,
            "사람과 물결의 흐름을 이용해 정적인 장소에 리듬을 더해보세요.",
            "서향", "셔터를 조금 느리게 설정하기",
            listOf("고정된 배경을 프레임에 남기기", "움직임의 시작과 끝 예측하기", "물결이나 사람의 흐름 관찰하기")
        ),
        // 사진: 앞에 붉은 담쟁이, 중간에 툇마루의 사람, 뒤에 기와지붕 —
        // 앞·중간·뒤 세 겹이 그대로 보입니다. 예전 "클로즈업" 자리였는데
        // 이 사진에 가까이 찍은 것은 하나도 없습니다.
        SiseonGuideItem(
            "layer", "레이어드", "LAYERED SCENE", R.drawable.guide_close,
            "앞·중간·뒤의 요소를 겹쳐 장소의 깊이와 이야기를 한 장에 담아보세요.",
            "사선", "가까운 요소를 화면 한쪽에 걸치기",
            listOf("앞쪽 잎·난간·문틀을 프레임에 걸치기", "중간에 빛이나 사람을 배치하기", "뒤쪽 장소 정보가 남도록 초점 조절하기")
        ),
        // 사진: 구절초가 화면을 가득 반복하는데 노란 우산 하나가 그 반복을
        // 끊습니다. 이 구도가 말하는 "3회 반복 + 1개 변화" 그 자체입니다.
        SiseonGuideItem(
            "pattern", "반복 패턴", "REPEATING PATTERN", R.drawable.guide_space,
            "기둥·창·기와·꽃의 반복 속에서 한 번 깨지는 지점을 찾아보세요.",
            "정면", "반복을 깨는 하나의 피사체 찾기",
            listOf("같은 형태가 세 번 이상 이어지는 곳 찾기", "사람이나 색 하나로 반복을 끊기", "반복 간격이 일정하게 보이도록 이동하기")
        )
    )

    fun byId(id: String?): SiseonGuideItem =
        items.firstOrNull { it.id == id } ?: items.first { it.id == "reflection" }

    /**
     * 날씨 여섯.
     *
     * 참고 사진은 전부 다른 장을 씁니다 — 한국관광공사가 찍은 정읍 실사진
     * 중에서 그 날씨의 빛에 실제로 맞는 컷을 골랐습니다. 맑음은 처마
     * 그림자가 진 고택, 흐림은 안개 낀 내장산, 일출은 능선 위 실루엣,
     * 일몰은 역광에 물든 성황산 은행나무, 야간은 등불이 켜진 정읍사, 비는 잔잔한
     * 수면입니다. 같은 사진에 문구만 바꿔 다는 것은 필터가 아닙니다.
     */
    val contexts: List<GuideContext> = listOf(
        GuideContext(
            id = "clear", chipLabel = "맑음", label = "CLEAR LIGHT",
            title = "그림자 경계를 찾아보세요", value = "맑음 · 대비 강함",
            hint = "빛과 그림자의 경계가 가장 선명한 상황",
            detail = "밝은 부분과 어두운 부분이 만나는 선을 구도의 기준으로 삼으세요.",
            emoji = "☀️", concept = "빛과 그림자",
            photoRes = R.drawable.guide_close,
            photoLabelEn = "SHADOW", photoLabelKo = "그림자",
            guidePoint = "처마가 만든 그림자의 끝선을 화면의 대각선으로 쓰세요.",
            tools = listOf("빛 방향 보기", "구도 가이드", "그림자 찾기"),
            frameQuestion = "그림자 위치가 잘 맞았나요?",
            toneRes = R.color.weather_clear, toneContainerRes = R.color.weather_clear_container,
            guideId = "layer"
        ),
        GuideContext(
            id = "cloudy", chipLabel = "흐림", label = "SOFT LIGHT",
            title = "부드러운 면을 따라가세요", value = "흐림 · 대비 낮음",
            hint = "표면의 질감과 색이 고르게 보이는 상황",
            detail = "강한 그림자 대신 건축물의 결, 인물의 표정, 색의 차이를 관찰하세요.",
            emoji = "☁️", concept = "인물 · 색감 · 건축 디테일",
            photoRes = R.drawable.guide_motion,
            photoLabelEn = "SOFT LIGHT", photoLabelKo = "부드러운 빛",
            guidePoint = "그늘이 없으니 색이 가장 고르게 나옵니다. 색으로 주인공을 정하세요.",
            tools = listOf("인물 구도", "건축 디테일", "색 찾기"),
            frameQuestion = "인물과 배경 비율은 어땠나요?",
            toneRes = R.color.weather_cloudy, toneContainerRes = R.color.weather_cloudy_container,
            guideId = "motion"
        ),
        GuideContext(
            id = "sunrise", chipLabel = "일출", label = "FIRST LIGHT",
            title = "첫 빛이 닿는 곳을 찾으세요", value = "일출 무렵",
            hint = "차가운 그림자에서 따뜻한 빛으로 바뀌는 순간",
            detail = "빛이 처음 닿는 지점을 주 피사체로 두고 주변의 여백을 남겨보세요.",
            emoji = "🌅", concept = "태양 + 사람 + 건축, 골든타임 카운트다운",
            photoRes = R.drawable.guide_low_angle,
            photoLabelEn = "FIRST LIGHT", photoLabelKo = "첫 빛",
            guidePoint = "능선 위에 사람 하나를 세우고 나머지 하늘은 비워 두세요.",
            tools = listOf("해 위치 보기", "여백 가이드", "골든타임"),
            frameQuestion = "첫 빛이 닿은 곳을 어디에 두셨나요?",
            toneRes = R.color.weather_sunrise, toneContainerRes = R.color.weather_sunrise_container,
            guideId = "space"
        ),
        GuideContext(
            id = "sunset", chipLabel = "일몰", label = "GOLDEN HOUR",
            title = "노을빛이 닿는 곳을 담아보세요", value = "일몰 무렵",
            hint = "낮은 빛이 길게 들어오는 상황",
            detail = "지붕선·수면·나뭇가지에 닿은 따뜻한 빛을 따라 이동해보세요.",
            emoji = "🌇", concept = "지금 아니면 놓치는 마지막 빛",
            // 성황산 — 낮은 해가 은행나무 뒤에서 들어와 잎을 통과합니다.
            // 전에는 구절초 언덕(낮 사진)이 붙어 있어 "일몰" 카드에 해가 없었습니다.
            photoRes = R.drawable.guide_last_light,
            photoLabelEn = "LAST LIGHT", photoLabelKo = "마지막 빛",
            guidePoint = "역광으로 들어온 빛이 꽃과 잎을 통과하는 자리를 찾으세요.",
            tools = listOf("일몰 카운트다운", "태양 위치", "촬영 포인트"),
            frameQuestion = "노을과 인물의 위치는 어땠나요?",
            toneRes = R.color.weather_sunset, toneContainerRes = R.color.weather_sunset_container,
            guideId = "pattern"
        ),
        GuideContext(
            id = "night", chipLabel = "야간", label = "BLUE HOUR",
            title = "밝은 점 하나를 남기세요", value = "야간",
            hint = "하늘과 조명이 함께 보이는 상황",
            detail = "프레임 안에 조명이나 반사처럼 시선을 붙잡는 밝은 점을 하나만 두세요.",
            emoji = "🌙", concept = "조명과 어둠의 대비",
            photoRes = R.drawable.guide_silhouette,
            photoLabelEn = "NIGHT LIGHT", photoLabelKo = "빛",
            guidePoint = "등불 하나를 밝은 점으로 두고 나머지는 검게 눌러 두세요.",
            tools = listOf("야간 촬영모드", "빛 찾기", "휴대폰 고정 안내"),
            frameQuestion = "빛과 어둠의 균형은 어땠나요?",
            toneRes = R.color.weather_night, toneContainerRes = R.color.weather_night_container,
            guideId = "silhouette"
        ),
        GuideContext(
            id = "rain", chipLabel = "비", label = "WET SURFACE",
            title = "젖은 표면의 반사광을 찾으세요", value = "비 · 반사 강함",
            hint = "바닥과 유리 표면이 두 번째 장면이 되는 상황",
            detail = "실제 피사체보다 바닥에 비친 색과 빛이 더 선명한 위치를 찾아보세요.",
            emoji = "🌧️", concept = "오늘만 찍을 수 있는 반사 · 젖은 표면",
            photoRes = R.drawable.guide_reflection,
            photoLabelEn = "REFLECTION", photoLabelKo = "반사",
            guidePoint = "수면이 잔잔해지는 순간에 실제와 반사의 경계를 화면 가운데 두세요.",
            tools = listOf("반사 찾기", "빗방울 찍기", "비 그친 직후 알림"),
            frameQuestion = "반사되는 영역을 잘 활용했나요?",
            toneRes = R.color.weather_rain, toneContainerRes = R.color.weather_rain_container,
            guideId = "reflection"
        )
    )

    fun contextById(id: String?): GuideContext =
        contexts.firstOrNull { it.id == id } ?: contexts.first { it.id == "sunset" }

    private val signals: Map<String, GuideSignal> = mapOf(
        "symmetry" to GuideSignal("BALANCE SIGNAL", "중앙축을 먼저 맞추세요", "좌우 여백 1 : 1", "반복되는 선이 균형을 이루는 지점", "화면 중앙의 기둥·문·창을 기준으로 수평과 좌우 여백을 확인하세요."),
        "silhouette" to GuideSignal("BACKLIGHT SIGNAL", "윤곽이 선명해지는 순간", "일몰 20–35분 전", "하늘이 피사체보다 밝을 때", "피사체를 어둡게 두고 하늘의 색과 윤곽을 먼저 노출에 맞추세요."),
        "reflection" to GuideSignal("SURFACE SIGNAL", "반사면을 찾아보세요", "물결이 잔잔한 순간", "실제 장면과 반사 경계가 만나는 지점", "수면의 밝은 반사광이 피사체 아래에 이어지는 위치를 찾으세요."),
        "leading" to GuideSignal("DIRECTION SIGNAL", "선의 끝을 따라가세요", "소실점 1개", "길·난간·담장이 만나는 지점", "선의 시작은 화면 아래에 두고, 끝점에 사람이나 빛을 배치하세요."),
        "frame" to GuideSignal("DEPTH SIGNAL", "장면 속 장면을 만드세요", "앞·중간·뒤 3층", "문·창·아치가 만드는 자연스러운 테두리", "테두리는 어둡게, 안쪽 주 피사체는 밝게 두면 깊이가 선명해집니다."),
        "top" to GuideSignal("PATTERN SIGNAL", "바닥의 반복을 찾아보세요", "패턴 3회 이상", "사람과 바닥의 크기 대비", "위에서 내려다보며 반복되는 선과 그림자 사이에 피사체를 놓으세요."),
        "space" to GuideSignal("BREATHING SIGNAL", "시선이 향하는 쪽을 비워두세요", "여백 60–70%", "피사체가 움직이거나 바라보는 방향", "비어 있는 공간이 사진의 고요함과 방향성을 말하게 두세요."),
        "motion" to GuideSignal("MOMENT SIGNAL", "움직임이 지나갈 길을 남기세요", "3장 연속 촬영", "사람·물결·빛이 흐르는 방향", "배경은 고정하고 피사체의 시작·중간·끝을 연속으로 비교하세요."),
        "layer" to GuideSignal("DEPTH STACK SIGNAL", "앞·중간·뒤를 겹쳐보세요", "3개 레이어", "가까운 프레임과 장소의 중심이 만나는 지점", "가까운 요소는 살짝 걸치고, 중간의 빛과 뒤쪽 장소 정보를 한 장에 남겨보세요."),
        "pattern" to GuideSignal("RHYTHM SIGNAL", "반복을 깨는 하나를 찾으세요", "3회 반복 + 1개 변화", "기둥·창·기와 사이에 들어오는 피사체", "반복되는 형태를 먼저 찾은 뒤 사람이나 색 하나를 리듬의 변화로 배치하세요.")
    )

    fun signalOf(guideId: String): GuideSignal = signals.getValue(
        if (guideId in signals) guideId else "reflection"
    )

    // ───────────────── 홈 → 가이드 연결 ─────────────────

    /**
     * 빛 구간을 가이드의 상황 칩으로 잇습니다.
     * 웹 시안의 칩(맑음·흐림·일출·일몰·야간·비) 중 시간이 정하는 넷만
     * 빛 구간에서 바로 나옵니다. 맑음·흐림·비는 하늘이 정하므로
     * 여기서 고르지 않습니다.
     */
    fun contextIdFor(phase: LightPhase): String = when (phase) {
        LightPhase.BLUE_DAWN, LightPhase.SUNRISE -> "sunrise"
        LightPhase.SUNSET -> "sunset"
        LightPhase.BLUE_DUSK, LightPhase.NIGHT -> "night"
        else -> "clear"
    }

    /**
     * 장소의 촬영 특성으로 첫 구도를 고릅니다.
     *
     * 앱의 구도 셋(삼분할·대칭·중앙)과 가이드의 14구도는 급이 다릅니다.
     * 대칭은 대칭으로, 중앙은 프레임 인 프레임으로 바로 이어지지만,
     * 삼분할은 "무엇을 어디에 두나"의 문제라 장소가 말하게 합니다 —
     * 물가면 반사, 해질 무렵이면 실루엣, 그 외에는 리딩라인.
     */
    /**
     * 다음 출사 추천 카드의 머리 문장 (시안 — "다음에는 물빛을 따라가보세요").
     * 추천 구도가 문장을 정합니다. 구도 이름을 그대로 쓰지 않는 이유는,
     * "반사 구도를 써보세요"보다 "물빛을 따라가보세요"가 몸을 움직이게
     * 하기 때문입니다.
     */
    fun nextHeadlineFor(guideId: String): String = when (guideId) {
        "reflection" -> "다음에는 물빛을 따라가보세요"
        "silhouette" -> "다음에는 빛을 등지고 서보세요"
        "symmetry" -> "다음에는 대칭의 축을 찾아보세요"
        "frame" -> "다음에는 장면 속 장면을 만들어보세요"
        "leading" -> "다음에는 선의 끝을 따라가보세요"
        "layer" -> "다음에는 앞과 뒤를 겹쳐보세요"
        "pattern" -> "다음에는 반복이 깨지는 곳을 찾아보세요"
        "space" -> "다음에는 하늘을 크게 비워보세요"
        else -> "다음에는 새로운 시선을 시험해보세요"
    }

    fun guideIdFor(facts: SpotFacts): String = guideIdFor(facts, facts.bestPhase)

    /**
     * 장소 × 그 시각의 빛 → 구도.
     *
     * 빛을 함께 보는 것이 이 함수의 전부입니다. 예전에는 [SpotFacts.guide]
     * 만 봤는데, 그것은 세 종류(삼분할·대칭·중앙)뿐이라 코스 세 칸이
     * 나란히 "좌우 대칭축"이 되는 일이 흔했습니다. 같은 곳이라도 한낮과
     * 야간에 찍는 법이 다르다는 것이 이 앱의 전제인데, 구도만 시각을
     * 무시하고 고정돼 있었던 셈입니다.
     *
     * 판정 순서는 **아는 것이 구체적인 순**입니다.
     *
     *   ① 장소의 촬영 특성 한 줄([SpotFacts.note])이 이미 무엇을 보라고
     *      말하는 경우 — "능선 위로 하늘을 크게 비우면", "연못 반영은".
     *      이보다 구체적인 근거는 없습니다.
     *   ② 그런 단서가 없으면 도착 시각의 빛이 정합니다. 일몰이면 실루엣,
     *      한낮이면 반복, 오전이면 겹침.
     *   ③ 둘 다 없으면 카메라 오버레이의 구도로 물러납니다.
     *
     * ①을 앞에 둔 이유는 코스 세 칸이 같은 시간대에 몰리는 일이 흔하기
     * 때문입니다. 빛만 보면 오전 세 곳이 나란히 같은 구도가 됩니다.
     *
     * @param phase 그 장소에 **도착하는 시각**의 빛. 최적 시간이 아닐 수 있습니다.
     */
    fun guideIdFor(facts: SpotFacts, phase: LightPhase): String {
        val note = facts.note

        fun noteHas(vararg words: String) = words.any { it in note }

        return when {
            // ① 장소가 스스로 말하는 것부터. 촬영 특성 표의 한 줄에 이미
            //    "무엇을 보라"가 적혀 있으면 시간보다 그것이 먼저입니다.
            noteHas("반사", "반영", "수면", "물", "호수", "저수지", "연못", "거울") -> "reflection"
            noteHas("하늘", "능선", "규모", "탁 트인", "여백") -> "space"
            noteHas("반복", "패턴", "기둥", "꽃밭", "줄지어") -> "pattern"
            noteHas("길", "터널", "선이", "이어지", "계단") -> "leading"
            noteHas("대칭", "정면") -> "symmetry"

            // 해가 낮은 시간 — 형태만 남기거나 하늘을 크게 비웁니다.
            phase == LightPhase.SUNSET || phase == LightPhase.SUNRISE ->
                if (facts.facing == Facing.WEST || facts.facing == Facing.EAST) {
                    "silhouette"
                } else {
                    "space"
                }

            // 조명이 켜지는 시간 — 밝은 점 하나를 테두리 안에 둡니다.
            phase == LightPhase.BLUE_DUSK || phase == LightPhase.BLUE_DAWN -> "frame"
            phase == LightPhase.NIGHT -> "silhouette"

            // 빛이 강한 한낮 — 반복과 그림자가 유일하게 재미있어지는 때입니다.
            phase == LightPhase.MIDDAY ->
                if (facts.facing == Facing.INDOOR) "frame" else "pattern"

            // 측광이 도는 오전·오후 — 결과 깊이가 살아납니다.
            phase == LightPhase.MORNING ->
                if (facts.guide == GuideOverlayView.GuideType.SYMMETRY) "symmetry" else "layer"
            phase == LightPhase.AFTERNOON ->
                if (facts.guide == GuideOverlayView.GuideType.SYMMETRY) "symmetry" else "leading"

            else -> when (facts.guide) {
                GuideOverlayView.GuideType.SYMMETRY -> "symmetry"
                GuideOverlayView.GuideType.CENTER -> "frame"
                GuideOverlayView.GuideType.THIRDS -> "leading"
                // 장소 표는 위 셋만 씁니다. 나머지 구도는 그 이름 그대로 구도 항목입니다.
                else -> facts.guide.id
            }
        }
    }
}
