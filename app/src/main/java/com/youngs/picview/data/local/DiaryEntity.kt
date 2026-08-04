package com.youngs.picview.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 하루치 출사 일기.
 *
 * 방문 기록([VisitLogEntity])은 자동으로 쌓이고, 일기는 사용자가
 * "일기 만들기"를 눌렀을 때만 생성·저장합니다.
 * 날짜 하나당 하나이므로 [dateKey] 에 유니크 인덱스를 겁니다.
 */
@Entity(
    tableName = "diary",
    indices = [Index(value = ["dateKey"], unique = true)]
)
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "2026-08-04" 형태. 하루 단위 묶음의 키입니다. */
    val dateKey: String,
    val title: String,
    val body: String,
    /** LLM 이 만들었는지, 템플릿 폴백인지. 화면에 표시하지는 않고 통계·디버깅용. */
    val generatedByLlm: Boolean,
    val createdAt: Long
)
