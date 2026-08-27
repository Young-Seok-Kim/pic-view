package com.youngs.picview.ui.diary

import com.youngs.picview.util.applyTopSystemBarInset
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.util.SpotBookmark
import kotlinx.coroutines.launch
import java.time.LocalTime
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
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDiaryBinding.bind(view)

        applyTopInset()

        val adapter = DiaryAdapter(
            onGenerate = { viewModel.generate(it) },
            onShare = { share(it) },
            onEdit = { showEditDialog(it) },
            onPhotoClick = { openPhoto(it) },
            onPrepareNext = { rec -> prepareNext(rec) }
        )
        binding.rvDiary.adapter = adapter

        viewModel.days.observe(viewLifecycleOwner) { days ->
            adapter.submitList(days)
            binding.layoutDiaryEmpty.isVisible = days.isEmpty()
            binding.rvDiary.isVisible = days.isNotEmpty()
            binding.layoutDiaryHeader.isVisible = days.isNotEmpty()
            if (days.isNotEmpty()) adapter.nextRec = buildNextRec(days)

            // 제목 옆 알약 — 이번 달에 며칠을 기록했는지 한 마디로.
            val today = java.time.LocalDate.now()
            val monthDays = days.count {
                it.date.year == today.year && it.date.month == today.month
            }
            binding.tvDiaryMonth.isVisible = monthDays > 0
            binding.tvDiaryMonth.text = getString(R.string.diary_month_count, monthDays)
        }

        mainViewModel.spotData.observe(viewLifecycleOwner) {
            val days = viewModel.days.value.orEmpty()
            if (days.isNotEmpty()) adapter.nextRec = buildNextRec(days)
        }

        viewModel.generating.observe(viewLifecycleOwner) { key ->
            adapter.generatingKey = key
        }
    }

    // ───────────────────── 시선이의 다음 추천 ─────────────────────

    /**
     * 오늘의 기록에서 다음 출사 한 곳을 고릅니다.
     *
     * 결정론 계산입니다 — 이미 다녀온 곳은 빼고, 물가(반사)를 먼저,
     * 없으면 점수 높은 곳을 고릅니다. 반사를 먼저 미는 이유는 기록의
     * 다음 걸음으로 가장 실패가 적은 구도라서입니다. 문장과 시각은
     * 그 장소의 촬영 특성과 천문값에서 나옵니다.
     */
    private fun buildNextRec(days: List<DiaryDay>): DiaryAdapter.NextRec? {
        val spots = mainViewModel.spotData.value.orEmpty()
            .filter { it.imageUrl.isNotBlank() }
        if (spots.isEmpty()) return null

        val visited = days.firstOrNull()?.visits?.map { it.title }.orEmpty().toSet()
        val candidates = spots.filterNot { it.title in visited }.ifEmpty { spots }

        val spot = candidates.firstOrNull {
            SiseonGuide.guideIdFor(SpotFactsTable.of(it.title, it.contentTypeId)) == "reflection"
        } ?: candidates.maxByOrNull { it.score } ?: return null

        val facts = SpotFactsTable.of(spot.title, spot.contentTypeId)
        val guideId = SiseonGuide.guideIdFor(facts)
        val guide = SiseonGuide.byId(guideId)

        val sun = mainViewModel.sunTimes
        val at = when (facts.bestPhase) {
            LightPhase.BLUE_DAWN -> sun.civilDawn ?: sun.sunrise?.minusMinutes(25)
            LightPhase.SUNRISE -> sun.sunrise
            LightPhase.MORNING -> sun.sunrise?.plusMinutes(60)
            LightPhase.MIDDAY -> LocalTime.of(11, 0)
            LightPhase.AFTERNOON -> LocalTime.of(14, 0)
            LightPhase.SUNSET -> sun.sunset?.minusMinutes(40)
            LightPhase.BLUE_DUSK -> sun.sunset
            LightPhase.NIGHT -> sun.civilDusk ?: sun.sunset?.plusMinutes(30)
        }
        val timeLabel = at?.let {
            val hhmm = "%02d:%02d".format(it.hour, it.minute)
            getString(
                R.string.diary_next_time,
                if (it >= LocalTime.now()) getString(R.string.home_spot_time_today, hhmm)
                else getString(R.string.home_spot_time_tomorrow, hhmm)
            )
        } ?: getString(R.string.diary_next_time, facts.bestPhase.shortLabel)

        return DiaryAdapter.NextRec(
            spot = spot,
            headline = SiseonGuide.nextHeadlineFor(guideId),
            compLabel = guide.title,
            timeLabel = "⏱ $timeLabel",
            desc = facts.note
        )
    }

    /**
     * "이 계획으로 출사 준비하기" — 그 장소를 찜해 둡니다.
     *
     * 예전에는 한 곳짜리 코스를 새로 만들어 코스 목록에 넣었습니다. 그래서
     * 누를 때마다 1곳짜리 코스가 하나씩 생기고 서로 합쳐지지 않았습니다.
     * 담아 둔 곳들로 코스를 짜는 길은 코스 탭의 '직접 고르기'에 있습니다.
     */
    private fun prepareNext(rec: DiaryAdapter.NextRec) {
        val added = SpotBookmark.toggle(requireContext(), rec.spot.contentId)
        Toast.makeText(
            requireContext(),
            if (added) R.string.bookmark_added else R.string.bookmark_removed,
            Toast.LENGTH_SHORT
        ).show()
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
