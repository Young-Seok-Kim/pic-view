package com.youngs.picview.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** 코스 + 정거장을 한 번에 읽어옵니다. */
data class SavedCourseWithStops(
    @Embedded val course: SavedCourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val stops: List<SavedStopEntity>
) {
    /** Room 은 정렬을 보장하지 않으므로 읽는 쪽에서 순서를 맞춥니다. */
    val orderedStops: List<SavedStopEntity> get() = stops.sortedBy { it.sortOrder }
}

@Dao
interface CourseDao {

    @Insert
    suspend fun insertCourse(course: SavedCourseEntity): Long

    @Insert
    suspend fun insertStops(stops: List<SavedStopEntity>)

    @Transaction
    suspend fun saveCourse(course: SavedCourseEntity, stops: List<SavedStopEntity>): Long {
        val id = insertCourse(course)
        insertStops(stops.map { it.copy(courseId = id) })
        return id
    }

    @Transaction
    @Query("SELECT * FROM saved_course ORDER BY createdAt DESC")
    fun observeCourses(): Flow<List<SavedCourseWithStops>>

    @Transaction
    @Query("SELECT * FROM saved_course WHERE id = :id")
    suspend fun getCourse(id: Long): SavedCourseWithStops?

    @Query("DELETE FROM saved_course WHERE id = :id")
    suspend fun deleteCourse(id: Long)

    @Query("SELECT COUNT(*) FROM saved_course")
    fun observeCourseCount(): Flow<Int>

    /** 그 날짜에 저장된 코스들. 같은 코스를 두 번 저장하지 않으려고 씁니다. */
    @Transaction
    @Query("SELECT * FROM saved_course WHERE planDateEpochDay = :epochDay")
    suspend fun coursesOn(epochDay: Long): List<SavedCourseWithStops>

    /** 저장한 코스 전부. 정거장은 외래키 CASCADE 로 함께 지워집니다. */
    @Query("DELETE FROM saved_course")
    suspend fun deleteAllCourses()
}

@Dao
interface DiaryDao {

    /** 같은 날짜에 다시 만들면 덮어씁니다(다시 만들기 지원). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(diary: DiaryEntity)

    @Query("SELECT * FROM diary ORDER BY dateKey DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diary WHERE dateKey = :dateKey LIMIT 1")
    suspend fun byDate(dateKey: String): DiaryEntity?

    @Query("DELETE FROM diary WHERE dateKey = :dateKey")
    suspend fun delete(dateKey: String)

    @Query("DELETE FROM diary")
    suspend fun deleteAll()
}

@Dao
interface VisitDao {

    @Insert
    suspend fun insert(log: VisitLogEntity): Long

    @Insert
    suspend fun insertPhoto(photo: VisitPhotoEntity)

    /**
     * 한 장소에서 찍은 사진 전부, 최근 것부터.
     *
     * 방문 기록이 여러 건이어도 장소 하나로 묶어서 봅니다. 상세 화면의
     * "내가 찍은 사진"이 이걸 봅니다.
     */
    @Query("""
        SELECT visit_photo.* FROM visit_photo
        INNER JOIN visit_log ON visit_photo.visitId = visit_log.id
        WHERE visit_log.contentId = :contentId
        ORDER BY visit_photo.takenAt DESC
    """)
    fun observePhotosByContentId(contentId: String): Flow<List<VisitPhotoEntity>>

    @Query("SELECT * FROM visit_photo WHERE id = :id")
    suspend fun getPhoto(id: Long): VisitPhotoEntity?

