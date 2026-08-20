package com.youngs.picview.ui.frame

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityPhotoFrameBinding
import com.youngs.picview.domain.frame.FourCutComposer
import com.youngs.picview.domain.frame.FrameTheme
import com.youngs.picview.domain.frame.PolaroidComposer
import com.youngs.picview.ui.palette.ColorPaletteActivity
import com.youngs.picview.util.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 정읍의 순간을 담다 — 포토 프레임.
 *
 * 찍은 사진에 정읍 테마 프레임을 입혀 저장·공유합니다. 출사 앱에서 사진은
 * 찍고 끝이 아니라 남에게 보여 주는 것으로 마무리되는데, 그때 "어디서 찍었는지"가
 * 사진에 같이 붙어야 정읍이 알려집니다. 그래서 프레임에 장소명과 날짜를 넣습니다.
 *
 * 두 모드가 있습니다.
 *  - 사진 프레임 : 정사각 폴라로이드 한 장 ([PolaroidComposer])
 *  - 네컷 사진   : 세로 스트립에 네 장 ([FourCutComposer])
 *
 * 미리보기와 저장본을 같은 코드로 만듭니다. 둘이 다르면
 * 본 것과 다른 게 저장되는데, 사용자가 알아채기 어려운 종류의 배신입니다.
 */
class PhotoFrameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_URI = "PHOTO_URI"
        const val EXTRA_PLACE = "PLACE"
        const val EXTRA_TAKEN_AT = "TAKEN_AT"

        /** 원본을 이보다 크게 읽지 않습니다. 네 장까지 들고 있어야 해서요. */
        private const val MAX_SIDE = 2048

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

    private enum class Mode { FOUR_CUT, SINGLE }

    private lateinit var binding: ActivityPhotoFrameBinding

    private var mode = Mode.SINGLE
    private var theme: FrameTheme = FrameTheme.MAPLE

    /** 사진 프레임(한 장) 모드의 원본. */
    private var source: Bitmap? = null

    /** 네컷 모드의 원본들. 처음 들어온 사진이 1번 칸이 됩니다. */
    private val fourPhotos = mutableListOf<Bitmap>()

    private var composed: Bitmap? = null

    /**
     * 색감 필터에서 돌아오면 원본을 물들인 것으로 갈아 끼웁니다.
     * 비트맵은 인텐트에 담기엔 커서 캐시 파일 경로로 주고받습니다.
     */
    private val colorFilter = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val path = result.data?.getStringExtra(ColorPaletteActivity.RESULT_PATH)
            ?: return@registerForActivityResult
        val graded = BitmapFactory.decodeFile(path) ?: return@registerForActivityResult

        source?.recycle()
        source = graded
        renderPreview()
    }

    /** 프레임 모드에서 사진 한 장 갈아 끼우기. */
    private val pickSingle = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val bitmap = decode(uri) ?: return@launch
            source = bitmap
            renderPreview()
        }
    }

    /** 네컷 모드에서 사진 네 장 고르기. 네 장 넘게 고르면 앞의 넷만 씁니다. */
    private val pickMultiple = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            val loaded = uris.take(4).mapNotNull { decode(it) }
            if (loaded.isEmpty()) return@launch
            fourPhotos.clear()
            fourPhotos.addAll(loaded)
            renderPreview()
        }
    }

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
        setupModeToggle()
        withStoragePermission { loadPhoto(uri) }

        binding.btnFrameColor.setOnClickListener {
            colorFilter.launch(ColorPaletteActivity.intent(this, uri))
        }
        binding.btnFramePick.setOnClickListener {
            if (mode == Mode.SINGLE) pickSingle.launch("image/*")
            else pickMultiple.launch("image/*")
        }
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

    // ─────────────────────── 모드 · 프레임 목록 ───────────────────────

    private lateinit var adapter: FrameSwatchAdapter

    private fun setupSwatches() {
        adapter = FrameSwatchAdapter { picked ->
            theme = picked
            renderPreview()
        }
        binding.rvFrames.adapter = adapter
    }

    private fun setupModeToggle() {
        binding.toggleFrameMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            mode = if (checkedId == R.id.btn_mode_fourcut) Mode.FOUR_CUT else Mode.SINGLE
            updateModeUi()
            renderPreview()
        }
        updateModeUi()
    }

    private fun updateModeUi() {
        binding.btnFramePick.setText(
            if (mode == Mode.SINGLE) R.string.frame_pick_one else R.string.frame_pick_four
        )
        // 색감 필터는 한 장짜리 흐름입니다. 네컷에서는 숨겨 헷갈리지 않게 합니다.
        binding.btnFrameColor.isVisible = mode == Mode.SINGLE
    }

    // ─────────────────────── 미리보기 ───────────────────────

    /**
     * 긴 변이 [MAX_SIDE] 를 넘지 않게 줄여 읽습니다.
     *
     * 네컷은 원본을 넉 장까지 들고 있어야 해서, 원본 크기 그대로 읽으면
     * 고화소 사진 네 장에 메모리가 바로 바닥납니다.
     */
    private suspend fun decode(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE / 2) {
                sample *= 2
            }

            contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(
                    it, null,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
        }.getOrNull()
    }

    private fun loadPhoto(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = decode(uri)
            if (bitmap == null) {
                Toast.makeText(
                    this@PhotoFrameActivity, R.string.frame_no_photo, Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }
            source = bitmap
            // 들어온 사진이 네컷의 첫 칸도 채웁니다.
            if (fourPhotos.isEmpty()) fourPhotos.add(bitmap)
            renderPreview()
        }
    }

    private fun renderPreview() {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                when (mode) {
                    Mode.SINGLE -> source?.let(::composeSingle)
                    Mode.FOUR_CUT -> composeFourCut()
                }
            } ?: return@launch
            composed = bitmap
            binding.ivFramePreview.setImageBitmap(bitmap)
        }
    }

    private fun composeSingle(photo: Bitmap): Bitmap {
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

    private fun composeFourCut(): Bitmap = FourCutComposer.compose(
        photos = fourPhotos,
        theme = theme,
        titleTypeface = ResourcesCompat.getFont(this, R.font.mapo_backpacking),
        bodyTypeface = ResourcesCompat.getFont(this, R.font.pretendard_bold)
    )

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
        displayName = "PicView_${if (mode == Mode.FOUR_CUT) "4cut" else "frame"}_" +
            "${System.currentTimeMillis()}.jpg",
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
        // 네컷 1번 칸은 source 와 같은 비트맵일 수 있어 한 번만 정리합니다.
        (fourPhotos + listOfNotNull(source)).distinct().forEach { it.recycle() }
    }
}

private fun String.toUri(): Uri = Uri.parse(this)
