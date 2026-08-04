package com.youngs.picview.domain.diary

import com.youngs.picview.data.local.DiaryEntity
import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.domain.light.LightPhase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 하루치 출사 기록.
 *
 * 방문 로그를 날짜로 묶은 것이고, 여기에 일기 본문이 붙습니다.
 * 일기가 아직 없으면 [diary] 가 null 이고 화면에 "일기 만들기"가 뜹니다.
 */
data class DiaryDay(
    val date: LocalDate,
    /** 그날 방문한 곳. 시간 순. */
    val visits: List<VisitLogEntity>,
    val diary: DiaryEntity?
) {
    val dateKey: String get() = date.toString()

    val hasDiary: Boolean get() = diary != null

    /** 그날 골든아워에 방문한 횟수. 일기 문구와 통계에 씁니다. */
    val goldenVisits: Int
        get() = visits.count {
            it.phaseName == LightPhase.SUNRISE.name || it.phaseName == LightPhase.SUNSET.name
        }

    val bestScore: Int get() = visits.maxOfOrNull { it.score } ?: 0

    val averageScore: Int
        get() = if (visits.isEmpty()) 0 else visits.sumOf { it.score } / visits.size

    /** 첫 방문 ~ 마지막 방문 시각. */
    val timeRange: Pair<LocalTime, LocalTime>?
        get() {
            if (visits.isEmpty()) return null
            val times = visits.map { it.visitedAt.toLocalTime() }
            return times.min() to times.max()
        }

    companion object {
        /** 방문 로그를 날짜별로 묶습니다. */
        fun group(
            visits: List<VisitLogEntity>,
            diaries: List<DiaryEntity>
        ): List<DiaryDay> {
            val diaryByDate = diaries.associateBy { it.dateKey }
            return visits
                .groupBy { it.visitedAt.toLocalDate() }
                .map { (date, dayVisits) ->
                    DiaryDay(
                        date = date,
                        visits = dayVisits.sortedBy { it.visitedAt },
                        diary = diaryByDate[date.toString()]
                    )
                }
                .sortedByDescending { it.date }
        }
    }
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun Long.toLocalTime(): LocalTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()

fun Long.toDiaryLocalTime(): LocalTime = toLocalTime()
