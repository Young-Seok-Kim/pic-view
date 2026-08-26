package com.youngs.picview.domain.my

import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.mission.Missions
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.guide.GuideOverlayView

/**
 * 촬영 성향 한 축.
 *
 * @param ratio 0f~1f. 화면에서는 막대와 백분율로 보입니다.
 */
data class TasteAxis(val label: String, val ratio: Float) {
    val percent: Int get() = Math.round(ratio * 100)
}

/**
 * 내가 어떤 장면을 자주 담는지.
 *
 * 사진 자체는 읽지 못합니다(반사인지 실루엣인지 화소로는 모릅니다).
 * 그래서 **찍은 자리와 시각**으로만 셉니다 — 어떤 장소였고 그때 빛이
 * 어느 구간이었는지. 이 둘은 체크인할 때 이미 기록돼 있어 추측이 아닙니다.
 *
 * 사진을 판독한 척하지 않는다는 [Missions] 의 원칙과 같은 선입니다.
 */
data class PhotoTaste(
    val axes: List<TasteAxis>,
    /** 성향을 센 사진 수. 화면에 "사진 N장 기준"으로 밝힙니다. */
    val sampleSize: Int
) {
    /** 가장 두드러진 축. 전부 0이면 null. */
    val strongest: TasteAxis? get() = axes.filter { it.ratio > 0f }.maxByOrNull { it.ratio }

    /**
     * 성향을 한 문장으로.
     *
     * 백분율 세 개만 두면 "그래서 나는 어떤 사람인가"가 안 남습니다.
     * 가장 높은 축 하나만 말로 옮깁니다.
     */
    val insight: String?
        get() = when (strongest?.label) {
            REFLECTION -> "좌우를 맞춰 세우는 장면을 즐기는 편이에요."
            GOLDEN -> "해가 낮아질수록 셔터를 자주 누르는 편이에요."
            WATERSIDE -> "물이 있는 자리를 먼저 찾는 편이에요."
            else -> null
        }

    companion object {
        const val REFLECTION = "반영"
        const val GOLDEN = "석양"
        const val WATERSIDE = "수변"

        /** 성향을 말하려면 최소 이만큼은 있어야 합니다. 한 장으로 100%는 거짓말입니다. */
        const val MIN_SAMPLE = 3

        /**
         * 방문 기록에서 성향을 셉니다.
         *
         * 직접 찍은 사진이 있는 기록만 셉니다. "다녀왔어요"만 누른 것은
         * 촬영 성향이 아니라 이동 기록입니다.
         */
        fun of(visits: List<VisitLogEntity>): PhotoTaste? {
            val photos = visits.filter { !it.photoUri.isNullOrBlank() }
            if (photos.size < MIN_SAMPLE) return null

            val n = photos.size.toFloat()

            val reflection = photos.count {
                SpotFactsTable.of(it.title, null).guide ==
                    GuideOverlayView.GuideType.SYMMETRY
            } / n

            val golden = photos.count { visit ->
                runCatching { LightPhase.valueOf(visit.phaseName) }.getOrNull()?.isGolden == true
            } / n

            val waterside = photos.count { visit ->
                Missions.WATER_PLACES.any { visit.title.contains(it) }
            } / n

            return PhotoTaste(
                axes = listOf(
                    TasteAxis(REFLECTION, reflection),
                    TasteAxis(GOLDEN, golden),
                    TasteAxis(WATERSIDE, waterside)
                ),
                sampleSize = photos.size
            )
        }
    }
}
