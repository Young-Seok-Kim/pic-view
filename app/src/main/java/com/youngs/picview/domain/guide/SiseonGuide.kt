package com.youngs.picview.domain.guide

import androidx.annotation.DrawableRes
import com.youngs.picview.R
import com.youngs.picview.domain.light.LightPhase
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

/** 지금 상황(빛)에 따른 조언. 웹 시안의 contextCards. */
data class GuideContext(
    val id: String,
    val chipLabel: String,
    val label: String,
    val title: String,
    val value: String,
    val hint: String,
    val detail: String
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

    val items: List<SiseonGuideItem> = listOf(
        SiseonGuideItem(
            "low", "낮은 각도", "LOW ANGLE", R.drawable.guide_low_angle,
            "아래에서 위로 올려 찍어 건축물과 하늘의 높이를 강조해보세요.",
            "서향", "카메라를 허리 아래로 낮추기",
            listOf("수평선은 화면 아래 1/3에 두기", "지붕선과 하늘을 함께 담기", "피사체의 수직선을 곧게 맞추기")
        ),
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
        SiseonGuideItem(
            "space", "여백", "NEGATIVE SPACE", R.drawable.guide_space,
            "비워둔 공간으로 피사체의 고요함과 방향성을 강조해보세요.",
            "측광", "시선이 향하는 쪽을 비워두기",
            listOf("피사체를 한쪽 1/3에 배치하기", "빈 공간의 색을 단순하게 유지하기", "작은 인물로 장소의 규모 보여주기")
        ),
        SiseonGuideItem(
            "close", "클로즈업", "CLOSE UP", R.drawable.guide_close,
            "장소의 질감과 작은 디테일을 가까이에서 기록해보세요.",
            "순광", "빛이 닿는 질감에 초점 맞추기",
            listOf("표면의 결을 찾아 가까이 이동하기", "배경을 단순하게 정리하기", "반복되는 작은 패턴 발견하기")
        ),
        SiseonGuideItem(
            "motion", "움직임", "MOTION", R.drawable.guide_motion,
            "사람과 물결의 흐름을 이용해 정적인 장소에 리듬을 더해보세요.",
            "서향", "셔터를 조금 느리게 설정하기",
            listOf("고정된 배경을 프레임에 남기기", "움직임의 시작과 끝 예측하기", "물결이나 사람의 흐름 관찰하기")
        ),
        SiseonGuideItem(
            "layer", "레이어드", "LAYERED SCENE", R.drawable.guide_frame,
            "앞·중간·뒤의 요소를 겹쳐 장소의 깊이와 이야기를 한 장에 담아보세요.",
            "사선", "가까운 요소를 화면 한쪽에 걸치기",
            listOf("앞쪽 잎·난간·문틀을 프레임에 걸치기", "중간에 빛이나 사람을 배치하기", "뒤쪽 장소 정보가 남도록 초점 조절하기")
        ),
        SiseonGuideItem(
            "pattern", "반복 패턴", "REPEATING PATTERN", R.drawable.guide_top,
            "기둥·창·기와·돌의 반복 속에서 한 번 깨지는 지점을 찾아보세요.",
            "정면", "반복을 깨는 하나의 피사체 찾기",
            listOf("같은 형태가 세 번 이상 이어지는 곳 찾기", "사람이나 색 하나로 반복을 끊기", "반복 간격이 일정하게 보이도록 이동하기")
        ),
        SiseonGuideItem(
            "diagonal", "대각선", "DIAGONAL COMPOSITION", R.drawable.guide_leading,
            "다리·처마·그림자의 대각선을 활용해 정적인 장면에 속도와 방향을 만들어보세요.",
            "측광", "한쪽 모서리에서 반대쪽으로 선 연결하기",
            listOf("대각선의 시작점을 모서리에 두기", "선 위에 사람이나 밝은 지점 배치하기", "수평선은 보조선으로 단순하게 유지하기")
        ),
        SiseonGuideItem(
            "perspective", "시점 전환", "CHANGE YOUR VIEW", R.drawable.guide_low_angle,
            "눈높이를 바꾸고 한 걸음 옆으로 이동해 익숙한 장소를 새롭게 보세요.",
            "이동", "서서·낮춰서·옆에서 세 번 비교하기",
            listOf("눈높이보다 낮은 시점 한 장 찍기", "좌우로 이동해 배경 겹침 바꾸기", "세 장 중 장소성이 가장 강한 컷 고르기")
        )
    )

    fun byId(id: String?): SiseonGuideItem =
        items.firstOrNull { it.id == id } ?: items.first { it.id == "reflection" }

    val contexts: List<GuideContext> = listOf(
        GuideContext("clear", "맑음", "CLEAR LIGHT", "그림자 경계를 찾아보세요", "맑음 · 대비 강함", "빛과 그림자의 경계가 가장 선명한 상황", "밝은 부분과 어두운 부분이 만나는 선을 구도의 기준으로 삼으세요."),
        GuideContext("cloudy", "흐림", "SOFT LIGHT", "부드러운 면을 따라가세요", "흐림 · 대비 낮음", "표면의 질감과 색이 고르게 보이는 상황", "강한 그림자 대신 건축물의 결, 인물의 표정, 색의 차이를 관찰하세요."),
        GuideContext("sunrise", "일출", "FIRST LIGHT", "첫 빛이 닿는 곳을 찾으세요", "일출 무렵", "차가운 그림자에서 따뜻한 빛으로 바뀌는 순간", "빛이 처음 닿는 지점을 주 피사체로 두고 주변의 여백을 남겨보세요."),
        GuideContext("sunset", "일몰", "GOLDEN HOUR", "노을빛이 닿는 곳을 담아보세요", "일몰 무렵", "낮은 빛이 길게 들어오는 상황", "지붕선·수면·나뭇가지에 닿은 따뜻한 빛을 따라 이동해보세요."),
        GuideContext("night", "야간", "BLUE HOUR", "밝은 점 하나를 남기세요", "야간", "하늘과 조명이 함께 보이는 상황", "프레임 안에 조명이나 반사처럼 시선을 붙잡는 밝은 점을 하나만 두세요."),
        GuideContext("rain", "비", "WET SURFACE", "젖은 표면의 반사광을 찾으세요", "비 · 반사 강함", "바닥과 유리 표면이 두 번째 장면이 되는 상황", "실제 피사체보다 바닥에 비친 색과 빛이 더 선명한 위치를 찾아보세요.")
    )

    fun contextById(id: String?): GuideContext =
        contexts.firstOrNull { it.id == id } ?: contexts.first { it.id == "sunset" }

    private val signals: Map<String, GuideSignal> = mapOf(
        "low" to GuideSignal("HEIGHT SIGNAL", "시선을 낮춰보세요", "허리 아래 30–50cm", "건축물의 높이감이 살아나는 위치", "카메라를 낮추고 지붕선이 하늘로 열리는 지점을 찾으세요."),
        "symmetry" to GuideSignal("BALANCE SIGNAL", "중앙축을 먼저 맞추세요", "좌우 여백 1 : 1", "반복되는 선이 균형을 이루는 지점", "화면 중앙의 기둥·문·창을 기준으로 수평과 좌우 여백을 확인하세요."),
        "silhouette" to GuideSignal("BACKLIGHT SIGNAL", "윤곽이 선명해지는 순간", "일몰 20–35분 전", "하늘이 피사체보다 밝을 때", "피사체를 어둡게 두고 하늘의 색과 윤곽을 먼저 노출에 맞추세요."),
        "reflection" to GuideSignal("SURFACE SIGNAL", "반사면을 찾아보세요", "물결이 잔잔한 순간", "실제 장면과 반사 경계가 만나는 지점", "수면의 밝은 반사광이 피사체 아래에 이어지는 위치를 찾으세요."),
        "leading" to GuideSignal("DIRECTION SIGNAL", "선의 끝을 따라가세요", "소실점 1개", "길·난간·담장이 만나는 지점", "선의 시작은 화면 아래에 두고, 끝점에 사람이나 빛을 배치하세요."),
        "frame" to GuideSignal("DEPTH SIGNAL", "장면 속 장면을 만드세요", "앞·중간·뒤 3층", "문·창·아치가 만드는 자연스러운 테두리", "테두리는 어둡게, 안쪽 주 피사체는 밝게 두면 깊이가 선명해집니다."),
        "top" to GuideSignal("PATTERN SIGNAL", "바닥의 반복을 찾아보세요", "패턴 3회 이상", "사람과 바닥의 크기 대비", "위에서 내려다보며 반복되는 선과 그림자 사이에 피사체를 놓으세요."),
        "space" to GuideSignal("BREATHING SIGNAL", "시선이 향하는 쪽을 비워두세요", "여백 60–70%", "피사체가 움직이거나 바라보는 방향", "비어 있는 공간이 사진의 고요함과 방향성을 말하게 두세요."),
        "close" to GuideSignal("TEXTURE SIGNAL", "빛이 닿은 질감에 가까이", "초점 거리 30cm 안팎", "표면의 결이 보이는 거리", "배경을 덜어내고 빛과 그림자가 만드는 작은 변화에 초점을 맞추세요."),
        "motion" to GuideSignal("MOMENT SIGNAL", "움직임이 지나갈 길을 남기세요", "3장 연속 촬영", "사람·물결·빛이 흐르는 방향", "배경은 고정하고 피사체의 시작·중간·끝을 연속으로 비교하세요."),
        "layer" to GuideSignal("DEPTH STACK SIGNAL", "앞·중간·뒤를 겹쳐보세요", "3개 레이어", "가까운 프레임과 장소의 중심이 만나는 지점", "가까운 요소는 살짝 걸치고, 중간의 빛과 뒤쪽 장소 정보를 한 장에 남겨보세요."),
        "pattern" to GuideSignal("RHYTHM SIGNAL", "반복을 깨는 하나를 찾으세요", "3회 반복 + 1개 변화", "기둥·창·기와 사이에 들어오는 피사체", "반복되는 형태를 먼저 찾은 뒤 사람이나 색 하나를 리듬의 변화로 배치하세요."),
        "diagonal" to GuideSignal("DIAGONAL SIGNAL", "선이 모서리에서 모서리로 흐르게", "대각선 1개", "다리·처마·그림자가 만드는 방향", "대각선의 시작은 모서리에 두고, 끝점에 빛이나 피사체를 놓아 장면에 속도를 더하세요."),
        "perspective" to GuideSignal("VIEW SHIFT SIGNAL", "눈높이를 바꿔 세 번 비교하세요", "높이 3단계", "서서·낮춰서·옆으로 이동한 차이", "같은 장소를 시점만 바꿔 촬영하면 배경 겹침과 빛의 방향이 달라지는 순간을 발견할 수 있어요.")
    )

    fun signalOf(guideId: String): GuideSignal = signals.getValue(
        if (guideId in signals) guideId else "reflection"
    )

    /** 셔터를 누르기 전 확인 네 가지. */
    val checklist: List<String> = listOf(
        "수평선과 수직선이 기울지 않았나요?",
        "모서리에 불필요한 물체가 없나요?",
        "주 피사체가 배경과 분리되어 있나요?",
        "빛의 방향과 피사체의 시선이 맞나요?"
    )

    /** 미션의 세 위치. 같은 장면을 세 번 움직여 비교합니다. */
    val missionSlots: List<String> = listOf("현재 위치", "두 걸음 가까이", "좌우 이동")

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
        "low" -> "다음에는 시선을 낮춰보세요"
        else -> "다음에는 새로운 시선을 시험해보세요"
    }

    fun guideIdFor(facts: SpotFacts): String = when (facts.guide) {
        GuideOverlayView.GuideType.SYMMETRY -> "symmetry"
        GuideOverlayView.GuideType.CENTER -> "frame"
        GuideOverlayView.GuideType.THIRDS -> when {
            listOf("반사", "수면", "물", "호수", "강").any { it in facts.note } -> "reflection"
            facts.bestPhase == LightPhase.SUNSET || facts.bestPhase == LightPhase.BLUE_DUSK -> "silhouette"
            else -> "leading"
        }
    }
}
