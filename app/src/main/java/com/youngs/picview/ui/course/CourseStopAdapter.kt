package com.youngs.picview.ui.course

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemCourseDayBinding
import com.youngs.picview.databinding.ItemCourseStopBinding
import com.youngs.picview.domain.course.CourseStop
import com.youngs.picview.domain.guide.SiseonGuide
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 타임라인의 한 줄.
 *
 * 정거장만 있던 목록에 **날짜 줄**을 더했습니다. 시각(13:42 · 15:33)만
 * 있으면 그것이 어느 날의 13시인지 알 수 없습니다. 지금은 하루짜리
 * 코스뿐이라 헷갈릴 일이 적지만, 지역을 넓히면 1박 2일 코스가 나오고
 * 그때는 "둘째 날 06시"와 "첫날 06시"가 같은 줄로 보이게 됩니다.
 */
sealed interface CourseRow {
    /** 날짜 구분선. [dayIndex] 가 0 이면 첫날입니다. */
    data class Day(val date: LocalDate, val dayIndex: Int) : CourseRow

    data class Stop(val stop: CourseStop) : CourseRow
}

/** 빛 스케줄 타임라인. */
class CourseStopAdapter(
    private val onClick: (CourseStop) -> Unit
) : ListAdapter<CourseRow, RecyclerView.ViewHolder>(DIFF) {

    class DayViewHolder(val binding: ItemCourseDayBinding) :
        RecyclerView.ViewHolder(binding.root)

    class StopViewHolder(val binding: ItemCourseStopBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is CourseRow.Day -> TYPE_DAY
        is CourseRow.Stop -> TYPE_STOP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DAY) {
            DayViewHolder(ItemCourseDayBinding.inflate(inflater, parent, false))
        } else {
            StopViewHolder(ItemCourseStopBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CourseRow.Day -> bindDay(holder as DayViewHolder, row)
            is CourseRow.Stop -> bindStop(holder as StopViewHolder, row.stop, position)
        }
    }

    /**
     * "9월 11일 (금)" — 이틀 이상이면 "둘째 날 · 9월 12일 (토)".
     *
     * 하루짜리 코스에서 "첫날"은 군더더기라 날짜만 씁니다.
     * 몇째 날인지는 이틀 이상일 때에만 뜻을 가집니다.
     */
    private fun bindDay(holder: DayViewHolder, row: CourseRow.Day) {
        val context = holder.itemView.context
        val date = row.date.format(DATE)
        val multiDay = currentList.count { it is CourseRow.Day } > 1

        holder.binding.tvDayLabel.text = if (multiDay) {
            context.getString(R.string.course_day_nth, row.dayIndex + 1, date)
        } else {
            date
        }
    }

    private fun bindStop(holder: StopViewHolder, stop: CourseStop, position: Int) {
        val context = holder.itemView.context
        val phaseColor = ContextCompat.getColor(context, stop.phase.colorRes)

        with(holder.binding) {
            tvStopTime.text = stop.arriveAt.format(TIME)
            tvStopTime.setTextColor(phaseColor)

            tvStopTitle.text = stop.spot.title
            tvStopScore.text = stop.spot.score.toString()
            tvStopScore.isVisible = stop.spot.score > 0

            // 노드 색 = 그 시간의 빛 색. 타임라인을 훑기만 해도 하루 흐름이 보입니다.
            viewStopNode.background?.mutate()
                ?.setColorFilter(phaseColor, PorterDuff.Mode.SRC_IN)

            // 축이 빛 구간의 이름을 직접 말합니다(시안 — 여명·골든아워·석양).
            tvStopPhaseWord.text = stop.phase.shortLabel
            tvStopPhaseWord.setTextColor(phaseColor)

            // 관광공사 API 대표 사진 그대로.
            Glide.with(ivStopPhoto)
                .load(stop.spot.imageUrl.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(ivStopPhoto)

            // 아래로 잇는 선은 다음 줄이 또 정거장일 때만 그립니다.
            // 날짜 줄이나 목록 끝에서 선이 허공으로 뻗으면 잘린 것처럼 보입니다.
            viewStopLine.isVisible = currentList.getOrNull(position + 1) is CourseRow.Stop

            tvStopPhase.text = "${stop.phase.label} · ${stop.facts.facing.phraseLabel}"
            tvStopPhase.setTextColor(phaseColor)
            tvStopPhase.background?.mutate()?.setColorFilter(
                ColorUtils.setAlphaComponent(phaseColor, PHASE_BADGE_ALPHA),
                PorterDuff.Mode.SRC_IN
            )

            tvStopReason.text = stop.reason

            // 구도는 카메라 오버레이의 세 종류가 아니라 시선 가이드의 열
            // 구도에서 고릅니다. 세 종류로는 "야간 · 문화시설" 세 칸이
            // 나란히 "좌우 대칭축"이 되어 추천이 없는 것과 같아집니다.
            // 도착 시각의 빛까지 함께 봅니다 — 같은 곳도 시간이 다르면
            // 찍는 법이 다르다는 것이 이 앱의 전제입니다.
            val guide = SiseonGuide.byId(SiseonGuide.guideIdFor(stop.facts, stop.phase))
            tvStopGuide.text = context.getString(R.string.course_guide_format, guide.title)

            if (stop.travelMinutes > 0) {
                tvStopTravel.isVisible = true
                tvStopTravel.text = context.getString(
                    R.string.course_travel_format, stop.travelMinutes, stop.travelKm
                )
            } else {
                tvStopTravel.isVisible = false
            }

            root.setOnClickListener { onClick(stop) }
        }
    }

    companion object {
        private const val TYPE_DAY = 0
        private const val TYPE_STOP = 1

        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

        /** 빛 색을 배지 배경으로 쓸 때의 투명도(0~255). */
        private const val PHASE_BADGE_ALPHA = 38

        /**
         * 정거장 목록에 날짜 줄을 끼워 넣습니다.
         *
         * 코스는 [java.time.LocalTime] 만 들고 있어 날짜가 없습니다. 그래서
         * **도착 시각이 앞 정거장보다 이르면 날이 넘어간 것**으로 봅니다 —
         * 23:00 다음의 01:00 은 자정을 지난 다음 날입니다. 하루 안에서는
         * 시각이 언제나 커지므로 오판할 여지가 없습니다.
         */
        fun rowsOf(stops: List<CourseStop>, startDate: LocalDate): List<CourseRow> {
            if (stops.isEmpty()) return emptyList()

            val rows = mutableListOf<CourseRow>()
            var dayIndex = 0
            var previous = stops.first().arriveAt

            rows += CourseRow.Day(startDate, dayIndex)

            stops.forEachIndexed { index, stop ->
                if (index > 0 && stop.arriveAt < previous) {
                    dayIndex++
                    rows += CourseRow.Day(startDate.plusDays(dayIndex.toLong()), dayIndex)
                }
                rows += CourseRow.Stop(stop)
                previous = stop.arriveAt
            }
            return rows
        }

        private val DIFF = object : DiffUtil.ItemCallback<CourseRow>() {
            override fun areItemsTheSame(oldItem: CourseRow, newItem: CourseRow) = when {
                oldItem is CourseRow.Day && newItem is CourseRow.Day ->
                    oldItem.date == newItem.date

                oldItem is CourseRow.Stop && newItem is CourseRow.Stop ->
                    oldItem.stop.spot.contentId == newItem.stop.spot.contentId &&
                        oldItem.stop.arriveAt == newItem.stop.arriveAt

                else -> false
            }

            override fun areContentsTheSame(oldItem: CourseRow, newItem: CourseRow) =
                oldItem == newItem
        }
    }
}
