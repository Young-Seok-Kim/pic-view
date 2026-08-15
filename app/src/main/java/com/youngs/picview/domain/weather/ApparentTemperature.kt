package com.youngs.picview.domain.weather

import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 체감온도.
 *
 * 홈에 기온만 두면 출사 판단에 부족합니다. 같은 28℃라도 습도 80%면 삼각대를
 * 들고 30분 걷기 어렵고, 같은 0℃라도 바람이 불면 손이 먼저 굳습니다.
 * 얼마나 머물 수 있느냐가 곧 몇 장을 찍을 수 있느냐라서, 촬영 계획에는
 * 기온보다 체감온도가 더 맞는 숫자입니다.
 *
 * 기상청이 쓰는 식을 그대로 옮겼습니다. 여름과 겨울이 서로 다른 식을
 * 쓰는데, 여름은 습도가, 겨울은 바람이 지배하기 때문입니다. 둘 중 어느
 * 조건에도 안 걸리는 봄·가을에는 기온이 곧 체감온도입니다.
 *
 * 필요한 값(기온·습도·풍속)은 모두 초단기실황 한 번의 응답에 함께 들어
 * 있습니다(T1H·REH·WSD). 추가 호출이 없습니다.
 */
object ApparentTemperature {

    /**
     * @param tempC 기온 ℃ (T1H)
     * @param humidity 상대습도 % (REH)
     * @param windMs 풍속 m/s (WSD)
     * @param month 관측 월. 기상청은 어느 식을 쓸지를 기온이 아니라 **월**로 가릅니다.
     * @return 체감온도 ℃. 계산 조건에 안 걸리면 [tempC] 를 그대로 돌려줍니다.
     */
    fun of(
        tempC: Double,
        humidity: Double?,
        windMs: Double?,
        month: Int = java.time.LocalDate.now().monthValue
    ): Double = when {
        month in SUMMER_MONTHS && humidity != null -> summer(tempC, humidity)
        month !in SUMMER_MONTHS && windMs != null &&
            tempC <= WINTER_MAX_TEMP && windMs >= WINTER_MIN_WIND -> winter(tempC, windMs)
        else -> tempC
    }

    /**
     * 여름철 체감온도(기상청 2020년 개정식).
     *
     * 습구온도를 먼저 구하고 그것으로 체감온도를 냅니다. 습구온도는 "이
     * 습도에서 물이 증발해 식힐 수 있는 한계 온도"라, 땀이 얼마나 듣는지를
     * 대신 말해 주는 값입니다.
     */
    private fun summer(ta: Double, rh: Double): Double {
        val tw = ta * atan(0.151977 * sqrt(rh + 8.313659)) +
            atan(ta + rh) -
            atan(rh - 1.67633) +
            0.00391838 * rh.pow(1.5) * atan(0.023101 * rh) -
            4.686035

        return -0.2442 +
            0.55399 * tw +
            0.45535 * ta -
            0.0022 * tw * tw +
            0.00278 * tw * ta +
            3.0
    }

    /**
     * 겨울철 체감온도(기상청 풍속냉각지수).
     *
     * 식이 km/h 를 받으므로 m/s 를 바꿔 넣습니다. 여기를 빠뜨리면 3.6배
     * 약한 바람으로 계산돼 체감온도가 실제보다 훨씬 높게 나옵니다.
     */
    private fun winter(ta: Double, windMs: Double): Double {
        val v = (windMs * 3.6).pow(0.16)
        return 13.12 + 0.6215 * ta - 11.37 * v + 0.3965 * ta * v
    }

    /**
     * 여름철 식을 쓰는 달(5~9월).
     *
     * 기온으로 가르지 않습니다. 24℃라도 습도가 90%면 땀이 안 마르는데,
     * "25℃ 이상"으로 잘라 두면 그런 날 체감이 기온과 같게 나와서 정보가
     * 사라집니다. 기상청도 월로 가릅니다.
     */
    private val SUMMER_MONTHS = 5..9

    /** 이보다 높으면 바람이 체감을 좌우하지 않습니다. */
    private const val WINTER_MAX_TEMP = 10.0

    /** 이보다 약한 바람은 식이 정의되지 않습니다. */
    private const val WINTER_MIN_WIND = 1.3
}
