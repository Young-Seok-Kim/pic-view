package com.youngs.picview.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.youngs.picview.data.local.PicViewDatabase
import com.youngs.picview.data.local.SavedCourseEntity
import com.youngs.picview.data.local.SavedCourseWithStops
import com.youngs.picview.data.local.SavedStopEntity
import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.data.local.VisitPhotoEntity
import com.youngs.picview.data.local.VisitPhotoWithPlace
import com.youngs.picview.domain.course.CourseStop
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    suspend fun save(
        course: ShootingCourse,
        summary: String,
        planDate: java.time.LocalDate = java.time.LocalDate.now()
    ): Long {
        val entity = SavedCourseEntity(
            createdAt = System.currentTimeMillis(),
            title = defaultTitle(course),
            summary = summary,
            totalKm = course.totalDistanceKm,
            totalMinutes = course.totalMinutes,
            travelMode = course.travelMode.name,
            sunriseMinute = course.sun.sunrise?.toMinuteOfDay() ?: -1,
            sunsetMinute = course.sun.sunset?.toMinuteOfDay() ?: -1,
            planDateEpochDay = planDate.toEpochDay()
        )

        val stops = course.stops.mapIndexed { index, stop -> stop.toEntity(index) }
        return courseDao.saveCourse(entity, stops)
    }

    /**
     * 같은 날, 같은 곳들로 이미 저장된 코스가 있는지.
     *
     * '저장'을 두 번 누르거나 뒤로 갔다 다시 들어와 또 누르면 목록에
     * 똑같은 카드가 둘 생겼습니다. 날짜와 정거장 구성(순서 포함)이 같으면
     * 같은 코스로 봅니다 — 같은 곳들을 다른 순서로 도는 것은 다른 코스라
     * 순서까지 봅니다.
     */
    suspend fun hasSameCourse(
        course: ShootingCourse,
        planDate: java.time.LocalDate
    ): Boolean {
        val signature = course.stops.map { it.spot.contentId }
        if (signature.isEmpty()) return false

        return courseDao.coursesOn(planDate.toEpochDay()).any { saved ->
            saved.orderedStops.map { it.contentId } == signature
        }
    }

    suspend fun delete(courseId: Long) = courseDao.deleteCourse(courseId)

    suspend fun get(courseId: Long): SavedCourseWithStops? = courseDao.getCourse(courseId)

    /**
     * 이 기기에 쌓인 기록을 전부 지웁니다 — 코스·방문·일기·찜·감정.
     *
     * 설정(큰 글씨 모드·글씨 크기·온보딩 여부)은 남깁니다. 기록을 지우겠다는
     * 뜻이 "앱을 처음처럼 되돌려 달라"는 뜻은 아닙니다. 글씨를 크게 맞춰 둔
     * 사람에게 기록 초기화가 글씨까지 되돌려 놓으면 그건 사고입니다.
     *
     * 갤러리의 사진 파일도 남깁니다. 사진은 사용자 것이고 이 앱은 그 주소만
     * 들고 있었을 뿐입니다.
     */
    suspend fun clearAllRecords() {
        courseDao.deleteAllCourses()
        visitDao.deleteAll()
        db.diaryDao().deleteAll()
        AppPrefs.clearRecords(appContext)
    }

    /** "내장산국립공원 외 4곳" 형태의 기본 이름. */
    private fun defaultTitle(course: ShootingCourse): String {
        val head = course.goldenStops.firstOrNull()?.spot?.title
            ?: course.stops.firstOrNull()?.spot?.title
            ?: "출사 코스"
        val rest = course.stops.size - 1
        return if (rest > 0) "$head 외 ${rest}곳" else head
    }

    // ───────────────────────── 방문 기록 ─────────────────────────

    /**
     * 방문 기록 전부, 최근 것부터.
     *
     * 대표 사진이 갤러리에서 지워진 기록은 photoUri 를 비워서 내보냅니다.
     * MY 탭 아카이브가 그 사진을 격자에 올리고, 누르면 포토 프레임이 "사진을
     * 불러오지 못했어요" 를 띄웠습니다(2026-09-04 폰에서 확인). 기록은
     * 그대로 두고 보이는 값만 정리합니다. 앱을 지웠다 다시 깔면 자기가
     * 저장한 사진도 안 보이는데, 그때 기록까지 지우면 되돌릴 수 없습니다.
     */
    fun observeVisits(): Flow<List<VisitLogEntity>> =
        visitDao.observeAll()
            .map { visits ->
                visits.map { v ->
                    val uri = v.photoUri
                    if (uri.isNullOrBlank() || photoExists(uri)) v else v.copy(photoUri = null)
                }
            }
            .flowOn(Dispatchers.IO)

    fun observeVisitedSpotCount(): Flow<Int> = visitDao.observeVisitedSpotCount()

    /** 다녀온 곳의 contentId 집합. 찜 목록이 방문 상태를 붙일 때 씁니다. */
    suspend fun visitedContentIds(): Set<String> = visitDao.visitedContentIds().toSet()

    /**
     * 방문 기록을 남깁니다.
     *
     * 같은 장소를 [DEDUP_WINDOW_MS] 안에 다시 기록하지는 않습니다.
     * 상세 화면을 여러 번 드나들 때마다 기록이 쌓이면 통계가 무의미해집니다.
     *
     * @return 실제로 기록했으면 true
     */
    suspend fun logVisit(
        spot: SpotItem,
        phase: LightPhase,
        photoUri: String? = null
    ): Boolean {
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
                installId = AppPrefs.installId(appContext),
                photoUri = photoUri
            )
        )
        return true
    }

    /**
     * 촬영한 사진을 방문 기록에 붙입니다. 기록이 없으면 새로 만듭니다.
     *
     * "다녀왔어요"는 이 경로로만 채워집니다. 사진을 찍었다는 건 그 자리에
     * 있었다는 뜻이라, 손으로 누르는 체크보다 믿을 만하고 GPS 상시 권한도
     * 필요 없습니다. 상세 화면은 이 기록을 그대로 보여 줄 뿐입니다.
     *
     * @return 새 기록을 만들었으면 true, 기존 기록에 사진만 붙였으면 false
     */
    suspend fun logCapture(spot: SpotItem, phase: LightPhase, photoUri: String): Boolean =
        recordPhoto(
            contentId = spot.contentId,
            title = spot.title,
            phase = phase,
            photoUri = photoUri,
            score = spot.score,
            imageUrl = spot.imageUrl
        )

    /** 한 장소의 방문 기록, 최근 것부터. 촬영 직후 바로 갱신되도록 Flow 입니다. */
    fun observeVisits(contentId: String): Flow<List<VisitLogEntity>> =
        visitDao.observeByContentId(contentId)

    /**
     * 한 장소에서 찍은 사진 중 **갤러리에 아직 있는 것**만, 최근 것부터.
     *
     * 기록에는 갤러리 주소만 있어서, 사용자가 갤러리에서 사진을 지우면 주소가
     * 허공을 가리킵니다. 그걸 그대로 띄우면 회색 자리표시만 나와 "사진이
     * 안 뜬다"로 보입니다(2026-09-04 폰에서 확인: 8월 23일 사진이 지워져
     * 있었음). 그래서 보여 주기 전에 한 장씩 있는지 묻고, 없는 것은 뺍니다.
     *
     * 기록 자체는 지우지 않습니다. 방문했다는 사실은 그대로이고, 사진을
     * 지운 것은 사용자의 선택입니다.
     */
    fun observePhotos(contentId: String): Flow<List<VisitPhotoEntity>> =
        visitDao.observePhotosByContentId(contentId)
            .map { photos -> photos.filter { photoExists(it.uri) } }
            .flowOn(Dispatchers.IO)

    /**
     * 내가 찍은 사진 전부(장소 포함) 가운데 갤러리에 아직 있는 것, 최근 것부터.
     * 코스별 사진 모아보기가 씁니다. 지워진 사진을 거르는 이유는
     * [observePhotos] 와 같습니다.
     */
    fun observeAllPhotos(): Flow<List<VisitPhotoWithPlace>> =
        visitDao.observeAllPhotos()
            .map { photos -> photos.filter { photoExists(it.photo.uri) } }
            .flowOn(Dispatchers.IO)

    /**
     * 사진 한 장을 기록에서 뺍니다.
     *
     * 사진 보기 화면과 상세의 사진 띠가 부릅니다. 갤러리 파일은 화면 쪽에서
     * 따로 지우고(시스템 확인이 필요할 수 있어서), 여기서는 기록만 다룹니다.
     *
     * **마지막 사진이었으면 방문 기록도 지웁니다.** 이 앱에서 "다녀왔어요"의
     * 근거는 사진뿐입니다. 사진을 다 지웠는데 다녀온 곳 목록에 그 줄이 남아
     * 있으면 무엇을 근거로 남았는지 설명할 수 없습니다.
     *
     * 사진이 남아 있고 지운 것이 대표 사진이었으면 남은 사진 중 가장 먼저
     * 찍은 것으로 바꿉니다. 대표 사진은 MY 탭 아카이브와 다녀온 곳 목록이
     * 쓰는데, 그대로 두면 거기서 지운 사진의 빈 자리가 계속 보입니다.
     */
    suspend fun deletePhoto(photoId: Long) {
        val photo = visitDao.getPhoto(photoId) ?: return
        visitDao.deletePhoto(photoId)

        val visit = visitDao.getVisit(photo.visitId) ?: return
        val remaining = visitDao.photosOfVisit(visit.id)
        if (remaining.isEmpty()) {
            visitDao.delete(visit.id)
        } else if (visit.photoUri == photo.uri) {
            visitDao.updateCover(visit.id, remaining.first().uri)
        }
    }

    /** 사진이 다 지워지고 껍데기만 남은 촬영 기록 정리. 앱을 켤 때 한 번 부릅니다. */
    suspend fun pruneEmptyVisits() {
        runCatching { visitDao.deleteOrphans() }
    }

    /**
     * 갤러리에 그 항목이 남아 있는지.
     *
     * 구형 기기에서 권한이 없어 물어볼 수 없으면 있는 것으로 칩니다.
     * 모르는 것과 없는 것은 다르고, 잘못 숨기는 쪽이 더 나쁩니다.
     */
    private fun photoExists(uri: String): Boolean = runCatching {
        appContext.contentResolver.query(
            Uri.parse(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(true)

    /**
     * 시선 가이드 미션에서 찍은 컷을 기록에 붙입니다.
     *
     * [logCapture] 와 달리 [SpotItem] 을 요구하지 않습니다. 미션 화면은
     * 장소 이름과 식별자만 들고 있고, 좌표나 점수는 모릅니다. 사진을
     * 남기자고 없는 값을 지어낼 이유가 없습니다.
     */
    suspend fun attachPhoto(
        contentId: String,
        title: String,
        phase: LightPhase,
        photoUri: String
    ) {
        // 장소 점수는 촬영 화면이 모릅니다. 0 은 "안 잼"이라는 뜻입니다.
        recordPhoto(contentId, title, phase, photoUri, score = 0, imageUrl = "")
    }

    /**
     * 사진 한 장을 기록으로 남기는 실제 작업.
     *
     * 예전에는 방문 기록의 photoUri 한 칸이 전부라, 한 자리에서 세 장을 찍으면
     * 첫 장만 남고 두 장은 앱이 잊었습니다. 지금은 [DEDUP_WINDOW_MS] 안의
     * 최근 방문 기록을 찾아(없으면 만들어) 거기에 사진을 **전부** 쌓습니다.
     * 방문 기록의 photoUri 는 첫 장을 대표로 채워 두는 용도로만 남습니다.
     *
     * @return 새 방문 기록을 만들었으면 true
     */
    private suspend fun recordPhoto(
        contentId: String,
        title: String,
        phase: LightPhase,
        photoUri: String,
        score: Int,
        imageUrl: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val since = now - DEDUP_WINDOW_MS

        val existing = visitDao.latestVisitId(contentId, since)
        val visitId = existing ?: visitDao.insert(
            VisitLogEntity(
                contentId = contentId,
                title = title,
                visitedAt = now,
                score = score,
                imageUrl = imageUrl,
                phaseName = phase.name,
                installId = AppPrefs.installId(appContext),
                photoUri = photoUri
            )
        )
        // 이미 있던 기록에 대표 사진이 비어 있으면 이 장으로 채웁니다.
        if (existing != null) visitDao.attachPhoto(contentId, photoUri, since)

        visitDao.insertPhoto(VisitPhotoEntity(visitId = visitId, uri = photoUri, takenAt = now))
        return existing == null
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

private fun Int.toLocalTimeOrNull(): LocalTime? =
    if (this < 0) null else LocalTime.of(this / 60, this % 60)

/** 저장된 정거장 → 도메인 모델. */
fun SavedStopEntity.toCourseStop(): CourseStop {
    val facts = facts()
    val arrive = arriveTime()
    return CourseStop(
        spot = toSpotItem(),
        facts = facts,
        arriveAt = arrive,
        leaveAt = arrive.plusMinutes(stayMinutes.toLong()),
        phase = phase(),
        travelMinutes = travelMinutes,
        travelKm = travelKm,
        reason = reason,
        isHighlight = isHighlight
    )
}

/**
 * 저장한 코스 → 도메인 모델.
 *
 * 저장할 때 스팟 정보를 비정규화해 둔 덕분에 API 재호출 없이 그대로 복원됩니다.
 * 코스 결과 화면이 '방금 만든 코스'와 '저장한 코스'를 같은 코드로 그릴 수 있습니다.
 */
fun SavedCourseWithStops.toShootingCourse(): ShootingCourse = ShootingCourse(
    stops = orderedStops.map { it.toCourseStop() },
    sun = SunTimes(
        sunrise = course.sunriseMinute.toLocalTimeOrNull(),
        sunset = course.sunsetMinute.toLocalTimeOrNull()
    ),
    travelMode = runCatching { TravelMode.valueOf(course.travelMode) }
        .getOrDefault(TravelMode.CAR),
    totalDistanceKm = course.totalKm
)

/** 출사 예정일. 옛 데이터(0)는 저장한 날로 봅니다. */
fun SavedCourseEntity.planDate(): java.time.LocalDate =
    if (planDateEpochDay > 0) java.time.LocalDate.ofEpochDay(planDateEpochDay)
    else java.time.Instant.ofEpochMilli(createdAt)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
