package com.youngs.picview.ui.map

import android.graphics.PointF
import android.os.Bundle
import com.youngs.picview.util.NaverMapSdkInit
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PolylineOverlay
import com.naver.maps.map.util.FusedLocationSource
import com.youngs.picview.MainActivity
import com.youngs.picview.ui.main.MainFragment
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentMapBinding
import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.ShotPurpose
import com.youngs.picview.domain.spot.ShotTokens
import com.youngs.picview.domain.spot.SpotFacts
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.domain.spot.SpotStory
import com.youngs.picview.domain.spot.SpotTheme
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.guide.GuideOverlayView
import com.youngs.picview.ui.guide.SiseonGuideActivity
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.SpotBookmark
import com.youngs.picview.util.TravelMode
import com.youngs.picview.util.applyTopSystemBarInset
import com.youngs.picview.util.distanceKmTo
import com.youngs.picview.util.estimateTravelMinutes
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime

/**
 * 촬영지 탐색 지도 — 시안 구조.
 *
 * 마커의 축이 관광공사 분류(자연·문화…)가 아니라 **촬영 목적**(반사·실루엣·
 * 인물·건축선·여백)입니다. 위쪽 칩으로 스토리(달·사랑·혁명·느림·계절)와
 * 목적을 거르고, 마커를 고르면 아래 시트가 "왜 지금 가는지"부터 답합니다.
 */
