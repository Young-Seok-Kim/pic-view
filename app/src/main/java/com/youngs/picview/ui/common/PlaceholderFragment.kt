package com.youngs.picview.ui.common

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentPlaceholderBinding

/**
 * 아직 구현하지 않은 탭 자리를 채웁니다.
 * Phase 를 진행하면서 실제 화면으로 하나씩 교체합니다.
 */
class PlaceholderFragment : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPlaceholderBinding.bind(view)

        binding.tvPlaceholderIcon.text = arguments?.getString(ARG_ICON).orEmpty()
        binding.tvPlaceholderTitle.text = arguments?.getString(ARG_TITLE).orEmpty()
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_ICON = "icon"

        fun newInstance(title: String, icon: String) = PlaceholderFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_ICON, icon)
            }
        }
    }
}
