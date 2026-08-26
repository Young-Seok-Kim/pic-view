package com.youngs.picview.util

import android.content.Context
import com.youngs.picview.R
import com.youngs.picview.domain.light.SunTimes
import java.time.Duration
import java.time.LocalTime

/**
 * "석양까지 58분" 한 줄.
 *
 * 같은 문장을 홈·코스·MY 세 화면이 씁니다. 화면마다 따로 만들어 두면
 * 한 곳에서 "1시간 0분"을 고쳐도 다른 두 곳은 그대로 남습니다.
 * 실제로 [durationText] 는 홈과 코스에 글자까지 똑같이 복사돼 있었습니다.
 */
object SunCountdown {

    /** 한 시간이 안 되면 "분"만 씁니다. "0시간 58분"은 읽는 데 방해가 됩니다. */
    fun durationText(context: Context, minutes: Long): String = if (minutes >= 60) {
        context.getString(R.string.home_duration_hm, minutes / 60, minutes % 60)
    } else {
        context.getString(R.string.home_duration_m, minutes)
    }

    /**
     * 다음 해 사건까지 남은 시간.
     *
     * 골든아워 안이면 남은 시간 대신 "지금이 골든아워"라고 말합니다.
     * 카운트다운은 나가야 할 이유를 만드는 장치인데, 이미 나갈 시간이면
     * 숫자보다 그 사실이 먼저입니다.
     *
     * 일출·일몰 값이 없으면 null 을 돌려줍니다. 부르는 쪽이 그 줄을
     * 통째로 숨길지 다른 문장을 넣을지 정합니다.
     */
    fun untilNextEvent(
        context: Context,
        sun: SunTimes,
        now: LocalTime = LocalTime.now()
    ): String? {
        if (!sun.hasData) return null
        if (sun.phaseAt(now).isGolden) return context.getString(R.string.course_hero_golden_now)

        val sunrise = sun.sunrise ?: return null
        val sunset = sun.sunset ?: return null

        val (template, target) = when {
            now < sunrise -> R.string.course_hero_until_sunrise to sunrise
            now < sunset -> R.string.course_hero_until_sunset to sunset
            else -> return context.getString(R.string.course_hero_sun_down)
        }
        val minutes = Duration.between(now, target).toMinutes()
        return context.getString(template, durationText(context, minutes))
    }
}
