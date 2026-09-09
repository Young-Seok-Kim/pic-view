package com.youngs.picview.ui.my

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.R
import com.youngs.picview.data.local.VisitPhotoWithPlace
import com.youngs.picview.data.repository.planDate
import com.youngs.picview.databinding.ItemCourseAlbumBinding
import com.youngs.picview.databinding.ItemCourseAlbumPhotoBinding
import com.youngs.picview.domain.my.CourseAlbum
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 코스별 사진 묶음 목록.
 *
 * 카드 하나가 코스 하나이고, 그 안에 세 줄 격자가 들어 있습니다. 안쪽
 * 격자는 바깥 목록과 함께 스크롤되도록 자체 스크롤을 끕니다.
 */
class CourseAlbumAdapter(
    private val onOpenCourse: (CourseAlbum) -> Unit,
    private val onPhotoClick: (album: CourseAlbum, index: Int) -> Unit
) : ListAdapter<CourseAlbum, CourseAlbumAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemCourseAlbumBinding) : RecyclerView.ViewHolder(binding.root)

    /** 카드마다 격자 어댑터를 새로 만들지 않도록 섬네일 뷰를 나눠 씁니다. */
    private val photoPool = RecyclerView.RecycledViewPool()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCourseAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        binding.rvAlbumPhotos.setRecycledViewPool(photoPool)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val album = getItem(position)
        val context = holder.itemView.context
        val course = album.course

        with(holder.binding) {
            if (course != null) {
                tvAlbumTitle.text = course.course.title
                tvAlbumMeta.text = context.getString(
                    R.string.course_photos_meta,
                    course.course.planDate().format(DATE),
                    album.stopCount,
                    album.visitedStopCount,
                    album.photos.size
                )
                tvAlbumOpen.isVisible = true
                layoutAlbumHeader.setOnClickListener { onOpenCourse(album) }
                tvAlbumOpen.setOnClickListener { onOpenCourse(album) }
            } else {
                tvAlbumTitle.setText(R.string.course_photos_loose_title)
                tvAlbumMeta.text = context.getString(
                    R.string.course_photos_loose_meta,
                    album.visitedStopCount,
                    album.photos.size
                )
                tvAlbumOpen.isVisible = false
                layoutAlbumHeader.setOnClickListener(null)
                layoutAlbumHeader.isClickable = false
            }

            val grid = rvAlbumPhotos.adapter as? PhotoAdapter
                ?: PhotoAdapter().also { rvAlbumPhotos.adapter = it }
            grid.onClick = { index -> onPhotoClick(album, index) }
            grid.submitList(album.photos)
        }
    }

    /** 카드 안 격자. */
    private class PhotoAdapter :
        ListAdapter<VisitPhotoWithPlace, PhotoAdapter.Holder>(PHOTO_DIFF) {

        var onClick: (Int) -> Unit = {}

        class Holder(val binding: ItemCourseAlbumPhotoBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemCourseAlbumPhotoBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = getItem(position)
            with(holder.binding) {
                tvAlbumPhotoPlace.text = item.title
                Glide.with(ivAlbumPhoto)
                    .load(Uri.parse(item.photo.uri))
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(ivAlbumPhoto)
                cardAlbumPhoto.setOnClickListener {
                    val index = holder.bindingAdapterPosition
                    if (index != RecyclerView.NO_POSITION) onClick(index)
                }
            }
        }
    }

    companion object {
        private val DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

        private val DIFF = object : DiffUtil.ItemCallback<CourseAlbum>() {
            override fun areItemsTheSame(oldItem: CourseAlbum, newItem: CourseAlbum) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CourseAlbum, newItem: CourseAlbum) =
                oldItem == newItem
        }

        private val PHOTO_DIFF = object : DiffUtil.ItemCallback<VisitPhotoWithPlace>() {
            override fun areItemsTheSame(
                oldItem: VisitPhotoWithPlace,
                newItem: VisitPhotoWithPlace
            ) = oldItem.photo.id == newItem.photo.id

            override fun areContentsTheSame(
                oldItem: VisitPhotoWithPlace,
                newItem: VisitPhotoWithPlace
            ) = oldItem == newItem
        }
    }
}
