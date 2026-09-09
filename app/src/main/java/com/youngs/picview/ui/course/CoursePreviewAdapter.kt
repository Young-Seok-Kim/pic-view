package com.youngs.picview.ui.course

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemCoursePreviewBinding
import com.youngs.picview.domain.course.CourseStop
import java.time.format.DateTimeFormatter

/**
 * 계획 탭 "오늘의 빛 코스" 가로 카드 목록.
 *
 * 한 장이 정거장 하나입니다. 페이지 스냅으로 한 장씩 넘겨 보고,
 * 카드의 첫 정보는 이름이 아니라 **도착 권장 시각**입니다 — 이 화면의
 * 약속이 "어디"가 아니라 "몇 시에 가면 빛이 맞는가"이기 때문입니다.
 */
class CoursePreviewAdapter(
    private val onClick: (CourseStop) -> Unit
) : ListAdapter<CourseStop, CoursePreviewAdapter.StopViewHolder>(DIFF) {

    class StopViewHolder(val binding: ItemCoursePreviewBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = StopViewHolder(
        ItemCoursePreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: StopViewHolder, position: Int) {
        val stop = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvStopOrdinal.text = ordinalOf(position)
            tvStopTitle.text = stop.spot.title
            tvStopAddr.text = shortAddress(stop.spot.addr1)
            tvStopArrive.text = stop.arriveAt.format(HOUR_MINUTE)

            val moving = stop.travelMinutes > 0
            tvStopTravelLabel.isVisible = moving
            tvStopTravel.text = if (moving) {
                context.getString(R.string.course_travel_minutes, stop.travelMinutes)
            } else {
                context.getString(R.string.course_travel_start)
            }

            // "왜 이 시각에 여기인가"가 코스의 말, 없으면 장소의 촬영 지식으로.
            tvStopNote.text = stop.reason.ifBlank { stop.facts.note }

            Glide.with(ivStopImage)
                .load(stop.spot.imageUrl.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(ivStopImage)

            cardStop.setOnClickListener { onClick(stop) }
        }
    }

    /**
     * 시·도 접두어를 뗀 주소.
     *
     * TourAPI 는 "전북특별자치도 정읍시 시기4길 13"처럼 줍니다. 정읍만
     * 다루는 화면에서 앞의 여덟 글자는 매 카드마다 같은 말을 반복하며
     * 정작 필요한 길 이름을 밀어냅니다.
     */
    private fun shortAddress(addr: String): String = addr
        .removePrefix("전북특별자치도 ")
        .removePrefix("전라북도 ")
        .trim()

    /** 시안 표기(1st·2nd…)를 따릅니다. 4번째부터는 4th, 5th 로 이어집니다. */
    private fun ordinalOf(position: Int): String = when (position) {
        0 -> "1st"
        1 -> "2nd"
        2 -> "3rd"
        else -> "${position + 1}th"
    }

    companion object {
        private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private val DIFF = object : DiffUtil.ItemCallback<CourseStop>() {
            override fun areItemsTheSame(oldItem: CourseStop, newItem: CourseStop) =
                oldItem.spot.contentId == newItem.spot.contentId &&
                    oldItem.arriveAt == newItem.arriveAt

            override fun areContentsTheSame(oldItem: CourseStop, newItem: CourseStop) =
                oldItem == newItem
        }
    }
}
