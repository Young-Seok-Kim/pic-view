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
import com.youngs.picview.databinding.ItemStampBinding
import com.youngs.picview.domain.mission.MissionProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 스탬프북.
 *
 * 미션과 **같은 데이터**를 다르게 보여 줍니다. 판정을 따로 만들지 않은 이유는
 * 두 벌이 되면 언젠가 어긋나기 때문입니다. 아래 목록이 "무엇을 해야 하는지"를
 * 설명한다면, 이 격자는 "얼마나 모았는지"를 한눈에 보여 줍니다.
 *
 * 안 찍힌 자리는 비워 두지 않고 점선 원으로 남깁니다. 빈 칸이 아니라
 * 채울 자리로 읽혀야 다음에 무엇을 할지 눈에 들어옵니다.
 */
class StampAdapter : ListAdapter<MissionProgress, StampAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemStampBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemStampBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val done = item.isComplete

        with(holder.binding) {
            tvStampEmoji.text = item.mission.type.icon
            // 안 찍힌 스탬프는 흐리게 둬서 찍힌 것과 한눈에 구분되게 합니다.
            tvStampEmoji.alpha = if (done) 1f else 0.3f

            layoutStampCircle.setBackgroundResource(
                if (done) R.drawable.bg_stamp_filled else R.drawable.bg_stamp_empty
            )

            // 미션 제목("내장산 계절 관찰일지")은 좁은 칸에 안 들어가고,
            // 유형 이름("빛")만 달면 여덟 칸 중 둘이 똑같아집니다.
            // 수식어가 붙은 배지 이름을 답니다.
            tvStampBadge.text = item.mission.badge
            tvStampBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (done) R.color.maple_600 else R.color.text_tertiary
                )
            )

            // 완료한 스탬프에만 날짜를 답니다. 언제 받았는지가 기록의 일부입니다.
            tvStampDate.isVisible = done
            tvStampDate.text = item.completedAt?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE)
            }.orEmpty()
        }
    }

    companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d")

        private val DIFF = object : DiffUtil.ItemCallback<MissionProgress>() {
            override fun areItemsTheSame(oldItem: MissionProgress, newItem: MissionProgress) =
                oldItem.mission.id == newItem.mission.id

            override fun areContentsTheSame(oldItem: MissionProgress, newItem: MissionProgress) =
                oldItem == newItem
        }
    }
}
