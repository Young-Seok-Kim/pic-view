package com.youngs.picview.ui.frame

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityPhotoFrameBinding
import com.youngs.picview.domain.frame.FrameTheme
import com.youngs.picview.domain.frame.PolaroidComposer
import com.youngs.picview.util.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.Manifest
import android.os.Build
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission

/**
 * 포토 프레임 (시안 5:2).
 *
 * 찍은 사진에 정읍 테마 프레임을 입혀 저장·공유합니다. 출사 앱에서 사진은
 * 찍고 끝이 아니라 남에게 보여 주는 것으로 마무리되는데, 그때 "어디서 찍었는지"가
 * 사진에 같이 붙어야 정읍이 알려집니다. 그래서 프레임에 장소명과 날짜를 넣습니다.
 *
 * 미리보기와 저장본을 같은 코드([PolaroidComposer])로 만듭니다. 둘이 다르면
 * 본 것과 다른 게 저장되는데, 사용자가 알아채기 어려운 종류의 배신입니다.
 */
class PhotoFrameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_URI = "PHOTO_URI"
        const val EXTRA_PLACE = "PLACE"
        const val EXTRA_TAKEN_AT = "TAKEN_AT"

        fun intent(
            context: android.content.Context,
            photoUri: Uri,
            place: String,
            takenAt: Long
        ) = Intent(context, PhotoFrameActivity::class.java).apply {
            putExtra(EXTRA_PHOTO_URI, photoUri.toString())
            putExtra(EXTRA_PLACE, place)
            putExtra(EXTRA_TAKEN_AT, takenAt)
        }
    }

    private lateinit var binding: ActivityPhotoFrameBinding

    private var source: Bitmap? = null
    private var composed: Bitmap? = null
    private var theme: FrameTheme = FrameTheme.MAPLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPhotoFrameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        binding.btnFrameBack.setOnClickListener { finish() }

        // 앱 안에서는 extra 로 넘어오고, 다른 앱에서 "공유 → 정읍 시선" 으로
        // 들어오면 data 에 담겨 옵니다. 둘 다 받습니다.
        val uri = intent.getStringExtra(EXTRA_PHOTO_URI)?.toUri()
            ?: intent.data
            ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

        if (uri == null) {
            Toast.makeText(this, R.string.frame_no_photo, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupSwatches()
        withStoragePermission { loadPhoto(uri) }

        binding.btnFrameSave.setOnClickListener { save(share = false) }
        binding.btnFrameShare.setOnClickListener { save(share = true) }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutFrameTop.updatePadding(top = bars.top + dp(8))
            binding.root.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // ─────────────────────── 프레임 목록 ───────────────────────

    private lateinit var adapter: FrameSwatchAdapter

    private fun setupSwatches() {
        adapter = FrameSwatchAdapter { picked ->
            theme = picked
            renderPreview()
        }
        binding.rvFrames.adapter = adapter
    }

    // ─────────────────────── 미리보기 ───────────────────────

    private fun loadPhoto(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap == null) {
                Toast.makeText(
                    this@PhotoFrameActivity, R.string.frame_no_photo, Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }
            source = bitmap
            renderPreview()
        }
    }

    private fun renderPreview() {
        val photo = source ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) { composeWith(photo) }
            composed = bitmap
            binding.ivFramePreview.setImageBitmap(bitmap)
        }
    }

    private fun composeWith(photo: Bitmap): Bitmap {
        val takenAt = intent.getLongExtra(EXTRA_TAKEN_AT, System.currentTimeMillis())
        val date = Instant.ofEpochMilli(takenAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREAN))

        return PolaroidComposer.compose(
            photo = photo,
            theme = theme,
            place = intent.getStringExtra(EXTRA_PLACE).orEmpty().ifBlank { getString(R.string.app_name) },
            dateText = date,
            credit = getString(R.string.frame_credit),
            titleTypeface = ResourcesCompat.getFont(this, R.font.mapo_backpacking),
            bodyTypeface = ResourcesCompat.getFont(this, R.font.pretendard_regular)
        )
    }

    // ─────────────────────── 저장·공유 ───────────────────────

    /**
     * 갤러리에 저장하고, [share] 면 이어서 공유창을 엽니다.
     *
     * 공유만 하고 저장하지 않는 선택지는 두지 않았습니다. 공유했는데 내 갤러리에는
     * 없는 상태가 더 헷갈립니다.
     */
    private fun save(share: Boolean) {
        val bitmap = composed ?: return
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { writeToGallery(bitmap) }
            if (uri == null) {
                Toast.makeText(
                    this@PhotoFrameActivity, R.string.frame_save_failed, Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            Toast.makeText(this@PhotoFrameActivity, R.string.frame_saved, Toast.LENGTH_SHORT).show()
            if (share) shareImage(uri)
        }
    }

    private fun writeToGallery(bitmap: Bitmap): Uri? = MediaStoreSaver.saveBitmap(
        context = this,
        displayName = "PicView_frame_${System.currentTimeMillis()}.jpg",
        bitmap = bitmap
    )

    private fun shareImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.frame_share)))
    }

    /**
     * 안드로이드 9 이하에서는 갤러리 사진을 읽고 저장하는 데 권한이 필요합니다.
     *
     * 10 부터는 앱이 자기가 저장한 사진을 권한 없이 읽을 수 있어 바로 진행합니다.
     * minSdk 가 26 이라 이 구간이 남아 있고, 빼먹으면 구형 기기에서 사진이
     * 통째로 안 보입니다.
     */
    private fun withStoragePermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onGranted()
            return
        }

        TedPermission.create()
            .setPermissionListener(object : PermissionListener {
                override fun onPermissionGranted() = onGranted()

                override fun onPermissionDenied(denied: MutableList<String>?) {
                    Toast.makeText(
                        this@PhotoFrameActivity, R.string.frame_no_photo, Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            })
            .setPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .check()
    }

    override fun onDestroy() {
        super.onDestroy()
        composed?.recycle()
        source?.recycle()
    }
}

private fun String.toUri(): Uri = Uri.parse(this)
