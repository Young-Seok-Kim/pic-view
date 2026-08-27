package com.youngs.picview.ui.course

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentSpotPickerBinding
import com.youngs.picview.databinding.ItemSpotPickBinding
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.adapter.SpotAdapter
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.LatLng
import com.youngs.picview.util.TravelMode
import com.youngs.picview.util.applyTopSystemBarInset
import com.youngs.picview.util.distanceKmTo
import com.youngs.picview.util.estimateTravelMinutes

/**
 * 코스에 담을 촬영지 고르기 — "장소는 내가, 순서는 빛이".
 *
 * 지금까지 코스는 전부 앱이 짰습니다. 조건만 고르고 결과를 받는 구조라
 * "나는 여기랑 여기만 갈래"가 안 됐고, 찜을 해 둬도 그 찜한 곳으로
 * 코스를 만들 수가 없었습니다.
 *
 * 그렇다고 순서까지 손으로 짜게 하지는 않습니다. 골든아워는 하루에 두 번,
 * 30분씩뿐이라 그 자리를 사람이 정하면 빛을 계산하는 뜻이 사라집니다.
 * 이 화면은 **고르는 일만** 돌려주고, 세우는 일은 [com.youngs.picview.domain.course.CoursePlanner]
 * 가 그대로 합니다.
 */
class SpotPickerFragment : Fragment(R.layout.fragment_spot_picker) {

    private var _binding: FragmentSpotPickerBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val courseViewModel: CourseViewModel by activityViewModels()

    private lateinit var adapter: PickAdapter

    /** 목록에 무엇을 보일지. 찜한 곳만 보는 쓰임이 가장 잦습니다. */
    private enum class Filter { ALL, FAVORITE, PICKED }

    private var filter = Filter.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSpotPickerBinding.bind(view)

        binding.rootPicker.applyTopSystemBarInset()
        binding.btnPickerBack.setOnClickListener { parentFragmentManager.popBackStack() }

        adapter = PickAdapter { spot ->
            courseViewModel.togglePicked(spot.contentId)
        }
        binding.rvPicker.adapter = adapter
        binding.rvPicker.itemAnimator = null

        buildFilterChips()

        binding.btnPickerClear.setOnClickListener { courseViewModel.clearPicked() }
        binding.btnPickerDone.setOnClickListener { parentFragmentManager.popBackStack() }

        // 담은 곳이 바뀌면 목록의 체크와 아래 버튼이 함께 따라옵니다.
        courseViewModel.pickedIds.observe(viewLifecycleOwner) { render() }
        mainViewModel.spotData.observe(viewLifecycleOwner) { render() }
    }

    private fun buildFilterChips() {
        val group = binding.chipsPickerFilter
        group.removeAllViews()

        val entries = listOf(
            R.string.picker_filter_all to Filter.ALL,
            R.string.picker_filter_favorite to Filter.FAVORITE,
            R.string.picker_filter_picked to Filter.PICKED
        )

        entries.forEachIndexed { index, (labelRes, value) ->
            group.addView(
                Chip(requireContext()).apply {
                    id = View.generateViewId()
                    setText(labelRes)
                    isCheckable = true
                    isChecked = index == 0
                    tag = value
                }
            )
        }

        group.setOnCheckedStateChangeListener { chips, checked ->
            val chip = checked.firstOrNull()?.let { chips.findViewById<Chip>(it) }
            filter = (chip?.tag as? Filter) ?: Filter.ALL
            render()
        }
    }

    private fun render() {
        val view = _binding ?: return
        val all = mainViewModel.spotData.value.orEmpty()
        val favorites = AppPrefs.favoriteSpots(requireContext())
        val picked = courseViewModel.pickedIds.value.orEmpty()

        val rows = all
            .filter { spot ->
                when (filter) {
                    Filter.ALL -> true
                    Filter.FAVORITE -> spot.contentId in favorites
                    Filter.PICKED -> spot.contentId in picked
                }
            }
            .map { spot ->
                PickRow(
                    spot = spot,
                    favorite = spot.contentId in favorites,
                    picked = spot.contentId in picked
                )
            }

        adapter.submitList(rows)

        view.tvPickerEmpty.isVisible = rows.isEmpty()
        view.rvPicker.isVisible = rows.isNotEmpty()
        view.tvPickerEmpty.setText(
            when {
                all.isEmpty() -> R.string.picker_empty_spots
                filter == Filter.FAVORITE -> R.string.picker_empty_favorites
                else -> R.string.picker_empty_picked
            }
        )

        // 아무것도 안 담았으면 "전체에서 짜기"입니다. 버튼이 그렇게 말해야
        // 담지 않고 나가도 코스가 나온다는 것을 알 수 있습니다.
        view.btnPickerDone.text = if (picked.isEmpty()) {
            getString(R.string.picker_done_all)
        } else {
            getString(R.string.picker_done_count, picked.size)
        }
        view.btnPickerClear.isVisible = picked.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** 고르기 목록의 한 줄. */
data class PickRow(
    val spot: SpotItem,
    val favorite: Boolean,
    val picked: Boolean
)

private class PickAdapter(
    private val onToggle: (SpotItem) -> Unit
) : ListAdapter<PickRow, PickAdapter.VH>(DIFF) {

    class VH(val binding: ItemSpotPickBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemSpotPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val context = holder.itemView.context
        val facts = SpotFactsTable.of(row.spot.title, row.spot.contentTypeId)

        with(holder.binding) {
            tvPickTitle.text = row.spot.title

            // "일몰 · 따뜻한 사광 · 차로 8분" — 언제 가는 곳이고 얼마나 먼지.
            val signal = "${facts.bestPhase.shortLabel} · ${facts.bestPhase.lightCharacter}"
            val coords = LatLng.parseOrNull(row.spot.mapy, row.spot.mapx)
            tvPickSignal.text = if (coords == null) {
                signal
            } else {
                val minutes = estimateTravelMinutes(
                    SpotAdapter.CITY_CENTER.distanceKmTo(coords), TravelMode.CAR
                )
                "$signal · " + context.getString(R.string.spot_travel_minutes, minutes)
            }

            tvPickFavorite.isVisible = row.favorite

            cbPick.isChecked = row.picked
            cardPick.isChecked = row.picked
            cardPick.strokeWidth = if (row.picked) STROKE_PICKED else STROKE_PLAIN

            Glide.with(ivPick)
                .load(row.spot.imageUrl.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(ivPick)

            // 카드 어디를 눌러도 담깁니다. 체크박스만 과녁으로 두면
            // 장갑 낀 손이나 흔들리는 차 안에서 자꾸 빗나갑니다.
            cardPick.setOnClickListener { onToggle(row.spot) }
        }
    }

    companion object {
        private const val STROKE_PLAIN = 1
        private const val STROKE_PICKED = 2

        private val DIFF = object : DiffUtil.ItemCallback<PickRow>() {
            override fun areItemsTheSame(oldItem: PickRow, newItem: PickRow) =
                oldItem.spot.contentId == newItem.spot.contentId

            override fun areContentsTheSame(oldItem: PickRow, newItem: PickRow) =
                oldItem == newItem
        }
    }
}
