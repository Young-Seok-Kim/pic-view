package com.youngs.picview.ui.my

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.youngs.picview.BuildConfig
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.local.SavedCourseWithStops
import com.youngs.picview.ui.course.CourseResultFragment
import com.youngs.picview.databinding.FragmentMyBinding
import com.youngs.picview.ui.mission.MissionFragment
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.FontStep

/**
 * MY — 내 출사 기록과 접근성 설정.
 *
 * 로그인이 없으므로 프로필은 서버 계정이 아니라 로컬 기록에서 계산합니다.
 * 시니어 전환과 글씨 크기는 Activity 재생성이 필요해서(테마·fontScale 은
 * 화면 생성 시점에만 적용됨) 확인 후 recreate 합니다.
 */
class MyFragment : Fragment(R.layout.fragment_my), MainActivity.TabRoot {

    private var _binding: FragmentMyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMyBinding.bind(view)

        applyTopInset()
        binding.btnMyMissions.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(MissionFragment())
        }

        // 저장한 코스는 이 화면 아래에 이미 목록이 있으므로 거기로 내려 줍니다.
        binding.layoutMyCourses.setOnClickListener {
            binding.scrollMy.smoothScrollTo(0, binding.rvMyCourses.top)
        }
        // 다녀온 곳은 별도 목록 화면으로.
        binding.layoutMyVisits.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(VisitedFragment())
        }
        setupCourses()
        setupSeniorToggle()
        setupFontSize()

        binding.tvMyFooter.text = getString(R.string.my_footer, BuildConfig.VERSION_NAME)
    }

    private fun applyTopInset() {
        binding.scrollMy.applyTopSystemBarInset()
    }

    // ─────────────────────── 저장한 코스 ───────────────────────

    private fun setupCourses() {
        val adapter = SavedCourseAdapter(
            onClick = { openCourse(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvMyCourses.adapter = adapter

        viewModel.courses.observe(viewLifecycleOwner) { courses ->
            adapter.submitList(courses)
            binding.tvMyNoCourse.isVisible = courses.isEmpty()
            binding.rvMyCourses.isVisible = courses.isNotEmpty()
        }

        viewModel.courseCount.observe(viewLifecycleOwner) {
            binding.tvMyCourseCount.text = it.toString()
        }
        viewModel.visitedSpotCount.observe(viewLifecycleOwner) {
            binding.tvMyVisitCount.text = it.toString()
        }
    }

    /** 저장한 코스를 누르면 그 코스의 빛 스케줄을 그대로 다시 엽니다. */
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

    // ─────────────────────── 시니어 모드 ───────────────────────

    private fun setupSeniorToggle() {
        val senior = AppPrefs.isSeniorMode(requireContext())

        binding.tvSeniorTitle.setText(
            if (senior) R.string.my_senior_title_on else R.string.my_senior_title
        )
        binding.btnSeniorToggle.setText(
            if (senior) R.string.my_senior_off else R.string.my_senior_on
        )

        binding.btnSeniorToggle.setOnClickListener {
            val enabling = !AppPrefs.isSeniorMode(requireContext())
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(
                    if (enabling) R.string.senior_switch_title
                    else R.string.senior_switch_back_title
                )
                .setMessage(
                    if (enabling) R.string.senior_switch_desc
                    else R.string.senior_switch_back_desc
                )
                .setPositiveButton(R.string.confirm) { _, _ ->
                    AppPrefs.setSeniorMode(requireContext(), enabling)
                    // 테마와 탭 구성이 Activity 생성 시점에 정해지므로 재생성합니다.
                    requireActivity().recreate()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    // ─────────────────────── 글씨 크기 ───────────────────────

    private fun setupFontSize() {
        val current = AppPrefs.fontStep(requireContext())
        binding.toggleFontSize.check(buttonIdFor(current))

        binding.toggleFontSize.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val step = stepFor(checkedId)
            if (step == AppPrefs.fontStep(requireContext())) return@addOnButtonCheckedListener

            AppPrefs.setFontStep(requireContext(), step)
            // fontScale 은 attachBaseContext 에서만 반영되므로 재생성이 필요합니다.
            requireActivity().recreate()
        }
    }

    private fun buttonIdFor(step: FontStep) = when (step) {
        FontStep.NORMAL -> R.id.btn_font_normal
        FontStep.LARGE -> R.id.btn_font_large
        FontStep.XLARGE -> R.id.btn_font_xlarge
    }

    private fun stepFor(buttonId: Int) = when (buttonId) {
        R.id.btn_font_large -> FontStep.LARGE
        R.id.btn_font_xlarge -> FontStep.XLARGE
        else -> FontStep.NORMAL
    }

    override fun scrollToTop() {
        _binding?.scrollMy?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
