package com.youngs.picview.ui.mission

import android.app.Application
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.google.android.material.chip.Chip
import com.youngs.picview.R
import com.youngs.picview.data.repository.DiaryRepository
import com.youngs.picview.databinding.FragmentMissionBinding
import com.youngs.picview.domain.mission.MissionProgress
import com.youngs.picview.domain.mission.MissionType
import com.youngs.picview.domain.season.Season
import com.youngs.picview.domain.spot.ShotTokens
import com.youngs.picview.ui.guide.GuideOverlayView
import com.youngs.picview.domain.mission.Missions
import com.youngs.picview.util.applyTopSystemBarInset

/**
 * 미션 & 기록.
 *
 * 예전에는 "배지 2개 획득"이 히어로였습니다. 그러면 이 화면이 수집판이
 * 되고, 다음에 무엇을 할지는 사용자가 스스로 찾아야 했습니다.
 *
 * 지금은 **다음 촬영 행동을 고르는 화면**입니다. 장면 진행 → 오늘의 추천
 * → 유형 필터 → 전체 목록 순으로 내려갑니다.
 *
 * 별도 저장소가 없습니다. 방문 기록만으로 매번 판정하므로 "미션 완료
 * 처리를 깜빡해서 배지가 안 나오는" 종류의 버그가 원천적으로 없습니다.
 */
class MissionFragment : Fragment(R.layout.fragment_mission) {

    private var _binding: FragmentMissionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MissionViewModel by viewModels()

    /** 일출·일몰은 앱 전체가 공유하는 값이라 액티비티 스코프에서 읽습니다. */
    private val mainViewModel: com.youngs.picview.ui.main.MainViewModel by activityViewModels()

    /** null 이면 전체. 필터 칩이 정합니다. */
    private var filter: MissionType? = null

    private var latest: List<MissionProgress> = emptyList()

    private lateinit var adapter: MissionAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMissionBinding.bind(view)

        binding.rootMission.applyTopSystemBarInset()
        binding.btnMissionBack.setOnClickListener { parentFragmentManager.popBackStack() }

        adapter = MissionAdapter()
        binding.rvMissions.adapter = adapter

        val stampAdapter = StampAdapter()
        binding.rvStamps.adapter = stampAdapter

        buildFilterChips()

        viewModel.progress.observe(viewLifecycleOwner) { list ->
            latest = list
            stampAdapter.submitList(list)
            renderSummary(list)
            renderToday(list)
            applyFilter()
        }

