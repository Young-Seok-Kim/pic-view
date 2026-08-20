package com.youngs.picview.ui.my

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.local.SavedCourseWithStops
import com.youngs.picview.databinding.FragmentSavedCoursesBinding
import com.youngs.picview.ui.course.CourseResultFragment
import com.youngs.picview.util.applyTopSystemBarInset

/**
 * 저장한 출사 코스 전체 목록.
 *
 * MY 탭이 시안 구조(요약 카드 + 전체보기)로 바뀌면서, 열람·삭제가 있는
 * 전체 목록은 이 화면이 전담합니다.
 */
class SavedCoursesFragment : Fragment(R.layout.fragment_saved_courses) {

    private var _binding: FragmentSavedCoursesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSavedCoursesBinding.bind(view)

        binding.layoutCoursesTop.applyTopSystemBarInset()
        binding.btnCoursesBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = SavedCourseAdapter(
            onClick = { openCourse(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvSavedCourses.adapter = adapter

        viewModel.courses.observe(viewLifecycleOwner) { courses ->
            adapter.submitList(courses)
            binding.tvCoursesEmpty.isVisible = courses.isEmpty()
        }
    }

    /** 저장한 코스를 누르면 그 코스의 타임라인을 그대로 다시 엽니다. */
    private fun openCourse(item: SavedCourseWithStops) {
        (activity as? MainActivity)?.pushScreen(
            CourseResultFragment.forSaved(item.course.id)
        )
    }

    private fun confirmDelete(item: SavedCourseWithStops) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_delete_course)
            .setMessage(getString(R.string.my_delete_confirm, item.course.title))
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.deleteCourse(item.course.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
