package com.youngs.picview.domain.weather

import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.ui.guide.GuideOverlayView

/**
 * 날씨를 촬영 행동으로 옮깁니다.
 *
 * 날씨를 "흐림 26℃"로 보여 주고 끝내면 그건 날씨 앱입니다. 출사에서
 * 흐림은 나쁜 날이 아니라 **다른 날**입니다. 맑은 날은 대비가 세서 넓은
 * 풍경과 원색이 살고, 흐린 날은 온 하늘이 소프트박스가 되어 그늘이 없어
 * 건축의 정면과 인물의 피부결이 살아납니다. 비 오는 날은 젖은 바닥이
 * 통째로 반사면이 됩니다.
 *
 * 그래서 같은 장소라도 날씨에 따라 **추천 구도가 바뀌어야 합니다.**
 * 이 객체가 그 변환을 맡습니다.
 */
data class WeatherAdvice(
    /** 지금 빛이 어떤 상태인지 한 줄. */
    val headline: String,
    /** 오늘 이 장소에서 무엇을 어떻게 담을지. */
    val action: String,
    /** 날씨가 바꾼 구도 가이드. 카메라 오버레이와 같은 타입입니다. */
    val guide: GuideOverlayView.GuideType
)

object WeatherAdviser {

    /**
     * @param sky   지금 하늘 상태
     * @param phase 지금(또는 추천 시간대)의 빛 구간
     * @param facing 이 장소를 담는 방향
     * @param fallbackGuide 날씨가 개입하지 않을 때 쓰는 장소 고유 구도
     */
    fun of(
        sky: SkyState?,
        phase: LightPhase,
        facing: Facing,
        fallbackGuide: GuideOverlayView.GuideType
    ): WeatherAdvice = when (sky) {

        // 비·눈은 날씨가 장소보다 강하게 그림을 정합니다. 구도까지 바꿉니다.
        SkyState.RAIN, SkyState.SHOWER -> WeatherAdvice(
            headline = "비가 표면을 반사면으로 바꿔 놨어요",
            action = "젖은 바닥과 처마 끝 물방울을 낮은 각도로 담아보세요. " +
                "웅덩이에 비친 상은 실물보다 색이 진합니다.",
            guide = GuideOverlayView.GuideType.SYMMETRY
        )

        SkyState.SNOW, SkyState.SLEET -> WeatherAdvice(
            headline = "눈이 온 세상을 반사판으로 만들어요",
            action = "밝은 눈에 맞추면 카메라가 어둡게 재니 노출을 한 칸 올려 " +
                "담아보세요. 그림자가 푸르게 남습니다.",
            guide = GuideOverlayView.GuideType.CENTER
        )

        // 흐림은 그늘이 사라져 정면이 살아납니다.
        SkyState.CLOUDY -> WeatherAdvice(
            headline = "온 하늘이 소프트박스가 됐어요",
            action = "그늘이 없어 ${facing.label} 정면을 그대로 담기 좋아요. " +
                "하늘은 적게 넣고 결과 질감을 크게 잡아보세요.",
            guide = GuideOverlayView.GuideType.SYMMETRY
        )

        SkyState.PARTLY -> WeatherAdvice(
            headline = "구름 사이로 짧은 빛이 들어와요",
            action = "구름이 해를 가렸다 열 때가 절정입니다. 자리를 잡고 " +
                "빛이 드는 순간을 기다렸다 누르세요.",
            guide = fallbackGuide
        )

        // 맑음은 빛 구간이 그림을 정합니다. 날씨는 비켜섭니다.
        else -> WeatherAdvice(
            headline = when {
                phase.isGolden -> "낮게 든 빛이 길게 늘어져요"
                phase == LightPhase.MIDDAY -> "빛이 강해 대비가 셉니다"
                else -> "맑아 원색과 대비가 살아나요"
            },
            action = when {
                phase.isGolden ->
                    "${facing.label}으로 길어진 빛을 이용해 처마선과 수면 반사를 담아보세요."
                phase == LightPhase.MIDDAY ->
                    "하늘이 하얗게 날아갑니다. 하늘을 적게 넣고 그늘의 결을 담아보세요."
                else ->
                    "멀리까지 또렷하게 보이니 넓은 풍경으로 담아보세요."
            },
            guide = if (phase == LightPhase.MIDDAY) fallbackGuide
            else GuideOverlayView.GuideType.THIRDS
        )
    }
}
