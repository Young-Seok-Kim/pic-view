package com.youngs.picview.domain

import com.youngs.picview.domain.season.Season
import com.youngs.picview.domain.season.SeasonHighlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 촬영 적기 판정 검증.
 *
 * 겨울(12월 → 2월)처럼 해를 넘기는 구간이 있어서 날짜 계산이 틀리기 쉽습니다.
 * D-day 가 음수로 나오거나 "지금이 절정"인데 D-300 이 뜨는 식의 버그를 막습니다.
 */
class SeasonTest {

    private fun highlight(id: String) = SeasonHighlights.ALL.first { it.id == id }

    @Test
    fun `계절은 월로 구분된다`() {
        assertEquals(Season.SPRING, Season.of(LocalDate.of(2026, 4, 1)))
        assertEquals(Season.SUMMER, Season.of(LocalDate.of(2026, 7, 15)))
        assertEquals(Season.AUTUMN, Season.of(LocalDate.of(2026, 10, 30)))
        assertEquals(Season.WINTER, Season.of(LocalDate.of(2026, 1, 5)))
        assertEquals(Season.WINTER, Season.of(LocalDate.of(2026, 12, 25)))
    }

    @Test
    fun `절정 기간 안이면 지금이 절정으로 판정된다`() {
        val maple = highlight("maple")   // 10월 28일 ~ 11월 15일
        assertTrue(maple.isPeakNow(LocalDate.of(2026, 11, 1)))
        assertFalse(maple.isPeakNow(LocalDate.of(2026, 10, 1)))
        assertFalse(maple.isPeakNow(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `절정이면 D-day 는 0이다`() {
        val maple = highlight("maple")
        assertEquals(0L, maple.daysUntilPeak(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `절정 전이면 남은 일수가 양수다`() {
        val maple = highlight("maple")
        val days = maple.daysUntilPeak(LocalDate.of(2026, 10, 1))
        assertEquals(27L, days)
    }

    @Test
    fun `절정이 지났으면 내년 날짜로 넘어간다`() {
        val cherry = highlight("cherry")   // 4월 1일 ~ 4월 12일
        val days = cherry.daysUntilPeak(LocalDate.of(2026, 8, 4))
        assertTrue("이미 지난 봄이면 내년까지 세야 한다", days > 200)
        assertEquals(LocalDate.of(2027, 4, 1), cherry.nextPeakStart(LocalDate.of(2026, 8, 4)))
    }

    @Test
    fun `해를 넘기는 겨울 구간도 절정으로 잡힌다`() {
        val snow = highlight("snow")   // 12월 20일 ~ 2월 10일
        assertTrue("12월 말", snow.isPeakNow(LocalDate.of(2026, 12, 25)))
        assertTrue("해 넘긴 1월", snow.isPeakNow(LocalDate.of(2027, 1, 15)))
        assertTrue("2월 초", snow.isPeakNow(LocalDate.of(2027, 2, 5)))
        assertFalse("2월 말은 아님", snow.isPeakNow(LocalDate.of(2027, 2, 20)))
    }

    @Test
    fun `D-day 는 절대 음수가 되지 않는다`() {
        // 1년 전체를 훑어 어느 날에도 음수가 안 나오는지 확인
        var date = LocalDate.of(2026, 1, 1)
        while (date < LocalDate.of(2027, 1, 1)) {
            SeasonHighlights.ALL.forEach { h ->
                val d = h.daysUntilPeak(date)
                assertTrue("${h.id} @ $date 에서 음수(D$d)", d >= 0)
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `모든 계절에 최소 하나의 촬영 적기가 있다`() {
        Season.entries.forEach { season ->
            assertTrue(
                "${season.label} 에 콘텐츠가 없으면 탭이 비어 보인다",
                SeasonHighlights.of(season).isNotEmpty()
            )
        }
    }

    @Test
    fun `임박한 순으로 정렬된다`() {
        val list = SeasonHighlights.upcoming(LocalDate.of(2026, 8, 4), limit = 3)
        assertEquals(3, list.size)
        // 절정 중인 것이 먼저
        assertTrue(list.first().isPeakNow(LocalDate.of(2026, 8, 4)))
    }
}
