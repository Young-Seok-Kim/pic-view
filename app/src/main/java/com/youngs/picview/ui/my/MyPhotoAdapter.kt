package com.youngs.picview.ui.my

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.R
import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.databinding.ItemMyPhotoBinding
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.ShotTokens
import com.youngs.picview.domain.spot.SpotFactsTable

/**
 * MY 탭 "최근 촬영 아카이브" 격자.
 *
 * 사진 아래에 그 장면의 촬영 제안 두 낱말을 답니다 — 구도와 빛.
 * 구도는 그 장소의 촬영 지식([SpotFactsTable])에서, 빛은 찍을 때 실제로
 * 기록된 구간에서 옵니다. 사진을 판독한 게 아니라 **찍은 자리와 시각**을
 * 옮겨 적는 것이라 틀릴 일이 없습니다.
 */
class MyPhotoAdapter(
    private val onClick: (VisitLogEntity) -> Unit
) : ListAdapter<VisitLogEntity, MyPhotoAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemMyPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemMyPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val visit = getItem(position)

        with(holder.binding) {
            tvMyPhotoTag.text = tagOf(visit)

            Glide.with(ivMyPhoto)
                .load(visit.photoUri)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(ivMyPhoto)

            root.setOnClickListener { onClick(visit) }
        }
    }

    /** "대칭 · 저녁 사광". 빛 구간을 못 읽으면 구도만 답니다. */
    private fun tagOf(visit: VisitLogEntity): String {
        val guide = ShotTokens.of(SpotFactsTable.of(visit.title, null).guide).label
        val phase = runCatching { LightPhase.valueOf(visit.phaseName) }.getOrNull()
            ?: return guide
        return "$guide · ${ShotTokens.of(phase).label}"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VisitLogEntity>() {
            override fun areItemsTheSame(oldItem: VisitLogEntity, newItem: VisitLogEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: VisitLogEntity, newItem: VisitLogEntity) =
                oldItem == newItem
        }
    }
}
