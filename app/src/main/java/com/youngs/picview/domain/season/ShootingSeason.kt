package com.youngs.picview.domain.season

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/** 사계절 구분. */
enum class Season(val label: String, val emoji: String, val months: Set<Int>) {
    SPRING("봄", "🌸", setOf(3, 4, 5)),
    SUMMER("여름", "🌿", setOf(6, 7, 8)),
    AUTUMN("가을", "🍁", setOf(9, 10, 11)),
    WINTER("겨울", "❄️", setOf(12, 1, 2));

    companion object {
        fun of(date: LocalDate): Season =
            entries.first { date.monthValue in it.months }

        fun now(): Season = of(LocalDate.now())
    }
}

/**
 * 정읍의 촬영 적기.
 *
 * 원래 이 화면은 관광공사 행사정보(searchFestival2)로 만들 계획이었습니다.
 * 그런데 실제로 조회해 보니 **정읍의 2026년 등록 축제가 0건**이고
 * (전북 전체도 0건), 2025년에도 정읍사문화제 하나뿐이었습니다.
 * 축제 목록으로는 화면이 비어 버립니다.
 *
 * 그래서 축제 대신 **피사체의 절정 시기**를 기준으로 바꿨습니다.
 * 출사에서는 "축제가 언제인가"보다 "단풍이 언제 절정인가"가 훨씬 중요하고,
 * 이쪽이 이 앱의 정체성에도 맞습니다. (스펙 §13 아이디어 A)
 *
 * 절정 시기는 해마다 기후로 며칠씩 밀리므로 범위로 잡고,
 * 화면에서는 D-day 와 함께 "평년 기준"임을 밝힙니다.
 */
data class SeasonHighlight(
    val id: String,
    /** 관광공사 목록에서 이 스팟을 찾을 때 쓰는 이름 조각. */
    val spotKeyword: String,
    val title: String,
    val subject: String,
    /** 절정 시작·종료 (연도 무관) */
    val peakStart: MonthDay,
    val peakEnd: MonthDay,
    val emoji: String,
    /** 그 시기에 어떻게 찍는지. */
    val tip: String
) {
    val season: Season get() = Season.entries.first { peakStart.monthValue in it.months }

    /** 올해(또는 내년) 기준 절정 시작일. 이미 지났으면 내년 날짜를 돌려줍니다. */
    fun nextPeakStart(today: LocalDate = LocalDate.now()): LocalDate {
        val thisYear = peakStart.atYear(today.year)
        val end = peakEndDate(today.year)
        return if (end >= today) thisYear else peakStart.atYear(today.year + 1)
    }

    private fun peakEndDate(year: Int): LocalDate {
        val start = peakStart.atYear(year)
        val end = peakEnd.atYear(year)
        // 12월 → 2월처럼 해를 넘기는 구간 처리
        return if (end < start) peakEnd.atYear(year + 1) else end
    }

    /** 지금 절정 기간 안인지. */
    fun isPeakNow(today: LocalDate = LocalDate.now()): Boolean {
        val year = today.year
        val start = peakStart.atYear(year)
        val end = peakEndDate(year)
        if (today in start..end) return true
        // 해를 넘긴 구간(겨울)은 작년 시작분도 확인
        val prevStart = peakStart.atYear(year - 1)
        val prevEnd = peakEndDate(year - 1)
        return today in prevStart..prevEnd
    }

    /** 절정까지 남은 일수. 이미 절정이면 0. */
    fun daysUntilPeak(today: LocalDate = LocalDate.now()): Long =
        if (isPeakNow(today)) 0
        else ChronoUnit.DAYS.between(today, nextPeakStart(today))
}

object SeasonHighlights {

