package com.youngs.picview.ui.diary

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.databinding.ItemDiaryPhotoBinding

/**
 * 그날 찍은 사진 줄.
 *
 * 사진 자체는 갤러리에 있고 여기서는 URI 로 불러 보여 주기만 합니다.
 * 사용자가 갤러리에서 지운 사진은 자리만 비어 보이는데, 앱이 사본을
 * 들고 있다가 "지웠는데 앱에는 남아 있는" 상태가 되는 것보다 낫습니다.
 */
class DiaryPhotoAdapter(
    private val onClick: (Uri) -> Unit
) : RecyclerView.Adapter<DiaryPhotoAdapter.Holder>() {

    private var uris: List<Uri> = emptyList()

    fun submit(list: List<Uri>) {
        uris = list
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemDiaryPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemDiaryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = uris.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val uri = uris[position]
        Glide.with(holder.binding.ivDiaryPhoto)
            .load(uri)
            .centerCrop()
            .into(holder.binding.ivDiaryPhoto)

        holder.itemView.setOnClickListener { onClick(uri) }
    }
}
