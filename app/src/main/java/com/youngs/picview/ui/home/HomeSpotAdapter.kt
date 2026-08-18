package com.youngs.picview.ui.home

import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemHomeSpotBinding
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.domain.spot.SpotTheme
import com.youngs.picview.ui.model.SpotItem
import java.time.LocalTime

/**
 * 홈의 '빛이 맞는 출사' 가로 목록 (시안 LIGHT MATCH).
 *
 * 카드의 첫 줄이 점수가 아니라 **시각**입니다. 이 목록의 약속은
 * "여기가 좋다"가 아니라 "몇 시에 가면 빛이 맞는다"이고, 그 시각이
 * 이미 지났으면 내일로 넘겨 말합니다.
 */
class HomeSpotAdapter(
    private val sunTimes: () -> SunTimes,
    private val onClick: (SpotItem) -> Unit,
    private val onPlanClick: (SpotItem) -> Unit
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

        with(holder.binding) {
            tvHomeSpotTitle.text = item.title
            tvHomeSpotTime.text = bestTimeLabel(context, sunTimes(), facts.bestPhase)

            // 그 시간의 빛이 무엇을 만드는지. 상세의 note 와 같은 문장이라
            // 카드에서 읽은 말이 상세에서도 그대로 이어집니다.
            tvHomeSpotDesc.text = facts.note

            // 주제 알약. 지도 마커와 같은 색을 써서 목록과 지도가 같은
            // 언어를 쓰게 합니다(초록은 어디서나 자연).
            val theme = SpotTheme.of(item.contentTypeId)
            tvHomeSpotTheme.text = theme.label
            tvHomeSpotTheme.setTextColor(ContextCompat.getColor(context, theme.colorRes))
            tvHomeSpotTheme.background?.mutate()?.setColorFilter(
                ContextCompat.getColor(context, theme.containerRes),
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
            layoutHomeSpotPlan.setOnClickListener { onPlanClick(item) }
        }
    }

    /**
     * 이 장소의 빛이 맞는 다음 시각 — "오늘 18:50" 또는 "내일 05:54".
     *
     * 골든아워는 천문값 그대로, 오전·한낮·오후는 구간의 어림 시작
     * 시각입니다. 시각을 모르면(천문 응답 없음) 구간 이름으로
     * 물러납니다("일몰에 좋아요").
     */
    private fun bestTimeLabel(context: Context, sun: SunTimes, phase: LightPhase): String {
        val time: LocalTime? = when (phase) {
            LightPhase.BLUE_DAWN -> sun.civilDawn ?: sun.sunrise?.minusMinutes(25)
            LightPhase.SUNRISE -> sun.sunrise
            LightPhase.MORNING -> sun.sunrise?.plusMinutes(60)
            LightPhase.MIDDAY -> LocalTime.of(11, 0)
            LightPhase.AFTERNOON -> LocalTime.of(14, 0)
            LightPhase.SUNSET -> sun.sunset?.minusMinutes(40)
            LightPhase.BLUE_DUSK -> sun.sunset
            LightPhase.NIGHT -> sun.civilDusk ?: sun.sunset?.plusMinutes(30)
        }

        if (time == null) {
            return context.getString(R.string.home_best_phase_format, phase.shortLabel)
        }

        val hhmm = "%02d:%02d".format(time.hour, time.minute)
        return "⏱ " + if (time >= LocalTime.now()) {
            context.getString(R.string.home_spot_time_today, hhmm)
        } else {
            context.getString(R.string.home_spot_time_tomorrow, hhmm)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SpotItem>() {
            override fun areItemsTheSame(oldItem: SpotItem, newItem: SpotItem) =
                oldItem.contentId == newItem.contentId

            override fun areContentsTheSame(oldItem: SpotItem, newItem: SpotItem) =
                oldItem == newItem
        }
    }
}
