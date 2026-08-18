package com.youngs.picview.domain

import com.youngs.picview.domain.light.LightPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 빛 구간의 화면 문구 검증.
 *
 * 상세 화면 "추천 촬영 대상" 칸이 [LightPhase.subject] 를 그대로 씁니다.
 * 어느 구간이든 빈 문자열이면 시안의 가운데 칸이 통째로 비어 보입니다.
 */
class LightPhaseTest {

    @Test
    fun `모든 구간에 촬영 대상 문장이 있다`() {
        LightPhase.entries.forEach { phase ->
            assertTrue("${phase.name} 의 subject 가 비어 있다", phase.subject.isNotBlank())
            assertTrue("${phase.name} 의 label 이 비어 있다", phase.label.isNotBlank())
            assertTrue("${phase.name} 의 hint 가 비어 있다", phase.hint.isNotBlank())
        }
    }

    @Test
    fun `좁은 칸용 짧은 이름은 골든아워를 줄인다`() {
        assertEquals("일출", LightPhase.SUNRISE.shortLabel)
        assertEquals("일몰", LightPhase.SUNSET.shortLabel)
        assertEquals("한낮", LightPhase.MIDDAY.shortLabel)
    }

    @Test
    fun `한낮과 야간은 야외 촬영에 불리로 분류된다`() {
        assertFalse(LightPhase.MIDDAY.isOutdoorFriendly)
        assertFalse(LightPhase.NIGHT.isOutdoorFriendly)
        assertTrue(LightPhase.SUNSET.isOutdoorFriendly)
        assertTrue(LightPhase.MORNING.isOutdoorFriendly)
    }
}
