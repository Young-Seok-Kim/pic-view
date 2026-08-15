package com.youngs.picview.domain.weather

/** 단기예보의 시간대별 기온 한 점. */
data class HourlyTemp(val hour: Int, val tempC: Double)

/**
 * 하늘 상태.
 *
 * 출사에서 맑음·흐림은 취향이 아니라 조건입니다. 맑으면 대비가 세서
 * 한낮 하늘이 하얗게 날아가고, 흐리면 온 하늘이 소프트박스가 되어 인물과
 * 숲의 결이 살아납니다. 그래서 히어로의 빛 보고서 옆에 둡니다.
 *
 * 기상청은 비가 오는지(PTY)와 구름 양(SKY)을 따로 줍니다. 비가 오면 구름
 * 양은 볼 필요가 없으므로 PTY 를 먼저 봅니다.
 */
enum class SkyState(val label: String, val emoji: String) {
    CLEAR("맑음", "☀"),
    PARTLY("구름 조금", "🌤"),
    CLOUDY("흐림", "☁"),
    RAIN("비", "🌧"),
    SLEET("진눈깨비", "🌨"),
    SNOW("눈", "❄"),
    SHOWER("소나기", "🌦");

    companion object {
        /**
         * @param pty 강수형태 코드 (0 없음 / 1 비 / 2 비·눈 / 3 눈 / 4 소나기)
         * @param sky 하늘상태 코드 (1 맑음 / 3 구름많음 / 4 흐림)
         */
        fun of(pty: String?, sky: String?): SkyState = when (pty) {
            "1" -> RAIN
            "2" -> SLEET
            "3" -> SNOW
            "4" -> SHOWER
            else -> when (sky) {
                "1" -> CLEAR
                "3" -> PARTLY
                "4" -> CLOUDY
                else -> CLEAR
            }
        }
    }
}
