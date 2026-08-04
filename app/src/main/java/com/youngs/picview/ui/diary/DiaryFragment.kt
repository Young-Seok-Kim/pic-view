package com.youngs.picview.ui.diary

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentDiaryBinding
import com.youngs.picview.domain.diary.DiaryDay

/**
 * 출사 기록.
 *
 * 방문 기록은 상세 화면의 "다녀왔어요"로 쌓이고, 여기서 날짜별로 묶입니다.
 * 일기 본문은 눌렀을 때만 생성합니다 — 자동 생성하면 쓰지도 않을 문장에
 * LLM 호출이 계속 나갑니다.
 */
class DiaryFragment : Fragment(R.layout.fragment_diary), MainActivity.TabRoot {

    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiaryViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDiaryBinding.bind(view)

        applyTopInset()

        val adapter = DiaryAdapter(
            onGenerate = { viewModel.generate(it) },
            onShare = { share(it) }
        )
        binding.rvDiary.adapter = adapter

        viewModel.days.observe(viewLifecycleOwner) { days ->
            adapter.submitList(days)
            binding.layoutDiaryEmpty.isVisible = days.isEmpty()
            binding.rvDiary.isVisible = days.isNotEmpty()
            binding.layoutDiaryHeader.isVisible = days.isNotEmpty()
        }

        viewModel.generating.observe(viewLifecycleOwner) { key ->
            adapter.generatingKey = key
        }
    }

    private fun applyTopInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootDiary) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    /** 일기를 텍스트로 공유합니다(카카오·인스타 등 설치된 앱). */
    private fun share(day: DiaryDay) {
        val diary = day.diary ?: return
        val text = buildString {
            append(diary.title).append("\n\n")
            append(diary.body).append("\n\n")
            append(getString(R.string.diary_share_tag))
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, diary.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.diary_share)))
        }.onFailure {
            Toast.makeText(requireContext(), R.string.senior_share_failed, Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun scrollToTop() {
        _binding?.rvDiary?.smoothScrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
