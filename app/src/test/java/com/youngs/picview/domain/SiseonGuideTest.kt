package com.youngs.picview.domain

import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.SpotFacts
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.ui.guide.GuideOverlayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 시선 가이드 데이터와 홈 → 가이드 연결 규칙 검증.
 *
 * 홈의 추천 장소가 가이드의 첫 구도·빛 상황을 정하므로, 매핑이
 * 어긋나면 "물가 장소를 눌렀는데 탑뷰로 시작"하는 식의 어색한
 * 첫 화면이 나갑니다.
 */
class SiseonGuideTest {

    private fun facts(
        guide: GuideOverlayView.GuideType,
        phase: LightPhase = LightPhase.MORNING,
        note: String = "촬영 안내"
    ) = SpotFacts(Facing.WEST, phase, guide, 60, note)

    @Test
    fun `구도는 10종이고 문구가 비지 않는다`() {
        // 관광공사 실사진이 있는 구도만 남겼습니다(README '시선 가이드' 참고).
        assertEquals(10, SiseonGuide.items.size)
        SiseonGuide.items.forEach { item ->
            assertTrue("${item.id} title", item.title.isNotBlank())
            assertTrue("${item.id} english", item.english.isNotBlank())
            assertTrue("${item.id} description", item.description.isNotBlank())
            assertTrue("${item.id} tips", item.tips.size == 3)
        }
    }

    @Test
    fun `모르는 id 는 기본 구도(반사)로 물러난다`() {
        assertEquals("reflection", SiseonGuide.byId(null).id)
        assertEquals("reflection", SiseonGuide.byId("없는것").id)
        assertEquals("symmetry", SiseonGuide.byId("symmetry").id)
    }

    @Test
    fun `대칭 장소는 빛과 상관없이 대칭으로 간다`() {
        assertEquals("symmetry", SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.SYMMETRY)))
        assertEquals(
            "symmetry",
            SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.SYMMETRY, LightPhase.AFTERNOON))
        )
        // 중앙 구도 장소는 조명이 켜지는 블루아워에 프레임 인 프레임으로.
        assertEquals(
            "frame",
            SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.CENTER, LightPhase.BLUE_DUSK))
        )
    }

    @Test
    fun `삼분할은 장소의 특성과 빛이 구도를 정한다`() {
        // 물가면 반사 — 촬영 특성 한 줄이 시각보다 먼저입니다.
        assertEquals(
            "reflection",
            SiseonGuide.guideIdFor(
                facts(GuideOverlayView.GuideType.THIRDS, note = "연못 수면 반영이 좋아요")
            )
        )
        // 해질 무렵 서향이면 실루엣
        assertEquals(
            "silhouette",
            SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.THIRDS, LightPhase.SUNSET))
        )
        // 측광이 도는 오전은 겹침(레이어드), 오후는 리딩라인
        assertEquals(
            "layer",
            SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.THIRDS, LightPhase.MORNING))
        )
        assertEquals(
            "leading",
            SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.THIRDS, LightPhase.AFTERNOON))
        )
        // 빛이 강한 한낮은 반복 패턴
        assertEquals(
            "pattern",
            SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.THIRDS, LightPhase.MIDDAY))
        )
    }

    @Test
    fun `빛 구간이 상황 칩으로 이어진다`() {
        assertEquals("sunrise", SiseonGuide.contextIdFor(LightPhase.SUNRISE))
        assertEquals("sunrise", SiseonGuide.contextIdFor(LightPhase.BLUE_DAWN))
        assertEquals("sunset", SiseonGuide.contextIdFor(LightPhase.SUNSET))
        assertEquals("night", SiseonGuide.contextIdFor(LightPhase.NIGHT))
        assertEquals("night", SiseonGuide.contextIdFor(LightPhase.BLUE_DUSK))
        assertEquals("clear", SiseonGuide.contextIdFor(LightPhase.MIDDAY))
    }

    @Test
    fun `모든 구도에 시그널 카드가 있다`() {
        SiseonGuide.items.forEach { item ->
            val signal = SiseonGuide.signalOf(item.id)
            assertTrue("${item.id} signal title", signal.title.isNotBlank())
            assertTrue("${item.id} signal detail", signal.detail.isNotBlank())
        }
    }
}
