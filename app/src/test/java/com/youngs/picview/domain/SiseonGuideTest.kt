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
    fun `구도는 14종이고 문구가 비지 않는다`() {
        assertEquals(14, SiseonGuide.items.size)
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
    fun `대칭과 중앙은 같은 성격의 구도로 이어진다`() {
        assertEquals("symmetry", SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.SYMMETRY)))
        assertEquals("frame", SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.CENTER)))
    }

    @Test
    fun `삼분할은 장소가 구도를 정한다`() {
        // 물가면 반사
        assertEquals(
            "reflection",
            SiseonGuide.guideIdFor(
                facts(GuideOverlayView.GuideType.THIRDS, note = "연못 수면 반영이 좋아요")
            )
        )
        // 해질 무렵이면 실루엣
        assertEquals(
            "silhouette",
            SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.THIRDS, LightPhase.SUNSET))
        )
        // 그 외에는 리딩라인
        assertEquals(
            "leading",
            SiseonGuide.guideIdFor(facts(GuideOverlayView.GuideType.THIRDS, LightPhase.MORNING))
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
