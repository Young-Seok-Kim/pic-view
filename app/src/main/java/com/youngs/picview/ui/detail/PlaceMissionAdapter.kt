package com.youngs.picview.ui.detail

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemPlaceMissionBinding
import com.youngs.picview.domain.mission.MissionProgress

/**
 * 이 장소에서 지금 할 수 있는 미션.
 *
 * 전체 미션 목록과 같은 데이터를 씁니다(`missionId` 가 같습니다). 다른
 * 것은 **무엇을 보여 주느냐** 입니다. 전체 목록은 조건을 다 적어 "무엇을
 * 할까"를 고르게 하고, 여기서는 다음 행동 한 줄만 적어 "지금 이거 할래?"
 * 에 답하게 합니다.
 */
class PlaceMissionAdapter(
    private val onPick: (MissionProgress) -> Unit
) : ListAdapter<MissionProgress, PlaceMissionAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemPlaceMissionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemPlaceMissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val type = item.mission.type
        val context = holder.itemView.context

        with(holder.binding) {
            tvPlaceMissionIcon.text = type.icon
            layoutPlaceMissionIcon.background?.mutate()?.setColorFilter(
                ContextCompat.getColor(context, type.softColorRes), PorterDuff.Mode.SRC_IN
            )
            tvPlaceMissionTitle.text = item.mission.title
            tvPlaceMissionHint.text = item.mission.nextHint
            tvPlaceMissionCount.text = context.getString(
                R.string.mission_count, item.current, item.target
            )
            tvPlaceMissionCount.setTextColor(ContextCompat.getColor(context, type.colorRes))

            rowPlaceMission.setOnClickListener { onPick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MissionProgress>() {
            override fun areItemsTheSame(oldItem: MissionProgress, newItem: MissionProgress) =
                oldItem.mission.id == newItem.mission.id

            override fun areContentsTheSame(
                oldItem: MissionProgress,
                newItem: MissionProgress
            ) = oldItem == newItem
        }
    }
}
