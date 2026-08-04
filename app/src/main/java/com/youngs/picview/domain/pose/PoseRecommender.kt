package com.youngs.picview.domain.pose

import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.Facing
import kotlin.math.roundToInt

/**
 * 지금의 빛과 장소에 맞춰 포즈를 점수화합니다.
 *
 * 포토스코어와 같은 방식(배점 가산)을 씁니다. 곱셈 가중치를 쓰면 골든아워에
 * 전부 만점 근처로 몰려서 순서에 의미가 없어집니다.
 *
 * 시안의 포즈 점수는 92·88·86… 처럼 고정값이었지만, 고정값이면 한밤중에도
 * 실루엣이 1등으로 뜹니다. 실루엣은 해를 등져야 성립하므로 그때는 아예
 * 후순위로 내려가야 맞습니다.
 */
object PoseRecommender {

    private const val BASE = 58.0

    fun recommend(
        phase: LightPhase,
        facing: Facing,
        group: GroupSize
    ): List<PoseScore> = Pose.entries
        .map { PoseScore(it, score(it, phase, facing, group), reason(it, phase, group)) }
        .sortedByDescending { it.score }

    private fun score(
        pose: Pose,
        phase: LightPhase,
        facing: Facing,
        group: GroupSize
    ): Int {
        val total = BASE + lightFit(pose, phase) + groupFit(pose, group) + placeFit(pose, facing)
        return total.roundToInt().coerceIn(0, 100)
    }

    /** 빛 적합도(-12 ~ +30). 포즈마다 필요한 빛이 다릅니다. */
    private fun lightFit(pose: Pose, phase: LightPhase): Double = when (pose) {
        // 해를 등져야 윤곽이 살아납니다. 골든아워·블루아워가 아니면 성립하지 않습니다.
        Pose.SILHOUETTE -> when (phase) {
            LightPhase.SUNSET, LightPhase.SUNRISE -> 30.0
            LightPhase.BLUE_DUSK, LightPhase.BLUE_DAWN -> 18.0
            LightPhase.MIDDAY -> -12.0   // 해가 머리 위라 역광이 안 나옵니다
            LightPhase.NIGHT -> -10.0
            else -> 4.0
        }

        // 정지 순간을 잡아야 하므로 셔터가 빨라질 만큼 밝아야 합니다.
        Pose.JUMP -> when (phase) {
            LightPhase.MORNING, LightPhase.MIDDAY, LightPhase.AFTERNOON -> 22.0
            LightPhase.SUNRISE, LightPhase.SUNSET -> 10.0
            LightPhase.NIGHT, LightPhase.BLUE_DAWN, LightPhase.BLUE_DUSK -> -12.0
            else -> 0.0
        }

        // 하늘이 배경이므로 하늘색이 살아 있어야 합니다.
        Pose.REACH_SKY -> when (phase) {
            LightPhase.BLUE_DAWN, LightPhase.BLUE_DUSK -> 26.0
            LightPhase.SUNRISE, LightPhase.SUNSET -> 22.0
            LightPhase.MORNING, LightPhase.AFTERNOON -> 14.0
            LightPhase.MIDDAY -> 2.0     // 하늘이 하얗게 날아갑니다
            LightPhase.NIGHT -> -8.0
        }

        // 부드러운 빛에서 표정이 편안하게 나옵니다.
        Pose.WALK_AWAY -> when (phase) {
            LightPhase.SUNRISE, LightPhase.SUNSET -> 24.0
            LightPhase.MORNING, LightPhase.AFTERNOON -> 16.0
            LightPhase.MIDDAY -> 4.0
            else -> 8.0
        }

        Pose.SITTING -> when (phase) {
            LightPhase.MORNING, LightPhase.AFTERNOON -> 18.0
            LightPhase.SUNRISE, LightPhase.SUNSET -> 14.0
            LightPhase.BLUE_DUSK, LightPhase.BLUE_DAWN -> 10.0
            else -> 8.0
        }

        // 빛을 가장 덜 타는 포즈. 어떤 시간대든 무난합니다.
        Pose.HAND_FRAME -> when (phase) {
            LightPhase.MIDDAY -> 14.0    // 다른 포즈가 약해지는 시간대의 대안
            LightPhase.NIGHT -> 6.0
            else -> 12.0
        }
    }

    /** 인원 적합도(-10 ~ +10). */
    private fun groupFit(pose: Pose, group: GroupSize): Double = when (pose) {
        Pose.JUMP -> when (group) {
            GroupSize.SOLO -> 0.0
            GroupSize.PAIR, GroupSize.SMALL -> 10.0   // 여럿이 뛰면 그림이 삽니다
            GroupSize.LARGE -> 4.0                    // 타이밍 맞추기가 어려워집니다
        }
        Pose.HAND_FRAME -> when (group) {
            GroupSize.SOLO -> 10.0
            GroupSize.PAIR -> 2.0
            else -> -10.0                             // 여럿이 할 포즈가 아닙니다
        }
        Pose.SILHOUETTE -> when (group) {
            GroupSize.SOLO, GroupSize.PAIR -> 8.0
            GroupSize.SMALL -> 2.0
            GroupSize.LARGE -> -6.0                   // 윤곽이 서로 겹칩니다
        }
        Pose.SITTING -> when (group) {
            GroupSize.SOLO, GroupSize.PAIR -> 8.0
            else -> 0.0
        }
        Pose.WALK_AWAY -> when (group) {
            GroupSize.SOLO, GroupSize.PAIR -> 8.0
            GroupSize.SMALL -> 4.0
            GroupSize.LARGE -> -4.0
        }
        Pose.REACH_SKY -> when (group) {
            GroupSize.LARGE, GroupSize.SMALL -> 10.0  // 여럿일수록 개방감이 큽니다
            else -> 4.0
        }
    }

    /** 장소 적합도(-20 ~ +4). 실내에서 하늘·역광 포즈는 성립하지 않습니다. */
    private fun placeFit(pose: Pose, facing: Facing): Double = when {
        facing == Facing.INDOOR && pose.needsOutdoor -> -20.0
        facing == Facing.INDOOR && pose == Pose.HAND_FRAME -> 4.0
        pose == Pose.SILHOUETTE && (facing == Facing.WEST || facing == Facing.EAST) -> 4.0
        else -> 0.0
    }

    /** 점수가 왜 그렇게 나왔는지 한 줄로. 목록에서 설명 대신 씁니다. */
    private fun reason(pose: Pose, phase: LightPhase, group: GroupSize): String = when {
        pose == Pose.SILHOUETTE && phase.isGolden -> "지금 역광이 가장 좋아요"
        pose == Pose.SILHOUETTE && phase == LightPhase.MIDDAY -> "해가 머리 위라 지금은 어려워요"
        pose == Pose.JUMP && phase == LightPhase.NIGHT -> "어두워서 흔들리기 쉬워요"
        pose == Pose.JUMP && group != GroupSize.SOLO -> "여럿이 함께 뛰면 더 살아나요"
        pose == Pose.REACH_SKY && (phase == LightPhase.BLUE_DAWN || phase == LightPhase.BLUE_DUSK) ->
            "푸른 하늘이 배경으로 깔려요"
        pose == Pose.HAND_FRAME && phase == LightPhase.MIDDAY -> "빛이 강한 지금 무난한 선택이에요"
        pose == Pose.WALK_AWAY && phase.isGolden -> "부드러운 빛에 걸음이 자연스러워요"
        else -> pose.tagline
    }
}

/** 포즈 한 건의 추천 결과. */
data class PoseScore(
    val pose: Pose,
    val score: Int,
    val reason: String
)
