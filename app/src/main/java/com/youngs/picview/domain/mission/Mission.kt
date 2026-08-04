package com.youngs.picview.domain.mission

import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.domain.light.LightPhase

/**
 * 출사 미션.
 *
 * 스펙 §16-5 ⑦ 의 "AI 여행 미션 & 배지"를 출사 기준으로 옮긴 것입니다.
 * 원안은 "쌍화차 인증" 같은 단순 방문 미션이었는데, 여기서는 **빛 조건**을
 * 조건에 넣었습니다. 그래야 "언제 가느냐"가 의미를 갖고, 이 앱이
 * 스탬프 앱이 아니라 출사 앱으로 남습니다.
 *
 * 판정은 전부 [VisitLogEntity] 로 합니다. 별도 저장이 필요 없고,
 * 방문 기록이 쌓이면 미션은 자동으로 채워집니다.
 */
data class Mission(
    val id: String,
    val title: String,
    /** 무엇을 해야 하는지 한 줄. */
    val requirement: String,
    val emoji: String,
    /** 완료 시 받는 배지 이름. */
    val badge: String,
    /** 목표 횟수. 1이면 한 번만 하면 됩니다. */
    val target: Int,
    /** 이 방문이 미션에 해당하는지 판정. */
    val matcher: (VisitLogEntity) -> Boolean
) {
    /** 진행 상황 계산. */
    fun progressOf(visits: List<VisitLogEntity>): MissionProgress {
        val matched = visits.filter(matcher)
        // 같은 장소를 여러 번 가도 한 번으로 셉니다. 미션이 반복 체크인으로
        // 뚫리면 배지가 의미를 잃습니다.
        val distinct = matched.distinctBy { it.contentId }
        return MissionProgress(
            mission = this,
            current = distinct.size.coerceAtMost(target),
            completedAt = if (distinct.size >= target) {
                matched.map { it.visitedAt }.sorted().getOrNull(target - 1)
            } else null
        )
    }
}

data class MissionProgress(
    val mission: Mission,
    val current: Int,
    /** 완료 시각(epoch millis). 미완료면 null. */
    val completedAt: Long?
) {
    val isComplete: Boolean get() = completedAt != null
    val target: Int get() = mission.target
    val ratio: Float get() = if (target == 0) 0f else current.toFloat() / target
}

/**
 * 미션 목록.
 *
 * 장소 이름은 관광공사 API 응답의 title 로 매칭합니다.
 * 정읍 대표 스팟은 스펙 §13 이 꼽은 전국구 콘텐츠 기준입니다.
 */
object Missions {

    private fun phaseOf(visit: VisitLogEntity): LightPhase? =
        runCatching { LightPhase.valueOf(visit.phaseName) }.getOrNull()

    /**
     * 유산 미션 대상. 부분 일치로 걸릴 만한 동명 시설과 겹치지 않게
     * 충분히 긴 이름으로 적습니다. ("정읍사" 만 쓰면 정읍사예술회관이 걸립니다)
     */
    private val HERITAGE_SITES = listOf(
        "무성서원",
        "피향정",
        "정읍사문화공원",
        "정읍사 문화공원"
    )

    val ALL: List<Mission> = listOf(
        Mission(
            id = "golden_hour",
            title = "골든아워 사냥꾼",
            requirement = "일출 또는 일몰 골든아워에 세 곳에서 촬영하기",
            emoji = "🌇",
            badge = "황금빛 수집가",
            target = 3,
            matcher = { phaseOf(it)?.isGolden == true }
        ),
        Mission(
            id = "blue_hour",
            title = "푸른 시간",
            requirement = "블루아워(해 뜨기 전 또는 진 직후)에 한 곳 담기",
            emoji = "🌌",
            badge = "블루아워 관찰자",
            target = 1,
            matcher = {
                phaseOf(it) == LightPhase.BLUE_DAWN || phaseOf(it) == LightPhase.BLUE_DUSK
            }
        ),
        Mission(
            id = "naejangsan",
            title = "내장산 정복",
            requirement = "내장산국립공원에서 촬영하기",
            emoji = "🍁",
            badge = "단풍 1번지",
            target = 1,
            matcher = { it.title.contains("내장산") }
        ),
        Mission(
            id = "heritage",
            title = "정읍의 유산",
            requirement = "무성서원·피향정·정읍사 문화공원 중 두 곳 담기",
            emoji = "🏛",
            badge = "유산 기록자",
            target = 2,
            /*
             * "정읍사" 로만 매칭하면 정읍사예술회관(공연장)까지 유산으로 잡힙니다.
             * 실제로 테스트 중에 그렇게 오판정됐습니다.
             * 정읍 지명은 상당수가 "정읍○○" 형태라 부분 일치가 위험합니다.
             */
            matcher = { visit ->
                HERITAGE_SITES.any { visit.title.contains(it) }
            }
        ),
        Mission(
            id = "high_score",
            title = "고득점 촬영",
            requirement = "포토스코어 80점 이상인 곳 세 군데 다녀오기",
            emoji = "⭐",
            badge = "안목 있는 사진가",
            target = 3,
            matcher = { it.score >= 80 }
        ),
        Mission(
            id = "explorer",
            title = "정읍 탐험가",
            requirement = "서로 다른 촬영지 열 곳 다녀오기",
            emoji = "🧭",
            badge = "정읍 완주",
            target = 10,
            matcher = { true }
        )
    )

    fun progress(visits: List<VisitLogEntity>): List<MissionProgress> =
        ALL.map { it.progressOf(visits) }
            // 완료한 것 → 진행 중 → 시작 전 순으로 보여 줍니다.
            .sortedWith(
                compareByDescending<MissionProgress> { it.isComplete }
                    .thenByDescending { it.ratio }
            )
}
