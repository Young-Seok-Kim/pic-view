package com.youngs.picview.domain.mission

import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.season.Season
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 출사 미션.
 *
 * 처음에는 방문 수를 셌습니다. `다녀왔어요` 를 누르면 기록이 쌓이고 미션은
 * 그 개수를 셀 뿐이었습니다. 그런 미션은 어느 관광 앱에나 있고, 이 앱이
 * 계산해 온 빛 데이터를 하나도 쓰지 않습니다.
 *
 * 지금은 **장면을 셉니다.** 조건이 "세 곳을 다녀오기"가 아니라 "황금시간에
 * 포토스코어 70점 이상으로 **찍은 사진** 두 장"입니다. 방문은 앱이 대신
 * 눌러 줄 수 있지만 사진은 사람이 찍어야 남습니다.
 *
 * 판정은 [VisitLogEntity] 로 합니다. 미션마다 세는 방식이 달라서(같은 장소를
 * 두 시간대에 찍었는지, 같은 계절에 몇 장인지) 단순 필터로는 부족합니다.
 * 그래서 미션이 세는 함수를 직접 들고 있습니다.
 *
 * @param places  이 미션을 수행할 수 있는 장소 이름 조각. 비어 있으면
 *                어디서나 가능합니다. 장소 상세 화면이 이 값으로 거릅니다.
 * @param countOf 조건을 얼마나 채웠는지 세는 함수.
 */
