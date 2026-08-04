package com.youngs.picview.ui.senior

import com.youngs.picview.util.applyTopSystemBarInset
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentSeniorHelpBinding
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.FontStep
import com.youngs.picview.util.LatLng

/**
 * 시니어 도움 탭 (스펙 §17-4 탭3 / §17-7).
 *
 * 전화는 ACTION_DIAL 로 겁니다. ACTION_CALL 은 CALL_PHONE 권한이 필요한데,
 * 실수로 눌러도 바로 걸리지 않는 편이 시니어에게 안전하고
 * 스토어 심사에서도 권한 사유를 설명할 필요가 없습니다.
 */
class SeniorHelpFragment : Fragment(R.layout.fragment_senior_help), MainActivity.TabRoot {

    private var _binding: FragmentSeniorHelpBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSeniorHelpBinding.bind(view)

        applyTopInset()
        setupCalls()
        setupFontSize()

        binding.btnBackToNormal.setOnClickListener { confirmBackToNormal() }
    }

    private fun applyTopInset() {
        binding.scrollSeniorHelp.applyTopSystemBarInset()
    }

    // ─────────────────────── 전화 ───────────────────────

    private fun setupCalls() {
        binding.btnCallInfo.setOnClickListener {
            confirmCall(getString(R.string.senior_tourist_info), TOURIST_INFO_NUMBER)
        }
        binding.btnCall119.setOnClickListener {
            confirmCall(getString(R.string.senior_call_119), EMERGENCY_NUMBER)
        }
        binding.btnShareLocation.setOnClickListener { shareLocation() }
    }

    private fun confirmCall(title: String, number: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(getString(R.string.senior_call_confirm, number))
            .setPositiveButton(R.string.confirm) { _, _ -> dial(number) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dial(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(requireContext(), R.string.senior_call_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 현재 위치를 문자·메신저로 보낼 수 있게 공유 시트를 엽니다.
     *
     * GPS 권한을 새로 요구하지 않도록, 지금은 마지막으로 본 촬영지 좌표를 씁니다.
     * (위치 권한은 지도 화면에서 이미 받아 두는 구조라 중복 요청을 피합니다)
     */
    private fun shareLocation() {
        val spot = viewModel.spotData.value?.firstOrNull()
        val coords = spot?.let { LatLng.parseOrNull(it.mapy, it.mapx) }

        val text = if (coords != null) {
            getString(
                R.string.senior_location_message,
                spot.title,
                "https://map.naver.com/v5/search/${Uri.encode(spot.title)}"
            )
        } else {
            getString(R.string.senior_location_unknown)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.senior_share_location)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.senior_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────── 글씨 크기 ───────────────────────

    private fun setupFontSize() {
        binding.toggleSeniorFont.check(buttonIdFor(AppPrefs.fontStep(requireContext())))

        binding.toggleSeniorFont.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val step = stepFor(checkedId)
            if (step == AppPrefs.fontStep(requireContext())) return@addOnButtonCheckedListener
            AppPrefs.setFontStep(requireContext(), step)
            requireActivity().recreate()
        }
    }

    private fun buttonIdFor(step: FontStep) = when (step) {
        FontStep.NORMAL -> R.id.btn_sfont_normal
        FontStep.LARGE -> R.id.btn_sfont_large
        FontStep.XLARGE -> R.id.btn_sfont_xlarge
    }

    private fun stepFor(buttonId: Int) = when (buttonId) {
        R.id.btn_sfont_large -> FontStep.LARGE
        R.id.btn_sfont_xlarge -> FontStep.XLARGE
        else -> FontStep.NORMAL
    }

    private fun confirmBackToNormal() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.senior_switch_back_title)
            .setMessage(R.string.senior_switch_back_desc)
            .setPositiveButton(R.string.confirm) { _, _ ->
                AppPrefs.setSeniorMode(requireContext(), false)
                requireActivity().recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun scrollToTop() {
        _binding?.scrollSeniorHelp?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** 정읍시 관광안내소 (스펙 §17-7) */
        private const val TOURIST_INFO_NUMBER = "063-539-5638"
        private const val EMERGENCY_NUMBER = "119"
    }
}
