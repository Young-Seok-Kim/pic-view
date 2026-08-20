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
import com.youngs.picview.domain.spot.ShotTokens
import com.youngs.picview.domain.spot.SpotFactsTable

/**
 * MY 탭 "최근 촬영" 사진 격자.
 *
 * 사진 위에 장소와 구도 태그가 얹힙니다. 태그는 그 장소의 촬영 지식
 * ([SpotFactsTable])에서 뽑아, 사진이 "무엇을 연습한 장면"이었는지 말합니다.
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
            tvMyPhotoPlace.text = visit.title

            val facts = SpotFactsTable.of(visit.title, null)
            tvMyPhotoTag.text = "#${ShotTokens.of(facts.guide).label}"

            Glide.with(ivMyPhoto)
                .load(visit.photoUri)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(ivMyPhoto)

            root.setOnClickListener { onClick(visit) }
        }
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
