package com.youngs.picview.domain.my

import com.youngs.picview.data.local.SavedCourseWithStops
import com.youngs.picview.data.local.VisitPhotoWithPlace
import com.youngs.picview.data.repository.planDate
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 코스 하나에 모인 내 사진.
 *
 * @param course 저장한 코스. 어느 코스에도 안 들어가는 사진은 null 로 묶입니다.
 * @param photos 이 코스의 정거장에서 찍은 사진, 최근 것부터.
 */
data class CourseAlbum(
    val course: SavedCourseWithStops?,
    val photos: List<VisitPhotoWithPlace>
) {
    /** 목록 갱신용 식별자. 코스 없는 묶음은 [LOOSE_ID]. */
    val id: Long get() = course?.course?.id ?: LOOSE_ID

    /** 코스의 정거장 수. 코스 없는 묶음은 찍은 장소 수. */
    val stopCount: Int get() = course?.stops?.size ?: visitedStopCount

    /** 사진이 남은 장소 수. "5곳 중 2곳"의 2 입니다. */
    val visitedStopCount: Int get() = photos.map { it.contentId }.distinct().size

    companion object {
        const val LOOSE_ID = -1L
    }
}

/**
 * 사진을 코스별로 나눕니다.
 *
 * 사진에는 "어느 코스를 돌다 찍었다"가 남지 않습니다. 촬영 화면은 장소만
 * 알기 때문입니다. 그래서 여기서 되짚습니다 — 그 장소가 정거장으로 들어 있는
 * 코스 가운데 **출사 예정일이 찍은 날과 가장 가까운** 코스를 고릅니다.
 * 같은 장소가 두 코스에 들어 있어도 사진은 한 코스에만 들어갑니다. 두 군데
 * 다 보이면 "사진 7장"이 실제보다 많아집니다.
 *
 * 어느 코스에도 없는 장소에서 찍은 사진은 맨 뒤에 "코스 없이 찍은 사진"으로
 * 따로 묶습니다. 버리면 "사진이 사라졌다"가 됩니다.
 */
object CourseAlbums {

    fun of(
        courses: List<SavedCourseWithStops>,
        photos: List<VisitPhotoWithPlace>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CourseAlbum> {
        val byCourse = linkedMapOf<Long, MutableList<VisitPhotoWithPlace>>()
        val loose = mutableListOf<VisitPhotoWithPlace>()

        for (photo in photos) {
            val takenDay = Instant.ofEpochMilli(photo.photo.takenAt).atZone(zone).toLocalDate()
            val best = courses
                .filter { saved -> saved.stops.any { it.contentId == photo.contentId } }
                .minWithOrNull(
                    compareBy<SavedCourseWithStops> {
                        abs(ChronoUnit.DAYS.between(it.course.planDate(), takenDay))
                    }.thenByDescending { it.course.createdAt }
                )
            if (best == null) {
                loose += photo
            } else {
                byCourse.getOrPut(best.course.id) { mutableListOf() } += photo
            }
        }

        val albums = courses
            .filter { byCourse.containsKey(it.course.id) }
            .sortedWith(
                compareByDescending<SavedCourseWithStops> { it.course.planDate() }
                    .thenByDescending { it.course.createdAt }
            )
            .map { CourseAlbum(it, byCourse.getValue(it.course.id)) }

        return if (loose.isEmpty()) albums else albums + CourseAlbum(null, loose)
    }
}
