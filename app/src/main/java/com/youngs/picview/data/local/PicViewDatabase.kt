package com.youngs.picview.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 앱 로컬 DB.
 *
 * 저장하는 건 **사용자가 만든 것**뿐입니다 — 저장한 코스와 방문 기록.
 * 관광공사 관광 정보 자체는 매번 API 로 받아옵니다(공모전 규정).
 */
@Database(
    entities = [
        SavedCourseEntity::class,
        SavedStopEntity::class,
        VisitLogEntity::class,
        DiaryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class PicViewDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun visitDao(): VisitDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var instance: PicViewDatabase? = null

        fun get(context: Context): PicViewDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context) = Room.databaseBuilder(
            context.applicationContext,
            PicViewDatabase::class.java,
            "picview.db"
        )
            // 저장한 코스는 다시 만들 수 있는 데이터라, 스키마가 바뀌면
            // 마이그레이션을 쓰기보다 초기화하는 편이 실수 여지가 적습니다.
            // 출시 후 실사용 데이터가 쌓이면 정식 Migration 으로 바꿔야 합니다.
            .fallbackToDestructiveMigration()
            .build()
    }
}
