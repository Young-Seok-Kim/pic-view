package com.youngs.picview.domain

import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.mission.Missions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 미션 판정 검증.
 *
 * 미션은 별도 저장 없이 방문 기록만으로 매번 계산합니다. 개편 후에는 방문
 * 수가 아니라 **사진이 남은 장면**을 세므로, "체크인만 반복하면 뚫리는지",
 * "사진 없는 방문이 세이는지" 같은 규칙이 지켜지는지 여기서 못 박아 둡니다.
 */
class MissionTest {

    private var seq = 0L

    /** 시각을 지정해 기록을 만듭니다. 기본은 정오 — 아침/저녁 어느 쪽도 아닌 시각. */
    private fun visit(
        title: String,
        phase: LightPhase = LightPhase.MORNING,
        score: Int = 70,
        contentId: String = title,
        withPhoto: Boolean = true,
        at: LocalDateTime = LocalDateTime.of(2026, 8, 18, 12, 0)
    ) = VisitLogEntity(
        id = ++seq,
        contentId = contentId,
        title = title,
        visitedAt = at.plusSeconds(seq).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        score = score,
        imageUrl = "",
        phaseName = phase.name,
        installId = "test",
        photoUri = if (withPhoto) "content://media/$seq" else null
    )

    private fun progressOf(id: String, visits: List<VisitLogEntity>) =
        Missions.progress(visits).first { it.mission.id == id }

    // ───────────────────── 공통 규칙 ─────────────────────

    @Test
    fun `사진이 없는 방문은 어떤 미션에도 세이지 않는다`() {
        val visits = listOf(
            visit("A", LightPhase.SUNSET, score = 90, withPhoto = false),
            visit("무성서원", withPhoto = false),
            visit("내장산국립공원", withPhoto = false)
        )
        val all = Missions.progress(visits)
        assertTrue("체크인만으로 장면이 채워지면 안 된다", all.all { it.current == 0 })
    }

    @Test
    fun `방문 기록이 없으면 전부 미완료다`() {
        val all = Missions.progress(emptyList())
        assertEquals(Missions.ALL.size, all.size)
        assertTrue(all.none { it.isComplete })
        assertTrue(all.all { it.current == 0 })
    }

    @Test
    fun `진행도는 목표를 넘지 않는다`() {
        // 같은 날 서로 다른 다섯 곳 → 빛의 이동 경로(목표 3)가 3에서 멈춰야 합니다.
        val visits = (1..5).map { visit("스팟$it", contentId = "$it") }
        val p = progressOf("light_route", visits)
        assertEquals("목표(3)를 초과 표시하면 안 된다", 3, p.current)
        assertEquals(1f, p.ratio, 0.001f)
        assertTrue(p.isComplete)
    }

    @Test
    fun `완료한 미션은 목록 뒤로 진행 중인 미션은 앞으로 온다`() {
        // blue_hour(목표 1)는 완료, postcard 는 1/3 진행 중이 됩니다.
        val visits = listOf(visit("A", LightPhase.BLUE_DUSK))
        val all = Missions.progress(visits)
        assertTrue("이미 한 일보다 반쯤 채운 것이 위", all.first().current > 0 && !all.first().isComplete)
        assertTrue("완료 미션은 맨 뒤", all.last().isComplete)
    }

    // ───────────────────── 빛 미션 ─────────────────────

    @Test
    fun `빛 사냥꾼은 황금시간 고득점 사진만 센다`() {
        val visits = listOf(
            visit("A", LightPhase.SUNSET, score = 85),                 // 세임
            visit("B", LightPhase.SUNRISE, score = 70),                // 세임
            visit("C", LightPhase.MIDDAY, score = 95),                 // 황금시간 아님
            visit("D", LightPhase.SUNSET, score = 69),                 // 점수 미달
            visit("E", LightPhase.SUNSET, score = 90, withPhoto = false) // 사진 없음
        )
        val p = progressOf("light_hunter", visits)
        assertEquals(2, p.current)
        assertTrue(p.isComplete)
        assertTrue("완료 시각이 있어야 한다", (p.completedAt ?: 0L) > 0L)
    }

    @Test
    fun `블루아워 미션은 새벽과 저녁 블루아워만 센다`() {
        val visits = listOf(
            visit("A", LightPhase.BLUE_DAWN),
            visit("B", LightPhase.SUNRISE),   // 골든아워는 블루아워가 아님
            visit("C", LightPhase.NIGHT)
        )
        assertEquals(1, progressOf("blue_hour", visits).current)
        assertTrue(progressOf("blue_hour", visits).isComplete)
    }

