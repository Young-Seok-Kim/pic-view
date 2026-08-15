package com.youngs.picview.domain.light

import java.time.Duration
import java.time.LocalTime

/**
 * 오늘 정읍의 태양 시각. 천문연 출몰시각 API 응답에서 만듭니다.
 *
 * 이 앱이 일반 여행 앱과 갈리는 지점이 여기입니다.
 * 다른 앱은 "몇 시에 문 여나"만 알지만, 우리는 "몇 시에 빛이 좋은가"를 압니다.
 * 코스 순서, 스팟 추천, 포토스코어가 전부 이 값에서 나옵니다.
 *
 * 값이 없을 수 있습니다(API 실패). 그 경우 [hasData] 가 false 이고
 * 시간대 판정은 시계 기준의 대략치로 폴백합니다.
 */
data class SunTimes(
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    /** 남중(태양이 가장 높은 시각). 없으면 일출·일몰의 중간으로 추정합니다. */
    val meridian: LocalTime? = null
) {

    val hasData: Boolean get() = sunrise != null && sunset != null

    /** 남중 시각. API 가 안 주면 일출·일몰 중간값으로 계산합니다. */
    val solarNoon: LocalTime?
        get() = meridian ?: run {
            val rise = sunrise ?: return null
            val set = sunset ?: return null
            rise.plusSeconds(Duration.between(rise, set).seconds / 2)
        }

    /** [time] 이 속한 빛 구간. */
    fun phaseAt(time: LocalTime): LightPhase {
        val rise = sunrise
        val set = sunset

        if (rise == null || set == null) return fallbackPhase(time)

        val toRise = Duration.between(time, rise).toMinutes()
        val toSet = Duration.between(time, set).toMinutes()

        return when {
            // 일출 전후 GOLDEN_MIN 분
            kotlin.math.abs(toRise) <= GOLDEN_MIN -> LightPhase.SUNRISE
            kotlin.math.abs(toSet) <= GOLDEN_MIN -> LightPhase.SUNSET

            // 골든아워 바로 바깥의 블루아워
            toRise in (GOLDEN_MIN + 1)..(GOLDEN_MIN + BLUE_MIN) -> LightPhase.BLUE_DAWN
            -toSet in (GOLDEN_MIN + 1)..(GOLDEN_MIN + BLUE_MIN) -> LightPhase.BLUE_DUSK

            time < rise || time > set -> LightPhase.NIGHT

            else -> {
                val noon = solarNoon
                when {
                    noon == null -> LightPhase.MORNING
                    kotlin.math.abs(Duration.between(time, noon).toMinutes()) <= MIDDAY_MIN ->
                        LightPhase.MIDDAY
                    time < noon -> LightPhase.MORNING
                    else -> LightPhase.AFTERNOON
                }
            }
        }
    }

    fun phaseNow(): LightPhase = phaseAt(LocalTime.now())

    fun isGoldenHourNow(): Boolean = phaseNow().isGolden

    /**
     * 오늘 남은 골든아워 구간. 코스를 짤 때 이 슬롯을 먼저 채웁니다.
     * 이미 지난 구간은 빼고 돌려줍니다.
     */
    fun upcomingGoldenWindows(from: LocalTime = LocalTime.now()): List<GoldenWindow> =
        buildList {
            sunrise?.let { r ->
                val end = r.plusMinutes(GOLDEN_MIN)
                if (end >= from) add(
                    GoldenWindow(LightPhase.SUNRISE, r.minusMinutes(GOLDEN_MIN), end, r)
                )
            }
            sunset?.let { s ->
                val end = s.plusMinutes(GOLDEN_MIN)
                if (end >= from) add(
                    GoldenWindow(LightPhase.SUNSET, s.minusMinutes(GOLDEN_MIN), end, s)
                )
            }
        }.sortedBy { it.start }

    /** 다음 골든아워까지 남은 시간(분). 없으면 null. */
    fun minutesToNextGolden(from: LocalTime = LocalTime.now()): Long? =
        upcomingGoldenWindows(from)
            .firstOrNull { it.start > from }
            ?.let { Duration.between(from, it.start).toMinutes() }

    /**
     * 다음 골든아워.
     *
     * [minutesToNextGolden] 은 오늘 남은 구간만 봅니다. 그래서 해가 진 뒤에는
     * 언제나 null 이라 "오늘은 끝났습니다"까지만 말할 수 있었습니다. 정작
     * 밤에 앱을 여는 사람이 알고 싶은 건 **내일 몇 시에 나가야 하나** 입니다.
     * 여기서는 오늘 남은 것이 없으면 내일 일출 골든아워로 넘어갑니다.
     */
    fun nextGolden(from: LocalTime = LocalTime.now()): NextGolden? {
        upcomingGoldenWindows(from).firstOrNull { it.start > from }?.let {
            return NextGolden(
                phase = it.phase,
                start = it.start,
                isTomorrow = false,
                minutesAway = Duration.between(from, it.start).toMinutes()
            )
        }

        val rise = sunrise ?: return null
        val start = rise.minusMinutes(GOLDEN_MIN)
        // 자정을 넘어가므로 "오늘 남은 시간 + 내일 새벽까지" 로 계산합니다.
        val minutes = MINUTES_PER_DAY - from.toSecondOfDay() / 60L + start.toSecondOfDay() / 60L
        return NextGolden(LightPhase.SUNRISE, start, isTomorrow = true, minutesAway = minutes)
    }

    /**
     * 지금과 가장 가까운 태양 사건.
     *
     * "일몰 후 58분" 처럼 지금이 하루의 어디쯤인지를 한 마디로 말하는 데 씁니다.
     * 일출·일몰 중 **더 가까운 쪽**을 고릅니다. 20:20 에 "일출 후 14시간" 은
     * 맞는 말이지만 아무 도움이 안 되고, "일몰 후 58분" 이라야 방금 무슨 일이
     * 있었는지가 읽힙니다.
     */
    fun nearestEvent(from: LocalTime = LocalTime.now()): SunEventGap? {
        val candidates = buildList {
            sunrise?.let { add(SunEvent.SUNRISE to it) }
            sunset?.let { add(SunEvent.SUNSET to it) }
        }
        val (event, at) = candidates.minByOrNull {
            kotlin.math.abs(Duration.between(from, it.second).toMinutes())
        } ?: return null

        val minutes = Duration.between(from, at).toMinutes()
        return SunEventGap(event, kotlin.math.abs(minutes), isBefore = minutes > 0)
    }

    /** 일출·일몰 정보가 없을 때의 대략 판정. */
    private fun fallbackPhase(time: LocalTime): LightPhase = when (time.hour) {
        in 0..4 -> LightPhase.NIGHT
        in 5..6 -> LightPhase.SUNRISE
        in 7..10 -> LightPhase.MORNING
        in 11..14 -> LightPhase.MIDDAY
        in 15..17 -> LightPhase.AFTERNOON
        in 18..19 -> LightPhase.SUNSET
        in 20..20 -> LightPhase.BLUE_DUSK
        else -> LightPhase.NIGHT
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        /** 일출·일몰 전후 이 분(分)까지를 골든아워로 봅니다. */
        const val GOLDEN_MIN = 30L

        /** 골든아워 바깥으로 이 분(分)까지가 블루아워. */
        const val BLUE_MIN = 25L

        /** 남중 전후 이 분(分)을 한낮으로 봅니다. */
        const val MIDDAY_MIN = 90L

        val EMPTY = SunTimes(null, null, null)

        /** "0543" 형태 문자열을 파싱합니다. 형식이 어긋나면 null. */
        fun parse(raw: String?): LocalTime? {
            val t = raw?.trim().orEmpty()
            if (t.length < 4) return null
            return runCatching {
                LocalTime.of(t.substring(0, 2).toInt(), t.substring(2, 4).toInt())
            }.getOrNull()
        }
    }
}

/** 태양 사건 두 가지. */
enum class SunEvent { SUNRISE, SUNSET }

/** 지금과 [event] 사이의 거리. [isBefore] 면 아직 오지 않은 것입니다. */
data class SunEventGap(val event: SunEvent, val minutes: Long, val isBefore: Boolean)

/** 다음 골든아워. [isTomorrow] 면 날짜가 넘어갑니다. */
data class NextGolden(
    val phase: LightPhase,
    val start: LocalTime,
    val isTomorrow: Boolean,
    val minutesAway: Long
)

/** 골든아워 한 구간. */
data class GoldenWindow(
    val phase: LightPhase,
    val start: LocalTime,
    val end: LocalTime,
    /** 실제 일출 또는 일몰 시각 */
    val peak: LocalTime
) {
    val durationMinutes: Long get() = Duration.between(start, end).toMinutes()
}
