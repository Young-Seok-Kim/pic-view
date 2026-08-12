package com.youngs.picview.ui.diary

import com.youngs.picview.util.applyTopSystemBarInset
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentDiaryBinding
import com.youngs.picview.domain.diary.DiaryDay
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngs.picview.databinding.DialogDiaryEditBinding
import com.youngs.picview.ui.frame.PhotoFrameActivity

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
            onShare = { share(it) },
            onEdit = { showEditDialog(it) },
            onPhotoClick = { openPhoto(it) }
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
        binding.rootDiary.applyTopSystemBarInset()
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

    // ───────────────────── 일기 고치기 ─────────────────────

    /**
     * 생성된 일기를 사용자가 고칩니다.
     *
     * LLM 이 쓴 문장이 사실과 다르거나 말투가 안 맞을 수 있습니다. 무엇보다
     * "내 일기"라면 내가 고칠 수 있어야 합니다. 고친 뒤에는 LLM 생성물로
     * 세지 않습니다.
     */
    private fun showEditDialog(day: DiaryDay) {
        val diary = day.diary ?: return
        val view = layoutInflater.inflate(R.layout.dialog_diary_edit, null)
        val binding = DialogDiaryEditBinding.bind(view)

        binding.etDiaryTitle.setText(diary.title)
        binding.etDiaryBody.setText(diary.body)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.diary_edit_title)
            .setView(view)
            .setPositiveButton(R.string.diary_edit_save) { _, _ ->
                val title = binding.etDiaryTitle.text?.toString().orEmpty()
                val body = binding.etDiaryBody.text?.toString().orEmpty()
                if (body.isBlank()) return@setPositiveButton

                viewModel.saveEdit(day, title, body)
                Toast.makeText(requireContext(), R.string.diary_edited, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 일기 사진을 누르면 포토 프레임으로 갑니다.
     *
     * 그냥 크게 보는 것보다 "이 사진으로 뭘 할 수 있는가"로 이어지는 편이
     * 낫습니다. 찍고 → 기록되고 → 일기가 되고 → 프레임을 입혀 공유까지,
     * 사진 한 장의 여정이 여기서 끝납니다.
     */
    private fun openPhoto(uri: Uri) {
        val day = viewModel.days.value?.firstOrNull { day ->
            day.visits.any { it.photoUri == uri.toString() }
        }
        val visit = day?.visits?.firstOrNull { it.photoUri == uri.toString() }

        startActivity(
            PhotoFrameActivity.intent(
                context = requireContext(),
                photoUri = uri,
                place = visit?.title.orEmpty(),
                takenAt = visit?.visitedAt ?: System.currentTimeMillis()
            )
        )
    }
}
