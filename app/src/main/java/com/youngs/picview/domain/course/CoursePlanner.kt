package com.youngs.picview.domain.course

import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.SpotFacts
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.LatLng
import com.youngs.picview.util.distanceKmTo
import com.youngs.picview.util.estimateTravelMinutes
import java.time.Duration
import java.time.LocalTime

/**
 * 출사 코스를 짭니다.
 *
 * 일반 여행 앱의 코스는 "영업시간 안에 몇 군데를 넣을까"의 문제지만,
 * 출사 코스는 **빛을 언제 어디서 받을까**의 문제입니다. 그래서 순서가 반대입니다.
 *
 *   1. 골든아워 슬롯을 먼저 예약한다  ← 하루에 두 번뿐이고 30분씩밖에 없음
 *   2. 그 슬롯에 방위가 맞는 스팟을 꽂는다 (일출↔동향, 일몰↔서향)
 *   3. 한낮에는 실내 스팟을 넣어 강한 빛을 피한다
 *   4. 남은 시간을 포토스코어·이동거리 기준으로 채운다
 *
 * 전부 결정론적 계산입니다. LLM 은 여기서 나온 결과를 설명하는 문장만 씁니다.
 * 그래서 네트워크가 끊기거나 LLM 이 실패해도 코스 자체는 항상 나옵니다.
 */
object CoursePlanner {

    /** 후보로 검토할 상위 스팟 수. 너무 넓히면 이동거리가 터집니다. */
    private const val CANDIDATE_POOL = 40

    fun plan(
        spots: List<SpotItem>,
        sun: SunTimes,
        request: CourseRequest
    ): ShootingCourse {

        val candidates = spots
            .mapNotNull { spot ->
                val coords = LatLng.parseOrNull(spot.mapy, spot.mapx) ?: return@mapNotNull null
                Candidate(
                    spot = spot,
                    coords = coords,
                    facts = SpotFactsTable.of(spot.title, spot.contentTypeId)
                )
            }
            .filter { matchesSubjects(it, request.subjects) }
            .take(CANDIDATE_POOL)

        if (candidates.isEmpty()) {
            return ShootingCourse(emptyList(), sun, request.travelMode, 0.0)
        }

        val used = mutableSetOf<String>()
        val anchors = pickGoldenAnchors(candidates, sun, request, used)
        val stops = buildTimeline(candidates, anchors, sun, request, used)

        val totalKm = stops.sumOf { it.travelKm }
        return ShootingCourse(stops, sun, request.travelMode, totalKm)
    }

    // ───────────────────── 1. 골든아워 앵커 ─────────────────────

    /**
     * 골든아워 구간마다 가장 어울리는 스팟을 하나씩 잡아 둡니다.
     * 이 스팟들은 시각이 고정되고, 나머지는 그 사이를 채우게 됩니다.
     */
    private fun pickGoldenAnchors(
        candidates: List<Candidate>,
        sun: SunTimes,
        request: CourseRequest,
        used: MutableSet<String>
    ): List<Anchor> {
        val anchors = mutableListOf<Anchor>()

        sun.upcomingGoldenWindows(request.startTime)
            .filter { it.peak >= request.startTime && it.peak <= request.endTime }
            .forEach { window ->
                val preferred = when (window.phase) {
                    LightPhase.SUNRISE -> Facing.EAST
                    else -> Facing.WEST
                }

                val pick = candidates
                    .filter { it.spot.contentId !in used }
                    // 방위가 맞는 곳 → 그 시간대가 최적인 곳 → 점수 높은 곳 순
                    .sortedWith(
                        compareByDescending<Candidate> { it.facts.facing == preferred }
                            .thenByDescending { it.facts.bestPhase == window.phase }
                            .thenByDescending { it.spot.score }
                    )
                    .firstOrNull { it.facts.facing != Facing.INDOOR }
                    ?: return@forEach

                used += pick.spot.contentId
                anchors += Anchor(
                    candidate = pick,
                    arriveAt = window.start,
                    phase = window.phase,
                    reason = buildString {
                        append(pick.facts.facing.label)
                        append(" · ")
                        append(window.phase.label)
                        append(" ${window.durationMinutes}분")
                    }
                )
            }

        return anchors.sortedBy { it.arriveAt }
    }

