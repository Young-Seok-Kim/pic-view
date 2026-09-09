package com.youngs.picview.ui.frame

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityFramedPhotosBinding
import com.youngs.picview.databinding.ItemFramedPhotoBinding
import com.youngs.picview.ui.base.BaseActivity
import com.youngs.picview.util.applyTopSystemBarInset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 프레임으로 만든 사진 모아 보기.
 *
 * 프레임을 씌워 저장하면 갤러리의 PicView 폴더에 들어가는데, 앱 안에서는
 * 그것을 다시 볼 자리가 없었습니다. "저장했어요"만 뜨고 어디로 갔는지
 * 모르는 상태였습니다. 여기서 앱이 만든 프레임 사진만 골라 보여 줍니다 —
 * 갤러리에서 앱이 저장한 것은 권한 없이 읽을 수 있습니다.
 *
 * 누르면 시스템 사진 뷰어로 크게 보고, 길게 누르면 공유합니다.
 */
class FramedPhotosActivity : BaseActivity() {

    private lateinit var binding: ActivityFramedPhotosBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFramedPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutFramedTop.applyTopSystemBarInset()
        binding.btnFramedBack.setOnClickListener { finish() }

        val adapter = FramedAdapter(
            onClick = { open(it) },
            onLongClick = { share(it) }
        )
        binding.rvFramed.layoutManager = GridLayoutManager(this, 2)
        binding.rvFramed.adapter = adapter

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { query() }
            adapter.submitList(items)
            binding.tvFramedCount.text = getString(R.string.framed_count, items.size)
            binding.layoutFramedEmpty.isVisible = items.isEmpty()
        }
    }

    /** 이 앱이 저장한 프레임·네컷 파일만. 이름으로 가립니다 — 저장할 때 붙이는 접두어입니다. */
    private fun query(): List<Framed> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR " +
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("PicView_frame_%", "PicView_4cut_%")
        val out = mutableListOf<Framed>()
        runCatching {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    out += Framed(
                        uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        isFourCut = c.getString(nameCol).startsWith("PicView_4cut_"),
                        addedAt = c.getLong(dateCol) * 1000L
                    )
                }
            }
        }
        return out
    }

    private fun open(item: Framed) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(view) }.onFailure {
            Toast.makeText(this, R.string.guide_photo_view_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(item: Framed) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.frame_share)))
    }

    data class Framed(val uri: Uri, val isFourCut: Boolean, val addedAt: Long)

    private class FramedAdapter(
        private val onClick: (Framed) -> Unit,
        private val onLongClick: (Framed) -> Unit
    ) : ListAdapter<Framed, FramedAdapter.Holder>(DIFF) {

        class Holder(val binding: ItemFramedPhotoBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemFramedPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = getItem(position)
            with(holder.binding) {
                Glide.with(ivFramed).load(item.uri)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .fitCenter()
                    .into(ivFramed)
                val kind = root.context.getString(
                    if (item.isFourCut) R.string.frame_mode_fourcut else R.string.frame_mode_single
                )
                tvFramedCaption.text = "$kind · " +
                    Instant.ofEpochMilli(item.addedAt).atZone(ZoneId.systemDefault()).format(WHEN)
                root.setOnClickListener { onClick(item) }
                root.setOnLongClickListener { onLongClick(item); true }
            }
        }

        companion object {
            private val WHEN = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
            private val DIFF = object : DiffUtil.ItemCallback<Framed>() {
                override fun areItemsTheSame(a: Framed, b: Framed) = a.uri == b.uri
                override fun areContentsTheSame(a: Framed, b: Framed) = a == b
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, FramedPhotosActivity::class.java)
    }
}
