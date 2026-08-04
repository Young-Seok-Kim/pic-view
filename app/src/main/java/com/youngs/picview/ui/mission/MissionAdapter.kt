package com.youngs.picview.ui.mission

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemMissionBinding
import com.youngs.picview.domain.mission.MissionProgress

/** 미션 목록. 완료한 것은 배지 색이 채워집니다. */
class MissionAdapter :
    ListAdapter<MissionProgress, MissionAdapter.MissionViewHolder>(DIFF) {

    class MissionViewHolder(val binding: ItemMissionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MissionViewHolder(
        ItemMissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: MissionViewHolder, position: Int) {
        val item = getItem(position)
        val mission = item.mission
        val context = holder.itemView.context

        val accent = ContextCompat.getColor(
            context,
            if (item.isComplete) R.color.golden_500 else R.color.bg_card_muted
        )

        with(holder.binding) {
            tvMissionEmoji.text = mission.emoji
            // 미완료 미션은 배지를 흐리게 둡니다. 다 똑같이 보이면 목표가 안 생깁니다.
            tvMissionEmoji.alpha = if (item.isComplete) 1f else 0.35f
            layoutMissionBadge.background?.mutate()
                ?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)

            tvMissionName.text = mission.title
            tvMissionReq.text = mission.requirement
            tvMissionCount.text = context.getString(
                R.string.mission_count, item.current, item.target
            )
            tvMissionCount.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.isComplete) R.color.golden_600 else R.color.text_tertiary
                )
            )

            pbMission.progress = (item.ratio * 100).toInt()

            tvMissionBadgeName.isVisible = item.isComplete
            tvMissionBadgeName.text = context.getString(
                R.string.mission_badge_earned, mission.badge
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MissionProgress>() {
            override fun areItemsTheSame(oldItem: MissionProgress, newItem: MissionProgress) =
                oldItem.mission.id == newItem.mission.id

            override fun areContentsTheSame(
                oldItem: MissionProgress,
                newItem: MissionProgress
            ) = oldItem.current == newItem.current && oldItem.isComplete == newItem.isComplete
        }
    }
}
