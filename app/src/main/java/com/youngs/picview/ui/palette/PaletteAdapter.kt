package com.youngs.picview.ui.palette

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemPaletteBinding
import com.youngs.picview.domain.palette.ColorPalette

/**
 * 정읍 색감 팔레트 목록.
 *
 * 색 조각만 보여 주면 "주황 필터"가 되고, 이름만 보여 주면 무슨 색인지
 * 모릅니다. 둘을 같이 두어야 고르는 행위가 "어디의 색으로 담을까"가 됩니다.
 */
class PaletteAdapter(
    private val onPick: (ColorPalette) -> Unit
) : RecyclerView.Adapter<PaletteAdapter.Holder>() {

    private val palettes = ColorPalette.entries
    private var selected = 0

    class Holder(val binding: ItemPaletteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemPaletteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = palettes.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val palette = palettes[position]
        val isSelected = position == selected

        with(holder.binding) {
            tvPaletteLabel.text = palette.label
            tvPaletteNote.text = palette.note

            listOf(viewSwatch1, viewSwatch2, viewSwatch3)
                .forEachIndexed { index, view ->
                    view.background?.mutate()
                        ?.setColorFilter(palette.swatches[index], PorterDuff.Mode.SRC_IN)
                }

            // 고른 칸은 테두리와 배지 둘 다로 표시합니다. 어두운 배경에서
            // 테두리 하나만으로는 어느 쪽인지 한눈에 안 들어옵니다.
            cardPalette.strokeColor = if (isSelected) {
                ContextCompat.getColor(root.context, R.color.maple_500)
            } else {
                0x00000000
            }
            tvPaletteSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            cardPalette.setOnClickListener {
                val previous = selected
                selected = holder.bindingAdapterPosition
                notifyItemChanged(previous)
                notifyItemChanged(selected)
                onPick(palette)
            }
        }
    }
}
