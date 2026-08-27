package com.youngs.picview.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemSpotCardBinding
import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.LatLng
import com.youngs.picview.util.TravelMode
import com.youngs.picview.util.distanceKmTo
import com.youngs.picview.util.estimateTravelMinutes

/**
 * 탐색 '빛이 맞는 출사' 목록 (시안).
 *
 * 사진 위 뱃지에서 "언제·어떤 빛"을 먼저 말하고, 아래에서 이름 → 이유 →
 * 구도·거리 순으로 좁혀 갑니다. 예전에는 이 여섯이 전부 같은 크기의
 * 글줄이라 무엇을 먼저 봐야 할지 알 수 없었습니다.
 *
 * @param onFavoriteClick 찜 토글. 찜 상태는 화면이 아니라 [com.youngs.picview.util.AppPrefs]
 *                        가 들고 있어, 어느 화면에서 눌러도 같은 값을 봅니다.
 */
class SpotAdapter(
    private val onItemClick: (SpotItem) -> Unit,
    private val onGuideClick: (SpotItem) -> Unit,
    private val onFavoriteClick: (SpotItem) -> Unit = {}
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
            // 사진 위에 얹히므로 바탕을 그 빛 구간의 색으로 칠합니다.
            tvSpotSignal.text =
                "${facts.bestPhase.shortLabel} · ${facts.bestPhase.lightCharacter}"
            tvSpotSignal.background?.mutate()?.setTint(
                ContextCompat.getColor(context, facts.bestPhase.heroColorRes)
            )

            // 이유는 촬영 지식 표의 문장 그대로. 카드에서 읽은 말이
            // 상세·홈에서도 같은 문장으로 이어집니다.
            tvSpotReason.text = facts.note

            // 구도 이름은 시선 가이드의 열 구도에서 가져옵니다. 카메라
            // 오버레이의 세 종류(삼분할·대칭·중앙)로는 목록이 "좌우 대칭축"
            // 으로 도배되어 추천이 없는 것과 같아집니다.
            // 아이콘이 "구도"라는 말을 대신하므로 낱말은 뺍니다.
            tvSpotGuideLine.text = SiseonGuide.byId(SiseonGuide.guideIdFor(facts)).title

            // 시내 기준 이동 시간. 좌표가 없으면 줄을 감춥니다.
            val coords = LatLng.parseOrNull(item.mapy, item.mapx)
            tvSpotTravel.visibility = if (coords == null) View.GONE else View.VISIBLE
            coords?.let {
                val minutes = estimateTravelMinutes(CITY_CENTER.distanceKmTo(it), TravelMode.CAR)
                tvSpotTravel.text = context.getString(R.string.spot_travel_minutes, minutes)
            }

            renderFavorite(btnSpotFavorite, AppPrefs.isFavorite(context, item.contentId))
            btnSpotFavorite.setOnClickListener {
                renderFavorite(
                    btnSpotFavorite, AppPrefs.toggleFavorite(context, item.contentId)
                )
                onFavoriteClick(item)
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

    /** 빈 하트 ↔ 채운 하트. 색은 늘 같아서 모양만으로 상태가 읽힙니다. */
    private fun renderFavorite(view: android.widget.ImageView, favorite: Boolean) {
        view.setImageResource(
            if (favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
        )
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
