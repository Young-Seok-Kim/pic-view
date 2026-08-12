package com.youngs.picview.domain.course

import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.domain.spot.SpotFacts
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.TravelMode
import java.time.LocalTime

/** 코스 한 정거장. 언제 도착해 어떤 빛에서 무엇을 찍는지. */
data class CourseStop(
    val spot: SpotItem,
    val facts: SpotFacts,
    val arriveAt: LocalTime,
    val leaveAt: LocalTime,
    val phase: LightPhase,
    /** 직전 스팟에서 여기까지 이동 시간(분). 첫 스팟은 0. */
    val travelMinutes: Int,
    val travelKm: Double,
    /** 왜 이 시각에 여기인지. 화면에 그대로 노출합니다. */
    val reason: String,
    /** 골든아워 앵커로 잡힌 스팟인지. 타임라인에서 강조합니다. */
    val isHighlight: Boolean
)

/** 완성된 출사 코스. */
data class ShootingCourse(
    val stops: List<CourseStop>,
    val sun: SunTimes,
    val travelMode: TravelMode,
    val totalDistanceKm: Double
) {
    val isEmpty: Boolean get() = stops.isEmpty()

    val startTime: LocalTime? get() = stops.firstOrNull()?.arriveAt
    val endTime: LocalTime? get() = stops.lastOrNull()?.leaveAt

    val totalMinutes: Int
        get() {
            val s = startTime ?: return 0
            val e = endTime ?: return 0
            return java.time.Duration.between(s, e).toMinutes().toInt()
        }

    val goldenStops: List<CourseStop> get() = stops.filter { it.phase.isGolden }

    /**
     * 코스 요약 한 줄. LLM 이 붙기 전에도 화면이 완성돼 보이도록
     * 규칙만으로 만들어 두는 폴백 문구입니다.
     * LLM 이 연결되면 이 문장을 더 자연스러운 표현으로 교체합니다.
     */
    fun fallbackSummary(): String {
        if (isEmpty) return "조건에 맞는 촬영지를 찾지 못했어요"

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val duration = buildString {
            if (hours > 0) append("${hours}시간")
            if (minutes > 0) {
                if (hours > 0) append(" ")
                append("${minutes}분")
            }
        }

        val golden = goldenStops.firstOrNull()
        val goldenPart = golden?.let {
            " · ${it.spot.title}에서 ${it.phase.label}"
        }.orEmpty()

        return "${stops.size}곳 · $duration · ${"%.0f".format(totalDistanceKm)}km$goldenPart"
    }
}

/** 코스 생성 조건. 코스 입력 화면의 값이 그대로 들어옵니다. */
data class CourseRequest(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val travelMode: TravelMode,
    /** 담고 싶은 피사체. 비어 있으면 전체. */
    val subjects: Set<Subject> = emptySet(),
    val maxStops: Int = 5,
    /**
     * 인원 구성에 따른 체류 시간 배수.
     *
     * 혼자면 1.0, 가족이면 1.35 처럼 늘어납니다. 여럿이 움직이면 한 곳에
     * 머무는 시간이 길어져 같은 시간에 도는 곳이 줄어듭니다. 배치 규칙 자체는
     * 건드리지 않고 체류 시간에만 곱해, 빛 슬롯 계산은 그대로 둡니다.
     */
    val stayFactor: Double = 1.0
) {
    companion object {
        /** "지금부터 일몰까지" 같은 기본 반나절 코스. */
        fun halfDayFrom(now: LocalTime, mode: TravelMode = TravelMode.CAR) = CourseRequest(
            startTime = now,
            endTime = now.plusHours(5).coerceAtMost(LocalTime.of(21, 0)),
            travelMode = mode,
            maxStops = 4
        )
    }
}

/**
 * 담고 싶은 피사체.
 * 일반 여행 앱의 "스타일 태그(#맛집 #카페)"를 출사 기준으로 바꾼 축입니다.
 */
enum class Subject(val label: String, val contentTypeIds: Set<String>) {
    LANDSCAPE("풍경", setOf("12", "28")),
    ARCHITECTURE("건축·문화재", setOf("14")),
    FOOD("맛집", setOf("39")),
    NIGHT("야경", setOf("12", "14")),

    // 시안의 스타일 태그를 맞춘 항목들.
    // 관광공사 분류가 12·14·28·39 넷뿐이라 유형이 겹치는 태그가 생기지만,
    // 고른 태그의 합집합으로 후보를 좁히므로 조합에 따라 결과는 달라집니다.
    NATURE("자연", setOf("12")),
    HEALING("힐링", setOf("12", "14")),
    HISTORY("역사", setOf("14")),
    ACTIVITY("체험", setOf("28"))
}
