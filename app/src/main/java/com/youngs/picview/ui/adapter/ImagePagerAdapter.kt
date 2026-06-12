package com.youngs.picview.ui.adapter

import com.youngs.picview.R
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.data.model.ImageItem

class ImagePagerAdapter(private val images: List<ImageItem>) :
    RecyclerView.Adapter<ImagePagerAdapter.ViewHolder>() {

    class ViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return ViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageView = holder.imageView

        Glide.with(imageView.context)
            .load(images[position].originImgUrl)
            .placeholder(R.drawable.baseline_image_24) // 로딩 중 이미지
            .error(R.drawable.baseline_error_outline_24) // 에러 발생 시 이미지
            .centerCrop()
            .into(imageView)
    }

    override fun getItemCount() = images.size
}