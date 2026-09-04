package com.youngs.picview.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 저장한 출사 코스.
 *
 * 스팟 정보를 비정규화해서 함께 저장합니다(제목·주소·사진 URL).
 * 관광공사 API 를 다시 부르지 않아도 저장한 코스가 그대로 보이게 하기 위해서입니다.
 * 공모전 규정상 금지된 건 "관광 정보를 DB 로 캐싱해 API 대신 쓰는 것"이고,
 * 사용자가 명시적으로 저장한 자기 기록은 여기 해당하지 않습니다.
 */
@Entity(tableName = "saved_course")
data class SavedCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 저장 시각(epoch millis) */
    val createdAt: Long,
    /** 코스 이름. 기본값은 대표 스팟 이름에서 만듭니다. */
    val title: String,
    /** 요약 문구(규칙 또는 LLM 생성분) */
    val summary: String,
    val totalKm: Double,
    val totalMinutes: Int,
    val travelMode: String,
    /** 그날의 일출·일몰(자정 기준 분). 없으면 -1 */
    val sunriseMinute: Int,
    val sunsetMinute: Int,
    /**
     * 출사 예정일(epoch day). 0 이면 옛 데이터 — 저장한 날로 봅니다.
     * 코스 탭이 "오늘의 출사 계획"을 가려내는 데 씁니다.
     */
    val planDateEpochDay: Long = 0
)

/** 저장한 코스의 정거장 하나. */
@Entity(
    tableName = "saved_stop",
    foreignKeys = [
        ForeignKey(
            entity = SavedCourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            // 코스를 지우면 정거장도 같이 지워집니다.
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("courseId")]
)
data class SavedStopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val sortOrder: Int,

    val contentId: String,
    val contentTypeId: String?,
    val title: String,
    val addr1: String,
    val imageUrl: String,
    val mapx: String,
    val mapy: String,
    val score: Int,

    /** 도착 시각(자정 기준 분) */
    val arriveMinute: Int,
    val stayMinutes: Int,
    val travelMinutes: Int,
    val travelKm: Double,

    val phaseName: String,
    val facingName: String,
    val guideName: String,
    val reason: String,
    val isHighlight: Boolean
)

/**
 * 방문 기록(익명 체크인).
 *
 * 로그인이 없으므로 사용자 식별자 대신 설치 UUID 하나만 붙습니다.
 * 기획서의 B2G 대시보드가 쓰는 "익명 비식별 집계"의 원천 데이터이고,
 * 출사 기록(일기) 화면의 재료이기도 합니다.
 */
@Entity(tableName = "visit_log", indices = [Index("visitedAt")])
data class VisitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,
    val title: String,
    /** 방문(체크인) 시각 epoch millis */
    val visitedAt: Long,
    val score: Int,
    val imageUrl: String,
    /** 그때의 빛 구간. 나중에 "일몰에 몇 번 나갔나" 같은 통계에 씁니다. */
    val phaseName: String,
    /** 익명 설치 식별자. 개인정보가 아니며 앱 삭제 시 사라집니다. */
    val installId: String,
    /**
     * 이곳에서 직접 찍은 사진의 MediaStore URI.
     *
     * 촬영 화면에서 셔터를 누르면 채워집니다. 사진 자체는 갤러리에 두고
     * 여기에는 가리키는 주소만 둡니다. 앱이 사진을 복사해 보관하면 용량이
     * 두 배가 되고, 갤러리에서 지운 사진이 앱에만 남는 문제가 생깁니다.
     */
    val photoUri: String? = null
)

/**
 * 한 방문에서 찍은 사진 한 장.
 *
 * [VisitLogEntity.photoUri] 는 칸이 하나라 첫 장만 남고, 같은 자리에서 더
 * 찍은 사진은 갤러리에만 있고 앱은 몰랐습니다. 그래서 상세 화면이 "다녀왔어요"
 * 까지는 말해도 **무엇을 찍었는지**는 보여 주지 못했습니다. 사진은 여기에
 * 전부 쌓고, 방문 기록의 photoUri 는 대표 사진(첫 장)으로만 씁니다.
 *
 * 방문 기록이 지워지면 함께 지워집니다. 사진 파일은 갤러리에 그대로 있습니다.
 */
@Entity(
    tableName = "visit_photo",
    foreignKeys = [
        ForeignKey(
            entity = VisitLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("visitId")]
)
data class VisitPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    /** 갤러리(MediaStore) 주소 */
    val uri: String,
    /** 찍은 시각 epoch millis */
    val takenAt: Long
)