    @Query("DELETE FROM visit_photo WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    /** 한 방문 기록에 남은 사진, 오래된 것부터. 대표 사진을 고를 때 씁니다. */
    @Query("SELECT * FROM visit_photo WHERE visitId = :visitId ORDER BY takenAt ASC")
    suspend fun photosOfVisit(visitId: Long): List<VisitPhotoEntity>

    @Query("SELECT * FROM visit_log WHERE id = :id")
    suspend fun getVisit(id: Long): VisitLogEntity?

    /** 방문 기록의 대표 사진을 바꿉니다. 남은 사진이 없으면 null 로 비웁니다. */
    @Query("UPDATE visit_log SET photoUri = :uri WHERE id = :visitId")
    suspend fun updateCover(visitId: Long, uri: String?)

    /** [since] 이후의 그 장소 최근 방문 기록 id. 사진을 붙일 곳을 찾는 데 씁니다. */
    @Query("""
        SELECT id FROM visit_log
        WHERE contentId = :contentId AND visitedAt >= :since
        ORDER BY visitedAt DESC LIMIT 1
    """)
    suspend fun latestVisitId(contentId: String, since: Long): Long?

    @Query("SELECT * FROM visit_log ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<VisitLogEntity>>

    @Query("SELECT * FROM visit_log WHERE visitedAt >= :since ORDER BY visitedAt ASC")
    suspend fun since(since: Long): List<VisitLogEntity>

    @Query("SELECT COUNT(DISTINCT contentId) FROM visit_log")
    fun observeVisitedSpotCount(): Flow<Int>

    /** 다녀온 곳의 contentId. 찜 목록의 '다녀옴 / 아직' 배지에 씁니다. */
    @Query("SELECT DISTINCT contentId FROM visit_log")
    suspend fun visitedContentIds(): List<String>

    /**
     * 한 장소의 방문 기록, 최근 것부터.
     *
     * 상세 화면의 "다녀왔어요"가 이걸 봅니다. Flow 라서 촬영 화면에서 찍고
     * 돌아오는 순간 표시가 저절로 바뀝니다.
     */
    @Query("SELECT * FROM visit_log WHERE contentId = :contentId ORDER BY visitedAt DESC")
    fun observeByContentId(contentId: String): Flow<List<VisitLogEntity>>


    /**
     * 이미 남아 있는 방문 기록에 대표 사진을 붙입니다.
     *
     * 촬영 화면에서 셔터를 누르면 그 장소의 가장 최근 기록을 찾아 채웁니다.
     * 사진이 이미 있으면 덮지 않습니다. 한 장소에서 여러 장을 찍었을 때
     * 첫 장(대개 가장 공들인 것)이 대표로 남는 편이 자연스럽습니다.
     * 나머지 장들은 [VisitPhotoEntity] 에 전부 쌓입니다.
     *
     * [since] 이전의 기록에는 붙이지 않습니다. 몇 주 전 기록에 오늘 사진이
     * 붙으면 일기에서 그 사진이 옛날 날짜로 들어가고 이달 촬영 수에서도
     * 빠집니다. 그런 경우엔 오늘 기록을 새로 만드는 게 맞습니다.
     */
    @Query("""
        UPDATE visit_log SET photoUri = :uri
        WHERE id = (
            SELECT id FROM visit_log
            WHERE contentId = :contentId AND photoUri IS NULL AND visitedAt >= :since
            ORDER BY visitedAt DESC LIMIT 1
        )
    """)
    suspend fun attachPhoto(contentId: String, uri: String, since: Long): Int

    /** 같은 장소를 짧은 시간 안에 여러 번 기록하지 않도록 확인용. */
    @Query(
        "SELECT COUNT(*) FROM visit_log WHERE contentId = :contentId AND visitedAt >= :since"
    )
    suspend fun countRecent(contentId: String, since: Long): Int

    @Query("DELETE FROM visit_log WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * 사진이 하나도 안 남은 촬영 기록을 지웁니다.
     *
     * 사진을 지워도 방문 줄이 남던 시절에 생긴 빈 기록을 정리합니다.
     * imageUrl 이 빈 것만 고릅니다. 촬영으로 만든 기록은 장소 사진을 안
     * 받아 두므로 imageUrl 이 비어 있고, 옛날 수동 체크로 만든 기록은 장소
     * 사진이 들어 있어 이 조건에 안 걸립니다. 그 기록은 사진이 없는 게
     * 정상이라 지우면 안 됩니다.
     */
    @Query("""
        DELETE FROM visit_log
        WHERE photoUri IS NULL AND imageUrl = ''
          AND id NOT IN (SELECT visitId FROM visit_photo)
    """)
    suspend fun deleteOrphans(): Int

    /**
     * 방문 기록 전부.
     *
     * 사진 파일은 건드리지 않습니다. [VisitLogEntity.photoUri] 는 갤러리에
     * 있는 사진을 가리키는 주소일 뿐이라, 기록을 지운다고 사용자가 찍은
     * 사진까지 지우면 앱이 월권을 하는 셈입니다.
     */
    @Query("DELETE FROM visit_log")
    suspend fun deleteAll()
}
