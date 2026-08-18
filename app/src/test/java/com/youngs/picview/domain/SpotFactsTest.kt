package com.youngs.picview.domain

import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.SpotFactsTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 촬영 방위 규칙 검증.
 *
 * 상세 화면이 이 값 하나로 두 가지를 가릅니다 — bearing 이 있으면
 * "서향 빛" 처럼 빛의 말로 바꿔 적고 나침반을 켜며, 없으면(실내·무관)
 * 이름 그대로 적고 나침반 그림 자체를 뺍니다. 여기가 어긋나면
 * "실내 빛" 같은 문장이나 아무 데도 안 켜진 나침반이 나갑니다.
 */
class SpotFactsTest {

    @Test
    fun `네 방위는 정의된 각도를 가진다`() {
        assertEquals(90, Facing.EAST.bearing)
        assertEquals(270, Facing.WEST.bearing)
        assertEquals(180, Facing.SOUTH.bearing)
        assertEquals(0, Facing.NORTH.bearing)
    }

    @Test
    fun `실내와 무관은 방위가 없다`() {
        assertNull(Facing.INDOOR.bearing)
        assertNull(Facing.ANY.bearing)
    }

    @Test
    fun `각도 표기는 방위가 있을 때만 붙는다`() {
        assertEquals("서향 · 270°", Facing.WEST.labelWithBearing)
        assertEquals("실내", Facing.INDOOR.labelWithBearing)
    }

    @Test
    fun `표에 있는 스팟은 이름으로 찾는다`() {
        val naejang = SpotFactsTable.of("내장산 국립공원", contentTypeId = "12")
        assertEquals(Facing.WEST, naejang.facing)
    }

    @Test
    fun `표에 없으면 카테고리 추정으로 채우고 문장은 비지 않는다`() {
        // 상세 카드에 이 문장이 그대로 붙습니다. 빈 값이면 카드가 비어 보입니다.
        listOf("12", "14", "28", "39", null, "미지정").forEach { typeId ->
            val facts = SpotFactsTable.defaultFor(typeId)
            assertNotNull(facts.facing)
            assertTrue("typeId=$typeId 의 note 가 비어 있다", facts.note.isNotBlank())
            assertTrue("체류 시간은 양수여야 한다", facts.stayMinutes > 0)
        }
    }
}
