package com.youngs.picview.ui.palette

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityColorPaletteBinding
import com.youngs.picview.domain.palette.ColorPalette
import com.youngs.picview.domain.palette.PaletteGrader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 색감 필터 (시안 13:2).
 *
 * 찍은 사진에 정읍의 색을 입힙니다. 여행 사진 앱에서 필터는 흔하지만,
 * 여기서는 필터 이름이 곧 장소입니다. "빈티지 3" 대신 "내장산 단풍"을
 * 고르게 해서, 사진이 남에게 보여질 때 정읍이 함께 따라가게 합니다.
 *
 * 포토 프레임에서 들어오고 다시 그리로 돌아갑니다. 그래서 상단 버튼이
 * 시안의 "저장"이 아니라 **"적용하기"** 입니다. 여기서 갤러리에 한 장,
 * 프레임에서 또 한 장 저장하면 같은 사진이 두 벌 남습니다. 저장은 사진
 * 한 장의 여정이 끝나는 프레임에서 한 번만 합니다.
 */
class ColorPaletteActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PHOTO_URI = "PHOTO_URI"

        /** 결과로 돌려주는 파일 경로. 비트맵은 인텐트에 담기엔 너무 큽니다. */
        const val RESULT_PATH = "GRADED_PATH"

        fun intent(context: Context, photoUri: Uri) =
            Intent(context, ColorPaletteActivity::class.java).apply {
                putExtra(EXTRA_PHOTO_URI, photoUri.toString())
            }
    }

    private lateinit var binding: ActivityColorPaletteBinding

    private var source: Bitmap? = null
    private var graded: Bitmap? = null

    private var palette = ColorPalette.MAPLE
    private var strength = DEFAULT_STRENGTH

    /** 진행 중인 보정 작업. 슬라이더를 밀면 앞의 작업은 버립니다. */
    private var grading: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityColorPaletteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        binding.btnPaletteBack.setOnClickListener { finish() }

        val uri = intent.getStringExtra(EXTRA_PHOTO_URI)?.let(Uri::parse)
        if (uri == null) {
            Toast.makeText(this, R.string.palette_no_photo, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupPalettes()
        setupStrength()
        loadPhoto(uri)

        binding.btnPaletteApply.setOnClickListener { applyAndReturn() }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutPaletteTop.updatePadding(top = bars.top + dp(8))
            binding.root.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // ─────────────────────── 입력 ───────────────────────

    private fun setupPalettes() {
        binding.rvPalettes.adapter = PaletteAdapter { picked ->
            palette = picked
            regrade()
        }
    }

    private fun setupStrength() {
        binding.sliderStrength.value = DEFAULT_STRENGTH.toFloat()
        binding.tvStrength.text = getString(R.string.percent_format, DEFAULT_STRENGTH)

        binding.sliderStrength.addOnChangeListener { _, value, _ ->
            strength = value.toInt()
            binding.tvStrength.text = getString(R.string.percent_format, strength)
            regrade()
        }
    }

    // ─────────────────────── 보정 ───────────────────────

    /**
     * 사진을 읽어 들이며 긴 변을 [MAX_EDGE] 로 줄입니다.
     *
     * 요즘 폰 사진은 4000px 이 넘습니다. 그대로 두면 픽셀이 천만 개가 넘어
     * 슬라이더를 한 칸 밀 때마다 화면이 멎습니다. 미리보기에도 저장본에도
     * 1600px 이면 충분합니다(프레임이 만드는 폴라로이드는 1080px 정사각).
     */
    private fun loadPhoto(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeScaled(uri) }
            if (bitmap == null) {
                Toast.makeText(
                    this@ColorPaletteActivity, R.string.palette_no_photo, Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }
            source = bitmap
            binding.viewCompare.original = bitmap
            regrade()
        }
    }

    private fun decodeScaled(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    /**
     * 팔레트나 세기가 바뀔 때마다 다시 물들입니다.
     *
     * 슬라이더는 한 번 미는 동안 이벤트가 수십 번 옵니다. 앞의 작업을 취소하지
     * 않으면 계산이 줄줄이 밀려 손을 뗀 뒤에도 한참 화면이 늦게 따라옵니다.
     */
    private fun regrade() {
        val photo = source ?: return
        grading?.cancel()
        grading = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                PaletteGrader.apply(photo, palette, strength)
            }
            graded?.recycle()
            graded = result
            binding.viewCompare.graded = result
        }
    }

    // ─────────────────────── 돌려주기 ───────────────────────

    /**
     * 결과를 캐시에 쓰고 경로만 돌려줍니다.
     *
     * 비트맵은 인텐트에 담기엔 너무 큽니다(1MB 제한). 갤러리에 저장하지 않는
     * 것은 중간 결과이기 때문입니다. 여기서 한 장, 프레임에서 또 한 장이
     * 남으면 사용자는 어느 것이 완성본인지 알 수 없습니다.
     */
    private fun applyAndReturn() {
        val result = graded ?: return
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { writeToCache(result) }
            if (path == null) {
                Toast.makeText(
                    this@ColorPaletteActivity, R.string.palette_no_photo, Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            setResult(RESULT_OK, Intent().putExtra(RESULT_PATH, path))
            finish()
        }
    }

    private fun writeToCache(bitmap: Bitmap): String? = runCatching {
        val file = File(cacheDir, "graded_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        file.absolutePath
    }.getOrNull()

    override fun onDestroy() {
        super.onDestroy()
        grading?.cancel()
        graded?.recycle()
        source?.recycle()
    }
}

/** 미리보기·저장에 쓰는 긴 변의 최대 길이(px). */
private const val MAX_EDGE = 1600

/** 시안의 기본 적용 세기. 이 정도라야 색이 보이면서 피사체도 알아봅니다. */
private const val DEFAULT_STRENGTH = 70
