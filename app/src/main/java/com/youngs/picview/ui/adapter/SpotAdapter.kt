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
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.guide.GuideOverlayView
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.LatLng
import com.youngs.picview.util.TravelMode
import com.youngs.picview.util.distanceKmTo
import com.youngs.picview.util.estimateTravelMinutes

/**
 * 탐색 '빛이 맞는 출사' 목록 (시안).
 *
 * 사진은 관광공사 실사진 그대로, 그 아래에 빛 신호 → 이름 → 이유 →
 * 구도·거리 → 시선 가이드 진입을 쌓습니다.
 */
class SpotAdapter(
    private val onItemClick: (SpotItem) -> Unit,
    private val onGuideClick: (SpotItem) -> Unit
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
        val context = holder.itemView.context
        val facts = SpotFactsTable.of(item.title, item.contentTypeId)

        with(holder.binding) {
            tvSpotTitle.text = item.title

            // 빛 신호 — "일몰 · 따뜻한 사광". 언제 가는지와 그때 빛의 결.
            tvSpotSignal.text =
                "${facts.bestPhase.shortLabel} · ${facts.bestPhase.lightCharacter}"

            // 이유는 촬영 지식 표의 문장 그대로. 카드에서 읽은 말이
            // 상세·홈에서도 같은 문장으로 이어집니다.
            tvSpotReason.text = facts.note

            tvSpotGuideLine.text = context.getString(
                R.string.spot_guide_line,
                context.getString(
                    when (facts.guide) {
                        GuideOverlayView.GuideType.THIRDS -> R.string.guide_name_thirds
                        GuideOverlayView.GuideType.SYMMETRY -> R.string.guide_name_symmetry
                        GuideOverlayView.GuideType.CENTER -> R.string.guide_name_center
                    }
                )
            )

            // 시내 기준 이동 시간. 좌표가 없으면 줄을 감춥니다.
            val coords = LatLng.parseOrNull(item.mapy, item.mapx)
            tvSpotTravel.visibility = if (coords == null) View.GONE else View.VISIBLE
            coords?.let {
                val minutes = estimateTravelMinutes(CITY_CENTER.distanceKmTo(it), TravelMode.CAR)
                tvSpotTravel.text = context.getString(R.string.spot_travel_car, minutes)
            }

            Glide.with(ivSpotImage)
                .load(item.imageUrl.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(ivSpotImage)

            root.setOnClickListener { onItemClick(item) }
            btnSpotGuide.setOnClickListener { onGuideClick(item) }
        }
    }

    fun updateData(newList: List<SpotItem>) = submitList(newList)

    companion object {
        /** 정읍 시내 기준점(시청 인근). 거리·이동 시간의 출발점입니다. */
        val CITY_CENTER = LatLng(35.5699, 126.8559)

        private val DIFF = object : DiffUtil.ItemCallback<SpotItem>() {
            override fun areItemsTheSame(oldItem: SpotItem, newItem: SpotItem) =
                oldItem.contentId == newItem.contentId

            override fun areContentsTheSame(oldItem: SpotItem, newItem: SpotItem) =
                oldItem == newItem && oldItem.score == newItem.score
        }
    }
}
