package com.youngs.picview.domain

import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.mission.Missions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 미션 판정 검증.
 *
 * 미션은 별도 저장 없이 방문 기록만으로 매번 계산합니다.
 * 그래서 "같은 곳을 반복 체크인하면 뚫리는지" 같은 규칙이 실제로 지켜지는지
 * 여기서 못 박아 둡니다.
 */
class MissionTest {

    private var seq = 0L

    private fun visit(
        title: String,
        phase: LightPhase = LightPhase.MORNING,
        score: Int = 70,
        contentId: String = title
    ) = VisitLogEntity(
        id = ++seq,
        contentId = contentId,
        title = title,
        visitedAt = 1_000_000L + seq * 1000L,
        score = score,
        imageUrl = "",
        phaseName = phase.name,
        installId = "test"
    )

    private fun progressOf(id: String, visits: List<VisitLogEntity>) =
        Missions.progress(visits).first { it.mission.id == id }

    @Test
    fun `골든아워 미션은 일출 일몰 방문만 센다`() {
        val visits = listOf(
            visit("A", LightPhase.SUNSET),
            visit("B", LightPhase.SUNRISE),
            visit("C", LightPhase.MIDDAY),   // 세지 않음
            visit("D", LightPhase.MORNING)   // 세지 않음
        )
        val p = progressOf("golden_hour", visits)
        assertEquals(2, p.current)
        assertFalse("아직 3곳이 안 됐으므로 미완료", p.isComplete)
    }

    @Test
    fun `같은 장소를 여러 번 체크인해도 한 번으로 센다`() {
        val visits = listOf(
            visit("내장산국립공원", LightPhase.SUNSET, contentId = "1"),
            visit("내장산국립공원", LightPhase.SUNSET, contentId = "1"),
            visit("내장산국립공원", LightPhase.SUNSET, contentId = "1")
        )
        val p = progressOf("golden_hour", visits)
        assertEquals("중복 체크인으로 미션이 뚫리면 안 된다", 1, p.current)
        assertFalse(p.isComplete)
    }

    @Test
    fun `조건을 채우면 완료로 표시되고 완료 시각이 남는다`() {
        val visits = listOf(
            visit("A", LightPhase.SUNSET, contentId = "1"),
            visit("B", LightPhase.SUNRISE, contentId = "2"),
            visit("C", LightPhase.SUNSET, contentId = "3")
        )
        val p = progressOf("golden_hour", visits)
        assertEquals(3, p.current)
        assertTrue(p.isComplete)
        assertTrue("완료 시각이 있어야 한다", (p.completedAt ?: 0L) > 0L)
    }

    @Test
    fun `장소 이름 기반 미션은 부분 일치로 판정한다`() {
        val visits = listOf(visit("내장산국립공원", contentId = "1"))
        assertTrue(progressOf("naejangsan", visits).isComplete)
    }

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
    fun `정읍사 문화공원은 유산으로 인정된다`() {
        val visits = listOf(visit("정읍사문화공원", contentId = "1"))
        assertEquals(1, progressOf("heritage", visits).current)
    }

    @Test
    fun `고득점 미션은 80점 미만을 제외한다`() {
        val visits = listOf(
            visit("A", score = 85, contentId = "1"),
            visit("B", score = 79, contentId = "2"),
            visit("C", score = 92, contentId = "3")
        )
        assertEquals(2, progressOf("high_score", visits).current)
    }

    @Test
    fun `진행도는 목표를 넘지 않는다`() {
        val visits = (1..20).map { visit("스팟$it", contentId = "$it") }
        val p = progressOf("explorer", visits)
        assertEquals("목표(10)를 초과 표시하면 안 된다", 10, p.current)
        assertEquals(1f, p.ratio, 0.001f)
    }

    @Test
    fun `방문 기록이 없으면 전부 미완료다`() {
        val all = Missions.progress(emptyList())
        assertEquals(Missions.ALL.size, all.size)
        assertTrue(all.none { it.isComplete })
        assertTrue(all.all { it.current == 0 })
    }

    @Test
    fun `완료한 미션이 목록 앞에 온다`() {
        val visits = listOf(visit("내장산국립공원", contentId = "1"))
        val all = Missions.progress(visits)
        assertTrue("완료 미션이 맨 앞", all.first().isComplete)
    }
}
