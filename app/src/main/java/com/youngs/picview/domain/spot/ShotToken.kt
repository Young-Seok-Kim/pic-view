package com.youngs.picview.domain.spot

import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.ui.guide.GuideOverlayView

/**
 * 촬영 제안 토큰.
 *
 * 구도와 방향과 빛을 따로 흩어 놓으면 "삼분할 격자"·"서향"·"저녁 황금시간"
 * 이 각각 다른 모양으로 세 군데에 나타납니다. 읽는 사람은 그때마다 무슨
 * 말인지 다시 해석해야 합니다.
 *
 * 하나의 촬영 제안으로 묶어 **같은 모양의 작은 조각**으로 씁니다.
 * `[⇆ 대칭] [← 서쪽 빛] [☀ 저녁 사광]` 처럼 장소 카드·미션 카드에서
 * 똑같이 생긴 것이 나오면, 한 번 배운 것을 계속 쓰게 됩니다.
 *
 * 아이콘만 두지 않고 언제나 이름을 함께 답니다. ⇆ 가 대칭인지 이동인지는
 * 사람마다 다르게 읽습니다.
 */
data class ShotToken(val icon: String, val label: String) {
    val text: String get() = "$icon $label"
}

object ShotTokens {

    /** 구도. 카메라 오버레이와 같은 타입에서 뽑습니다. */
    fun of(guide: GuideOverlayView.GuideType): ShotToken = when (guide) {
        GuideOverlayView.GuideType.THIRDS -> ShotToken("▣", "삼분할")
        GuideOverlayView.GuideType.SYMMETRY -> ShotToken("⇆", "대칭")
        GuideOverlayView.GuideType.CENTER -> ShotToken("⊙", "가까이")
        else -> ShotToken("▣", guide.label)
    }

    /**
     * 방향.
     *
     * 방위 자체보다 **해가 어느 쪽에서 오는가**를 말합니다. "서향"은 지도
     * 용어지만 "서쪽 빛"은 사진의 말입니다.
     */
    fun of(facing: Facing): ShotToken? = when (facing) {
        Facing.EAST -> ShotToken("→", "동쪽 빛")
        Facing.WEST -> ShotToken("←", "서쪽 빛")
        Facing.SOUTH -> ShotToken("↓", "남쪽 채광")
        Facing.NORTH -> ShotToken("↑", "부드러운 빛")
        // 실내·무관은 방향이 없습니다. 빈 토큰을 만들면 자리만 차지합니다.
        Facing.INDOOR, Facing.ANY -> null
    }

    /** 빛. 시간대가 아니라 그 시간의 빛이 어떤 결인지를 답니다. */
    fun of(phase: LightPhase): ShotToken = when (phase) {
        LightPhase.SUNRISE -> ShotToken("☀", "아침 사광")
        LightPhase.SUNSET -> ShotToken("☀", "저녁 사광")
        LightPhase.BLUE_DAWN, LightPhase.BLUE_DUSK -> ShotToken("◑", "푸른 빛")
        LightPhase.MIDDAY -> ShotToken("☼", "강한 정광")
        LightPhase.MORNING, LightPhase.AFTERNOON -> ShotToken("◐", "측면광")
        LightPhase.NIGHT -> ShotToken("✦", "점광원")
    }

    /**
     * 한 장소의 촬영 제안 한 벌.
     *
     * 순서는 구도 → 방향 → 빛입니다. 현장에서 정하는 순서와 같습니다.
     * 어디에 설지(방향) 전에 무엇을 담을지(구도)가 먼저 정해집니다.
     */
    fun setOf(
        guide: GuideOverlayView.GuideType,
        facing: Facing,
        phase: LightPhase
    ): List<ShotToken> = listOfNotNull(of(guide), of(facing), of(phase))
}
