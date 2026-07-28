package com.youngs.picview.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemSpotCardBinding
import com.youngs.picview.ui.model.SpotItem

class SpotAdapter(
    private val onItemClick: (SpotItem) -> Unit
) : ListAdapter<SpotItem, SpotAdapter.SpotViewHolder>(DIFF) {

    class SpotViewHolder(val binding: ItemSpotCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotViewHolder {
        val binding = ItemSpotCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SpotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
        val item = getItem(position)

        with(holder.binding) {
            tvSpotTitle.text = item.title
            tvSpotAddress.text = item.addr1
            tvSpotAddress.visibility = if (item.addr1.isBlank()) View.GONE else View.VISIBLE

            tvSpotScore.text = item.score.toString()
            layoutScore.visibility = if (item.score > 0) View.VISIBLE else View.GONE

            Glide.with(ivSpotImage)
                .load(item.imageUrl.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(ivSpotImage)

            root.setOnClickListener { onItemClick(item) }
        }
    }

    fun updateData(newList: List<SpotItem>) = submitList(newList)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SpotItem>() {
            override fun areItemsTheSame(oldItem: SpotItem, newItem: SpotItem) =
                oldItem.contentId == newItem.contentId

            override fun areContentsTheSame(oldItem: SpotItem, newItem: SpotItem) =
                oldItem == newItem && oldItem.score == newItem.score
        }
    }
}