    // ───────────────────── 비교 미션 ─────────────────────

    @Test
    fun `아침저녁 비교는 같은 장소에서 둘 다 찍어야 2가 된다`() {
        val morning = LocalDateTime.of(2026, 8, 18, 8, 0)
        val evening = LocalDateTime.of(2026, 8, 18, 18, 0)
        val visits = listOf(
            visit("내장산", contentId = "1", at = morning),
            visit("내장산", contentId = "1", at = evening)
        )
        val p = progressOf("morning_evening", visits)
        assertEquals(2, p.current)
        assertTrue(p.isComplete)
    }

    @Test
    fun `아침 사진만 쌓여서는 비교 미션이 늘지 않는다`() {
        val morning = LocalDateTime.of(2026, 8, 18, 8, 0)
        val visits = (1..5).map { visit("내장산", contentId = "1", at = morning.plusMinutes(it.toLong())) }
        assertEquals("한쪽만 열 장이어도 1", 1, progressOf("morning_evening", visits).current)
    }

    // ───────────────────── 장소 미션 ─────────────────────

    @Test
    fun `유산 미션은 세 곳 중 두 곳이면 완료된다`() {
        val visits = listOf(
            visit("무성서원", contentId = "1"),
            visit("피향정", contentId = "2")
        )
        assertTrue(progressOf("heritage", visits).isComplete)
    }

    @Test
    fun `유산 미션은 이름이 비슷한 다른 시설을 잡지 않는다`() {
        // 실제로 오판정됐던 사례: "정읍사" 부분 일치가 공연장까지 잡았다
        val visits = listOf(
            visit("정읍사예술회관", contentId = "1"),
            visit("정읍문화원", contentId = "2"),
            visit("정읍시립박물관", contentId = "3")
        )
        assertEquals(
            "정읍○○ 이름만으로 유산 미션이 채워지면 안 된다",
            0, progressOf("heritage", visits).current
        )
    }

    @Test
    fun `유산 미션은 같은 곳을 두 번 찍어도 한 곳으로 센다`() {
        val visits = listOf(
            visit("무성서원", contentId = "1"),
            visit("무성서원", contentId = "1")
        )
        val p = progressOf("heritage", visits)
        assertEquals("중복 촬영으로 미션이 뚫리면 안 된다", 1, p.current)
        assertFalse(p.isComplete)
    }

    @Test
    fun `정읍사 문화공원은 유산으로 인정된다`() {
        val visits = listOf(visit("정읍사문화공원", contentId = "1"))
        assertEquals(1, progressOf("heritage", visits).current)
    }

    @Test
    fun `빛의 이동 경로는 하루 안의 서로 다른 장소만 잇는다`() {
        val day1 = LocalDateTime.of(2026, 8, 18, 9, 0)
        val day2 = LocalDateTime.of(2026, 8, 19, 9, 0)
        val visits = listOf(
            visit("A", contentId = "1", at = day1),
            visit("B", contentId = "2", at = day1.plusHours(3)),
            visit("C", contentId = "3", at = day2)   // 다른 날 — 이어지지 않음
        )
        assertEquals("가장 많이 이은 날 기준", 2, progressOf("light_route", visits).current)
    }

    // ───────────────────── 기록 미션 ─────────────────────

    @Test
    fun `엽서 미션은 사진이 남은 기록 수를 센다`() {
        val visits = listOf(
            visit("A", contentId = "1"),
            visit("B", contentId = "2"),
            visit("C", contentId = "3", withPhoto = false)
        )
        assertEquals(2, progressOf("postcard", visits).current)
    }

    // ───────────────────── 장소 연결 ─────────────────────

    @Test
    fun `장소가 지정된 미션은 그 장소에서만 보인다`() {
        val heritage = Missions.ALL.first { it.id == "heritage" }
        assertTrue(heritage.availableAt("무성서원"))
        assertFalse(heritage.availableAt("옥정호"))

        val anywhere = Missions.ALL.first { it.id == "postcard" }
        assertTrue("장소 제한이 없는 미션은 어디서나", anywhere.availableAt("아무 곳"))
    }

    @Test
    fun `전체 장면 수는 미션 목표의 합이다`() {
        assertEquals(Missions.ALL.sumOf { it.target }, Missions.totalScenes)
    }
}
