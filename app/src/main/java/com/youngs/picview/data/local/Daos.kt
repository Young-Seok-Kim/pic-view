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
    suspend fun insert(log: VisitLogEntity)

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
     * 이미 남아 있는 방문 기록에 사진을 붙입니다.
     *
     * 촬영 화면에서 셔터를 누르면 그 장소의 가장 최근 기록을 찾아 채웁니다.
     * 사진이 이미 있으면 덮지 않습니다. 한 장소에서 여러 장을 찍었을 때
     * 첫 장(대개 가장 공들인 것)이 남는 편이 자연스럽습니다.
     */
    @Query("""
        UPDATE visit_log SET photoUri = :uri
        WHERE id = (
            SELECT id FROM visit_log
            WHERE contentId = :contentId AND photoUri IS NULL
            ORDER BY visitedAt DESC LIMIT 1
        )
    """)
    suspend fun attachPhoto(contentId: String, uri: String): Int

    /** 같은 장소를 짧은 시간 안에 여러 번 기록하지 않도록 확인용. */
    @Query(
        "SELECT COUNT(*) FROM visit_log WHERE contentId = :contentId AND visitedAt >= :since"
    )
    suspend fun countRecent(contentId: String, since: Long): Int

    @Query("DELETE FROM visit_log WHERE id = :id")
    suspend fun delete(id: Long)

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
