package com.youngs.picview.ui.my

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemLightNoteBinding
import com.youngs.picview.domain.mission.MissionProgress

/**
 * 빛 수집 노트.
 *
 * 미션·스탬프북과 **같은 데이터**를 세 번째 모양으로 보여 줍니다. 판정을
 * 따로 만들지 않은 이유는 두 벌이 되면 언젠가 어긋나기 때문입니다.
 * 여기서는 "지금 몇 개를 모았고 다음은 무엇인가"만 말합니다.
 *
 * 못 받은 칸도 그림을 보여 주되 흐리게 둡니다. 아예 가리면 무엇을 모을 수
 * 있는지 알 수 없어서 모으고 싶어지지 않습니다.
 */
class LightNoteAdapter(
    private val onClick: (MissionProgress) -> Unit
) : ListAdapter<MissionProgress, LightNoteAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemLightNoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemLightNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val done = item.isComplete

        with(holder.binding) {
            layoutNoteSlot.setBackgroundResource(
                if (done) R.drawable.bg_note_slot_earned else R.drawable.bg_note_slot_locked
            )

            ivNoteArt.setImageResource(item.mission.stampRes)
            ivNoteArt.alpha = if (done) 1f else 0.3f

            tvNoteBadge.text = item.mission.badge
            tvNoteBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (done) R.color.maple_600 else R.color.text_tertiary
                )
            )

            ivNoteState.setImageResource(
                if (done) R.drawable.bg_check_badge else R.drawable.bg_lock_badge
            )

            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MissionProgress>() {
            override fun areItemsTheSame(oldItem: MissionProgress, newItem: MissionProgress) =
                oldItem.mission.id == newItem.mission.id

            override fun areContentsTheSame(oldItem: MissionProgress, newItem: MissionProgress) =
                oldItem == newItem
        }
    }
}
