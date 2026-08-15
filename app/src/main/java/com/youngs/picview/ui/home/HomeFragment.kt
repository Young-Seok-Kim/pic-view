package com.youngs.picview.ui.home

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.view.isVisible
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunEvent
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.util.byBatchim
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import android.graphics.PorterDuff
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
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
        val now = LocalTime.now()
        val phase = sun.phaseAt(now)

        // 카드 바탕도 지금의 빛을 따라갑니다. 붉은 카드가 "야간"이라고
        // 말하고 있으면 글자와 색이 서로 다른 말을 합니다.
        binding.cardLight.setCardBackgroundColor(
            ContextCompat.getColor(requireContext(), phase.heroColorRes)
        )

        binding.tvLightNow.text = buildNowLine(sun, now)
        binding.tvLightPhase.text = buildPhaseHeadline(phase)
        binding.tvLightHint.text = getString(R.string.home_subject_line, phase.subject)

        binding.viewDayLight.sunTimes = sun

        renderNextShoot(sun, now)
        renderDaylight(sun)
        renderWeather()
    }

    /**
     * "지금은 **야간**이에요." — 구간 이름만 색을 올립니다.
     *
     * 문장 전체가 한 색이면 눈이 왼쪽부터 읽어 내려가야 답을 만납니다.
     * 정작 궁금한 낱말은 가운데 하나뿐이라 그것만 띄웁니다.
     *
     * 어미는 받침에 따라 갈립니다. "야간이에요" 와 "일출 골든아워예요" 를
     * 하나로 고정하면 여덟 구간 중 절반이 어색해집니다.
     */
    private fun buildPhaseHeadline(phase: LightPhase): CharSequence {
        val text = getString(
            R.string.home_is_now,
            phase.label,
            phase.label.byBatchim(
                getString(R.string.home_copula_batchim),
                getString(R.string.home_copula_plain)
            )
        )
        val start = text.indexOf(phase.label)
        if (start < 0) return text

        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.hero_accent)),
                start, start + phase.label.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** "지금 20:20 · 일몰 후 58분" — 앞의 점은 살아 있는 관측이라는 표시입니다. */
    private fun buildNowLine(sun: SunTimes, now: LocalTime): String {
        val head = "● " + getString(R.string.home_now_at, now.format(HOUR_MINUTE))
        val gap = sun.nearestEvent(now) ?: return head

        val text = durationText(gap.minutes)
        val tail = when {
            gap.event == SunEvent.SUNRISE && gap.isBefore -> R.string.home_gap_before_sunrise
            gap.event == SunEvent.SUNRISE -> R.string.home_gap_after_sunrise
            gap.isBefore -> R.string.home_gap_before_sunset
            else -> R.string.home_gap_after_sunset
        }
        return "$head · " + getString(tail, text)
    }

    /**
     * 다음 출사 적기.
     *
     * 문장과 남은 시간을 나눠 답니다. 문장은 "언제 무엇을", 알약은 "얼마나
     * 남았나" 입니다. 둘을 한 줄에 합치면 길어져서 정작 시각이 안 읽힙니다.
     */
    private fun renderNextShoot(sun: SunTimes, now: LocalTime) {
        val next = sun.nextGolden(now)
        if (next == null) {
            binding.tvLightNext.isVisible = false
            binding.tvNextGolden.text = getString(R.string.course_sun_unknown)
            return
        }

        binding.tvLightNext.isVisible = true
        binding.tvLightNext.text = getString(
            R.string.home_next_shoot,
            getString(
                if (next.isTomorrow) R.string.home_next_tomorrow else R.string.home_next_today
            ),
            next.start.format(HOUR_MINUTE),
            getString(
                if (next.phase == LightPhase.SUNRISE) R.string.home_next_tail_sunrise
                else R.string.home_next_tail_sunset
            )
        )

        binding.tvNextGolden.text = if (sun.phaseAt(now).isGolden) {
            getString(R.string.course_golden_now)
        } else {
            getString(R.string.home_until_shoot, durationText(next.minutesAway))
        }
    }

    /** 낮 길이. 일출·일몰이 다 있을 때만 보입니다. */
    private fun renderDaylight(sun: SunTimes) {
        val sunrise = sun.sunrise
        val sunset = sun.sunset
        val minutes = if (sunrise != null && sunset != null) {
            (sunset.toSecondOfDay() - sunrise.toSecondOfDay()) / 60
        } else 0

        binding.tvDaylightLine.isVisible = minutes > 0
        if (minutes > 0) {
            binding.tvDaylightLine.text =
                getString(R.string.sun_arc_daylight, minutes / 60, minutes % 60)
        }
    }

    /** 한 시간이 안 되면 "분"만 씁니다. "0시간 58분"은 읽는 데 방해가 됩니다. */
    private fun durationText(minutes: Long): String = if (minutes >= 60) {
        getString(R.string.home_duration_hm, minutes / 60, minutes % 60)
    } else {
        getString(R.string.home_duration_m, minutes)
    }

    /**
     * 기온 · 체감 · 습도.
     *
     * 셋을 한 줄에 둡니다. 기온만으로는 "삼각대 들고 30분 서 있을 만한가"가
     * 안 나옵니다. 여름엔 습도가, 겨울엔 바람이 그 답을 바꿉니다.
     */
    private fun renderWeather() {
        val temp = viewModel.temperatureC.value
        val feels = viewModel.feelsLikeC.value
        val humidity = viewModel.humidityPercent.value

        binding.statWeather.tvWeatherTemp.text = temp?.let { "${it.roundToInt()}℃" } ?: "—"
        binding.statWeather.tvWeatherFeels.text = feels?.let { "${it.roundToInt()}℃" } ?: "—"
        binding.statWeather.tvWeatherHumidity.text =
            humidity?.let { "${it.roundToInt()}%" } ?: "—"

        binding.statWeather.tvWeatherNote.text = weatherNote(temp, feels)
        binding.statWeather.tvWeatherNote.isVisible = temp != null && feels != null
    }

    /**
     * 기온과 체감의 차이를 한 줄로 풀어 씁니다.
     *
     * 숫자 셋을 나란히 두면 "그래서 어떻다는 건가"가 남습니다. 차이가
     * 어디서 왔는지(여름은 습도, 겨울은 바람) 말해 주면 숫자가 판단이 됩니다.
     *
     * 1℃ 차이는 말하지 않습니다. 반올림 때문에 생기는 값이라 근거가 약합니다.
     */
    private fun weatherNote(temp: Double?, feels: Double?): String {
        if (temp == null || feels == null) return ""
        val diff = (feels - temp).roundToInt()
        return when {
            diff >= 2 -> getString(R.string.home_weather_hotter, diff)
            diff <= -2 -> getString(R.string.home_weather_colder, -diff)
            else -> getString(R.string.home_weather_same)
        }
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

        // 일기는 하단 탭에 이미 있어 타일까지 두면 같은 곳으로 가는 길이
        // 두 개가 됩니다. 대신 이 화면의 주 행동인 빛 스케줄을 첫 칸에 둡니다.
        val actions = listOf(
            QuickAction(
                R.drawable.qa_course, R.string.home_qa_course, R.string.home_qa_course_desc
            ) { goToTab(R.id.tab_course) },
            QuickAction(
                R.drawable.qa_map, R.string.home_open_map, R.string.home_qa_map_desc
            ) { (activity as? MainActivity)?.pushScreen(MapFragment()) },
            QuickAction(
                R.drawable.qa_calendar, R.string.home_qa_calendar, R.string.home_qa_calendar_desc
            ) { (activity as? MainActivity)?.pushScreen(CalendarFragment()) },
            QuickAction(
                R.drawable.qa_mission, R.string.home_qa_mission, R.string.home_qa_mission_desc
            ) { (activity as? MainActivity)?.pushScreen(MissionFragment()) }
        )

        // 타일 그림은 정사각형입니다. 화면 폭에서 여백과 칸 사이 간격을 뺀 뒤
        // 넷으로 나눠 높이를 직접 정합니다. adjustViewBounds 만으로는
        // 칸마다 높이가 조금씩 달라져 라벨 줄이 어긋납니다.
        val gap = resources.getDimensionPixelSize(R.dimen.space_s)
        val side = resources.getDimensionPixelSize(R.dimen.screen_padding)
        val tile = (resources.displayMetrics.widthPixels - side * 2 - gap * 3) / actions.size

        actions.forEachIndexed { index, action ->
            val item = ItemQuickActionBinding.inflate(layoutInflater, container, false)
            item.ivQaIcon.setImageResource(action.iconRes)
            item.ivQaIcon.layoutParams.height = tile
            item.tvQaLabel.setText(action.labelRes)
            item.tvQaDesc.setText(action.descRes)
            item.ivQaIcon.contentDescription = getString(action.labelRes)
            item.ivQaIcon.setOnClickListener { action.onClick() }

            // 타일 사이 간격. 마지막 칸 뒤에는 넣지 않습니다.
            if (index < actions.lastIndex) {
                (item.root.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = gap
            }
            container.addView(item.root)
        }
    }

    private data class QuickAction(
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int,
        @StringRes val descRes: Int,
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

        private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
