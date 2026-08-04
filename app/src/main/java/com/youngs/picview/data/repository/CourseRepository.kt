package com.youngs.picview.data.repository

import android.content.Context
import com.youngs.picview.data.local.PicViewDatabase
import com.youngs.picview.data.local.SavedCourseEntity
import com.youngs.picview.data.local.SavedCourseWithStops
import com.youngs.picview.data.local.SavedStopEntity
import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.domain.course.CourseStop
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.AppPrefs
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/**
 * 저장한 코스와 방문 기록.
 *
 * 도메인 모델([ShootingCourse])과 DB 엔티티 사이 변환을 여기서만 합니다.
 * 화면은 도메인 모델만 알면 되고, DB 스키마가 바뀌어도 화면은 안 건드립니다.
 */
class CourseRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = PicViewDatabase.get(appContext)
    private val courseDao = db.courseDao()
    private val visitDao = db.visitDao()

    // ───────────────────────── 코스 ─────────────────────────

    fun observeCourses(): Flow<List<SavedCourseWithStops>> = courseDao.observeCourses()

    fun observeCourseCount(): Flow<Int> = courseDao.observeCourseCount()

    suspend fun save(course: ShootingCourse, summary: String): Long {
        val entity = SavedCourseEntity(
            createdAt = System.currentTimeMillis(),
            title = defaultTitle(course),
            summary = summary,
            totalKm = course.totalDistanceKm,
            totalMinutes = course.totalMinutes,
            travelMode = course.travelMode.name,
            sunriseMinute = course.sun.sunrise?.toMinuteOfDay() ?: -1,
            sunsetMinute = course.sun.sunset?.toMinuteOfDay() ?: -1
        )

        val stops = course.stops.mapIndexed { index, stop -> stop.toEntity(index) }
        return courseDao.saveCourse(entity, stops)
    }

    suspend fun delete(courseId: Long) = courseDao.deleteCourse(courseId)

    suspend fun get(courseId: Long): SavedCourseWithStops? = courseDao.getCourse(courseId)

    /** "내장산국립공원 외 4곳" 형태의 기본 이름. */
    private fun defaultTitle(course: ShootingCourse): String {
        val head = course.goldenStops.firstOrNull()?.spot?.title
            ?: course.stops.firstOrNull()?.spot?.title
            ?: "출사 코스"
        val rest = course.stops.size - 1
        return if (rest > 0) "$head 외 ${rest}곳" else head
    }

    // ───────────────────────── 방문 기록 ─────────────────────────

    fun observeVisits(): Flow<List<VisitLogEntity>> = visitDao.observeAll()

    fun observeVisitedSpotCount(): Flow<Int> = visitDao.observeVisitedSpotCount()

    /**
     * 방문 기록을 남깁니다.
     *
     * 같은 장소를 [DEDUP_WINDOW_MS] 안에 다시 기록하지는 않습니다.
     * 상세 화면을 여러 번 드나들 때마다 기록이 쌓이면 통계가 무의미해집니다.
     *
     * @return 실제로 기록했으면 true
     */
    suspend fun logVisit(spot: SpotItem, phase: LightPhase): Boolean {
        val now = System.currentTimeMillis()
        val recent = visitDao.countRecent(spot.contentId, now - DEDUP_WINDOW_MS)
        if (recent > 0) return false

        visitDao.insert(
            VisitLogEntity(
                contentId = spot.contentId,
                title = spot.title,
                visitedAt = now,
                score = spot.score,
                imageUrl = spot.imageUrl,
                phaseName = phase.name,
                installId = AppPrefs.installId(appContext)
            )
        )
        return true
    }

    companion object {
        /** 같은 장소 재기록을 막는 시간(3시간). */
        private const val DEDUP_WINDOW_MS = 3 * 60 * 60 * 1000L
    }
}

// ───────────────────────── 변환 ─────────────────────────

private fun LocalTime.toMinuteOfDay() = hour * 60 + minute

private fun CourseStop.toEntity(order: Int) = SavedStopEntity(
    courseId = 0, // saveCourse 가 실제 id 로 채웁니다
    sortOrder = order,
    contentId = spot.contentId,
    contentTypeId = spot.contentTypeId,
    title = spot.title,
    addr1 = spot.addr1,
    imageUrl = spot.imageUrl,
    mapx = spot.mapx,
    mapy = spot.mapy,
    score = spot.score,
    arriveMinute = arriveAt.toMinuteOfDay(),
    stayMinutes = facts.stayMinutes,
    travelMinutes = travelMinutes,
    travelKm = travelKm,
    phaseName = phase.name,
    facingName = facts.facing.name,
    guideName = facts.guide.name,
    reason = reason,
    isHighlight = isHighlight
)

/** 저장된 정거장을 다시 도메인 모델로. 상세 화면 이동 등에 씁니다. */
fun SavedStopEntity.toSpotItem() = SpotItem(
    contentId = contentId,
    contentTypeId = contentTypeId,
    title = title,
    addr1 = addr1,
    tip = "",
    imageUrl = imageUrl,
    mapx = mapx,
    mapy = mapy
).apply { this.score = this@toSpotItem.score }

fun SavedStopEntity.phase(): LightPhase =
    runCatching { LightPhase.valueOf(phaseName) }.getOrDefault(LightPhase.MORNING)

fun SavedStopEntity.facing(): Facing =
    runCatching { Facing.valueOf(facingName) }.getOrDefault(Facing.ANY)

fun SavedStopEntity.arriveTime(): LocalTime =
    LocalTime.of(arriveMinute / 60, arriveMinute % 60)

/** 저장된 정거장에도 촬영 특성이 필요할 때(구도 이름 등). */
fun SavedStopEntity.facts() = SpotFactsTable.of(title, contentTypeId)
