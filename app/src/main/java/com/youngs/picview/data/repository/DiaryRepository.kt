package com.youngs.picview.data.repository

import android.content.Context
import com.youngs.picview.data.local.DiaryEntity
import com.youngs.picview.data.local.PicViewDatabase
import com.youngs.picview.domain.diary.DiaryDay
import com.youngs.picview.domain.diary.DiaryWriters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 출사 기록(일기).
 *
 * 방문 로그는 [CourseRepository.logVisit] 이 쌓고, 여기서는 그걸 날짜로 묶어
 * 일기와 짝지어 내보냅니다.
 */
class DiaryRepository(context: Context) {

    private val db = PicViewDatabase.get(context.applicationContext)
    private val visitDao = db.visitDao()
    private val diaryDao = db.diaryDao()

    /** 방문 기록과 일기를 합쳐 날짜별로 묶어 흘려보냅니다. */
    fun observeDays(): Flow<List<DiaryDay>> =
        combine(visitDao.observeAll(), diaryDao.observeAll()) { visits, diaries ->
            DiaryDay.group(visits, diaries)
        }

    /**
     * 그날 일기를 만들어 저장합니다. 이미 있으면 덮어씁니다(다시 만들기).
     *
     * @return 생성된 일기. 방문 기록이 없으면 null.
     */
    suspend fun generate(day: DiaryDay): DiaryEntity? {
        if (day.visits.isEmpty()) return null

        val draft = DiaryWriters.default.write(day)
        val entity = DiaryEntity(
            dateKey = day.dateKey,
            title = draft.title,
            body = draft.body,
            generatedByLlm = draft.generatedByLlm,
            createdAt = System.currentTimeMillis()
        )
        diaryDao.upsert(entity)
        return entity
    }

    suspend fun delete(dateKey: String) = diaryDao.delete(dateKey)
}
