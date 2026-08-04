package com.youngs.picview.ui.course

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.data.repository.toShootingCourse
import com.youngs.picview.databinding.FragmentCourseResultBinding
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.ui.detail.DetailFragment
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * 빛 스케줄 타임라인.
 *
 * 두 가지 경로로 열립니다.
 *  - 방금 만든 코스 : [CourseViewModel] 이 들고 있는 결과를 그립니다.
 *  - 저장한 코스    : [ARG_COURSE_ID] 로 열면 Room 에서 불러와 같은 화면에 그립니다.
 *
 * 저장한 코스를 볼 때는 '저장' 버튼을 숨깁니다(이미 저장돼 있으니까).
 */
class CourseResultFragment : Fragment(R.layout.fragment_course_result) {

    private var _binding: FragmentCourseResultBinding? = null
    private val binding get() = _binding!!

    private val courseViewModel: CourseViewModel by activityViewModels()

    private val savedCourseId: Long?
        get() = arguments?.getLong(ARG_COURSE_ID, -1L)?.takeIf { it >= 0L }

    private lateinit var adapter: CourseStopAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCourseResultBinding.bind(view)

        applyTopInset()

        adapter = CourseStopAdapter { stop ->
            (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(stop.spot))
        }
        binding.rvCourseStops.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourseStops.adapter = adapter

        binding.btnResultBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val courseId = savedCourseId
        if (courseId != null) showSavedCourse(courseId) else showGeneratedCourse()
    }

    // ─────────────────── 방금 만든 코스 ───────────────────

    private fun showGeneratedCourse() {
        binding.btnResultSave.isVisible = true
        binding.btnResultSave.setOnClickListener { courseViewModel.saveCurrent() }

        courseViewModel.saved.observe(viewLifecycleOwner) { state ->
            val message = when (state) {
                SaveState.SUCCESS -> R.string.course_saved
                SaveState.FAILED -> R.string.course_save_failed
                SaveState.IDLE -> return@observe
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            courseViewModel.consumeSaved()
        }

        courseViewModel.course.observe(viewLifecycleOwner) { course ->
            course ?: return@observe
            render(course)
        }

        // 설명 문구는 규칙 요약 → LLM 문장 순으로 두 번 들어옵니다.
        courseViewModel.narration.observe(viewLifecycleOwner) {
            binding.tvResultNarration.text = it
        }
        courseViewModel.narrating.observe(viewLifecycleOwner) { generating ->
            // 타임라인은 이미 떠 있으므로 문구 생성 중에도 화면을 막지 않습니다.
            binding.progressResult.isVisible = false
            binding.tvResultNarration.alpha = if (generating) 0.7f else 1f
        }
    }

    // ─────────────────── 저장한 코스 ───────────────────

    private fun showSavedCourse(courseId: Long) {
        binding.btnResultSave.isVisible = false
        binding.progressResult.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val saved = runCatching {
                CourseRepository(requireContext()).get(courseId)
            }.getOrNull()

            val view = _binding ?: return@launch
            view.progressResult.isVisible = false

            if (saved == null) {
                view.tvResultNarration.text = getString(R.string.course_saved_missing)
                return@launch
            }

            view.tvResultTitle.text = saved.course.title
            view.tvResultNarration.text = saved.course.summary
            render(saved.toShootingCourse())
        }
    }

    // ─────────────────── 공통 렌더 ───────────────────

    private fun render(course: ShootingCourse) {
        adapter.submitList(course.stops)

        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val sun = course.sun
        binding.tvResultSun.text = if (sun.hasData) {
            getString(
                R.string.course_sun_format,
                sun.sunrise!!.format(fmt),
                sun.sunset!!.format(fmt)
            )
        } else {
            getString(R.string.course_sun_unknown)
        }

        binding.tvResultStats.text = course.fallbackSummary()
    }

    private fun applyTopInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutResultTop) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_COURSE_ID = "course_id"

        /** 저장한 코스를 여는 진입점. */
        fun forSaved(courseId: Long) = CourseResultFragment().apply {
            arguments = Bundle().apply { putLong(ARG_COURSE_ID, courseId) }
        }
    }
}