    // ───────────────────── 2. 타임라인 채우기 ─────────────────────

    private fun buildTimeline(
        candidates: List<Candidate>,
        anchors: List<Anchor>,
        sun: SunTimes,
        request: CourseRequest,
        used: MutableSet<String>
    ): List<CourseStop> {

        val stops = mutableListOf<CourseStop>()
        var cursor = request.startTime
        var previous: Candidate? = null
        val anchorQueue = ArrayDeque(anchors)

        // 골든아워 자리는 먼저 떼어 둡니다.
        // 이렇게 하지 않으면 채우기 스팟이 maxStops 를 다 써버려서
        // 정작 하루의 하이라이트인 일몰 스팟이 코스에서 빠집니다.
        var fillerBudget = (request.maxStops - anchors.size).coerceAtLeast(0)

        while (stops.size < request.maxStops && cursor < request.endTime) {

            val nextAnchor = anchorQueue.firstOrNull()

            // 다음 앵커 시각이 다가왔으면 앵커를 먼저 배치합니다.
            if (nextAnchor != null) {
                val travel = travelBetween(previous, nextAnchor.candidate, request)
                val departBy = nextAnchor.arriveAt.minusMinutes(travel.minutes.toLong())
                if (cursor >= departBy) {
                    anchorQueue.removeFirst()
                    stops += toStop(
                        candidate = nextAnchor.candidate,
                        arriveAt = nextAnchor.arriveAt,
                        phase = nextAnchor.phase,
                        travel = travel,
                        reason = nextAnchor.reason,
                        isHighlight = true
                    )
                    previous = nextAnchor.candidate
                    cursor = nextAnchor.arriveAt.plusMinutes(
                        nextAnchor.candidate.facts.stayMinutes.toLong()
                    )
                    continue
                }
            }

            // 앵커 전까지 남는 시간에 들어갈 스팟을 고릅니다.
            if (fillerBudget <= 0) {
                // 채우기 자리를 다 썼으면 남은 앵커 시각까지 시간만 흘려보냅니다.
                if (nextAnchor == null) break
                cursor = nextAnchor.arriveAt.minusMinutes(
                    travelBetween(previous, nextAnchor.candidate, request).minutes.toLong()
                )
                continue
            }

            val phaseNow = sun.phaseAt(cursor)
            val slotEnd = nextAnchor?.arriveAt ?: request.endTime

            val pick = pickFiller(candidates, used, previous, phaseNow, request, cursor, slotEnd)
                ?: if (nextAnchor != null) {
                    cursor = nextAnchor.arriveAt.minusMinutes(
                        travelBetween(previous, nextAnchor.candidate, request).minutes.toLong()
                    )
                    continue
                } else break

            val travel = travelBetween(previous, pick, request)
            val arriveAt = cursor.plusMinutes(travel.minutes.toLong())
            if (arriveAt >= request.endTime) break

            used += pick.spot.contentId
            fillerBudget--
            stops += toStop(
                candidate = pick,
                arriveAt = arriveAt,
                phase = sun.phaseAt(arriveAt),
                travel = travel,
                reason = reasonFor(pick.facts, sun.phaseAt(arriveAt)),
                isHighlight = false
            )
            previous = pick
            cursor = arriveAt.plusMinutes(pick.facts.stayMinutes.toLong())
        }

        // 앵커가 아직 남았는데 슬롯을 못 채웠다면 뒤에 그대로 붙입니다.
        anchorQueue.forEach { anchor ->
            if (stops.size >= request.maxStops) return@forEach
            val travel = travelBetween(previous, anchor.candidate, request)
            stops += toStop(
                anchor.candidate, anchor.arriveAt, anchor.phase,
                travel, anchor.reason, isHighlight = true
            )
            previous = anchor.candidate
        }

        return stops.sortedBy { it.arriveAt }
    }

