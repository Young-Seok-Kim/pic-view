package com.youngs.picview.domain.spot

import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.ui.guide.GuideOverlayView

/** 촬영 시 피사체를 바라보는 방향. 골든아워 배치에 씁니다. */
enum class Facing(val label: String) {
    EAST("동향"),
    WEST("서향"),
    SOUTH("남향"),
    NORTH("북향"),
    INDOOR("실내"),
    ANY("무관")
}

/**
 * 스팟 하나의 촬영 특성.
 *
 * @param facing      주 피사체를 담는 방향
 * @param bestPhase   가장 좋은 빛 구간
 * @param guide       구도 가이드(카메라 오버레이와 동일 타입)
 * @param stayMinutes 권장 체류 시간
 * @param note        코스 카드에 붙는 한 줄. 왜 이 시간에 오는지 설명합니다.
 * @param indoorOption 빛이 강한 한낮에 피신처가 되는 곳인지.
 *                     Facing.INDOOR 는 '실내를 찍는 곳'이고, 이 값은
 *                     '실내로 들어갈 수 있는 곳'이라 범위가 더 넓습니다.
 *                     문화시설은 야외 건축을 찍더라도 전시관이 있어 한낮에 쓸 수 있습니다.
 */
data class SpotFacts(
    val facing: Facing,
    val bestPhase: LightPhase,
    val guide: GuideOverlayView.GuideType,
    val stayMinutes: Int,
    val note: String,
    val indoorOption: Boolean = false
)

/**
 * 정읍 대표 촬영지의 촬영 특성 표.
 *
 * TourAPI 는 방위나 촬영 적기를 주지 않습니다. 그래서 스펙 §13 이 꼽은
 * 정읍의 전국구 콘텐츠 5곳과 주요 스팟은 실제 지형·좌향을 반영해 직접 정리하고,
 * 나머지는 카테고리 기반 추정([defaultFor])으로 채웁니다.
 *
 * 하드코딩이지만 좌표·이름·사진은 전부 TourAPI 실시간 값을 쓰고,
 * 여기 담긴 건 "그 장소를 어떻게 찍는가"라는 촬영 지식뿐입니다.
 */
object SpotFactsTable {

    /** 제목에 이 키워드가 들어가면 해당 특성을 적용합니다. 위에서부터 먼저 맞는 것. */
    private val curated: List<Pair<String, SpotFacts>> = listOf(
        "내장산" to SpotFacts(
            facing = Facing.WEST,
            bestPhase = LightPhase.SUNSET,
            guide = GuideOverlayView.GuideType.THIRDS,
            stayMinutes = 120,
            note = "단풍터널은 역광이 살아나는 늦은 오후가 절정이에요"
        ),
        "구절초" to SpotFacts(
            facing = Facing.WEST,
            bestPhase = LightPhase.SUNSET,
            guide = GuideOverlayView.GuideType.THIRDS,
            stayMinutes = 90,
            note = "꽃밭을 역광으로 담으면 꽃잎이 투명하게 빛나요"
        ),
        "무성서원" to SpotFacts(
            facing = Facing.SOUTH,
            bestPhase = LightPhase.MORNING,
            guide = GuideOverlayView.GuideType.SYMMETRY,
            stayMinutes = 50,
            note = "한옥 대칭은 그림자가 짧은 오전이 가장 깔끔해요"
        ),
        "쌍화차" to SpotFacts(
            facing = Facing.INDOOR,
            bestPhase = LightPhase.MIDDAY,
            guide = GuideOverlayView.GuideType.CENTER,
            stayMinutes = 60,
            note = "빛이 강한 한낮엔 실내가 오히려 유리해요",
            indoorOption = true
        ),
        "정읍사" to SpotFacts(
            facing = Facing.EAST,
            bestPhase = LightPhase.BLUE_DUSK,
            guide = GuideOverlayView.GuideType.SYMMETRY,
            stayMinutes = 60,
            note = "해가 진 직후 조명과 하늘이 함께 담기는 20분이 좋아요"
        ),
        "피향정" to SpotFacts(
            facing = Facing.EAST,
            bestPhase = LightPhase.SUNRISE,
            guide = GuideOverlayView.GuideType.SYMMETRY,
            stayMinutes = 40,
            note = "연못 반영은 바람이 없는 이른 아침에 가장 선명해요"
        ),
        "옥정호" to SpotFacts(
            facing = Facing.EAST,
            bestPhase = LightPhase.SUNRISE,
            guide = GuideOverlayView.GuideType.THIRDS,
            stayMinutes = 70,
            note = "물안개는 일출 직후 30분 안에 걷힙니다"
        ),
        "동학농민혁명" to SpotFacts(
            facing = Facing.ANY,
            bestPhase = LightPhase.MORNING,
            guide = GuideOverlayView.GuideType.SYMMETRY,
            stayMinutes = 50,
            note = "넓은 광장은 그림자가 긴 오전이 입체적이에요"
        )
    )

    /** 제목으로 촬영 특성을 찾습니다. 표에 없으면 카테고리 기반 추정. */
    fun of(title: String, contentTypeId: String?): SpotFacts {
        curated.firstOrNull { (keyword, _) -> title.contains(keyword) }
            ?.let { return it.second }
        return defaultFor(contentTypeId)
    }

    /**
     * 카테고리만으로 추정하는 기본값.
     * 자연은 일몰·서향, 실내 계열은 한낮으로 몰아 강한 빛을 피합니다.
     */
    fun defaultFor(contentTypeId: String?): SpotFacts = when (contentTypeId) {
        // 관광지·자연
        "12" -> SpotFacts(
            Facing.WEST, LightPhase.SUNSET,
            GuideOverlayView.GuideType.THIRDS, 70,
            "자연 풍경은 해질 무렵 색이 가장 깊어져요"
        )
        // 문화시설
        "14" -> SpotFacts(
            Facing.SOUTH, LightPhase.MORNING,
            GuideOverlayView.GuideType.SYMMETRY, 50,
            "건축물은 측광이 들어오는 오전이 입체적이에요",
            // 전시관·실내 공간이 있어 한낮 피신처로도 쓸 수 있습니다.
            indoorOption = true
        )
        // 레포츠
        "28" -> SpotFacts(
            Facing.ANY, LightPhase.AFTERNOON,
            GuideOverlayView.GuideType.THIRDS, 60,
            "움직임을 담으려면 빛이 충분한 오후가 좋아요"
        )
        // 음식점
        "39" -> SpotFacts(
            Facing.INDOOR, LightPhase.MIDDAY,
            GuideOverlayView.GuideType.CENTER, 60,
            "실내라 시간대 영향이 적어요",
            indoorOption = true
        )
        else -> SpotFacts(
            Facing.ANY, LightPhase.AFTERNOON,
            GuideOverlayView.GuideType.THIRDS, 50,
            "배경과 피사체의 조화를 살펴보세요"
        )
    }
}
