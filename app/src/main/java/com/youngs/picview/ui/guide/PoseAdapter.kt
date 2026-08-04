package com.youngs.picview.ui.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemPoseBinding
import com.youngs.picview.domain.pose.PoseScore

/**
 * 포즈 추천 목록. 지금 빛에 맞는 순서로 정렬된 상태로 들어옵니다.
 *
 * 고른 포즈는 카드 색을 바꿔 표시합니다. 카메라 미리보기 위에 얹히는
 * 목록이라 배경이 어두워, 선택 여부를 테두리만으로 알리기엔 약합니다.
 */
class PoseAdapter(
    private val onSelect: (PoseScore) -> Unit
) : RecyclerView.Adapter<PoseAdapter.Holder>() {

    private var items: List<PoseScore> = emptyList()
    private var selected: Int = 0

    fun submit(list: List<PoseScore>) {
        items = list
        // 순서가 바뀌면 이전 선택 위치는 의미가 없으므로 1등으로 되돌립니다.
        selected = 0
        notifyDataSetChanged()
        items.firstOrNull()?.let(onSelect)
    }

    inner class Holder(val binding: ItemPoseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemPoseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        val isSelected = position == selected

        with(holder.binding) {
            tvPoseEmoji.text = item.pose.emoji
            tvPoseLabel.text = item.pose.label
            tvPoseReason.text = item.reason
            tvPoseScore.text = ctx.getString(R.string.pose_score_format, item.score)

            cardPose.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    if (isSelected) R.color.pose_card_bg_selected else R.color.pose_card_bg
                )
            )
            cardPose.strokeColor = ContextCompat.getColor(
                ctx,
                if (isSelected) R.color.pose_card_stroke_selected else R.color.pose_card_stroke
            )

            val lp = cardPose.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginEnd = ctx.resources.getDimensionPixelSize(R.dimen.space_s)
            cardPose.layoutParams = lp

            root.setOnClickListener {
                val previous = selected
                selected = holder.bindingAdapterPosition
                notifyItemChanged(previous)
                notifyItemChanged(selected)
                onSelect(item)
            }
        }
    }
}