class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var naverMap: NaverMap? = null
    private lateinit var locationSource: FusedLocationSource
    private lateinit var sheetBehavior: BottomSheetBehavior<*>

    private val markers = mutableListOf<Marker>()
    private var route: PolylineOverlay? = null
    private var hasFittedCamera = false

    private var allSpots: List<SpotItem> = emptyList()
    private var selectedStory: SpotStory? = null
    private var selectedPurpose: ShotPurpose? = null

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** 한반도 범위를 벗어나거나 값이 없는 좌표는 버립니다. */
    private fun SpotItem.toLatLngOrNull(): LatLng? {
        val lat = mapy.toDoubleOrNull() ?: return null
        val lng = mapx.toDoubleOrNull() ?: return null
        if (lat !in 33.0..39.0 || lng !in 124.0..132.0) return null
        return LatLng(lat, lng)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 지도 SDK 는 여기서 처음 씁니다. 뷰를 만들기 전에 초기화해야 합니다.
        NaverMapSdkInit.ensure(requireContext())
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMapBinding.bind(view)

        // 지도는 상태바 뒤까지 깔리고, 떠 있는 상단 묶음만 아래로 내립니다.
        binding.layoutMapTopbar.applyTopSystemBarInset()

        locationSource = FusedLocationSource(this, 1000)

        binding.btnMapBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // 검색은 탐색 탭(목록·검색)이 전담합니다. 지도를 닫고 그리로 보내되,
        // 검색칸까지 열어 둡니다. 탭만 바꿔 놓으면 "돋보기를 눌렀는데 목록이
        // 나왔다"로 읽힙니다.
        binding.btnMapSearch.setOnClickListener {
            parentFragmentManager.popBackStack()
            val main = activity as? MainActivity ?: return@setOnClickListener
            main.selectTab(R.id.tab_explore)
            (main.tabFragment(R.id.tab_explore) as? MainFragment)?.focusSearch()
        }

        binding.btnMapLayers.setOnClickListener {
            binding.cardLegend.isVisible = !binding.cardLegend.isVisible
        }

        sheetBehavior = BottomSheetBehavior.from(binding.sheetSpot)
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        buildLegend()
        renderStatus()
        renderRecoPill()
        setupChips()

        // 천문 정보는 지도보다 늦게 도착할 수 있습니다. 오면 상태줄을 다시 씁니다.
        viewModel.goldenHourData.observe(viewLifecycleOwner) {
            renderStatus()
            renderRecoPill()
        }

        // MapFragment 로드
        val fm = childFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_view_container) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_view_container, it).commit()
            }
        mapFragment.getMapAsync(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated) { // 권한 거부 시
                naverMap?.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // ─────────────────────── 상단 : 상태 · 칩 ───────────────────────

    /** "지금은 석양 · 일몰까지 58분 · 반사 추천" — 지금 빛이 지도의 첫 정보입니다. */
    private fun renderStatus() {
        val sun = viewModel.sunTimes
        val now = LocalTime.now()
        val phase = sun.phaseAt(now)
        val rise = sun.sunrise
        val set = sun.sunset

        val countdown = when {
            rise == null || set == null -> getString(R.string.course_hero_golden_unknown)
            now < rise -> getString(
                R.string.map_until_sunrise, durationText(minutesBetween(now, rise))
            )
            now < set -> getString(
                R.string.map_until_sunset, durationText(minutesBetween(now, set))
            )
            else -> sun.nextGolden(now)?.let {
                getString(
                    R.string.course_next_golden, it.minutesAway / 60, it.minutesAway % 60
                )
            } ?: getString(R.string.course_hero_golden_unknown)
        }

        binding.tvMapStatus.text = "${phaseEmoji(phase)} " + getString(
            R.string.map_status_format,
            phaseWord(phase),
            countdown,
            "${ShotPurpose.recommendedFor(phase).label} 추천"
        )
    }

    /** 시안 표기를 따르는 구간 이름. "일몰 골든아워"는 상태줄에서 "석양"으로 줄입니다. */
    private fun phaseWord(phase: LightPhase): String = when (phase) {
        LightPhase.SUNSET -> "석양"
        LightPhase.SUNRISE -> "아침빛"
        else -> phase.label
    }

    private fun phaseEmoji(phase: LightPhase): String = when (phase) {
        LightPhase.SUNRISE -> "🌄"
        LightPhase.SUNSET -> "🌅"
        LightPhase.BLUE_DAWN, LightPhase.BLUE_DUSK -> "🌆"
        LightPhase.NIGHT -> "🌙"
        else -> "☀️"
    }

    private fun minutesBetween(from: LocalTime, to: LocalTime): Long =
        Duration.between(from, to).toMinutes()

    private fun durationText(minutes: Long): String = if (minutes >= 60) {
        getString(R.string.home_duration_hm, minutes / 60, minutes % 60)
    } else {
        getString(R.string.home_duration_m, minutes)
    }

    private val storyByChip = mapOf(
        R.id.chip_story_moon to SpotStory.MOON,
        R.id.chip_story_love to SpotStory.LOVE,
        R.id.chip_story_revolution to SpotStory.REVOLUTION,
        R.id.chip_story_slow to SpotStory.SLOW,
        R.id.chip_story_season to SpotStory.SEASON
    )

    private val purposeByChip = mapOf(
        R.id.chip_purpose_reflect to ShotPurpose.REFLECT,
        R.id.chip_purpose_silhouette to ShotPurpose.SILHOUETTE,
        R.id.chip_purpose_person to ShotPurpose.PERSON,
        R.id.chip_purpose_arch to ShotPurpose.ARCH_LINE,
        R.id.chip_purpose_space to ShotPurpose.SPACE
    )

    private fun setupChips() {
        binding.chipsStory.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedStory = checkedIds.firstOrNull()?.let(storyByChip::get)
            buildLegend()
            refreshMarkers()
        }
        binding.chipsPurpose.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedPurpose = checkedIds.firstOrNull()?.let(purposeByChip::get)
            refreshMarkers()
        }
    }

    /** 지금 빛에 어울리는 이야기를 아래 알약으로 권합니다. 누르면 그 칩이 켜집니다. */
    private fun renderRecoPill() {
        val story = SpotStory.recommendedFor(viewModel.sunTimes.phaseNow())
        binding.tvMapReco.text =
            "${story.emoji} " + getString(R.string.map_reco_walk, story.label)

        binding.pillMapReco.setOnClickListener {
            storyByChip.entries.firstOrNull { it.value == story }?.let { (chipId, _) ->
                binding.chipsStory.check(chipId)
            }
        }
    }

    /**
     * 스토리를 고른 순간의 피드백 — "🌙 지금 달 코스 12곳".
     *
     * 칩을 눌러도 지도만 바뀌면 무엇이 걸러졌는지 세어 봐야 압니다.
     * 고른 이야기의 이름과 곳 수를 그 이야기의 색으로 되돌려 줍니다.
     */
    private fun renderStoryBadge(count: Int) {
        val badge = binding.tvStoryBadge
        val story = selectedStory
        if (story == null) {
            badge.isVisible = false
            return
        }
        badge.isVisible = true
        badge.text = "${story.emoji} " + getString(R.string.map_story_badge, story.label, count)
        badge.background?.mutate()?.setTint(
            ContextCompat.getColor(requireContext(), story.colorRes)
        )
    }

    /**
     * 지도 범례. 스토리를 고르면 마커의 축이 바뀌므로 범례도 함께 바뀝니다.
     * 마커는 스토리 색인데 범례가 목적 색이면 서로 다른 지도를 설명합니다.
     */
    private fun buildLegend() {
        val container = binding.layoutLegendRows
        container.removeAllViews()

        val story = selectedStory
        binding.tvLegendTitle.setText(
            if (story == null) R.string.map_legend_purpose else R.string.map_legend_story
        )

        val rows: List<Pair<String, Int>> = if (story == null) {
            ShotPurpose.values().map { it.label to it.colorRes }
        } else {
            SpotStory.values().map { "${it.emoji} ${it.label}" to it.colorRes }
        }

        rows.forEach { (text, colorRes) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }

            val dot = ImageView(requireContext()).apply {
                setImageResource(R.drawable.dot_indicator_active)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), colorRes)
                )
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
            }

            val label = TextView(requireContext()).apply {
                this.text = text
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 11f
                setPadding(dp(6), 0, 0, 0)
            }

            row.addView(dot)
            row.addView(label)
            container.addView(row)
        }
    }

    // ─────────────────────── 지도 · 마커 ───────────────────────

    override fun onMapReady(map: NaverMap) {
        this.naverMap = map

        map.locationSource = locationSource

        // 기본 위치 버튼(왼쪽 아래)은 범례 카드에 가려집니다.
        // 오른쪽 아래에 둔 우리 버튼을 지도에 묶어 대신 씁니다.
        map.uiSettings.isLocationButtonEnabled = false
        binding.btnMyLocation.map = map

        // 내 위치는 표시하되 카메라를 따라가지는 않습니다.
        // Follow 로 두면 정읍 밖에서 앱을 열었을 때 촬영지가 하나도 안 보입니다.
        map.locationTrackingMode = LocationTrackingMode.NoFollow

        viewModel.spotData.observe(viewLifecycleOwner) { spots ->
            allSpots = spots.orEmpty()
            refreshMarkers()
        }

        map.setOnMapClickListener { _, _ ->
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    /** 스토리·목적 필터를 거쳐 마커를 다시 깝니다. */
    private fun refreshMarkers() {
        val map = naverMap ?: return

        markers.forEach { it.map = null }
        markers.clear()
        route?.map = null
        route = null

        data class Entry(
            val spot: SpotItem,
            val position: LatLng,
            val facts: SpotFacts,
            val purpose: ShotPurpose
        )

        val entries = allSpots.mapNotNull { spot ->
            val position = spot.toLatLngOrNull() ?: return@mapNotNull null
            val facts = SpotFactsTable.of(spot.title, spot.contentTypeId)
            val theme = SpotTheme.of(spot.contentTypeId)
            val purpose = ShotPurpose.of(spot.title, facts, theme)

            val story = selectedStory
            if (story != null && story !in SpotStory.of(spot.title, theme)) return@mapNotNull null
            if (selectedPurpose != null && purpose != selectedPurpose) return@mapNotNull null

            Entry(spot, position, facts, purpose)
        }

        // 스토리를 고른 동안에는 마커의 축이 바뀝니다.
        // 목적(반사·실루엣…)은 "무엇을 찍는가"의 축이라, '달을 따라 걷기'를
        // 골라도 마커가 그대로면 스토리를 고른 값이 화면에 남지 않습니다.
        val story = selectedStory

        entries.forEach { entry ->
            val marker = Marker()
            marker.position = entry.position
            marker.captionText = entry.spot.title
            marker.icon = OverlayImage.fromResource(story?.markerRes ?: entry.purpose.markerRes)
            // 핀의 아래 꼭짓점이 실제 좌표를 가리키게 합니다.
            marker.anchor = PointF(0.5f, 1.0f)

            // 80여 개가 기본 크기로 깔리면 서로 겹쳐 지도가 안 보입니다.
            marker.width = dp(28)
            marker.height = dp(37)
            marker.isHideCollidedMarkers = true
            marker.isHideCollidedCaptions = true
            marker.captionMinZoom = 11.0
            marker.captionTextSize = 11f

            marker.map = map
            marker.onClickListener = Overlay.OnClickListener {
                showSheet(entry.spot, entry.facts, entry.purpose)
                true
            }
            markers.add(marker)
        }

        renderStoryBadge(entries.size)

        // 스토리를 골랐고 몇 곳으로 좁혀졌으면 추천 순서대로 걷기 동선을 잇습니다.
        // (spotData 는 포토스코어 내림차순으로 정렬돼 있습니다)
        if (story != null && entries.size in 2..6) {
            route = PolylineOverlay().apply {
                coords = entries.map { it.position }
                // 동선도 그 스토리의 색으로 잇습니다. 마커와 선이 따로 놀면
                // 걷는 순서가 이 이야기의 것이라는 게 읽히지 않습니다.
                color = ContextCompat.getColor(requireContext(), story.colorRes)
                width = dp(3)
                setPattern(dp(8), dp(8))
                this.map = map
            }
        }

        // 지도를 처음 열면 촬영지 전체가 한눈에 들어오도록 맞춥니다.
        if (!hasFittedCamera && markers.isNotEmpty()) {
            val bounds = LatLngBounds.Builder()
                .apply { markers.forEach { include(it.position) } }
                .build()
            map.moveCamera(CameraUpdate.fitBounds(bounds, dp(56)))
            hasFittedCamera = true
        }
    }

    // ─────────────────────── 장소 시트 ───────────────────────

    private fun showSheet(spot: SpotItem, facts: SpotFacts, purpose: ShotPurpose) {
        val phase = viewModel.sunTimes.phaseNow()

        binding.tvSheetTitle.text = spot.title
        binding.tvSheetReason.text = reasonFor(purpose)
        binding.tvSheetNote.text = facts.note

        Glide.with(binding.ivSheetPhoto)
            .load(spot.imageUrl.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.bg_image_placeholder)
            .error(R.drawable.bg_image_placeholder)
            .centerCrop()
            .into(binding.ivSheetPhoto)

        binding.tvStatLight.text = "${phaseEmoji(phase)} ${phase.shortLabel}"
        binding.tvStatTravel.text = "🚘 " + getString(R.string.map_minutes, travelMinutesTo(spot))
        binding.tvStatStay.text =
            "🕐 " + getString(R.string.map_stay_minutes, facts.stayMinutes)
        binding.tvStatLevel.text = "🚶 " + levelLabel(facts.stayMinutes)
        binding.tvSheetComp.text = compositionLine(purpose, facts)

        binding.layoutSheetTitle.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(spot))
        }

        binding.btnSheetGuide.setOnClickListener {
            startActivity(
                SiseonGuideActivity.intent(
                    requireContext(),
                    spotTitle = spot.title,
                    contextId = SiseonGuide.contextIdFor(facts.bestPhase),
                    guideId = SiseonGuide.guideIdFor(facts),
                    // 미션에서 찍은 사진이 이 장소의 방문 기록("다녀왔어요")이 되도록.
                    contentId = spot.contentId,
                    phaseName = viewModel.sunTimes.phaseNow().name
                )
            )
        }

        val save = View.OnClickListener { savePlan(spot) }
        binding.btnSheetSave.setOnClickListener(save)
        binding.btnSheetBookmark.setOnClickListener(save)

        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    /** "왜 지금 가는지" 한 줄. 촬영 목적이 곧 그 이유입니다. */
    private fun reasonFor(purpose: ShotPurpose): String = when (purpose) {
        ShotPurpose.REFLECT -> "수면에 빛이 길게 번지는 시간"
        ShotPurpose.SILHOUETTE -> "해를 등지고 형태만 남기는 시간"
        ShotPurpose.PERSON -> "부드러운 빛이 얼굴에 도는 시간"
        ShotPurpose.ARCH_LINE -> "처마와 담의 선이 또렷해지는 시간"
        ShotPurpose.SPACE -> "비워 둔 하늘이 그림이 되는 시간"
    }

    /**
     * 이동 시간(분). 위치 권한이 있으면 내 위치, 없으면 정읍역 기준입니다.
     * 코스 계산과 같은 근사식([estimateTravelMinutes])을 씁니다.
     */
    private fun travelMinutesTo(spot: SpotItem): Int {
        val target = com.youngs.picview.util.LatLng.parseOrNull(spot.mapy, spot.mapx)
            ?: return TravelMode.CAR.minMinutes

        // 내 위치가 정읍권 밖이면(여행 전에 미리 보는 경우) 몇 백 분짜리
        // 숫자가 나와 오히려 판단을 흐립니다. 그때는 정읍역 기준으로 말합니다.
        val last = locationSource.lastLocation
        val from = last
            ?.let { com.youngs.picview.util.LatLng(it.latitude, it.longitude) }
            ?.takeIf { it.distanceKmTo(JEONGEUP_STATION) <= NEARBY_KM }
            ?: JEONGEUP_STATION
        return estimateTravelMinutes(from.distanceKmTo(target), TravelMode.CAR)
    }

    /** 체류 시간이 길수록 발품이 드는 곳입니다. 걸음 난이도로 옮겨 말합니다. */
    private fun levelLabel(stayMinutes: Int): String = when {
        stayMinutes <= 45 -> "가볍게"
        stayMinutes <= 80 -> "천천히"
        else -> "여유롭게"
    }

    /** "실루엣 + 여백" — 목적과 구도를 한 벌로 권합니다. */
    private fun compositionLine(purpose: ShotPurpose, facts: SpotFacts): String {
        val composition = when (facts.guide) {
            GuideOverlayView.GuideType.THIRDS -> "여백"
            GuideOverlayView.GuideType.SYMMETRY -> "대칭"
            GuideOverlayView.GuideType.CENTER -> "가까이"
        }
        // "여백 + 여백" 처럼 같은 말이 겹치면 빛의 결로 바꿔 말합니다.
        val second = if (composition == purpose.label) {
            ShotTokens.of(facts.bestPhase).label
        } else {
            composition
        }
        return "${purpose.label} + $second"
    }

    /**
     * 시트의 담기 — 찜 토글.
     *
     * 예전에는 한 곳짜리 코스를 만들어 코스 목록에 넣었습니다. 지도에서
     * 여러 곳을 담아도 코스 하나로 모이지 않고 1곳짜리 코스가 여럿
     * 생겼습니다. 지금은 찜으로 모이고, 코스 탭의 '직접 고르기'에서
     * 그 찜한 곳을 골라 코스를 짭니다.
     */
    private fun savePlan(spot: SpotItem) {
        val added = SpotBookmark.toggle(requireContext(), spot)
        renderSheetSaveState(spot)
        Toast.makeText(
            requireContext(),
            if (added) R.string.bookmark_added else R.string.bookmark_removed,
            Toast.LENGTH_SHORT
        ).show()
    }

    /** 담긴 곳인지 시트에 표시합니다. 눌렀는데 아무 변화가 없으면 안 됩니다. */
    private fun renderSheetSaveState(spot: SpotItem) {
        val saved = SpotBookmark.isSaved(requireContext(), spot.contentId)
        binding.btnSheetBookmark.setImageResource(
            if (saved) R.drawable.ic_heart_filled else R.drawable.ic_heart
        )
        binding.btnSheetSave.setText(
            if (saved) R.string.bookmark_saved_label else R.string.bookmark_save_label
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 위치를 모를 때 이동 시간의 기준점 — 정읍역. */
        private val JEONGEUP_STATION = com.youngs.picview.util.LatLng(35.5637, 126.8420)

        /** 이 거리(km) 안에 있어야 "내 위치 기준 이동 시간"이 뜻이 있습니다. */
        private const val NEARBY_KM = 60.0
    }
}