        viewModel.placeCount.observe(viewLifecycleOwner) {
            binding.tvStatPlaces.text = it.toString()
        }
    }

    // ─────────────────────── 요약 ───────────────────────

    /**
     * "장면 3 / 17".
     *
     * 배지 개수가 아니라 장면 수를 셉니다. 배지는 받은 결과지만 장면은 남긴
     * 사진이라, 세는 단위가 곧 이 앱이 무엇을 하는 앱인지를 말합니다.
     */
    private fun renderSummary(list: List<MissionProgress>) {
        val scenes = list.sumOf { it.current }
        val total = Missions.totalScenes
        binding.tvMissionSummary.text = getString(R.string.mission_scene_count, scenes, total)

        val percent = if (total == 0) 0 else scenes * 100 / total
        binding.pbMissionTotal.progress = percent
        binding.tvMissionPercent.text = getString(R.string.mission_percent, percent)
        binding.tvMissionSummaryDesc.setText(
            if (scenes == 0) R.string.mission_summary_empty else R.string.mission_summary_desc
        )

        binding.tvStampCount.text = getString(
            R.string.stamp_count_format, list.size, list.count { it.isComplete }
        )

        // 나의 기록 — 지금까지 얼마나 했나.
        binding.tvStatScenes.text = "$scenes / $total"
        binding.tvStatStamps.text = list.count { it.isComplete }.toString()
        binding.tvStatSeason.text = Season.now().label

        renderTally(list)
    }

    /** 유형별로 몇 장면을 모았는지. 무엇에 치우쳤는지가 보입니다. */
    private fun renderTally(list: List<MissionProgress>) {
        val group = binding.chipsMissionTally
        group.removeAllViews()

        list.groupBy { it.mission.type }
            .mapValues { (_, items) -> items.sumOf { it.current } }
            .filterValues { it > 0 }
            .toList()
            .sortedByDescending { it.second }
            .forEach { (type, count) ->
                group.addView(
                    Chip(requireContext()).apply {
                        text = getString(R.string.mission_tally, type.label, count)
                        isClickable = false
                        isCheckable = false
                        setChipBackgroundColorResource(R.color.scrim_soft)
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                        chipStrokeWidth = 0f
                        textSize = 11f
                        chipMinHeight = resources.displayMetrics.density * 26
                    }
                )
            }
    }

    // ─────────────────────── 오늘의 추천 ───────────────────────

    /**
     * 스물 몇 개 중에서 고르라고 하면 아무것도 안 고릅니다.
     * 가장 가까이 있는 하나를 위로 꺼내 둡니다.
     *
     * 고르는 기준은 **진행 중인 것 중 가장 많이 채운 것**입니다. 반쯤 채운
     * 미션이 손도 안 댄 것보다 끝내고 싶어집니다.
     */
    private fun renderToday(list: List<MissionProgress>) {
        val today = list.firstOrNull { !it.isComplete && it.current > 0 }
            ?: list.firstOrNull { !it.isComplete }

        binding.tvTodayLabel.isVisible = today != null
        binding.cardTodayMission.isVisible = today != null
        if (today == null) return

        val type = today.mission.type
        binding.tvTodayIcon.text = type.icon
        binding.layoutTodayBadge.background?.mutate()?.setColorFilter(
            ContextCompat.getColor(requireContext(), type.softColorRes), PorterDuff.Mode.SRC_IN
        )
        binding.tvTodayTitle.text = today.mission.title
        binding.tvTodayDesc.text = today.mission.nextHint

        // 추천 시간·구도·방향. 미션 이름만으로는 언제 어디서 어떻게인지
        // 모릅니다. 값은 상세 화면과 같은 계산(SunTimes·ShotTokens)에서
        // 나오므로 두 화면이 다른 말을 하지 않습니다.
        renderTodayFacts()

        binding.cardTodayMission.setOnClickListener {
            // 추천을 누르면 그 유형만 남깁니다. 곧바로 비슷한 미션이
            // 모여 보여서 "이 갈래로 가겠다"는 결정이 이어집니다.
            selectFilter(type)
        }
    }

    /**
     * 오늘의 추천에 붙는 시간·구도·빛.
     *
     * 미션은 장소를 가리지 않는 것이 많아 방위를 특정할 수 없습니다. 그래서
     * 방향 자리에 **빛의 결**을 답니다. 장소가 정해지는 상세 화면에서는
     * 방위 토큰이 함께 나옵니다.
     */
    private fun renderTodayFacts() {
        val sun = mainViewModel.sunTimes
        val window = sun.upcomingGoldenWindows().firstOrNull()
            ?: sun.upcomingGoldenWindows(java.time.LocalTime.MIN).firstOrNull()

        binding.tvTodayTime.text = window?.let {
            getString(
                R.string.detail_window_range,
                "%02d:%02d".format(it.start.hour, it.start.minute),
                "%02d:%02d".format(it.end.hour, it.end.minute)
            )
        } ?: "—"

        val phase = window?.phase ?: sun.phaseNow()
        binding.tvTodayGuide.text = ShotTokens.of(GuideOverlayView.GuideType.THIRDS).text
        binding.tvTodayLight.text = ShotTokens.of(phase).text
    }

    // ─────────────────────── 필터 ───────────────────────

    private fun buildFilterChips() {
        val group = binding.chipsMissionFilter
        group.removeAllViews()

        group.addView(filterChip(null, getString(R.string.mission_filter_all), checked = true))
        // 미션이 하나도 없는 유형은 칩을 만들지 않습니다. 눌러도 빈 목록이
        // 나오는 칩은 화면만 늘립니다.
        MissionType.entries
            .filter { type -> Missions.ALL.any { it.type == type } }
            .forEach { group.addView(filterChip(it, it.label, checked = false)) }
    }

    private fun filterChip(type: MissionType?, label: String, checked: Boolean) =
        Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = checked
            setEnsureMinTouchTargetSize(false)
            setChipBackgroundColorResource(R.color.chip_bg_selector)
            setTextColor(ContextCompat.getColorStateList(context, R.color.chip_content_selector))
            chipStrokeWidth = resources.displayMetrics.density
            setChipStrokeColorResource(R.color.chip_stroke_selector)
            // 글자 아이콘(☀·☁) 대신 다른 화면과 같은 벡터 아이콘을 답니다.
            type?.let {
                setChipIconResource(it.iconRes)
                isChipIconVisible = true
                chipIconSize = resources.displayMetrics.density * 15
                setChipIconTintResource(R.color.chip_content_selector)
            }
            setOnClickListener { selectFilter(type) }
        }

    private fun selectFilter(type: MissionType?) {
        filter = type
        val group = binding.chipsMissionFilter
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            val chipType = if (i == 0) null else MissionType.entries
                .filter { t -> Missions.ALL.any { it.type == t } }[i - 1]
            chip.isChecked = chipType == type
        }
        applyFilter()
    }

    private fun applyFilter() {
        val shown = latest.filter { filter == null || it.mission.type == filter }
        adapter.submitList(shown)
        binding.tvMissionEmpty.isVisible = shown.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MissionViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DiaryRepository(app)

    /** 방문 기록이 바뀌면 미션 진행도가 자동으로 다시 계산됩니다. */
    private val days = repository.observeDays().asLiveData()

    val progress: LiveData<List<MissionProgress>> =
        days.map { list -> Missions.progress(list.flatMap { it.visits }) }

    /** 서로 다른 촬영지 수. 미션과 달리 사진이 없어도 셉니다(다녀온 것은 사실). */
    val placeCount: LiveData<Int> =
        days.map { list -> list.flatMap { it.visits }.distinctBy { it.contentId }.size }
}
