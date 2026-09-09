package com.youngs.picview.ui.my

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.databinding.FragmentCoursePhotosBinding
import com.youngs.picview.domain.my.CourseAlbum
import com.youngs.picview.domain.my.CourseAlbums
import com.youngs.picview.ui.course.CourseResultFragment
import com.youngs.picview.ui.photo.PhotoViewerActivity
import com.youngs.picview.util.applyTopSystemBarInset
import kotlinx.coroutines.flow.combine

/**
 * 코스별 사진 모아보기.
 *
 * 다녀온 곳이 '장소 단위', 일기가 '하루 단위'라면 여기는 '코스 단위'입니다.
 * "그날 그 코스를 돌면서 뭘 찍었지"에 답합니다. 사진을 누르면 그 코스의
 * 사진만 넘겨 가며 크게 보고, 코스 이름을 누르면 타임라인으로 갑니다.
 *
 * 사진·코스 둘 다 Flow 라서 사진을 지우거나 코스를 저장하면 바로 다시 묶입니다.
 */
class CoursePhotosFragment : Fragment(R.layout.fragment_course_photos) {

    private var _binding: FragmentCoursePhotosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CoursePhotosViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCoursePhotosBinding.bind(view)

        binding.rootCoursePhotos.applyTopSystemBarInset()
        binding.btnCoursePhotosBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = CourseAlbumAdapter(
            onOpenCourse = { album ->
                val id = album.course?.course?.id ?: return@CourseAlbumAdapter
                (activity as? MainActivity)?.pushScreen(CourseResultFragment.forSaved(id))
            },
            onPhotoClick = { album, index -> openViewer(album, index) }
        )
        binding.rvCourseAlbums.adapter = adapter

        viewModel.albums.observe(viewLifecycleOwner) { albums ->
            adapter.submitList(albums)
            binding.tvCoursePhotosEmpty.isVisible = albums.isEmpty()
            binding.rvCourseAlbums.isVisible = albums.isNotEmpty()
            binding.tvCoursePhotosCount.text =
                getString(R.string.course_photos_count, albums.size)
            binding.tvCoursePhotosCount.isVisible = albums.isNotEmpty()
        }
    }

    /** 그 코스의 사진만 넘겨 가며 크게 봅니다. 제목 자리는 코스 이름입니다. */
    private fun openViewer(album: CourseAlbum, index: Int) {
        val photos = album.photos
        if (index !in photos.indices) return
        startActivity(
            PhotoViewerActivity.intent(
                requireContext(),
                photoIds = photos.map { it.photo.id },
                uris = photos.map { it.photo.uri },
                takenAt = photos.map { it.photo.takenAt },
                place = album.course?.course?.title
                    ?: getString(R.string.course_photos_loose_title),
                start = index
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class CoursePhotosViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CourseRepository(app)

    val albums: LiveData<List<CourseAlbum>> =
        combine(repository.observeCourses(), repository.observeAllPhotos()) { courses, photos ->
            CourseAlbums.of(courses, photos)
        }.asLiveData()
}
