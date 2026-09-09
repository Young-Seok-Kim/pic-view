package com.youngs.picview.domain.photo

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 사진 한 장을 읽은 결과. 촬영 성향은 이것으로 셉니다.
 *
 * @param reflection 위아래나 좌우가 거울처럼 되풀이됨 — 수면 반영, 대칭 구도
 * @param silhouette 밝은 배경 앞에 어두운 형태
 * @param waterside 호수·강·폭포·바닷가가 찍힘
 * @param sunset 노을이 찍힘
 * @param labels 라벨러가 붙인 이름들. 왜 그렇게 읽었는지 되짚을 때 봅니다
 */
data class PhotoReading(
    val reflection: Boolean,
    val silhouette: Boolean,
    val waterside: Boolean,
    val sunset: Boolean,
    val labels: List<String> = emptyList()
)

/**
 * 밝기만으로 읽는 구도 — 반영(대칭)과 실루엣.
 *
 * 서버도 모델도 없이 픽셀 계산만으로 잡습니다. 이 앱은 기록을 단말에만 두는
 * 것이 원칙이라, 사진을 어디로 보내지 않고 읽는 편이 앱의 성격과 맞습니다.
 * 라벨러(ML Kit)는 "호수"·"노을" 같은 **무엇**은 잘 말하지만 **어떻게**
 * 찍었는지는 말하지 않아서, 구도 두 가지는 여기서 직접 셉니다.
 *
 * 입력은 회색조 밝기(0~255) 배열입니다. Bitmap 을 모르는 순수 코드라
 * 단위 테스트에서 합성 이미지로 검증합니다.
 */
object PhotoTraits {

    /** 읽는 방법의 판. 문턱값이나 계산이 바뀌면 올려서 옛 결과를 다시 읽게 합니다. */
    const val VERSION = 1

    /**
     * 거울 상관이 이 이상이면 반영으로 봅니다.
     *
     * 관광공사 실사진 스무 장으로 맞춘 값입니다. 수면 반영·창틀 프레임·
     * 가운데 계단은 0.6 을 넘고, 대칭이 아닌 풍경은 전부 0.45 아래였습니다.
     */
    private const val MIRROR_MIN = 0.5f

    /**
     * 거울 상관이 그냥 상관보다 이만큼은 커야 합니다.
     *
     * 하늘·바다처럼 가로줄만 있는 사진은 뒤집어도 안 뒤집어도 비슷해서
     * 거울 상관이 높게 나옵니다. 되풀이가 진짜 "거울"인지는 뒤집었을 때만
     * 맞아야 합니다.
     */
    private const val MIRROR_MARGIN = 0.15f

    /** 띠 안의 밝기 편차가 이보다 작으면 비교할 결이 없는 것입니다(단색 하늘 등). */
    private const val MIN_DETAIL = 6f

    /** 어두운 픽셀 · 밝은 픽셀의 경계. */
    private const val DARK = 55
    private const val BRIGHT = 170

    /**
     * 어두운 형태가 이만큼, 밝은 쪽이 이만큼은 있어야 실루엣입니다.
     *
     * 밝은 쪽 문턱이 낮은 것은 밤 사진 때문입니다 — 앱의 실루엣 예시
     * (등불 앞 인물 조형)는 배경이 어둡고 형태만 빛나서, 밝은 픽셀이
     * 15% 남짓입니다. 그래도 중간 밝기가 비어 있으면 실루엣입니다.
     */
    private const val SILHOUETTE_DARK_MIN = 0.18f
    private const val SILHOUETTE_BRIGHT_MIN = 0.12f

    /** 중간 밝기가 이보다 많으면 실루엣이 아니라 보통 사진입니다. */
    private const val SILHOUETTE_MID_MAX = 0.45f

    /**
     * 반영·대칭 여부.
     *
     * 위아래(수면 반영) 또는 좌우(건물 대칭) 어느 쪽이든 거울처럼 되풀이되면
     * true 입니다. 축은 가운데 언저리에서 찾습니다 — 수면은 화면 한가운데
     * 있지 않은 경우가 많습니다.
     */
    fun isReflection(luma: IntArray, width: Int, height: Int): Boolean =
        mirrorScore(luma, width, height, vertical = true) >= MIRROR_MIN ||
            mirrorScore(luma, width, height, vertical = false) >= MIRROR_MIN

