package com.youngs.picview.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        VisitPhotoEntity::class,
        DiaryEntity::class,
        PhotoAnalysisEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class PicViewDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun visitDao(): VisitDao
    abstract fun diaryDao(): DiaryDao
    abstract fun photoAnalysisDao(): PhotoAnalysisDao

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
            // 4 → 5 는 방문 기록·일기가 이미 쌓인 뒤의 변경이라 정식으로
            // 옮깁니다. 그보다 옛 버전은 다시 만들 수 있는 데이터뿐이라
            // 초기화합니다.
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()

        /**
         * 사진 표 추가.
         *
         * 방문 기록에 이미 붙어 있던 대표 사진을 새 표로 옮겨 둡니다.
         * 그래야 업데이트 전에 찍은 사진도 상세 화면에서 보입니다.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `visit_photo` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`visitId` INTEGER NOT NULL, " +
                        "`uri` TEXT NOT NULL, " +
                        "`takenAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`visitId`) REFERENCES `visit_log`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_visit_photo_visitId` " +
                        "ON `visit_photo` (`visitId`)"
                )
                db.execSQL(
                    "INSERT INTO visit_photo (visitId, uri, takenAt) " +
                        "SELECT id, photoUri, visitedAt FROM visit_log " +
                        "WHERE photoUri IS NOT NULL AND photoUri != ''"
                )
            }
        }

        /**
         * 사진 읽기 결과 표 추가. 옮길 데이터는 없습니다 — 업데이트 전에
         * 찍은 사진은 MY 탭을 열 때 새로 읽습니다.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photo_analysis` (" +
                        "`uri` TEXT NOT NULL, " +
                        "`reflection` INTEGER NOT NULL, " +
                        "`silhouette` INTEGER NOT NULL, " +
                        "`waterside` INTEGER NOT NULL, " +
                        "`sunset` INTEGER NOT NULL, " +
                        "`labels` TEXT NOT NULL, " +
                        "`version` INTEGER NOT NULL, " +
                        "`analyzedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`uri`))"
                )
            }
        }
    }
}
