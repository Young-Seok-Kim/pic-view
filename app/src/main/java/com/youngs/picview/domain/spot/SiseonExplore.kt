package com.youngs.picview.domain.spot

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.youngs.picview.R
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.ui.guide.GuideOverlayView

/**
 * 촬영 목적 — 탐색 지도의 마커·범례·필터가 공유하는 축.
 *
 * 관광공사 분류(자연/문화…)는 "무엇이 있는 곳인가"를 말하지만,
 * 사진가가 지도에서 고르는 기준은 "무엇을 찍으러 가는가"입니다.
 * 그래서 지도는 장소의 촬영 특성([SpotFacts])에서 목적을 유도해 색을 나눕니다.
 */
enum class ShotPurpose(
    val label: String,
    @ColorRes val colorRes: Int,
    @DrawableRes val markerRes: Int
) {
    REFLECT("반사", R.color.purpose_reflect, R.drawable.marker_purpose_reflect),
    SILHOUETTE("실루엣", R.color.purpose_silhouette, R.drawable.marker_purpose_silhouette),
    PERSON("인물", R.color.purpose_person, R.drawable.marker_purpose_person),
    ARCH_LINE("건축선", R.color.purpose_arch, R.drawable.marker_purpose_arch),
    SPACE("여백", R.color.purpose_space, R.drawable.marker_purpose_space);

    companion object {
        /** 물가·반영을 말하는 낱말. note 나 이름에 있으면 반사가 최우선입니다. */
        private val REFLECT_WORDS = listOf("반영", "수면", "물안개", "연못", "호수", "저수지")
        private val REFLECT_TITLES = listOf("피향정", "옥정호", "정읍사")

        /**
         * 촬영 특성 → 목적.
         *
         * 순서가 판정입니다. 반사(물가)가 가장 희소한 조건이라 먼저 잡고,
         * 대칭 구도·문화시설은 건축선으로, 서향 일몰 스팟은 역광 실루엣으로,
         * 가까이 찍는 곳(맛집·체험)은 인물로 몹니다. 남는 자연은 여백입니다.
         */
        fun of(title: String, facts: SpotFacts, theme: SpotTheme): ShotPurpose = when {
            REFLECT_TITLES.any { title.contains(it) } ||
                REFLECT_WORDS.any { facts.note.contains(it) } -> REFLECT

            facts.guide == GuideOverlayView.GuideType.SYMMETRY ||
                theme == SpotTheme.CULTURE -> ARCH_LINE

            facts.bestPhase == LightPhase.SUNSET && facts.facing == Facing.WEST -> SILHOUETTE

            facts.guide == GuideOverlayView.GuideType.CENTER ||
                theme == SpotTheme.FOOD || theme == SpotTheme.LEPORTS -> PERSON

            else -> SPACE
        }

        /** 이 빛 구간에 가장 어울리는 목적. 상태줄의 "○○ 추천"에 씁니다. */
        fun recommendedFor(phase: LightPhase): ShotPurpose = when (phase) {
            LightPhase.SUNRISE -> REFLECT       // 바람 없는 아침 수면
            LightPhase.SUNSET -> REFLECT        // 수면에 번지는 노을
            LightPhase.BLUE_DAWN, LightPhase.BLUE_DUSK -> SILHOUETTE
            LightPhase.MORNING -> ARCH_LINE     // 결이 사는 측광
            LightPhase.AFTERNOON -> PERSON      // 부드러운 역사광
            LightPhase.MIDDAY -> SPACE          // 강한 빛은 여백으로 피함
            LightPhase.NIGHT -> SILHOUETTE      // 점광원 앞 검은 형태
        }
    }
}

/**
 * 정읍의 스토리 축 — 달·사랑·혁명·느림·계절.
 *
 * 정읍사(달·사랑), 동학농민혁명(혁명), 고택·서원·쌍화차(느림),
 * 내장산·구절초(계절)처럼 정읍이 실제로 가진 이야기 다섯 갈래입니다.
 * 한 장소가 여러 이야기에 걸칠 수 있어 집합으로 돌려줍니다.
 */
enum class SpotStory(
    val label: String,
    val emoji: String,
    /** 스토리를 골랐을 때 지도 마커·배지가 함께 입는 색. */
    @ColorRes val colorRes: Int,
    /**
     * 색 + 아이콘 이중 코딩 마커.
     * 색 하나로만 나누면 밝은 야외 화면이나 색각 이상에서 구분이 사라집니다.
     */
    @DrawableRes val markerRes: Int
) {
    MOON("달", "🌙", R.color.story_moon, R.drawable.marker_story_moon),
    LOVE("사랑", "🤍", R.color.story_love, R.drawable.marker_story_love),
    REVOLUTION("혁명", "🚩", R.color.story_revolution, R.drawable.marker_story_revolution),
    SLOW("느림", "🐌", R.color.story_slow, R.drawable.marker_story_slow),
    SEASON("계절", "🍃", R.color.story_season, R.drawable.marker_story_season);

    companion object {
        private val KEYWORDS: List<Pair<SpotStory, List<String>>> = listOf(
            MOON to listOf("정읍사", "달빛"),
            LOVE to listOf("정읍사", "망부"),
            REVOLUTION to listOf("동학", "황토현", "전봉준", "만석보", "혁명"),
            SLOW to listOf("고택", "서원", "향교", "쌍화", "사찰", "내장사"),
            SEASON to listOf("내장산", "구절초", "벚꽃", "단풍", "옥정호")
        )

        fun of(title: String, theme: SpotTheme): Set<SpotStory> {
            val matched = KEYWORDS
                .filter { (_, words) -> words.any { title.contains(it) } }
                .map { it.first }
                .toSet()
            if (matched.isNotEmpty()) return matched

            // 이름에 단서가 없으면 분류로 어림잡습니다.
            // 자연은 계절의 이야기, 나머지는 느리게 걷는 이야기입니다.
            return when (theme) {
                SpotTheme.NATURE -> setOf(SEASON)
                else -> setOf(SLOW)
            }
        }

        /** 지금 빛에 어울리는 이야기. 지도 하단 추천 알약에 씁니다. */
        fun recommendedFor(phase: LightPhase): SpotStory = when (phase) {
            LightPhase.NIGHT, LightPhase.BLUE_DUSK, LightPhase.BLUE_DAWN -> MOON
            LightPhase.SUNRISE, LightPhase.MORNING -> SLOW
            else -> SEASON
        }
    }
}
