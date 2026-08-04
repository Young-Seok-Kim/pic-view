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

    /** 같은 장소를 짧은 시간 안에 여러 번 기록하지 않도록 확인용. */
    @Query(
        "SELECT COUNT(*) FROM visit_log WHERE contentId = :contentId AND visitedAt >= :since"
    )
    suspend fun countRecent(contentId: String, since: Long): Int

    @Query("DELETE FROM visit_log WHERE id = :id")
    suspend fun delete(id: Long)
}
