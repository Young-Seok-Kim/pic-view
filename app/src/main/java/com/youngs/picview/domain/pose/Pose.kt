package com.youngs.picview.domain.pose

import androidx.annotation.DrawableRes
import com.youngs.picview.R

/**
 * 촬영 포즈.
 *
 * 이 앱은 "어디를 언제 찍을까"까지는 포토스코어와 빛 스케줄로 답해 왔지만,
 * 정작 카메라를 켠 순간에는 구도선 세 개만 그어 주고 끝났습니다.
 * 촬영지에 도착해 카메라를 든 사람이 실제로 막히는 지점은 구도가 아니라
 * "그래서 어떻게 서지"입니다. 포즈는 그 마지막 한 걸음을 메웁니다.
 *
 * 각 포즈는 잘 나오는 빛이 서로 다릅니다. 실루엣은 해를 등져야 하고,
 * 점프샷은 셔터가 충분히 빨라질 만큼 밝아야 합니다. 그래서 추천 순서를
 * 고정하지 않고 [PoseRecommender] 가 지금의 빛으로 매번 다시 계산합니다.
 */
enum class Pose(
    val emoji: String,
    val label: String,
    /** 목록에 붙는 한 줄 설명 */
    val tagline: String,
    /** 화면 위에 띄우는 촬영 요령 */
    val tip: String,
    /**
     * 이 자세를 보여 주는 예시 그림.
     *
     * 실사진을 쓰지 않습니다. 여기서 알려 줄 것은 "정읍이 이렇게 생겼다"가
     * 아니라 "몸을 이렇게 두라"이고, 그건 사진보다 그림이 정확합니다.
     * 모르는 사람 얼굴을 예시로 걸지 않아도 된다는 이점도 있습니다.
     * (장면 사진은 [com.youngs.picview.domain.guide.SiseonGuide] 쪽에서
     *  관광공사 실사진으로 다룹니다 — 둘의 역할이 다릅니다.)
     */
    @DrawableRes val artRes: Int
) {
    WALK_AWAY(
        "🚶", "뒤돌아 걷기", "자연스러운 걸음",
        "카메라를 등지고 천천히 걸어가세요. 멈춘 순간보다 걷는 중간이 자연스럽습니다.",
        R.drawable.pose_walk_away
    ),

    JUMP(
        "🦘", "점프샷", "신남 에너지!",
        "셋을 세고 뛰어오르는 정점에서 누르세요. 무릎을 접으면 더 높아 보입니다.",
        R.drawable.pose_jump
    ),

    SILHOUETTE(
        "🌅", "실루엣", "역광 활용",
        "해를 등지고 서세요. 팔다리를 몸에서 떼야 윤곽이 뭉치지 않습니다.",
        R.drawable.pose_silhouette
    ),

    SITTING(
        "🪑", "앉아서 감상", "여유로운 분위기",
        "풍경 쪽으로 시선을 두고 앉으세요. 카메라를 보지 않는 편이 편안해 보입니다.",
        R.drawable.pose_sitting
    ),

    REACH_SKY(
        "🙌", "하늘 향해", "개방감 극대화",
        "카메라를 낮춰 아래에서 올려 찍으세요. 하늘이 넓게 들어옵니다.",
        R.drawable.pose_reach_sky
    ),

    HAND_FRAME(
        "🖼", "손 프레임", "창의적 구도",
        "손으로 네모를 만들어 풍경을 담으세요. 손에 초점을 맞추면 배경이 부드러워집니다.",
        R.drawable.pose_hand_frame
    );

    /** 하늘·햇빛이 있어야 성립하는 포즈인지. 실내에서는 추천하지 않습니다. */
    val needsOutdoor: Boolean
        get() = this == SILHOUETTE || this == REACH_SKY || this == JUMP
}

/** 함께 찍는 인원. 포즈에 따라 어울리는 규모가 다릅니다. */
enum class GroupSize(val label: String) {
    SOLO("1인"),
    PAIR("2인"),
    SMALL("3~4인"),
    LARGE("5인+")
}

/**
 * 무엇을 찍는가.
 *
 * 처음에는 인물만 다뤘는데, 출사에서 사람이 프레임에 없는 경우가 오히려
 * 많습니다. 풍경만 담는 날도 있고, 내장산에서는 새와 다람쥐를, 쌍화차 거리에서는
 * 음식을 찍습니다. 피사체가 바뀌면 필요한 조언이 통째로 달라집니다.
 *
 * 인물은 "어떻게 서지"가 문제라 포즈 목록이 나오고, 나머지는 "어떻게 담지"가
 * 문제라 촬영 요령이 나옵니다.
 */
enum class Subject(val emoji: String, val label: String) {
    PERSON("🧍", "인물"),
    LANDSCAPE("🏞", "풍경"),
    ANIMAL("🐦", "동물"),
    FOOD("🍲", "음식");

    /**
     * 이 피사체를 담는 요령. 빛 구간에 따라 달라지는 것만 갈라 씁니다.
     *
     * 인물은 [Pose] 목록이 따로 있으므로 여기서는 쓰지 않습니다.
     */
    fun tipFor(phase: com.youngs.picview.domain.light.LightPhase): String = when (this) {
        PERSON -> ""

        LANDSCAPE -> when {
            phase.isGolden -> "하늘을 3분의 1만 두고 땅을 넓게 담으세요. 지금 빛이 가장 부드럽습니다."
            phase == com.youngs.picview.domain.light.LightPhase.MIDDAY ->
                "빛이 강해 하늘이 하얗게 날아갑니다. 하늘을 적게 넣고 그늘의 결을 담으세요."
            phase == com.youngs.picview.domain.light.LightPhase.NIGHT ->
                "삼각대에 올리고 2~10초로 길게 여세요. 손으로는 흔들립니다."
            else -> "수평선을 화면 3분할 선에 맞추면 안정적으로 보입니다."
        }

        ANIMAL -> when {
            phase == com.youngs.picview.domain.light.LightPhase.NIGHT ->
                "어두워서 움직임이 흐릅니다. 가만히 있을 때를 기다리세요."
            else -> "눈높이를 낮춰 눈에 초점을 맞추세요. 앞쪽에 여백을 두면 시선이 살아납니다."
        }

        FOOD -> when {
            phase == com.youngs.picview.domain.light.LightPhase.NIGHT ->
                "조명을 정면에서 받으면 납작해집니다. 창가나 옆빛을 찾으세요."
            else -> "창가에서 옆빛이나 역광으로 담으면 김과 윤기가 살아납니다."
        }
    }
}
