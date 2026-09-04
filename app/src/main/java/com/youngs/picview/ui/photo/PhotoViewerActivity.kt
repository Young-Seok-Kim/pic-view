package com.youngs.picview.ui.photo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityPhotoViewerBinding
import com.youngs.picview.databinding.ItemPhotoPageBinding
import com.youngs.picview.ui.base.BaseActivity
import com.youngs.picview.ui.frame.PhotoFrameActivity
import com.youngs.picview.util.applyTopSystemBarInset
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 내가 찍은 사진 크게 보기.
 *
 * 상세 화면의 "내가 찍은 사진" 띠에서 한 장을 누르면 열립니다. 옆으로 넘기며
 * 그 장소에서 찍은 사진을 전부 볼 수 있고, 아래 버튼으로 바로 포토 프레임에
 * 넣을 수 있습니다. 오른쪽 위 휴지통으로 한 장씩 지웁니다.
 *
 * 사진은 갤러리 주소만 받습니다. 갤러리에서 지운 사진은 상세 화면이 미리
 * 걸러내므로 여기까지 오지 않습니다.
 */
class PhotoViewerActivity : BaseActivity() {

    /** 사진 화면은 글씨가 거의 없어 시니어 테마의 큰 여백이 오히려 방해됩니다. */
    override val appliesSeniorTheme = false

    private lateinit var binding: ActivityPhotoViewerBinding

    /** 화면에 남아 있는 사진. 지우면 여기서도 빠집니다. */
    private val photos = mutableListOf<Photo>()
    private var place = ""

    private lateinit var adapter: PageAdapter

    /** 시스템 삭제 확인창의 결과. 승인되면 기록에서도 뺍니다. */
    private var pendingDelete: Photo? = null
    private val deleteRequest =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val photo = pendingDelete ?: return@registerForActivityResult
            pendingDelete = null
            if (result.resultCode == Activity.RESULT_OK) removeRecord(photo)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 검은 바탕이 상태바 뒤까지 깔리게 합니다. 안 그러면 상태바 자리만
        // 앱 바탕색(크림)으로 남아 사진 화면이 잘린 것처럼 보입니다.
        enableEdgeToEdge()
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: LongArray(0)
        val uris = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
        val takenAt = intent.getLongArrayExtra(EXTRA_TAKEN_AT) ?: LongArray(uris.size)
        place = intent.getStringExtra(EXTRA_PLACE).orEmpty()
        val start = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, (uris.size - 1).coerceAtLeast(0))

        if (uris.isEmpty()) {
            finish()
            return
        }
        uris.forEachIndexed { i, uri ->
            photos += Photo(ids.getOrElse(i) { 0L }, uri, takenAt.getOrElse(i) { 0L })
        }

        binding.layoutViewerTop.applyTopSystemBarInset()
        binding.btnViewerClose.setOnClickListener { finish() }
        binding.tvViewerPlace.text = place
        binding.tvViewerPlace.isVisible = place.isNotBlank()

        adapter = PageAdapter(photos)
        binding.vpPhotos.adapter = adapter
        binding.vpPhotos.setCurrentItem(start, false)
        binding.vpPhotos.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = renderCaption(position)
        })
        renderCaption(start)

        binding.btnViewerFrame.setOnClickListener {
            val photo = photos.getOrNull(binding.vpPhotos.currentItem) ?: return@setOnClickListener
            startActivity(
                PhotoFrameActivity.intent(this, Uri.parse(photo.uri), place, photo.takenAt)
            )
        }
        binding.btnViewerDelete.setOnClickListener { confirmDelete() }
    }

    private fun renderCaption(position: Int) {
        binding.tvViewerCounter.text =
            getString(R.string.photo_viewer_counter, position + 1, photos.size)
        val at = photos.getOrNull(position)?.takenAt?.takeIf { it > 0L }
        binding.tvViewerWhen.isVisible = at != null
        if (at != null) {
            val text = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(WHEN)
            binding.tvViewerWhen.text = getString(R.string.photo_viewer_when, text)
        }
    }

    // ───────────────────── 삭제 ─────────────────────

    private fun confirmDelete() {
        val photo = photos.getOrNull(binding.vpPhotos.currentItem) ?: return
        PhotoDeleter.confirm(this) { delete(photo) }
    }

    private fun delete(photo: Photo) {
        lifecycleScope.launch {
            when (val outcome = PhotoDeleter.delete(this@PhotoViewerActivity, photo.id, photo.uri)) {
                PhotoDeleter.Outcome.Done -> removeFromScreen(photo)
                is PhotoDeleter.Outcome.NeedsSystemPrompt -> {
                    pendingDelete = photo
                    deleteRequest.launch(IntentSenderRequest.Builder(outcome.sender).build())
                }
                PhotoDeleter.Outcome.Failed -> Toast.makeText(
                    this@PhotoViewerActivity, R.string.photo_viewer_delete_failed, Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** 시스템 확인창이 승인된 뒤. 갤러리는 이미 지워졌으니 기록만 정리합니다. */
    private fun removeRecord(photo: Photo) {
        lifecycleScope.launch {
            PhotoDeleter.removeRecord(this@PhotoViewerActivity, photo.id)
            removeFromScreen(photo)
        }
    }

    /** 화면에서 걷어 냅니다. 마지막 장이었으면 화면을 닫습니다. */
    private fun removeFromScreen(photo: Photo) {
        val index = photos.indexOf(photo)
        if (index >= 0) {
            photos.removeAt(index)
            adapter.notifyItemRemoved(index)
        }
        Toast.makeText(this, R.string.photo_viewer_deleted, Toast.LENGTH_SHORT).show()
        if (photos.isEmpty()) {
            finish()
        } else {
            renderCaption(binding.vpPhotos.currentItem.coerceAtMost(photos.size - 1))
        }
    }

    // ───────────────────── 페이지 ─────────────────────

    private data class Photo(val id: Long, val uri: String, val takenAt: Long)

    private class PageAdapter(private val photos: List<Photo>) :
        RecyclerView.Adapter<PageAdapter.Holder>() {

        class Holder(val binding: ItemPhotoPageBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemPhotoPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = photos.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            Glide.with(holder.binding.ivPhotoPage)
                .load(Uri.parse(photos[position].uri))
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .fitCenter()
                .into(holder.binding.ivPhotoPage)
        }
    }

    companion object {
        private const val EXTRA_IDS = "extra_ids"
        private const val EXTRA_URIS = "extra_uris"
        private const val EXTRA_TAKEN_AT = "extra_taken_at"
        private const val EXTRA_PLACE = "extra_place"
        private const val EXTRA_START = "extra_start"

        private val WHEN: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN)

        /**
         * @param photoIds 기록의 사진 id. 삭제할 때 기록을 찾는 데 씁니다.
         * @param uris 사진 갤러리 주소들. 보여 줄 순서 그대로.
         * @param takenAt 각 사진을 찍은 시각(epoch millis). [uris] 와 같은 길이.
         * @param start 처음에 펼칠 사진의 번호
         */
        fun intent(
            context: Context,
            photoIds: List<Long>,
            uris: List<String>,
            takenAt: List<Long>,
            place: String,
            start: Int
        ) = Intent(context, PhotoViewerActivity::class.java).apply {
            putExtra(EXTRA_IDS, photoIds.toLongArray())
            putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
            putExtra(EXTRA_TAKEN_AT, takenAt.toLongArray())
            putExtra(EXTRA_PLACE, place)
            putExtra(EXTRA_START, start)
        }
    }
}
