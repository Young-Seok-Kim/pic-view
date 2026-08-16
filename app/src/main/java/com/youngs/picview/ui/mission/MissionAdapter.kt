package com.youngs.picview.ui.mission

import android.content.res.ColorStateList
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

/**
 * 미션 목록.
 *
 * 예전에는 완료한 것만 색이 살고 나머지는 통째로 흐렸습니다. 그러면 목록이
 * "아직 못 한 것들"로 읽혀서 볼수록 하기 싫어집니다. 지금은 유형 색을
 * 언제나 켜 두고, 완료한 것에는 체크를 **더합니다.** 아이콘을 바꾸지
 * 않으니 같은 미션이라는 것이 유지됩니다.
 */
class MissionAdapter(
    private val onPick: (MissionProgress) -> Unit = {}
) : ListAdapter<MissionProgress, MissionAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemMissionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemMissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val mission = item.mission
        val type = mission.type
        val context = holder.itemView.context

        with(holder.binding) {
            tvMissionEmoji.text = type.icon
            layoutMissionBadge.background?.mutate()?.setColorFilter(
                ContextCompat.getColor(context, type.softColorRes), PorterDuff.Mode.SRC_IN
            )
            tvMissionCheck.isVisible = item.isComplete

            tvMissionName.text = mission.title
            tvMissionReq.text = mission.requirement
            tvMissionCount.text = context.getString(
                R.string.mission_count, item.current, item.target
            )
            tvMissionCount.setTextColor(ContextCompat.getColor(context, type.colorRes))

            pbMission.progress = (item.ratio * 100).toInt()
            // 진행 막대도 유형 색을 씁니다. 목록을 훑을 때 색이 갈래를
            // 되풀이해 줘서 아이콘을 하나하나 확인하지 않게 됩니다.
            //
            // progressDrawable 에 색 필터를 걸면 **바탕 레이어까지** 물들어
            // 0/3 인 막대가 꽉 찬 것처럼 보입니다. 진행 레이어만 물들이는
            // progressTintList 를 씁니다.
            pbMission.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, type.colorRes)
            )

            tvMissionStatus.text = item.statusLine
            tvMissionStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.isComplete) type.colorRes else R.color.text_tertiary
                )
            )

            tvMissionTags.isVisible = mission.tags.isNotEmpty()
            tvMissionTags.text = mission.tags.joinToString("  ") { "#$it" }

            cardMission.strokeColor = ContextCompat.getColor(
                context,
                if (item.isComplete) type.colorRes else R.color.card_stroke
            )
            cardMission.setOnClickListener { onPick(item) }
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
