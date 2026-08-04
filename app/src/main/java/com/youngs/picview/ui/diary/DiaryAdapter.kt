package com.youngs.picview.ui.diary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.R
import com.youngs.picview.databinding.ItemDiaryDayBinding
import com.youngs.picview.domain.diary.DiaryDay
import com.youngs.picview.domain.diary.toDiaryLocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 날짜별 출사 기록 목록. */
class DiaryAdapter(
    private val onGenerate: (DiaryDay) -> Unit,
    private val onShare: (DiaryDay) -> Unit
) : ListAdapter<DiaryDay, DiaryAdapter.DayViewHolder>(DIFF) {

    /** 일기를 만들고 있는 날짜. 해당 카드만 로딩 표시합니다. */
    var generatingKey: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class DayViewHolder(val binding: ItemDiaryDayBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = DayViewHolder(
        ItemDiaryDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = getItem(position)
        val context = holder.itemView.context
        val generating = generatingKey == day.dateKey

        with(holder.binding) {
            tvDiaryDate.text = day.date.format(DATE)

            tvDiaryStats.text = context.getString(
                R.string.diary_stats_format, day.visits.size, day.averageScore
            )

            tvDiaryVisits.text = day.visits.joinToString(" · ") { visit ->
                "${visit.visitedAt.toDiaryLocalTime().format(TIME)} ${visit.title}"
            }

            // 일기가 있으면 본문을, 없으면 만들기 버튼만 보여 줍니다.
            val diary = day.diary
            viewDiaryDivider.isVisible = diary != null
            tvDiaryEntryTitle.isVisible = diary != null
            tvDiaryBody.isVisible = diary != null
            tvDiaryEntryTitle.text = diary?.title.orEmpty()
            tvDiaryBody.text = diary?.body.orEmpty()

            btnDiaryGenerate.setText(
                if (diary == null) R.string.diary_generate else R.string.diary_regenerate
            )
            btnDiaryShare.isVisible = diary != null

            // 생성 중에는 버튼을 잠그고 스피너를 띄웁니다.
            layoutDiaryActions.isVisible = !generating
            progressDiary.isVisible = generating

            btnDiaryGenerate.setOnClickListener { onGenerate(day) }
            btnDiaryShare.setOnClickListener { onShare(day) }
        }
    }

    companion object {
        private val DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private val DIFF = object : DiffUtil.ItemCallback<DiaryDay>() {
            override fun areItemsTheSame(oldItem: DiaryDay, newItem: DiaryDay) =
                oldItem.dateKey == newItem.dateKey

            override fun areContentsTheSame(oldItem: DiaryDay, newItem: DiaryDay) =
                oldItem == newItem
        }
    }
}
