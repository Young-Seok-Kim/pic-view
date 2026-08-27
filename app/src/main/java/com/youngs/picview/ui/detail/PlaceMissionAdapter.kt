package com.youngs.picview.ui.detail

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
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
            renderDots(layoutPlaceMissionDots, item.current, item.target, type.colorRes)

            rowPlaceMission.setOnClickListener { onPick(item) }
        }
    }

    /**
     * 진행도를 도트로도 겹쳐 보여 줍니다 (●●○).
     *
     * 숫자는 좁은 칸에서 가장 먼저 잘리는 것입니다. 도트는 잘려도 남은
     * 개수가 보이고, 무엇보다 "두 개 중 하나"가 계산 없이 읽힙니다.
     */
    private fun renderDots(container: LinearLayout, current: Int, target: Int, colorRes: Int) {
        container.removeAllViews()
        if (target <= 0 || target > MAX_DOTS) return

        val context = container.context
        val density = context.resources.displayMetrics.density
        val size = (5 * density).toInt()
        val gap = (3 * density).toInt()
        val filled = ContextCompat.getColor(context, colorRes)
        val empty = ContextCompat.getColor(context, R.color.divider)

        repeat(target) { index ->
            container.addView(
                View(context).apply {
                    setBackgroundResource(R.drawable.dot_indicator_active)
                    backgroundTintList = ColorStateList.valueOf(
                        if (index < current) filled else empty
                    )
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        if (index > 0) marginStart = gap
                    }
                }
            )
        }
    }

    companion object {
        /** 도트로 세는 것이 숫자보다 빠른 한계. 이보다 많으면 숫자만 둡니다. */
        private const val MAX_DOTS = 6

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
