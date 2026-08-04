package com.youngs.picview.ui.home

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemHomeSpotBinding
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.model.SpotItem

/** 홈의 '지금 찍기 좋은 곳' 가로 목록. */
class HomeSpotAdapter(
    private val onClick: (SpotItem) -> Unit
) : ListAdapter<SpotItem, HomeSpotAdapter.SpotViewHolder>(DIFF) {

    class SpotViewHolder(val binding: ItemHomeSpotBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SpotViewHolder(
        ItemHomeSpotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val facts = SpotFactsTable.of(item.title, item.contentTypeId)
        val phaseColor = ContextCompat.getColor(context, facts.bestPhase.colorRes)

        with(holder.binding) {
            tvHomeSpotTitle.text = item.title

            tvHomeSpotScore.text = context.getString(R.string.home_score_format, item.score)
            tvHomeSpotScore.isVisible = item.score > 0

            // 목록에서 "언제 오면 좋은지"를 바로 알려 줍니다.
            tvHomeSpotPhase.text = context.getString(
                R.string.home_best_phase_format, facts.bestPhase.label
            )
            tvHomeSpotPhase.setTextColor(phaseColor)
            tvHomeSpotPhase.background?.mutate()?.setColorFilter(
                ColorUtils.setAlphaComponent(phaseColor, PHASE_BADGE_ALPHA),
                PorterDuff.Mode.SRC_IN
            )

            Glide.with(ivHomeSpot)
                .load(item.imageUrl.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(ivHomeSpot)

            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private const val PHASE_BADGE_ALPHA = 38

        private val DIFF = object : DiffUtil.ItemCallback<SpotItem>() {
            override fun areItemsTheSame(oldItem: SpotItem, newItem: SpotItem) =
                oldItem.contentId == newItem.contentId

            override fun areContentsTheSame(oldItem: SpotItem, newItem: SpotItem) =
                oldItem == newItem
        }
    }
}
