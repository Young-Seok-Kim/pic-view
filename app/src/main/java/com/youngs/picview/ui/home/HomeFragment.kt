package com.youngs.picview.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollHome) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
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
        bindStat(binding.statTemp, "🌡", viewModel.weatherData.value.orEmpty()
            .substringAfterLast(" ").ifBlank { "—" }, getString(R.string.home_stat_temp))
        bindStat(binding.statSky, "☀", phase.label, getString(R.string.home_stat_light))
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
        binding.btnHomeCalendar.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(CalendarFragment())
        }
        binding.tvHomeBestMore.setOnClickListener { goToTab(R.id.tab_explore) }
        binding.btnHomeMap.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(MapFragment())
        }
    }

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
