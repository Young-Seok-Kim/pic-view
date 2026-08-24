package com.youngs.picview.ui.calendar

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentCalendarBinding
import com.youngs.picview.databinding.ItemSeasonHighlightBinding
import com.youngs.picview.domain.season.Season
import com.youngs.picview.domain.season.SeasonHighlight
import com.youngs.picview.domain.season.SeasonHighlights
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.main.MainViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 사계절 촬영 캘린더.
 *
 * 원래 관광공사 행사정보(searchFestival2)로 만들려 했는데, 정읍의 2026년
 * 등록 축제가 0건이라 화면이 비어 버립니다(전북 전체도 0건).
 * 그래서 축제 대신 **피사체의 절정 시기**를 기준으로 바꿨습니다.
 * 출사에서는 "축제가 언제인가"보다 "단풍이 언제 절정인가"가 더 중요합니다.
 */
class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCalendarBinding.bind(view)

        applyTopInset()
        binding.btnCalendarBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = HighlightAdapter { openSpot(it) }
        binding.rvCalendar.adapter = adapter

        binding.chipsSeason.setOnCheckedStateChangeListener { _, checked ->
            val season = seasonOf(checked.firstOrNull()) ?: return@setOnCheckedStateChangeListener
            adapter.submitList(SeasonHighlights.of(season))
            // 계절을 바꾸면 히어로와 달력도 그 계절로 옮깁니다. 가을을 눌렀는데
            // 달력이 1월에 머물면 점이 하나도 안 보입니다.
            shownMonth = season.monthToShow(YearMonth.now())
            renderMonth()
            renderHeadline(season)
            binding.tvNowGo.text = "${season.emoji} " + getString(R.string.calendar_now_go)
        }

        setupMonthNav()

        // 날짜를 누르면 그날에 맞는 피사체로 히어로가 바뀝니다.
        binding.viewMonthGrid.onDayClick = { day -> onDaySelected(shownMonth.atDay(day)) }

        // 지금 계절부터 보여 줍니다. 1월에 봄부터 보여 주면 쓸모가 없습니다.
        val now = Season.now()
        binding.chipsSeason.check(chipOf(now))
        adapter.submitList(SeasonHighlights.of(now))
        renderMonth()
        renderHeadline(now)
        binding.tvNowGo.text = "${now.emoji} " + getString(R.string.calendar_now_go)

        setupStats()
    }

    // ─────────────────────── 오늘의 지표 ───────────────────────

    /**
     * 촬영 지수 · 일출 · 습도.
     *
     * 촬영 지수는 지금 촬영지들의 포토스코어 중 최고점입니다 — "오늘 어딘가는
     * 이만큼 나온다"는 뜻이라, 평균보다 나갈 이유를 잘 말해 줍니다.
     */
    private fun setupStats() {
        mainViewModel.spotData.observe(viewLifecycleOwner) { spots ->
            val best = spots.orEmpty().maxOfOrNull { it.score } ?: 0
            binding.tvStatIndex.text = best.toString()
            binding.tvStatIndexLabel.setText(
                when {
                    best >= 85 -> R.string.calendar_index_great
                    best >= 70 -> R.string.calendar_index_good
                    best >= 55 -> R.string.calendar_index_normal
                    else -> R.string.calendar_index_low
                }
            )
        }

        mainViewModel.goldenHourData.observe(viewLifecycleOwner) {
            binding.tvStatSun.text =
                mainViewModel.sunTimes.sunrise?.format(DateTimeFormatter.ofPattern("HH:mm"))
                    ?: PLACEHOLDER
        }

        mainViewModel.humidityPercent.observe(viewLifecycleOwner) { humidity ->
            binding.tvStatHumid.text =
                humidity?.let { "${it.toInt()}%" } ?: PLACEHOLDER
        }
    }

    // ─────────────────────── 월 달력 ───────────────────────

    /** 달력이 보여 주는 달. */
    private var shownMonth: YearMonth = YearMonth.now()

    private fun setupMonthNav() {
        binding.btnMonthPrev.setOnClickListener {
            shownMonth = shownMonth.minusMonths(1)
            renderMonth()
        }
        binding.btnMonthNext.setOnClickListener {
            shownMonth = shownMonth.plusMonths(1)
            renderMonth()
        }
    }

    /**
     * 달력을 다시 그립니다.
     *
     * 절정 기간에 해당하는 날짜에 점을 찍습니다. 절정이 달을 넘어가는 경우
     * (예: 10/25~11/3)도 이 달에 걸치는 부분만 잘라 표시합니다.
     */
    private fun renderMonth() {
        binding.tvMonthLabel.text =
            getString(R.string.calendar_month_title, shownMonth.monthValue)

        // 달이 바뀌면 이전 달에서 고른 날짜 표시는 지웁니다.
        binding.viewMonthGrid.selectedDay = null

        val marked = mutableSetOf<Int>()
        val recommended = mutableSetOf<Int>()
        SeasonHighlights.ALL.forEach { highlight ->
            for (day in 1..shownMonth.lengthOfMonth()) {
                val date = shownMonth.atDay(day)
                when {
                    inPeak(highlight, date) -> marked += day
                    // 절정 앞뒤 닷새는 추천 — 절정만 찍으면 "그날 아니면
                    // 못 찍는다"로 읽히는데, 실제로는 어깨 기간도 좋습니다.
                    inPeak(highlight, date.plusDays(RECO_MARGIN_DAYS)) ||
                        inPeak(highlight, date.minusDays(RECO_MARGIN_DAYS)) -> recommended += day
                }
            }
        }

        binding.viewMonthGrid.yearMonth = shownMonth
        binding.viewMonthGrid.markedDays = marked
        binding.viewMonthGrid.recommendedDays = recommended - marked
    }

    /** [date] 가 이 피사체의 절정 구간 안인지. 연말을 넘기는 구간도 셉니다. */
    private fun inPeak(highlight: SeasonHighlight, date: LocalDate): Boolean {
        val md = MonthDay.from(date)
        val start = highlight.peakStart
        val end = highlight.peakEnd
        return if (start <= end) md >= start && md <= end
        else md >= start || md <= end
    }

    /**
     * 고른 계절의 대표 피사체를 히어로에 크게.
     *
     * 지금이 절정인 것을 먼저, 없으면 절정이 가장 가까운 것을 올립니다.
     * 배경 그림도 계절을 따라 바뀝니다 — 겨울을 골랐는데 단풍빛 카드면
     * 그림과 글이 서로 다른 말을 합니다.
     */
    private fun renderHeadline(season: Season) {
        val today = LocalDate.now()
        val candidates = SeasonHighlights.of(season)
        val next = candidates.firstOrNull { it.isPeakNow(today) }
            ?: candidates.minByOrNull { it.daysUntilPeak(today) }
            ?: return
        renderHeadlineFor(next, today)
    }

    /**
     * 달력 날짜를 누르면 그날에 맞는 피사체로 히어로를 바꿉니다.
     *
     * 그날이 절정인 피사체를 먼저, 없으면 그날 기준으로 절정이 가장 가까운
     * 피사체를 올립니다. 계절 칩은 건드리지 않습니다 — 칩을 바꾸면 달력이
     * 그 계절의 달로 넘어가 버려, 보고 있던 달력이 발밑에서 움직입니다.
     */
    private fun onDaySelected(date: LocalDate) {
        binding.viewMonthGrid.selectedDay = date.dayOfMonth
        val highlight = SeasonHighlights.ALL.firstOrNull { inPeak(it, date) }
            ?: SeasonHighlights.ALL.minByOrNull { it.daysUntilPeak(date) }
            ?: return
        renderHeadlineFor(highlight, date)
    }

    /** [date] 기준으로 히어로 카드를 채웁니다. 오늘이면 문구가 기존과 같습니다. */
    private fun renderHeadlineFor(next: SeasonHighlight, date: LocalDate) {
        val today = LocalDate.now()

        binding.ivCalendarHero.setImageResource(
            when (next.season) {
                Season.SPRING -> R.drawable.season_spring
                Season.SUMMER -> R.drawable.season_summer
                Season.AUTUMN -> R.drawable.season_autumn
                Season.WINTER -> R.drawable.season_winter
            }
        )

        val fmt = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
        binding.tvCalendarDday.text = when {
            date == today ->
                if (next.isPeakNow(today)) getString(R.string.calendar_peak_now)
                else getString(R.string.calendar_dday, next.daysUntilPeak(today))
            next.isPeakNow(date) ->
                getString(R.string.calendar_selected_peak, date.format(fmt))
            else ->
                getString(
                    R.string.calendar_selected_dday,
                    date.format(fmt), next.daysUntilPeak(date)
                )
        }
        binding.tvCalendarNowTitle.text = next.title
        binding.tvCalendarNowPeriod.text = periodText(next)
        binding.tvCalendarNowTip.text = next.tip

        binding.cardCalendarNow.setOnClickListener { openSpot(next) }
        binding.btnCalendarPoint.setOnClickListener { openSpot(next) }
    }

    private fun periodText(h: SeasonHighlight): String {
        val fmt = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
        val year = LocalDate.now().year
        return getString(
            R.string.calendar_period,
            h.peakStart.atYear(year).format(fmt),
            h.peakEnd.atYear(year).format(fmt)
        )
    }

    /** 해당 촬영지가 목록에 있으면 상세로 보냅니다. */
    private fun openSpot(highlight: SeasonHighlight) {
        val spot = mainViewModel.spotData.value
            ?.firstOrNull { it.title.contains(highlight.spotKeyword) }
            ?: return
        (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(spot))
    }

    private fun seasonOf(chipId: Int?) = when (chipId) {
        R.id.chip_spring -> Season.SPRING
        R.id.chip_summer -> Season.SUMMER
        R.id.chip_autumn -> Season.AUTUMN
        R.id.chip_winter -> Season.WINTER
        else -> null
    }

    private fun chipOf(season: Season) = when (season) {
        Season.SPRING -> R.id.chip_spring
        Season.SUMMER -> R.id.chip_summer
        Season.AUTUMN -> R.id.chip_autumn
        Season.WINTER -> R.id.chip_winter
    }

    private fun applyTopInset() {
        binding.rootCalendar.applyTopSystemBarInset()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PLACEHOLDER = "—"

        /** 절정 앞뒤로 이 일수만큼을 '추천'으로 표시합니다. */
        private const val RECO_MARGIN_DAYS = 5L
    }
}

private class HighlightAdapter(
    private val onClick: (SeasonHighlight) -> Unit
) : ListAdapter<SeasonHighlight, HighlightAdapter.VH>(DIFF) {

    class VH(val binding: ItemSeasonHighlightBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemSeasonHighlightBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
        val year = today.year

        with(holder.binding) {
            tvSeasonEmoji.text = item.emoji
            tvSeasonTitle.text = item.title
            tvSeasonTip.text = item.tip

            tvSeasonPeriod.text = context.getString(
                R.string.calendar_period_subject,
                item.peakStart.atYear(year).format(fmt),
                item.peakEnd.atYear(year).format(fmt),
                item.subject
            )

            val peakNow = item.isPeakNow(today)
            tvSeasonDday.text = if (peakNow) {
                context.getString(R.string.calendar_peak_now)
            } else {
                context.getString(R.string.calendar_dday, item.daysUntilPeak(today))
            }
            tvSeasonDday.isVisible = true

            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SeasonHighlight>() {
            override fun areItemsTheSame(oldItem: SeasonHighlight, newItem: SeasonHighlight) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SeasonHighlight, newItem: SeasonHighlight) =
                oldItem == newItem
        }
    }
}