    /**
     * 정읍 대표 피사체의 절정 시기.
     * 스펙 §13 이 꼽은 전국구 콘텐츠 5개를 축으로 구성했습니다.
     */
    val ALL: List<SeasonHighlight> = listOf(
        SeasonHighlight(
            id = "cherry",
            spotKeyword = "정읍천",
            title = "정읍천 벚꽃",
            subject = "벚꽃길",
            peakStart = MonthDay.of(4, 1),
            peakEnd = MonthDay.of(4, 12),
            emoji = "🌸",
            tip = "만개 후 사흘이 지나면 꽃비가 내립니다. 흐린 날이 꽃색이 더 곱게 나와요."
        ),
        SeasonHighlight(
            id = "spring_green",
            spotKeyword = "내장산",
            title = "내장산 신록",
            subject = "연둣빛 계곡",
            peakStart = MonthDay.of(4, 25),
            peakEnd = MonthDay.of(5, 20),
            emoji = "🌱",
            tip = "비 온 다음 날 아침이 가장 선명합니다. 계곡물과 함께 담아보세요."
        ),
        SeasonHighlight(
            id = "lotus",
            spotKeyword = "피향정",
            title = "피향정 연꽃",
            subject = "연못과 정자",
            peakStart = MonthDay.of(7, 10),
            peakEnd = MonthDay.of(8, 10),
            emoji = "🪷",
            tip = "연꽃은 아침에 피고 한낮에 오므립니다. 해뜨고 두 시간 안에 가세요."
        ),
        SeasonHighlight(
            id = "valley",
            spotKeyword = "내장산",
            title = "내장산 여름 계곡",
            subject = "숲과 물",
            peakStart = MonthDay.of(7, 15),
            peakEnd = MonthDay.of(8, 25),
            emoji = "💧",
            tip = "숲속은 빛이 부족합니다. 흐린 날 장노출로 물을 부드럽게 담아보세요."
        ),
        SeasonHighlight(
            id = "gujeolcho",
            spotKeyword = "구절초",
            title = "구절초 지방정원 개화",
            subject = "구절초 군락",
            peakStart = MonthDay.of(9, 20),
            peakEnd = MonthDay.of(10, 10),
            emoji = "🌼",
            tip = "역광으로 담으면 흰 꽃잎이 투명하게 빛납니다. 일몰 한 시간 전이 최적."
        ),
        SeasonHighlight(
            id = "maple",
            spotKeyword = "내장산",
            title = "내장산 단풍",
            subject = "단풍터널",
            peakStart = MonthDay.of(10, 28),
            peakEnd = MonthDay.of(11, 15),
            emoji = "🍁",
            tip = "대한민국 단풍 1번지. 단풍터널은 역광이 살아나는 늦은 오후가 절정입니다."
        ),
        SeasonHighlight(
            id = "snow",
            spotKeyword = "내장산",
            title = "내장산 설경",
            subject = "눈 덮인 능선",
            peakStart = MonthDay.of(12, 20),
            peakEnd = MonthDay.of(2, 10),
            emoji = "❄️",
            tip = "눈 온 다음 날 아침에만 볼 수 있습니다. 노출을 +1 정도 올려야 눈이 하얗게 나와요."
        ),
        SeasonHighlight(
            id = "ssanghwa",
            spotKeyword = "쌍화차",
            title = "쌍화차 거리",
            subject = "겨울 골목",
            peakStart = MonthDay.of(12, 1),
            peakEnd = MonthDay.of(2, 28),
            emoji = "☕",
            tip = "김이 오르는 찻잔은 창가 역광에서 가장 잘 보입니다."
        )
    )

    fun of(season: Season): List<SeasonHighlight> =
        ALL.filter { it.season == season }.sortedBy { it.peakStart }

    /** 지금 절정이거나 가장 가까운 것. 홈·캘린더 헤드라인에 씁니다. */
    fun upcoming(today: LocalDate = LocalDate.now(), limit: Int = 3): List<SeasonHighlight> =
        ALL.sortedWith(
            compareByDescending<SeasonHighlight> { it.isPeakNow(today) }
                .thenBy { it.daysUntilPeak(today) }
        ).take(limit)
}