    /**
     * 거울 점수 0~1. 축 후보들 가운데 가장 높은 값입니다.
     *
     * 축 위쪽 띠와 아래쪽 띠를 뒤집어 맞댄 상관(NCC)에서, 안 뒤집고 맞댄
     * 상관을 뺀 여유가 [MIRROR_MARGIN] 이상일 때만 셉니다. NCC 는 밝기와
     * 대비 차이에 무디어서, 수면에 비친 상이 실제보다 어둡고 흐려도 잡힙니다.
     *
     * 맞대기 전에 띠마다 **줄 평균을 양방향으로 뺍니다**([flattenBand]).
     * 하늘은 위가 밝고 산은 아래가 어두운 식의 큰 기울기는 어느 사진에나
     * 있어서, 그대로 두면 대칭이 아닌 풍경도 절반끼리 "닮았다"고 나옵니다.
     * 기울기를 걷어 내면 남는 건 나무·건물 같은 결이고, 그 결이 거울처럼
     * 맞아야 진짜 반영입니다.
     *
     * @param vertical true 면 가로축(위아래 거울), false 면 세로축(좌우 거울)
     */
    fun mirrorScore(luma: IntArray, width: Int, height: Int, vertical: Boolean): Float {
        val length = if (vertical) height else width
        val breadth = if (vertical) width else height
        if (length < 8 || breadth < 4) return 0f

        fun at(pos: Int, across: Int): Int =
            if (vertical) luma[pos * width + across] else luma[across * width + pos]

        var best = 0f
        val from = (length * 0.3f).toInt()
        val to = (length * 0.7f).toInt()
        val minBand = max(2, length / 8)

        for (axis in from..to) {
            val band = min(axis, length - axis)
            if (band < minBand) continue

            // 축 위쪽 띠(축에서 먼 순서로 뒤집어)와 아래쪽 띠를 같은 길이의 벡터로.
            val n = band * breadth
            val upper = FloatArray(n)
            val lowerFlipped = FloatArray(n)
            val lowerPlain = FloatArray(n)
            var i = 0
            for (k in 1..band) {
                for (a in 0 until breadth) {
                    upper[i] = at(axis - k, a).toFloat()
                    lowerFlipped[i] = at(axis + k - 1, a).toFloat()
                    lowerPlain[i] = at(axis + band - k, a).toFloat()
                    i++
                }
            }

            flattenBand(upper, band, breadth)
            flattenBand(lowerFlipped, band, breadth)
            flattenBand(lowerPlain, band, breadth)

            val flipped = ncc(upper, lowerFlipped) ?: continue
            val plain = ncc(upper, lowerPlain) ?: continue
            val score = if (flipped - plain >= MIRROR_MARGIN) flipped else 0f
            if (score > best) best = score
        }
        return best
    }

    /**
     * 실루엣 여부 — 밝기 분포가 어둠과 밝음 양쪽으로 갈라져 있는지.
     *
     * 해를 등지고 사람이나 나무를 세우면 형태는 검게, 하늘은 하얗게 남고
     * 중간 밝기가 거의 없습니다. 그 모양을 봅니다.
     */
    fun isSilhouette(luma: IntArray): Boolean {
        if (luma.isEmpty()) return false
        var dark = 0
        var bright = 0
        for (v in luma) {
            if (v < DARK) dark++ else if (v > BRIGHT) bright++
        }
        val n = luma.size.toFloat()
        val darkRatio = dark / n
        val brightRatio = bright / n
        val midRatio = 1f - darkRatio - brightRatio
        return darkRatio >= SILHOUETTE_DARK_MIN &&
            brightRatio >= SILHOUETTE_BRIGHT_MIN &&
            midRatio <= SILHOUETTE_MID_MAX
    }

    /**
     * 띠에서 큰 기울기를 걷어 냅니다 — 각 줄의 평균과 각 열의 평균을 빼고
     * 전체 평균을 되돌립니다. 띠는 [rows]×[cols] 로 펼쳐진 배열입니다.
     */
    private fun flattenBand(band: FloatArray, rows: Int, cols: Int) {
        val rowMean = FloatArray(rows)
        val colMean = FloatArray(cols)
        var total = 0f
        for (r in 0 until rows) for (c in 0 until cols) {
            val v = band[r * cols + c]
            rowMean[r] += v
            colMean[c] += v
            total += v
        }
        for (r in 0 until rows) rowMean[r] /= cols
        for (c in 0 until cols) colMean[c] /= rows
        total /= (rows * cols)
        for (r in 0 until rows) for (c in 0 until cols) {
            band[r * cols + c] += total - rowMean[r] - colMean[c]
        }
    }

    /**
     * 정규화 상관계수. 두 띠 가운데 하나라도 결이 없으면(편차가 작으면) null.
     * 단색 하늘끼리는 어떻게 맞대도 "닮았다"고 나오는데, 그건 거울이 아닙니다.
     */
    private fun ncc(a: FloatArray, b: FloatArray): Float? {
        val n = a.size
        var meanA = 0f
        var meanB = 0f
        for (i in 0 until n) {
            meanA += a[i]
            meanB += b[i]
        }
        meanA /= n
        meanB /= n

        var cov = 0f
        var varA = 0f
        var varB = 0f
        for (i in 0 until n) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            cov += da * db
            varA += da * da
            varB += db * db
        }
        val sdA = sqrt(varA / n)
        val sdB = sqrt(varB / n)
        if (sdA < MIN_DETAIL || sdB < MIN_DETAIL) return null
        return (cov / n) / (sdA * sdB)
    }
}
