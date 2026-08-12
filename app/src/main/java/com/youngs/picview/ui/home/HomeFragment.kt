package com.youngs.picview.ui.home

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentHomeBinding
import com.youngs.picview.ui.calendar.CalendarFragment
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.map.MapFragment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import android.graphics.PorterDuff
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.youngs.picview.databinding.ItemQuickActionBinding
import com.youngs.picview.ui.mission.MissionFragment

/**
 * 홈 — "지금 빛이 어떤가"를 첫 화면에서 답합니다.
 *
 * 시안의 홈은 AI 캐릭터 인사말이 히어로였지만, 출사 앱에서 사용자가
 * 앱을 여는 이유는 하나입니다. 지금 나가야 하는지 아닌지.
 * 그래서 히어로를 현재 빛 구간 + 다음 골든아워 카운트다운으로 바꿨습니다.
 */
class HomeFragment : Fragment(R.layout.fragment_home), MainActivity.TabRoot {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        applyTopInset()
        renderDate()
        setupActions()
        setupBestSpots()
        renderLight()
    }

    private fun applyTopInset() {
        binding.scrollHome.applyTopSystemBarInset()
    }

    private fun renderDate() {
        binding.tvHomeDate.text = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
    }

    // ─────────────────────── 지금의 빛 ───────────────────────

    private fun renderLight() {
        val sun = viewModel.sunTimes
        val phase = sun.phaseNow()

        binding.tvLightPhaseLabel.text = getString(R.string.home_now_is)
        binding.tvLightPhase.text = phase.label
        binding.tvLightHint.text = phase.hint

        binding.viewDayLight.sunTimes = sun

        val minutes = sun.minutesToNextGolden()
        binding.tvNextGolden.text = when {
            !sun.hasData -> getString(R.string.course_sun_unknown)
            phase.isGolden -> getString(R.string.course_golden_now)
            minutes != null -> getString(
                R.string.home_next_golden, minutes / 60, minutes % 60
            )
            else -> getString(R.string.home_golden_tomorrow)
        }

        // 현재 촬영 조건
        val temp = viewModel.temperatureC.value
        bindStat(
            binding.statTemp, "🌡",
            if (temp != null) "${temp.roundToInt()}℃" else "—",
            getString(R.string.home_stat_temp)
        )
        bindStat(binding.statSky, "☀", phase.shortLabel, getString(R.string.home_stat_light))
        bindStat(
            binding.statSpots, "📍",
            "${viewModel.spotData.value?.size ?: 0}", getString(R.string.home_stat_spots)
        )
    }

    private fun bindStat(
        stat: com.youngs.picview.databinding.ItemHomeStatBinding,
        icon: String,
        value: String,
        label: String
    ) {
        stat.tvStatIcon.text = icon
        stat.tvStatValue.text = value
        stat.tvStatLabel.text = label
    }

    // ─────────────────────── 목록 ───────────────────────

    private fun setupBestSpots() {
        val adapter = HomeSpotAdapter { spot ->
            (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(spot))
        }
        binding.rvHomeBest.adapter = adapter

        viewModel.spotData.observe(viewLifecycleOwner) { spots ->
            adapter.submitList(spots.orEmpty().take(BEST_COUNT))
            renderLight()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressHome.isVisible = loading
        }

        viewModel.goldenHourData.observe(viewLifecycleOwner) { renderLight() }
    }

    private fun setupActions() {
        binding.btnHomeMakeCourse.setOnClickListener { goToTab(R.id.tab_course) }
        binding.tvHomeBestMore.setOnClickListener { goToTab(R.id.tab_explore) }
        buildQuickActions()
    }

    /**
     * 빠른 이동 타일.
     *
     * 전에는 넓은 버튼 두 개(지도·촬영 캘린더)였습니다. 버튼은 "누르면 뭔가
     * 실행된다"는 신호라 화면 이동에는 무겁고, 두 개뿐이라 미션·일기 같은
     * 나머지 기능이 홈에서 보이지 않았습니다.
     *
     * 타일 색은 시안의 QA_0~3 에서 가져왔습니다. 넷을 나란히 두었을 때 서로
     * 구분되게 하려는 것이고, 단풍 한 계열만 쓰던 화면에 숨통을 틔워 줍니다.
     */
    private fun buildQuickActions() {
        val container = binding.layoutQuickActions
        container.removeAllViews()

        val actions = listOf(
            QuickAction("🗺", R.string.home_open_map, R.color.qa_course) {
                (activity as? MainActivity)?.pushScreen(MapFragment())
            },
            QuickAction("📅", R.string.home_qa_calendar, R.color.qa_calendar) {
                (activity as? MainActivity)?.pushScreen(CalendarFragment())
            },
            QuickAction("📓", R.string.tab_diary, R.color.qa_diary) {
                goToTab(R.id.tab_diary)
            },
            QuickAction("🏅", R.string.home_qa_mission, R.color.qa_live) {
                (activity as? MainActivity)?.pushScreen(MissionFragment())
            }
        )

        actions.forEachIndexed { index, action ->
            val item = ItemQuickActionBinding.inflate(layoutInflater, container, false)
            item.tvQaEmoji.text = action.emoji
            item.tvQaLabel.setText(action.labelRes)
            item.layoutQaTile.background?.mutate()?.setColorFilter(
                ContextCompat.getColor(requireContext(), action.colorRes),
                PorterDuff.Mode.SRC_IN
            )
            item.layoutQaTile.setOnClickListener { action.onClick() }

            // 타일 사이 간격. 마지막 칸 뒤에는 넣지 않습니다.
            if (index < actions.lastIndex) {
                (item.root.layoutParams as ViewGroup.MarginLayoutParams).marginEnd =
                    resources.getDimensionPixelSize(R.dimen.space_s)
            }
            container.addView(item.root)
        }
    }

    private data class QuickAction(
        val emoji: String,
        @StringRes val labelRes: Int,
        @ColorRes val colorRes: Int,
        val onClick: () -> Unit
    )

    private fun goToTab(tabId: Int) {
        (activity as? MainActivity)?.selectTab(tabId)
    }

    override fun scrollToTop() {
        _binding?.scrollHome?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 홈에 노출할 상위 스팟 수. */
        private const val BEST_COUNT = 8
    }
}
