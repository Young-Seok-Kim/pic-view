package com.youngs.picview.ui.detail

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.R
import com.youngs.picview.data.local.VisitPhotoEntity
import com.youngs.picview.databinding.ItemDetailMyPhotoBinding

/**
 * 상세 화면 "내가 찍은 사진" 가로 띠.
 *
 * 섬네일만 나열합니다. 누르면 몇 번째 사진인지와 함께 돌려주고, 상세 화면이
 * 그 번호부터 펼치는 큰 화면을 엽니다.
 */
class MyShotAdapter(
    private val onClick: (index: Int) -> Unit,
    /** 꾹 누르면. 보기 화면까지 안 들어가고 바로 지우고 싶을 때의 길입니다. */
    private val onLongClick: (photo: VisitPhotoEntity) -> Unit
) : ListAdapter<VisitPhotoEntity, MyShotAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemDetailMyPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemDetailMyPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val photo = getItem(position)
        Glide.with(holder.binding.ivMyShot)
            .load(Uri.parse(photo.uri))
            .placeholder(R.drawable.bg_image_placeholder)
            .error(R.drawable.bg_image_placeholder)
            .centerCrop()
            .into(holder.binding.ivMyShot)
        holder.binding.root.setOnClickListener {
            val index = holder.bindingAdapterPosition
            if (index != RecyclerView.NO_POSITION) onClick(index)
        }
        holder.binding.root.setOnLongClickListener {
            val index = holder.bindingAdapterPosition
            if (index == RecyclerView.NO_POSITION) return@setOnLongClickListener false
            onLongClick(getItem(index))
            true
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VisitPhotoEntity>() {
            override fun areItemsTheSame(oldItem: VisitPhotoEntity, newItem: VisitPhotoEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: VisitPhotoEntity, newItem: VisitPhotoEntity) =
                oldItem == newItem
        }
    }
}
