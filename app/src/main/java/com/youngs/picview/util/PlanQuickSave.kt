package com.youngs.picview.util

import android.content.Context
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.domain.course.CourseStop
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.model.SpotItem
import java.time.LocalTime

/**
 * 한 곳짜리 출사 일정 저장.
 *
 * 홈 카드의 "출사 계획에 담기", 일기의 "이 계획으로 출사 준비하기"가
 * 같은 저장을 씁니다. 새 저장소를 만들지 않고 코스 저장을 그대로 쓰므로
 * 저장하면 코스 목록에 정거장 하나짜리 코스로 나타납니다.
 */
object PlanQuickSave {

    /** 저장 성공 여부를 돌려줍니다. suspend — 호출 쪽 스코프에서 부릅니다. */
    suspend fun save(context: Context, spot: SpotItem, sun: SunTimes): Boolean {
        val facts = SpotFactsTable.of(spot.title, spot.contentTypeId)
        val window = sun.upcomingGoldenWindows(LocalTime.MIN)
            .firstOrNull { it.phase == facts.bestPhase }
            ?: sun.upcomingGoldenWindows(LocalTime.MIN).firstOrNull()

        val arrive = window?.start ?: LocalTime.of(9, 0)
        val stop = CourseStop(
            spot = spot,
            facts = facts,
            arriveAt = arrive,
            leaveAt = window?.end ?: arrive.plusMinutes(60),
            phase = window?.phase ?: facts.bestPhase,
            travelMinutes = 0,
            travelKm = 0.0,
            reason = facts.note,
            isHighlight = true
        )

        return runCatching {
            CourseRepository(context).save(
                ShootingCourse(
                    stops = listOf(stop),
                    sun = sun,
                    travelMode = TravelMode.CAR,
                    totalDistanceKm = 0.0
                ),
                summary = spot.title
            )
        }.isSuccess
    }
}
