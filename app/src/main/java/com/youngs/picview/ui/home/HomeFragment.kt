package com.youngs.picview.ui.home

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.guide.SiseonGuideActivity
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.SpotBookmark
import kotlinx.coroutines.launch

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

    /** 지금 목록의 맨 위 장소. 시선 가이드가 이 장소의 말로 시작합니다. */
    private var topSpot: SpotItem? = null

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
            .format(
                DateTimeFormatter.ofPattern(
                    getString(R.string.course_date_format), Locale.KOREAN
                )
            )
        // 날짜 알약을 누르면 촬영 캘린더로 — 시안의 헤더 동선입니다.
        binding.cardHomeDate.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(CalendarFragment())
        }
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
            binding.tvDaylightLine.text = highlightDuration(
                getString(R.string.sun_arc_daylight, minutes / 60, minutes % 60),
                "${minutes / 60}시간 ${minutes % 60}분"
            )
        }
    }

    /**
     * 문장 안의 시간만 굵고 진하게.
     *
     * 문장 전체가 한 색이면 "13시간 29분"을 찾으려고 처음부터 읽어야 합니다.
     * 나머지는 매일 같은 말이고 바뀌는 건 숫자뿐입니다.
     */
    private fun highlightDuration(text: String, duration: String): CharSequence {
        val start = text.indexOf(duration)
        if (start < 0) return text

        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.maple_600)),
                start, start + duration.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                start, start + duration.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
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

        binding.statWeather.viewTempCurve.temps = viewModel.hourlyTemps.value.orEmpty()

        val sky = viewModel.skyState.value
        binding.tvLightSky.isVisible = sky != null
        if (sky != null) binding.tvLightSky.text = "${sky.emoji} ${sky.label}"
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
        val adapter = HomeSpotAdapter(
            sunTimes = { viewModel.sunTimes },
            onClick = { spot ->
                (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(spot))
            },
            onPlanClick = { spot -> savePlan(spot) }
        )
        binding.rvHomeBest.adapter = adapter

        viewModel.spotData.observe(viewLifecycleOwner) { spots ->
            val best = spots.orEmpty().take(BEST_COUNT)
            adapter.submitList(best)
            topSpot = best.firstOrNull()
            renderLight()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressHome.isVisible = loading
        }

        viewModel.goldenHourData.observe(viewLifecycleOwner) { renderLight() }

        // 예보는 실황·촬영지보다 늦게 도착할 수 있습니다. 도착하면 다시 그립니다.
        viewModel.hourlyTemps.observe(viewLifecycleOwner) { renderWeather() }
    }

    private fun setupActions() {
        binding.tvHomeBestMore.setOnClickListener { goToTab(R.id.tab_explore) }

        // 시선 가이드로. 맨 위 추천 장소가 있으면 그 장소의 빛·구도로
        // 시작하고, 없으면 가이드의 기본 장면으로 엽니다.
        val openGuide = View.OnClickListener {
            val spot = topSpot
            val intent = if (spot != null) {
                val facts = SpotFactsTable.of(spot.title, spot.contentTypeId)
                SiseonGuideActivity.intent(
                    requireContext(),
                    spotTitle = spot.title,
                    contextId = SiseonGuide.contextIdFor(facts.bestPhase),
                    guideId = SiseonGuide.guideIdFor(facts)
                )
            } else {
                SiseonGuideActivity.intent(requireContext())
            }
            startActivity(intent)
        }
        binding.cardGuideEntry.setOnClickListener(openGuide)
        binding.btnGuideOpen.setOnClickListener(openGuide)

        // 배너 제목에서 "시선 가이드" 낱말만 주황으로(시안 표기).
        // 문장 전체가 한 색이면 어디를 누르라는 건지 눈이 다시 찾아야 합니다.
        val bannerTitle = getString(R.string.home_guide_title)
        val highlight = getString(R.string.home_guide_highlight)
        val start = bannerTitle.indexOf(highlight)
        if (start >= 0) {
            binding.tvGuideBannerTitle.text = SpannableString(bannerTitle).apply {
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(requireContext(), R.color.maple_500)
                    ),
                    start, start + highlight.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        buildQuickActions()
    }

    /**
     * 카드의 "담아 두기" — 그 장소를 찜합니다.
     *
     * 예전에는 한 곳짜리 코스를 만들어 코스 목록에 넣었습니다. 담은 곳들이
     * 모여 한 계획이 되는 것이 아니라 저장할 때마다 따로 놀았습니다.
     * 지금은 찜 하나로 모이고, 코스 탭의 '직접 고르기'에서 그 찜한 곳을
     * 골라 담으면 순서는 빛이 세웁니다.
     */
    private fun savePlan(spot: SpotItem) {
        val added = SpotBookmark.toggle(requireContext(), spot)
        Toast.makeText(
            requireContext(),
            if (added) R.string.bookmark_added else R.string.bookmark_removed,
            Toast.LENGTH_SHORT
        ).show()
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
