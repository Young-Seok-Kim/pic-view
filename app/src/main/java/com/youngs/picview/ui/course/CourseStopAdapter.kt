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
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemCourseStopBinding
import com.youngs.picview.domain.course.CourseStop
import com.youngs.picview.ui.guide.GuideOverlayView
import java.time.format.DateTimeFormatter

/** 빛 스케줄 타임라인. */
class CourseStopAdapter(
    private val onClick: (CourseStop) -> Unit
) : ListAdapter<CourseStop, CourseStopAdapter.StopViewHolder>(DIFF) {

    class StopViewHolder(val binding: ItemCourseStopBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = StopViewHolder(
        ItemCourseStopBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
        val stop = getItem(position)
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

            // 마지막 정거장은 아래로 이어지는 선을 그리지 않습니다.
            viewStopLine.isVisible = position < itemCount - 1

            tvStopPhase.text = "${stop.phase.label} · ${stop.facts.facing.label}"
            tvStopPhase.setTextColor(phaseColor)
            tvStopPhase.background?.mutate()?.setColorFilter(
                ColorUtils.setAlphaComponent(phaseColor, PHASE_BADGE_ALPHA),
                PorterDuff.Mode.SRC_IN
            )

            tvStopReason.text = stop.reason

            tvStopGuide.text = context.getString(
                R.string.course_guide_format, guideName(context, stop.facts.guide)
            )

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

    private fun guideName(
        context: android.content.Context,
        type: GuideOverlayView.GuideType
    ): String = context.getString(
        when (type) {
            GuideOverlayView.GuideType.THIRDS -> R.string.guide_name_thirds
            GuideOverlayView.GuideType.SYMMETRY -> R.string.guide_name_symmetry
            GuideOverlayView.GuideType.CENTER -> R.string.guide_name_center
        }
    )

    companion object {
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** 빛 색을 배지 배경으로 쓸 때의 투명도(0~255). */
        private const val PHASE_BADGE_ALPHA = 38

        private val DIFF = object : DiffUtil.ItemCallback<CourseStop>() {
            override fun areItemsTheSame(oldItem: CourseStop, newItem: CourseStop) =
                oldItem.spot.contentId == newItem.spot.contentId &&
                        oldItem.arriveAt == newItem.arriveAt

            override fun areContentsTheSame(oldItem: CourseStop, newItem: CourseStop) =
                oldItem == newItem
        }
    }
}
