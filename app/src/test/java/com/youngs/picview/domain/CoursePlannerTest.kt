package com.youngs.picview.domain

import com.youngs.picview.domain.course.CoursePlanner
import com.youngs.picview.domain.course.CourseRequest
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 출사 코스 생성 검증.
 *
 * 코스 배치는 화면만 봐서는 맞는지 알기 어렵습니다.
 * "일몰엔 서향, 한낮엔 실내" 같은 규칙이 실제로 지켜지는지 여기서 못 박아 둡니다.
 */
class CoursePlannerTest {

    // 정읍 실제 좌표 (mapx=경도, mapy=위도)
    private fun spot(
        id: String,
        title: String,
        typeId: String,
        lat: Double,
        lng: Double,
        score: Int
    ) = SpotItem(
        contentId = id,
        contentTypeId = typeId,
        title = title,
        addr1 = "전북특별자치도 정읍시",
        tip = "",
        imageUrl = "",
        mapx = lng.toString(),
        mapy = lat.toString()
    ).apply { this.score = score }

    private val naejangsan = spot("1", "내장산국립공원", "12", 35.4897, 126.8897, 95)
    private val gujeolcho = spot("2", "구절초지방정원", "12", 35.6089, 126.9553, 90)
    private val museong = spot("3", "무성서원", "14", 35.6288, 126.8203, 88)
    private val ssanghwa = spot("4", "쌍화차거리", "39", 35.5697, 126.8560, 80)
    private val pihyang = spot("5", "피향정", "14", 35.6425, 126.8508, 78)

    private val allSpots = listOf(naejangsan, gujeolcho, museong, ssanghwa, pihyang)

    /** 2026년 8월 4일 정읍 기준 대략치 */
    private val sun = SunTimes(
        sunrise = LocalTime.of(5, 47),
        sunset = LocalTime.of(19, 39),
        meridian = LocalTime.of(12, 43)
    )

    @Test
    fun `일몰 골든아워에는 서향 스팟이 배치된다`() {
        val request = CourseRequest(
            startTime = LocalTime.of(15, 0),
            endTime = LocalTime.of(21, 0),
            travelMode = TravelMode.CAR,
            maxStops = 4
        )

        val course = CoursePlanner.plan(allSpots, sun, request)

        val sunsetStop = course.stops.firstOrNull { it.phase == LightPhase.SUNSET }
        assertNotNull("일몰 구간에 스팟이 하나는 배치돼야 한다", sunsetStop)

        // 내장산·구절초가 서향으로 정의돼 있으므로 둘 중 하나여야 한다
        assertTrue(
            "일몰 앵커는 서향 스팟이어야 한다 (실제: ${sunsetStop!!.spot.title})",
            sunsetStop.facts.facing == com.youngs.picview.domain.spot.Facing.WEST
        )
        assertTrue("일몰 스팟은 강조 표시돼야 한다", sunsetStop.isHighlight)
    }

    @Test
    fun `한낮 구간에는 실내 스팟이 우선된다`() {
        val request = CourseRequest(
            startTime = LocalTime.of(11, 30),
            endTime = LocalTime.of(15, 0),
            travelMode = TravelMode.CAR,
            maxStops = 3
        )

        val course = CoursePlanner.plan(allSpots, sun, request)
        val middayStops = course.stops.filter { it.phase == LightPhase.MIDDAY }

        assertTrue("한낮 구간에 스팟이 있어야 한다", middayStops.isNotEmpty())
        assertEquals(
            "한낮 첫 스팟은 실내(쌍화차거리)여야 한다",
            "쌍화차거리",
            middayStops.first().spot.title
        )
    }

    @Test
    fun `정거장은 시간 순서대로 정렬되고 겹치지 않는다`() {
        val request = CourseRequest(
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(21, 0),
            travelMode = TravelMode.CAR,
            maxStops = 5
        )

        val course = CoursePlanner.plan(allSpots, sun, request)
        assertTrue("코스가 비면 안 된다", course.stops.isNotEmpty())

        course.stops.zipWithNext { a, b ->
            assertTrue(
                "도착 시각이 역순이면 안 된다: ${a.spot.title}(${a.arriveAt}) → ${b.spot.title}(${b.arriveAt})",
                a.arriveAt <= b.arriveAt
            )
        }
    }

