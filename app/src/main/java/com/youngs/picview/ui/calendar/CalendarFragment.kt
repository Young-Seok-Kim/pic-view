package com.youngs.picview.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
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
        }

        // 지금 계절부터 보여 줍니다. 1월에 봄부터 보여 주면 쓸모가 없습니다.
        binding.chipsSeason.check(chipOf(Season.now()))
        adapter.submitList(SeasonHighlights.of(Season.now()))

        renderHeadline()
    }

    /** 지금 절정이거나 가장 임박한 피사체를 상단에 크게. */
    private fun renderHeadline() {
        val next = SeasonHighlights.upcoming(limit = 1).firstOrNull() ?: return
        val today = LocalDate.now()

        binding.tvCalendarDday.text = if (next.isPeakNow(today)) {
            getString(R.string.calendar_peak_now)
        } else {
            getString(R.string.calendar_dday, next.daysUntilPeak(today))
        }
        binding.tvCalendarNowTitle.text = "${next.emoji} ${next.title}"
        binding.tvCalendarNowPeriod.text = periodText(next)
        binding.tvCalendarNowTip.text = next.tip

        binding.cardCalendarNow.setOnClickListener { openSpot(next) }
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCalendar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
