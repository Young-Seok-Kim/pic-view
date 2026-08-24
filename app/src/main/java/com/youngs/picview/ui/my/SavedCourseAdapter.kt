package com.youngs.picview.ui.my

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.data.local.SavedCourseWithStops
import com.youngs.picview.data.repository.arriveTime
import com.youngs.picview.databinding.ItemSavedCourseBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 저장한 코스 목록 (MY 탭 · 코스 탭 공용).
 *
 * [onDelete] 를 넘기지 않으면 삭제 버튼이 숨겨집니다 — 코스 탭의
 * 미리보기 자리에서는 열람만 하고, 삭제는 전체 목록에서 합니다.
 */
class SavedCourseAdapter(
    private val onClick: (SavedCourseWithStops) -> Unit,
    private val onDelete: ((SavedCourseWithStops) -> Unit)? = null
) : ListAdapter<SavedCourseWithStops, SavedCourseAdapter.CourseViewHolder>(DIFF) {

    class CourseViewHolder(val binding: ItemSavedCourseBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = CourseViewHolder(
        ItemSavedCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val item = getItem(position)
        val course = item.course
        val stops = item.orderedStops

        with(holder.binding) {
            tvSavedTitle.text = course.title

            val date = Instant.ofEpochMilli(course.createdAt)
                .atZone(ZoneId.systemDefault())
                .format(DATE)
            val hours = course.totalMinutes / 60
            val minutes = course.totalMinutes % 60
            val duration = buildString {
                if (hours > 0) append("${hours}시간 ")
                append("${minutes}분")
            }
            tvSavedDate.text = "$date 저장 · ${stops.size}곳 · $duration"

            tvSavedSummary.text = course.summary

            // 앞의 세 곳만 미리보기로. 전부 쓰면 카드가 길어집니다.
            tvSavedStops.text = stops.take(PREVIEW_STOPS).joinToString(" → ") { stop ->
                "${stop.arriveTime().format(TIME)} ${stop.title}"
            } + if (stops.size > PREVIEW_STOPS) " …" else ""

            root.setOnClickListener { onClick(item) }
            btnSavedDelete.isVisible = onDelete != null
            btnSavedDelete.setOnClickListener { onDelete?.invoke(item) }
        }
    }

    companion object {
        private const val PREVIEW_STOPS = 3

        private val DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private val DIFF = object : DiffUtil.ItemCallback<SavedCourseWithStops>() {
            override fun areItemsTheSame(
                oldItem: SavedCourseWithStops,
                newItem: SavedCourseWithStops
            ) = oldItem.course.id == newItem.course.id

            override fun areContentsTheSame(
                oldItem: SavedCourseWithStops,
                newItem: SavedCourseWithStops
            ) = oldItem == newItem
        }
    }
}