    @Test
    fun `같은 스팟이 두 번 들어가지 않는다`() {
        val request = CourseRequest(
            startTime = LocalTime.of(5, 0),
            endTime = LocalTime.of(21, 0),
            travelMode = TravelMode.CAR,
            maxStops = 5
        )

        val course = CoursePlanner.plan(allSpots, sun, request)
        val ids = course.stops.map { it.spot.contentId }
        assertEquals("중복 배치가 없어야 한다", ids.size, ids.distinct().size)
    }

    @Test
    fun `좌표가 없는 스팟은 코스에서 제외된다`() {
        val broken = SpotItem(
            contentId = "99", contentTypeId = "12", title = "좌표없음",
            addr1 = "", tip = "", imageUrl = "", mapx = "", mapy = ""
        ).apply { score = 99 }

        val request = CourseRequest(
            startTime = LocalTime.of(15, 0),
            endTime = LocalTime.of(21, 0),
            travelMode = TravelMode.CAR
        )

        val course = CoursePlanner.plan(listOf(broken) + allSpots, sun, request)
        assertTrue(
            "좌표 없는 스팟이 들어가면 안 된다",
            course.stops.none { it.spot.contentId == "99" }
        )
    }

    @Test
    fun `일출 일몰 정보가 없어도 코스는 나온다`() {
        val request = CourseRequest(
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(17, 0),
            travelMode = TravelMode.CAR
        )

        val course = CoursePlanner.plan(allSpots, SunTimes.EMPTY, request)
        assertTrue("천문 API 가 실패해도 코스는 나와야 한다", course.stops.isNotEmpty())
    }

    @Test
    fun `표시 거리와 소요 시간이 같은 기준으로 계산된다`() {
        val request = CourseRequest(
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(21, 0),
            travelMode = TravelMode.CAR,
            maxStops = 5
        )
        val course = CoursePlanner.plan(allSpots, sun, request)

        course.stops.filter { it.travelMinutes > 0 }.forEach { stop ->
            // travelKm 은 도로 환산 거리여야 합니다.
            // 직선거리를 그대로 보여 주면 "16.7km 인데 35분" 처럼
            // 두 숫자의 기준이 어긋나 보입니다.
            val impliedKm = stop.travelKm
            val minutesFromKm = (impliedKm / TravelMode.CAR.averageKmh * 60.0) +
                    TravelMode.CAR.fixedOverheadMinutes
            assertTrue(
                "${stop.spot.title}: ${stop.travelKm}km 인데 ${stop.travelMinutes}분 " +
                        "(거리로 계산하면 ${minutesFromKm.toInt()}분)",
                kotlin.math.abs(minutesFromKm - stop.travelMinutes) <= 1.5
            )
        }
    }

    @Test
    fun `총 거리는 각 구간 거리의 합이다`() {
        val request = CourseRequest(
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(21, 0),
            travelMode = TravelMode.CAR,
            maxStops = 5
        )
        val course = CoursePlanner.plan(allSpots, sun, request)
        assertEquals(
            course.stops.sumOf { it.travelKm }, course.totalDistanceKm, 0.001
        )
    }

    @Test
    fun `요약 문구는 LLM 없이도 만들어진다`() {
        val request = CourseRequest(
            startTime = LocalTime.of(15, 0),
            endTime = LocalTime.of(21, 0),
            travelMode = TravelMode.CAR
        )

        val summary = CoursePlanner.plan(allSpots, sun, request).fallbackSummary()
        assertTrue("요약이 비면 안 된다", summary.isNotBlank())
        assertTrue("스팟 수가 들어가야 한다", summary.contains("곳"))
    }
}
