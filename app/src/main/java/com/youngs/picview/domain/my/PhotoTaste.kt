package com.youngs.picview.domain.my

import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.photo.PhotoReading

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
 * 사진을 **직접 읽어** 셉니다([PhotoReading]). 전에는 장소 이름과 시각으로
 * 짐작했는데 — 피향정에서 찍으면 무조건 반영·수변 — 그건 "어디 갔나"지
 * "무엇을 찍었나"가 아니었습니다. 지금은 사진에 물가가 있는지, 거울처럼
 * 되풀이되는지, 형태가 검게 남았는지를 기기 안에서 읽습니다.
 *
 * 석양만은 시각도 함께 봅니다. 노을이 화면에 안 잡혀도 골든아워에 나가
 * 찍은 사진은 "해가 낮을 때 셔터를 누른" 사진입니다.
 *
 * @param sampleSize 읽기가 끝나 성향에 들어간 사진 수. 화면에 "사진 N장 기준"으로 밝힙니다.
 * @param pending 아직 읽는 중인 사진 수.
 */
data class PhotoTaste(
    val axes: List<TasteAxis>,
    val sampleSize: Int,
    val pending: Int
) {
    /** 말할 만큼 읽었는지. 한 장으로 100%는 거짓말입니다. */
    val isReady: Boolean get() = sampleSize >= MIN_SAMPLE

    /** 가장 두드러진 축. 전부 0이면 null. */
    val strongest: TasteAxis? get() = axes.filter { it.ratio > 0f }.maxByOrNull { it.ratio }

    /**
     * 성향을 한 문장으로.
     *
     * 백분율 네 개만 두면 "그래서 나는 어떤 사람인가"가 안 남습니다.
     * 가장 높은 축 하나만 말로 옮깁니다.
     */
    val insight: String?
        get() = when (strongest?.label) {
            REFLECTION -> "좌우를 맞춰 세우거나 수면에 비추는 장면을 즐기는 편이에요."
            SILHOUETTE -> "빛을 등지고 형태만 남기는 장면을 즐기는 편이에요."
            GOLDEN -> "해가 낮아질수록 셔터를 자주 누르는 편이에요."
            WATERSIDE -> "물이 있는 자리를 먼저 찾는 편이에요."
            else -> null
        }

    companion object {
        const val REFLECTION = "반영"
        const val SILHOUETTE = "실루엣"
        const val GOLDEN = "석양"
        const val WATERSIDE = "수변"

        /** 성향을 말하려면 최소 이만큼은 있어야 합니다. */
        const val MIN_SAMPLE = 3

        /**
         * 방문 기록과 사진 읽기 결과에서 성향을 셉니다.
         *
         * 직접 찍은 사진이 있는 기록만 셉니다. "다녀왔어요"만 누른 것은
         * 촬영 성향이 아니라 이동 기록입니다. 그중 읽기가 끝난 사진만
         * 분모에 들어가고, 나머지는 [PhotoTaste.pending] 으로 셉니다.
         *
         * @param readings 사진 주소 → 읽은 결과
         */
        fun of(visits: List<VisitLogEntity>, readings: Map<String, PhotoReading>): PhotoTaste {
            val photos = visits.filter { !it.photoUri.isNullOrBlank() }
            val read = photos.mapNotNull { visit ->
                readings[visit.photoUri]?.let { visit to it }
            }
            val n = read.size.toFloat().coerceAtLeast(1f)

            val reflection = read.count { (_, r) -> r.reflection } / n
            val silhouette = read.count { (_, r) -> r.silhouette } / n
            val golden = read.count { (visit, r) ->
                r.sunset || runCatching { LightPhase.valueOf(visit.phaseName) }
                    .getOrNull()?.isGolden == true
            } / n
            val waterside = read.count { (_, r) -> r.waterside } / n

            return PhotoTaste(
                axes = listOf(
                    TasteAxis(REFLECTION, reflection),
                    TasteAxis(SILHOUETTE, silhouette),
                    TasteAxis(GOLDEN, golden),
                    TasteAxis(WATERSIDE, waterside)
                ),
                sampleSize = read.size,
                pending = photos.size - read.size
            )
        }
    }
}