data class Mission(
    val id: String,
    val type: MissionType,
    val title: String,
    /** 무엇을 해야 하는지 한 줄. */
    val requirement: String,
    /** 진행 중일 때 다음 행동을 일러 주는 한 줄. */
    val nextHint: String,
    val target: Int,
    val places: List<String> = emptyList(),
    val countOf: (List<VisitLogEntity>) -> Int
) {
    /** 이 미션을 [spotTitle] 장소에서 할 수 있는지. */
    fun availableAt(spotTitle: String): Boolean =
        places.isEmpty() || places.any { spotTitle.contains(it) }

    fun progressOf(visits: List<VisitLogEntity>): MissionProgress {
        val current = countOf(visits).coerceAtMost(target)
        return MissionProgress(
            mission = this,
            current = current,
            completedAt = if (current >= target) {
                visits.maxOfOrNull { it.visitedAt }
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

    /**
     * 카드 아래에 붙는 한 줄.
     *
     * 완료했으면 그 유형의 완료 문구를, 아직이면 다음에 무엇을 하면 되는지를
     * 말합니다. 조건을 그대로 되풀이하면 이미 읽은 문장을 두 번 읽게 됩니다.
     */
    val statusLine: String
        get() = if (isComplete) mission.type.doneVerb else mission.nextHint
}

/**
 * 미션 목록.
 *
 * 조건은 **실제로 판정할 수 있는 것만** 씁니다. 화면에 조건을 적어 놓고
 * 영원히 안 채워지는 미션은 없느니만 못합니다.
 *
 * 사진 내용은 읽지 못합니다(반사·그림자·색). 그래서 "물에 비친 정읍" 같은
 * 미션은 피사체가 아니라 **장소**로 조건을 잡습니다. 연못·저수지가 있는
 * 곳에서 찍었다면 물을 봤을 가능성이 높다 — 이 정도가 정직한 선입니다.
 * 사진을 판독한 척하지 않습니다.
 */
object Missions {

    private fun phaseOf(visit: VisitLogEntity): LightPhase? =
        runCatching { LightPhase.valueOf(visit.phaseName) }.getOrNull()

    private fun timeOf(visit: VisitLogEntity): LocalDateTime =
        Instant.ofEpochMilli(visit.visitedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()

    /** 직접 찍은 사진이 있는 기록만. 방문만 한 것과 가르는 기준입니다. */
    private fun List<VisitLogEntity>.withPhoto() = filter { !it.photoUri.isNullOrBlank() }

    /**
     * 물이 있는 곳.
     *
     * 관광공사 분류에 "수변"이 없어서 이름으로 거릅니다. 정읍의 수변
     * 촬영지는 대부분 이름에 물이 들어갑니다(옥정호·내장저수지·용추폭포).
     */
    private val WATER_PLACES = listOf(
        "옥정호", "저수지", "연못", "폭포", "호수", "지방정원", "피향정"
    )

    private val HERITAGE_SITES = listOf("무성서원", "피향정", "정읍사문화공원", "정읍사 문화공원")

    val ALL: List<Mission> = listOf(

        Mission(
            id = "light_hunter",
            type = MissionType.LIGHT,
            title = "오늘의 빛 사냥꾼",
            requirement = "황금시간에 포토스코어 70점 이상인 곳에서 사진 2장 남기기",
            nextHint = "다음 황금시간에 한 장 더 남겨보세요",
            target = 2,
            countOf = { visits ->
                visits.withPhoto().count { phaseOf(it)?.isGolden == true && it.score >= 70 }
            }
        ),

        Mission(
            id = "water_mirror",
            type = MissionType.WEATHER,
            title = "물에 비친 정읍",
            requirement = "물가에서 반사가 살아나는 장면 3장 남기기",
            nextHint = "바람이 잔잔한 이른 아침에 수면이 가장 잘 비칩니다",
            target = 3,
            places = WATER_PLACES,
            countOf = { visits ->
                visits.withPhoto().count { visit ->
                    WATER_PLACES.any { visit.title.contains(it) }
                }
            }
        ),

        Mission(
            id = "morning_evening",
            type = MissionType.COMPARE,
            title = "아침과 저녁의 차이",
            requirement = "같은 장소를 아침과 저녁에 각각 담아 빛의 변화 비교하기",
            nextHint = "아침에 찍은 곳을 저녁에 한 번 더 찾아가 보세요",
            target = 2,
            countOf = { visits ->
                // 장소별로 묶어, 아침과 저녁 사진을 **둘 다** 가진 곳을 찾습니다.
                // 아침 사진만 열 장 있어도 이 미션은 1 입니다. 비교가 목적이라
                // 한쪽만 쌓이는 것은 진행이 아닙니다.
                visits.withPhoto()
                    .groupBy { it.contentId }
                    .values
                    .maxOfOrNull { group ->
                        val morning = if (group.any { timeOf(it).hour < 12 }) 1 else 0
                        val evening = if (group.any { timeOf(it).hour >= 17 }) 1 else 0
                        morning + evening
                    } ?: 0
            }
        ),

        Mission(
            id = "blue_hour",
            type = MissionType.LIGHT,
            title = "푸른 시간 수집가",
            requirement = "해 뜨기 전이나 진 직후 블루아워에 사진 1장 남기기",
            nextHint = "일몰 직후 25분 동안만 열리는 시간입니다",
            target = 1,
            countOf = { visits ->
                visits.withPhoto().count {
                    phaseOf(it) == LightPhase.BLUE_DAWN || phaseOf(it) == LightPhase.BLUE_DUSK
                }
            }
        ),

        Mission(
            id = "naejangsan_season",
            type = MissionType.SEASON,
            title = "내장산 계절 관찰일지",
            requirement = "이번 계절의 내장산에서 나뭇잎·능선·길을 3장 남기기",
            nextHint = "같은 계절 안에 세 장을 모아야 변화가 보입니다",
            target = 3,
            places = listOf("내장산"),
            countOf = { visits ->
                val now = Season.now()
                visits.withPhoto().count { visit ->
                    visit.title.contains("내장산") &&
                        Season.of(timeOf(visit).toLocalDate()) == now
                }
            }
        ),

        Mission(
            id = "postcard",
            type = MissionType.RECORD,
            title = "나만의 정읍 엽서",
            requirement = "찍은 사진 3장에 프레임을 입혀 나만의 정읍 완성하기",
            nextHint = "일기에서 사진을 눌러 프레임을 입혀보세요",
            target = 3,
            countOf = { visits -> visits.withPhoto().size }
        ),

        Mission(
            id = "heritage",
            type = MissionType.PLACE,
            title = "정읍의 오래된 장면",
            requirement = "무성서원·피향정·정읍사 문화공원 중 두 곳의 건축 디테일 담기",
            nextHint = "처마 끝과 기와의 결을 가까이 담아보세요",
            target = 2,
            places = HERITAGE_SITES,
            countOf = { visits ->
                visits.withPhoto()
                    .filter { visit -> HERITAGE_SITES.any { visit.title.contains(it) } }
                    .distinctBy { it.contentId }
                    .size
            }
        ),

        Mission(
            id = "light_route",
            type = MissionType.PLACE,
            title = "빛의 이동 경로",
            requirement = "하루 안에 서로 다른 촬영지 세 곳을 이어 담기",
            nextHint = "오전·오후·저녁으로 나눠 세 곳을 이어보세요",
            target = 3,
            countOf = { visits ->
                // 하루 안에 몇 곳을 이었는지. 날짜별로 묶어 가장 많은 날을 씁니다.
                visits.withPhoto()
                    .groupBy { timeOf(it).toLocalDate() }
                    .values
                    .maxOfOrNull { day -> day.distinctBy { it.contentId }.size } ?: 0
            }
        )
    )

    /** 전체 장면 수. "장면 6 / 24" 의 분모입니다. */
    val totalScenes: Int get() = ALL.sumOf { it.target }

    fun progress(visits: List<VisitLogEntity>): List<MissionProgress> =
        ALL.map { it.progressOf(visits) }
            // 진행 중인 것을 맨 위로 올립니다. 완료한 것은 이미 한 일이고,
            // 손도 안 댄 것보다 반쯤 채운 것이 더 하고 싶어집니다.
            .sortedWith(
                compareBy<MissionProgress> { it.isComplete }
                    .thenByDescending { if (it.current > 0) it.ratio else -1f }
            )

    /**
     * 이 장소에서 지금 할 수 있는 미션.
     *
     * 완료한 것은 뺍니다. 이미 채운 미션을 현장에서 또 권하면 화면이 할 일을
     * 못 합니다. 지금 빛 구간에 맞는 것을 위로 올려 "지금 바로"를 만듭니다.
     */
    fun forPlace(
        spotTitle: String,
        visits: List<VisitLogEntity>,
        phase: LightPhase,
        limit: Int = 3
    ): List<MissionProgress> = progress(visits)
        .filter { it.mission.availableAt(spotTitle) && !it.isComplete }
        .sortedWith(
            compareByDescending<MissionProgress> { fitsNow(it.mission, phase) }
                .thenByDescending { it.ratio }
        )
        .take(limit)

    /** 지금 빛에서 바로 할 수 있는 미션인지. 정렬에만 씁니다. */
    private fun fitsNow(mission: Mission, phase: LightPhase): Boolean = when (mission.id) {
        "light_hunter" -> phase.isGolden
        "blue_hour" -> phase == LightPhase.BLUE_DAWN || phase == LightPhase.BLUE_DUSK
        else -> true
    }
}
