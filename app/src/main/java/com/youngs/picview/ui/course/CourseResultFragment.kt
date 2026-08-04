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
import androidx.recyclerview.widget.LinearLayoutManager
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentCourseResultBinding
import com.youngs.picview.ui.detail.DetailFragment
import java.time.format.DateTimeFormatter

/** 계산된 빛 스케줄을 타임라인으로 보여 줍니다. */
class CourseResultFragment : Fragment(R.layout.fragment_course_result) {

    private var _binding: FragmentCourseResultBinding? = null
    private val binding get() = _binding!!

    private val courseViewModel: CourseViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCourseResultBinding.bind(view)

        applyTopInset()

        val adapter = CourseStopAdapter { stop ->
            (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(stop.spot))
        }
        binding.rvCourseStops.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourseStops.adapter = adapter

        binding.btnResultBack.setOnClickListener { parentFragmentManager.popBackStack() }
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
            adapter.submitList(course.stops)
            renderSummary(course)
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

    private fun renderSummary(course: com.youngs.picview.domain.course.ShootingCourse) {
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
}
