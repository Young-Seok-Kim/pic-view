package com.youngs.picview.ui.my

import com.youngs.picview.util.applyTopSystemBarInset
import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import com.youngs.picview.ui.main.MainViewModel
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
 *
 * 한 곳에 한 줄입니다. 예전에는 방문 건마다 한 줄이라 같은 곳을 두 번 가면
 * 두 줄이 나왔고, 위의 "2곳" 과 아래 세 줄이 서로 안 맞아 보였습니다. 여러 번
 * 간 곳은 가장 최근 방문을 앞세우고 "2번 다녀옴" 을 붙입니다.
 */
class VisitedFragment : Fragment(R.layout.fragment_visited) {

    private var _binding: FragmentVisitedBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VisitedViewModel by viewModels()

    /** 홈이 받아 둔 촬영지 목록. 방문 기록에 없는 주소·좌표를 여기서 채웁니다. */
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVisitedBinding.bind(view)

        applyTopInset()
        binding.btnVisitedBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = VisitedAdapter { place ->
            (activity as? MainActivity)?.pushScreen(
                DetailFragment.newInstance(resolveSpot(place.latest))
            )
        }
        binding.rvVisited.adapter = adapter

        viewModel.visits.observe(viewLifecycleOwner) { visits ->
            val places = VisitedPlace.groupOf(visits)
            adapter.submitList(places)
            binding.tvVisitedEmpty.isVisible = places.isEmpty()
            binding.rvVisited.isVisible = places.isNotEmpty()
            binding.tvVisitedCount.text = getString(R.string.visited_count, places.size)
        }
    }

    /**
     * 상세로 넘길 장소 정보.
     *
     * 방문 기록에는 이름과 사진뿐이라 그대로 넘기면 상세의 주소 줄이 비고
     * 길찾기가 갈 곳을 모릅니다. 홈 목록에 같은 곳이 있으면 그 온전한
     * 정보를 씁니다. 없으면(목록을 못 받았을 때) 최소 정보로 넘기고,
     * 상세 화면이 API 로 주소를 채웁니다.
     */
    private fun resolveSpot(visit: VisitLogEntity): SpotItem =
        mainViewModel.spotData.value.orEmpty()
            .firstOrNull { it.contentId == visit.contentId }
            ?: visit.toSpotItem()

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

/**
 * 한 장소로 묶은 방문.
 *
 * @param latest 가장 최근 방문. 제목·시각·점수는 여기서 옵니다.
 * @param count 몇 번 다녀왔는지
 * @param thumbnail 내가 찍은 사진 중 하나. 없으면 장소 사진, 그것도 없으면 null.
 */
private data class VisitedPlace(
    val latest: VisitLogEntity,
    val count: Int,
    val thumbnail: String?
) {
    companion object {
        /** 최근 방문 순으로 정렬된 [visits] 를 장소별로 묶습니다. 순서는 유지됩니다. */
        fun groupOf(visits: List<VisitLogEntity>): List<VisitedPlace> =
            visits.groupBy { it.contentId }.values.map { group ->
                VisitedPlace(
                    latest = group.first(),
                    count = group.size,
                    thumbnail = group.firstNotNullOfOrNull { it.photoUri?.takeIf(String::isNotBlank) }
                        ?: group.firstNotNullOfOrNull { it.imageUrl.takeIf(String::isNotBlank) }
                )
            }.sortedByDescending { it.latest.visitedAt }
    }
}

private class VisitedAdapter(
    private val onClick: (VisitedPlace) -> Unit
) : ListAdapter<VisitedPlace, VisitedAdapter.VH>(DIFF) {

    class VH(val binding: ItemVisitedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemVisitedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val place = getItem(position)
        val item = place.latest
        val context = holder.itemView.context

        val at = Instant.ofEpochMilli(item.visitedAt).atZone(ZoneId.systemDefault())
        val phase = runCatching { LightPhase.valueOf(item.phaseName) }.getOrNull()

        with(holder.binding) {
            tvVisitedTitle.text = item.title
            val when_ = context.getString(
                R.string.visited_when, at.format(WHEN), phase?.label.orEmpty()
            )
            tvVisitedWhen.text = if (place.count > 1) {
                "$when_ · " + context.getString(R.string.visited_times, place.count)
            } else when_
            tvVisitedScore.text = context.getString(R.string.home_score_format, item.score)
            tvVisitedScore.isVisible = item.score > 0

            // 촬영으로 남은 기록은 imageUrl 이 비어 있습니다(그 자리에서 찍었으니
            // 관광공사 사진을 받아 둘 이유가 없습니다). 내가 찍은 사진을 먼저
            // 걸고, 없을 때만 장소 사진으로 내려갑니다.
            Glide.with(ivVisited)
                .load(place.thumbnail)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(ivVisited)

            root.setOnClickListener { onClick(place) }
        }
    }

    companion object {
        private val WHEN: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN)

        private val DIFF = object : DiffUtil.ItemCallback<VisitedPlace>() {
            override fun areItemsTheSame(oldItem: VisitedPlace, newItem: VisitedPlace) =
                oldItem.latest.contentId == newItem.latest.contentId

            override fun areContentsTheSame(oldItem: VisitedPlace, newItem: VisitedPlace) =
                oldItem == newItem
        }
    }
}
