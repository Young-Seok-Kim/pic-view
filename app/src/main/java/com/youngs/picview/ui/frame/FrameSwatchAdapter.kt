package com.youngs.picview.ui.frame

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.databinding.ItemFrameSwatchBinding
import com.youngs.picview.domain.frame.FrameTheme

/**
 * 프레임 테마 카드 목록.
 *
 * 색만 보여 주지 않고 색 견본 + 테마 장식 + 장소 이름을 함께 답니다.
 * 고르는 행위가 "어떤 색으로 할까"가 아니라 "어디서 찍은 사진으로 할까"가
 * 되도록 하려는 것입니다.
 */
class FrameSwatchAdapter(
    private val onPick: (FrameTheme) -> Unit
) : RecyclerView.Adapter<FrameSwatchAdapter.Holder>() {

    private val themes = FrameTheme.entries
    private var selected = 0

    class Holder(val binding: ItemFrameSwatchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemFrameSwatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = themes.size

    /** 저장해 둔 프레임을 되살릴 때. 콜백은 부르지 않습니다 — 부르는 쪽이 이미 압니다. */
    fun select(theme: FrameTheme) {
        val index = themes.indexOf(theme)
        if (index < 0 || index == selected) return
        val previous = selected
        selected = index
        notifyItemChanged(previous)
        notifyItemChanged(selected)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val theme = themes[position]
        val isSelected = position == selected

        with(holder.binding) {
            cardSwatch.setCardBackgroundColor(theme.paperColor)
            viewSw1.setBackgroundColor(theme.swatches[0])
            viewSw2.setBackgroundColor(theme.swatches[1])
            viewSw3.setBackgroundColor(theme.swatches[2])
            tvSwatchMotif.text = theme.motif

            tvSwatchLabel.text = theme.label
            tvSwatchLabel.setBackgroundColor(theme.swatches[0])

            // 고른 카드만 테마색 테두리 + 체크. 어두운 배경이라
            // 밝기 차이만으로는 구분이 약합니다.
            cardSwatch.strokeColor =
                if (isSelected) theme.accentColor else 0x33FFFFFF
            ivSwatchCheck.isVisible = isSelected
            cardSwatch.alpha = if (isSelected) 1f else 0.8f

            cardSwatch.setOnClickListener {
                val previous = selected
                selected = holder.bindingAdapterPosition
                notifyItemChanged(previous)
                notifyItemChanged(selected)
                onPick(theme)
            }
        }
    }
}
