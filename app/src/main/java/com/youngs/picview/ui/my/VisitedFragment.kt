package com.youngs.picview.ui.my

import com.youngs.picview.util.applyTopSystemBarInset
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.databinding.FragmentVisitedBinding
import com.youngs.picview.databinding.ItemVisitedBinding
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.model.SpotItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 다녀온 곳 목록.
 *
 * 일기 탭이 '하루 단위'로 묶어 보여 준다면, 여기는 '장소 단위'로 훑는 화면입니다.
 * MY 탭의 '다녀온 곳' 숫자를 누르면 열립니다.
 */
class VisitedFragment : Fragment(R.layout.fragment_visited) {

    private var _binding: FragmentVisitedBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VisitedViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVisitedBinding.bind(view)

        applyTopInset()
        binding.btnVisitedBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = VisitedAdapter { visit ->
            (activity as? MainActivity)?.pushScreen(
                DetailFragment.newInstance(visit.toSpotItem())
            )
        }
        binding.rvVisited.adapter = adapter

        viewModel.visits.observe(viewLifecycleOwner) { visits ->
            adapter.submitList(visits)
            binding.tvVisitedEmpty.isVisible = visits.isEmpty()
            binding.rvVisited.isVisible = visits.isNotEmpty()
            binding.tvVisitedCount.text = getString(
                R.string.visited_count, visits.map { it.contentId }.distinct().size
            )
        }
    }

    private fun applyTopInset() {
        binding.rootVisited.applyTopSystemBarInset()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class VisitedViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CourseRepository(app)
    val visits: LiveData<List<VisitLogEntity>> = repository.observeVisits().asLiveData()
}

/**
 * 방문 기록에는 좌표가 없어서 상세로 넘길 때 최소 정보만 채웁니다.
 * 상세 화면은 contentId 로 사진·개요를 다시 받아오므로 표시에는 문제가 없습니다.
 */
private fun VisitLogEntity.toSpotItem() = SpotItem(
    contentId = contentId,
    contentTypeId = null,
    title = title,
    addr1 = "",
    tip = "",
    imageUrl = imageUrl,
    mapx = "",
    mapy = ""
).apply { this.score = this@toSpotItem.score }

private class VisitedAdapter(
    private val onClick: (VisitLogEntity) -> Unit
) : ListAdapter<VisitLogEntity, VisitedAdapter.VH>(DIFF) {

    class VH(val binding: ItemVisitedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemVisitedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        val at = Instant.ofEpochMilli(item.visitedAt).atZone(ZoneId.systemDefault())
        val phase = runCatching { LightPhase.valueOf(item.phaseName) }.getOrNull()

        with(holder.binding) {
            tvVisitedTitle.text = item.title
            tvVisitedWhen.text = context.getString(
                R.string.visited_when, at.format(WHEN), phase?.label.orEmpty()
            )
            tvVisitedScore.text = context.getString(R.string.home_score_format, item.score)
            tvVisitedScore.isVisible = item.score > 0

            Glide.with(ivVisited)
                .load(item.imageUrl.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(ivVisited)

            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val WHEN: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN)

        private val DIFF = object : DiffUtil.ItemCallback<VisitLogEntity>() {
            override fun areItemsTheSame(oldItem: VisitLogEntity, newItem: VisitLogEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: VisitLogEntity, newItem: VisitLogEntity) =
                oldItem == newItem
        }
    }
}
