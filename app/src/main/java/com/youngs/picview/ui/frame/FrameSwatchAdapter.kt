package com.youngs.picview.ui.frame

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.databinding.ItemFrameSwatchBinding
import com.youngs.picview.domain.frame.FrameTheme

/**
 * 프레임 테마 목록.
 *
 * 색만 보여 주지 않고 장소 이름을 함께 답니다. 고르는 행위가 "어떤 색으로
 * 할까"가 아니라 "어디서 찍은 사진으로 할까"가 되도록 하려는 것입니다.
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

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val theme = themes[position]
        val isSelected = position == selected

        with(holder.binding) {
            tvSwatchLabel.text = theme.label

            viewSwatchColor.background?.mutate()?.setColorFilter(
                theme.accentColor, PorterDuff.Mode.SRC_IN
            )

            // 고른 칸만 테마색 테두리로 표시합니다. 어두운 배경이라
            // 밝기 차이만으로는 구분이 약합니다.
            layoutSwatch.background?.mutate()?.setColorFilter(
                if (isSelected) theme.accentColor else 0x22FFFFFF,
                PorterDuff.Mode.SRC_IN
            )
            layoutSwatch.alpha = if (isSelected) 1f else 0.75f

            layoutSwatch.setOnClickListener {
                val previous = selected
                selected = holder.bindingAdapterPosition
                notifyItemChanged(previous)
                notifyItemChanged(selected)
                onPick(theme)
            }
        }
    }
}
