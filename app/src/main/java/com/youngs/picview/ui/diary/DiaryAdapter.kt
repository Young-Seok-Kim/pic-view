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
import android.net.Uri
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.youngs.picview.util.AppPrefs

/** 날짜별 출사 기록 목록. */
class DiaryAdapter(
    private val onGenerate: (DiaryDay) -> Unit,
    private val onShare: (DiaryDay) -> Unit,
    private val onEdit: (DiaryDay) -> Unit,
    private val onPhotoClick: (Uri) -> Unit,
    private val onPrepareNext: (NextRec) -> Unit
) : ListAdapter<DiaryDay, DiaryAdapter.DayViewHolder>(DIFF) {

    /**
     * 시선이의 다음 추천 (시안).
     * 가장 최근 기록 카드에만 붙습니다. 프래그먼트가 촬영지 목록과
     * 천문값으로 계산해 넣어 줍니다.
     */
    data class NextRec(
        val spot: com.youngs.picview.ui.model.SpotItem,
        val headline: String,
        val compLabel: String,
        val timeLabel: String,
        val desc: String
    )

    var nextRec: NextRec? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

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

            btnDiaryEdit.isVisible = diary != null
            btnDiaryEdit.setOnClickListener { onEdit(day) }

            // 그날 찍은 사진. 촬영 화면에서 셔터를 누르면 방문 기록에 붙습니다.
            val photos = day.visits.mapNotNull { it.photoUri?.toUri() }
            rvDiaryPhotos.isVisible = photos.isNotEmpty()
            if (photos.isNotEmpty()) {
                val photoAdapter = rvDiaryPhotos.adapter as? DiaryPhotoAdapter
                    ?: DiaryPhotoAdapter(onPhotoClick).also { rvDiaryPhotos.adapter = it }
                photoAdapter.submit(photos)
            }

            btnDiaryGenerate.setOnClickListener { onGenerate(day) }
            btnDiaryShare.setOnClickListener { onShare(day) }

            renderFeelings(this, day)
            renderNextRec(this, position)
        }
    }

    /** 오늘의 감정 칩. 누르면 그 날짜에 저장됩니다. */
    private fun renderFeelings(binding: ItemDiaryDayBinding, day: DiaryDay) {
        val context = binding.root.context
        val selected = AppPrefs.diaryFeelings(context, day.dateKey)

        binding.chipsDiaryFeelings.removeAllViews()
        FEELINGS.forEach { labelRes ->
            val label = context.getString(labelRes)
            binding.chipsDiaryFeelings.addView(
                Chip(context).apply {
                    text = label
                    isCheckable = true
                    isChecked = label in selected
                    setOnClickListener {
                        AppPrefs.toggleDiaryFeeling(context, day.dateKey, label)
                    }
                }
            )
        }
    }

    /** 가장 최근 카드에만 다음 추천을 붙입니다. */
    private fun renderNextRec(binding: ItemDiaryDayBinding, position: Int) {
        val rec = nextRec.takeIf { position == 0 }
        binding.layoutDiaryNext.isVisible = rec != null
        rec ?: return

        binding.tvNextHeadline.text = rec.headline
        binding.tvNextTitle.text = rec.spot.title
        binding.tvNextComp.text = rec.compLabel
        binding.tvNextTime.text = rec.timeLabel
        binding.tvNextDesc.text = rec.desc

        Glide.with(binding.ivNextPhoto)
            .load(rec.spot.imageUrl.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.bg_image_placeholder)
            .error(R.drawable.bg_image_placeholder)
            .centerCrop()
            .into(binding.ivNextPhoto)

        binding.btnNextPrepare.setOnClickListener { onPrepareNext(rec) }
    }

    companion object {
        /** 시안의 감정 넷. */
        private val FEELINGS = listOf(
            R.string.diary_feeling_warm,
            R.string.diary_feeling_calm,
            R.string.diary_feeling_full,
            R.string.diary_feeling_flutter
        )

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
