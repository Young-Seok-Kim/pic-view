package com.youngs.picview.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.R
import com.youngs.picview.ui.model.SpotItem

class SpotAdapter(
    private var spotList: List<SpotItem>,
    private val onItemClick: (SpotItem) -> Unit
) :
    RecyclerView.Adapter<SpotAdapter.SpotViewHolder>() {

    class SpotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivSpot: ImageView = view.findViewById(R.id.iv_spot_image)
        val tvTitle: TextView = view.findViewById(R.id.tv_spot_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spot_card, parent, false)
        return SpotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
        val item = spotList[position]
        holder.tvTitle.text = item.title

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(android.R.drawable.ic_menu_camera) // 로딩 중 이미지
            .error(android.R.drawable.ic_menu_camera)      // 오류 시 이미지
            .centerCrop()
            .into(holder.ivSpot)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = spotList.size

    // 데이터 갱신을 위한 함수
    fun updateData(newList: List<SpotItem>) {
        this.spotList = newList
        notifyDataSetChanged()
    }
}