    /**
     * 빈 슬롯에 넣을 스팟 선정.
     * 지금 빛 구간에 어울리는지 + 가까운지 + 점수 순으로 고릅니다.
     */
    private fun pickFiller(
        candidates: List<Candidate>,
        used: Set<String>,
        previous: Candidate?,
        phase: LightPhase,
        request: CourseRequest,
        cursor: LocalTime,
        slotEnd: LocalTime
    ): Candidate? {
        val slotMinutes = Duration.between(cursor, slotEnd).toMinutes()
        if (slotMinutes < MIN_SLOT_MINUTES) return null

        return candidates
            .asSequence()
            .filter { it.spot.contentId !in used }
            .filter { it.facts.stayMinutes <= slotMinutes + STAY_TOLERANCE }
            .sortedWith(
                // 1) 지금 빛에 맞는가 (한낮엔 실내, 그 외엔 야외)
                compareByDescending<Candidate> { fitsPhase(it.facts, phase) }
                    // 2) 직전과 같은 카테고리는 뒤로 미룹니다.
                    //    이게 없으면 한낮에 음식점·찻집만 세 곳 연속으로 붙어서
                    //    출사 코스가 아니라 카페 투어가 됩니다.
                    .thenBy { it.spot.contentTypeId == previous?.spot?.contentTypeId }
                    .thenBy { previous?.let { p -> p.coords.distanceKmTo(it.coords) } ?: 0.0 }
                    .thenByDescending { it.spot.score }
            )
            .firstOrNull()
    }

    /** 이 스팟이 지금 빛에 어울리는지. */
    private fun fitsPhase(facts: SpotFacts, phase: LightPhase): Boolean = when {
        // 한낮에는 '실내를 찍는 곳'뿐 아니라 '실내로 들어갈 수 있는 곳'까지 허용합니다.
        // INDOOR 만 허용하면 후보가 음식점밖에 안 남아서 카페 투어가 됩니다.
        phase == LightPhase.MIDDAY -> facts.indoorOption
        phase == LightPhase.NIGHT -> facts.bestPhase == LightPhase.BLUE_DUSK ||
                facts.bestPhase == LightPhase.NIGHT
        else -> facts.facing != Facing.INDOOR
    }

    /**
     * 코스 카드에 붙는 한 줄.
     *
     * 스팟의 고유 촬영 팁(facts.note)은 **그 스팟의 최적 시간대에 배치됐을 때만**
     * 씁니다. 그러지 않으면 오후에 넣어 놓고 "오전이 가장 깔끔해요" 같은
     * 앞뒤 안 맞는 문구가 나옵니다.
     */
    private fun reasonFor(facts: SpotFacts, phase: LightPhase): String = when {
        phase == facts.bestPhase -> facts.note
        !fitsPhase(facts, phase) -> phase.hint
        else -> "${phase.label} · ${facts.facing.label}에 담기 좋아요"
    }

    private fun matchesSubjects(candidate: Candidate, subjects: Set<Subject>): Boolean {
        if (subjects.isEmpty()) return true
        val type = candidate.spot.contentTypeId ?: return false
        return subjects.any { type in it.contentTypeIds }
    }

    private fun travelBetween(
        from: Candidate?,
        to: Candidate,
        request: CourseRequest
    ): Travel {
        if (from == null) return Travel(0, 0.0)
        val km = from.coords.distanceKmTo(to.coords)
        return Travel(estimateTravelMinutes(km, request.travelMode), km)
    }

    private fun toStop(
        candidate: Candidate,
        arriveAt: LocalTime,
        phase: LightPhase,
        travel: Travel,
        reason: String,
        isHighlight: Boolean
    ) = CourseStop(
        spot = candidate.spot,
        facts = candidate.facts,
        arriveAt = arriveAt,
        leaveAt = arriveAt.plusMinutes(candidate.facts.stayMinutes.toLong()),
        phase = phase,
        travelMinutes = travel.minutes,
        travelKm = travel.km,
        reason = reason,
        isHighlight = isHighlight
    )

    private const val MIN_SLOT_MINUTES = 30L

    /** 체류 시간이 슬롯보다 조금 길어도 허용하는 여유(분). */
    private const val STAY_TOLERANCE = 20L

    private data class Candidate(
        val spot: SpotItem,
        val coords: LatLng,
        val facts: SpotFacts
    )

    private data class Anchor(
        val candidate: Candidate,
        val arriveAt: LocalTime,
        val phase: LightPhase,
        val reason: String
    )

    private data class Travel(val minutes: Int, val km: Double)
}
