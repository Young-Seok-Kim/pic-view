package com.youngs.picview.domain.spot

import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.ui.guide.GuideOverlayView

/** 촬영 시 피사체를 바라보는 방향. 골든아워 배치에 씁니다. */
enum class Facing(val label: String, val bearing: Int? = null) {
    EAST("동향", 90),
    WEST("서향", 270),
    SOUTH("남향", 180),
    NORTH("북향", 0),
    INDOOR("실내"),
    ANY("무관");

    /**
     * "서향 · 270°" 처럼 각도를 함께 씁니다.
     *
     * 각도는 추정치가 아니라 방위 이름 자체의 정의입니다(서 = 270°).
     * 지형을 실측해 얻은 값이 아니므로 소수점까지 적지 않습니다.
     * 실내·무관은 방향이 없으므로 이름만 씁니다.
     */
    val labelWithBearing: String
        get() = bearing?.let { "$label · $it°" } ?: label

    /**
     * 방위가 문장에 들어갈 때 쓰는 이름.
     *
     * "무관"은 분류표의 말이지 현장의 말이 아닙니다. 그대로 두면
     * "오전 · 무관에 담기 좋아요" 같은 줄이 나옵니다. 방향이 없는
     * 곳은 방향 대신 그 곳의 성격을 말하게 합니다.
     */
    val phraseLabel: String
        get() = when (this) {
            ANY -> "어느 방향에서든"
            INDOOR -> "실내"
            else -> label
        }
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

    /**
     * 이름 끝머리로 읽는 장소의 성격.
     *
     * [curated] 는 정읍의 대표 촬영지 여덟 곳만 다룹니다. 나머지는 곧장
     * [defaultFor] 로 떨어졌는데, 그 값은 **contentTypeId 하나만** 봤습니다.
     * 그래서 정읍사예술회관·정읍시립미술관·정읍문화원이 전부 문화시설(14)
     * 이라는 이유로 방위·시간·구도가 똑같아졌고, 코스 세 칸에 "추천 구도 ·
     * 좌우 대칭축"이 세 번 나왔습니다.
     *
     * 한국의 장소 이름은 끝머리가 그 장소의 생김새를 꽤 정확히 말합니다 —
     * 미술관은 실내 전시, 회관은 정면 계단, 저수지는 물, 시장은 사람.
     * 그래서 접미사를 2차 판별로 둡니다. 좌표·이름·사진은 여전히 전부
     * TourAPI 실값이고, 여기 있는 것은 촬영 지식뿐입니다.
     */
    private val byName: List<Pair<List<String>, SpotFacts>> = listOf(
        listOf("저수지", "호수", "연못", "습지") to SpotFacts(
            Facing.EAST, LightPhase.SUNRISE,
            GuideOverlayView.GuideType.THIRDS, 60,
            "바람이 자는 이른 아침에 수면이 거울이 됩니다"
        ),
        listOf("폭포", "계곡", "천") to SpotFacts(
            Facing.NORTH, LightPhase.MORNING,
            GuideOverlayView.GuideType.THIRDS, 50,
            "그늘이 진 계곡은 대비가 낮은 오전이 편합니다"
        ),
        listOf("미술관", "박물관", "전시관", "기념관") to SpotFacts(
            Facing.INDOOR, LightPhase.MIDDAY,
            GuideOverlayView.GuideType.CENTER, 70,
            "실내 전시는 창가 자연광 한 자리를 먼저 찾아보세요",
            indoorOption = true
        ),
        listOf("회관", "문화원", "센터", "청사") to SpotFacts(
            Facing.SOUTH, LightPhase.AFTERNOON,
            GuideOverlayView.GuideType.SYMMETRY, 40,
            "정면 계단과 기둥은 그림자가 길어지는 오후가 입체적이에요"
        ),
        listOf("공원", "광장", "생태") to SpotFacts(
            Facing.WEST, LightPhase.SUNSET,
            GuideOverlayView.GuideType.THIRDS, 70,
            "탁 트인 곳은 해가 낮아질수록 색이 깊어집니다"
        ),
        listOf("사", "암", "향교", "서원", "고택", "종택") to SpotFacts(
            Facing.SOUTH, LightPhase.MORNING,
            GuideOverlayView.GuideType.SYMMETRY, 55,
            "한옥 대칭은 그림자가 짧은 오전이 가장 깔끔해요"
        ),
        listOf("시장", "거리", "마을") to SpotFacts(
            Facing.ANY, LightPhase.AFTERNOON,
            GuideOverlayView.GuideType.CENTER, 60,
            "사람이 지나는 시간대에 가야 장면이 살아납니다"
        ),
        listOf("산", "봉", "재", "능선", "전망대") to SpotFacts(
            Facing.WEST, LightPhase.SUNSET,
            GuideOverlayView.GuideType.THIRDS, 90,
            "능선 위로 하늘을 크게 비우면 규모가 읽힙니다"
        )
    )

    /**
     * 제목으로 촬영 특성을 찾습니다.
     *
     * 순서가 판정입니다 — 손으로 정리한 표 → 이름 끝머리 → 카테고리 기본값.
     * 뒤로 갈수록 아는 것이 적어집니다.
     */
    fun of(title: String, contentTypeId: String?): SpotFacts {
        curated.firstOrNull { (keyword, _) -> title.contains(keyword) }
            ?.let { return it.second }

        // 끝머리는 이름의 마지막 글자부터 봅니다. "정읍사예술회관"은
        // '회관'으로 읽혀야지 '사'(사찰)로 읽히면 안 됩니다.
        byName.firstOrNull { (suffixes, _) ->
            suffixes.any { title.endsWith(it) }
        }?.let { return it.second }

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
