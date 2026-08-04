package com.youngs.picview.ui.guide

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.youngs.picview.BuildConfig
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityGuideBinding
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.pose.GroupSize
import com.youngs.picview.domain.pose.PoseRecommender
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.SpotFactsTable

class GuideActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SPOT_NAME = "SPOT_NAME"
        const val EXTRA_SPOT_TYPE = "SPOT_TYPE"

        /** 지금의 빛 구간([LightPhase] 이름). 포즈 추천 순서를 정하는 데 씁니다. */
        const val EXTRA_PHASE = "PHASE"
    }

    private lateinit var binding: ActivityGuideBinding
    private var imageCapture: ImageCapture? = null

    private lateinit var poseAdapter: PoseAdapter

    /** 이 장소의 방위. 실내면 하늘·역광 포즈를 후순위로 내립니다. */
    private lateinit var facing: Facing

    /** 지금의 빛. 화면이 떠 있는 동안은 바뀌지 않습니다. */
    private lateinit var phase: LightPhase

    private var groupSize: GroupSize = GroupSize.SOLO

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedImageUri: Uri? = result.data?.data
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()

        if (showDemoBackdropIfPresent()) {
            // 데모 배경을 쓰는 동안에는 카메라를 켜지 않습니다.
            setupGuide()
            return
        }

        TedPermission.create()
            .setPermissionListener(object : PermissionListener {
                override fun onPermissionGranted() {
                    startCamera()
                }

                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                    Toast.makeText(this@GuideActivity, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            })
            .setDeniedMessage("카메라 권한을 허용해야 촬영 가이드를 사용할 수 있습니다.")
            .setPermissions(Manifest.permission.CAMERA)
            .check()

        setupGuide()
    }

    /**
     * 장소 종류에 맞는 구도 가이드를 화면에 반영합니다.
     *
     * 예전에는 장소 이름에 '산', '향교' 가 들어있는지로 판단했는데,
     * 이름에 안 걸리면 엉뚱한 가이드가 나왔습니다.
     * 앱 다른 화면과 같은 기준인 contentTypeId 로 판단합니다.
     */
    private fun setupGuide() {
        val spotName = intent.getStringExtra(EXTRA_SPOT_NAME) ?: ""
        val spotType = intent.getStringExtra(EXTRA_SPOT_TYPE)

        binding.tvSpotName.text = spotName

        val guideType = when (spotType) {
            "14" -> GuideOverlayView.GuideType.SYMMETRY // 문화시설 : 건축 대칭
            "39" -> GuideOverlayView.GuideType.CENTER   // 음식점 : 근접 촬영
            else -> GuideOverlayView.GuideType.THIRDS   // 자연·레포츠 등 풍경
        }

        setOnClickListener()

        binding.guideOverlay.guideType = guideType
        binding.tvGuideMessage.setText(
            when (guideType) {
                GuideOverlayView.GuideType.THIRDS -> R.string.guide_thirds
                GuideOverlayView.GuideType.SYMMETRY -> R.string.guide_symmetry
                GuideOverlayView.GuideType.CENTER -> R.string.guide_center
            }
        )

        setupPoses(spotName, spotType)
    }

    /**
     * 포즈 추천.
     *
     * 순서를 고정하지 않고 [PoseRecommender] 가 지금의 빛·방위·인원으로 매번
     * 다시 계산합니다. 한밤중에 "실루엣"이 1등으로 떠 있으면 안 되기 때문입니다.
     */
    private fun setupPoses(spotName: String, spotType: String?) {
        phase = runCatching { LightPhase.valueOf(intent.getStringExtra(EXTRA_PHASE).orEmpty()) }
            .getOrDefault(LightPhase.AFTERNOON)
        facing = SpotFactsTable.of(spotName, spotType).facing

        poseAdapter = PoseAdapter { selected ->
            // 고른 포즈의 촬영 요령을 상단 안내 문구 자리에 띄웁니다.
            binding.tvGuideMessage.text = selected.pose.tip
        }
        binding.rvPoses.adapter = poseAdapter

        binding.chipGroupPeople.setOnCheckedStateChangeListener { _, checked ->
            groupSize = when (checked.firstOrNull()) {
                R.id.chip_people_pair -> GroupSize.PAIR
                R.id.chip_people_small -> GroupSize.SMALL
                R.id.chip_people_large -> GroupSize.LARGE
                else -> GroupSize.SOLO
            }
            refreshPoses()
        }

        refreshPoses()
    }

    private fun refreshPoses() {
        poseAdapter.submit(PoseRecommender.recommend(phase, facing, groupSize))
    }

    /**
     * 스토어 스크린샷 촬영용. 디버그 빌드에서 앱 전용 외부 저장소에
     * demo_backdrop.jpg 가 있으면 카메라 미리보기 대신 그 사진을 깔아 줍니다.
     *
     *   adb push 사진.jpg /sdcard/Android/data/com.youngs.picview/files/demo_backdrop.jpg
     *
     * 릴리즈 빌드에서는 항상 false 를 반환하므로 동작하지 않습니다.
     */
    private fun showDemoBackdropIfPresent(): Boolean {
        if (!BuildConfig.DEBUG) return false

        val file = java.io.File(getExternalFilesDir(null), "demo_backdrop.jpg")
        if (!file.exists()) return false

        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return false
        binding.ivDemoBackdrop.setImageBitmap(bitmap)
        binding.ivDemoBackdrop.visibility = android.view.View.VISIBLE
        binding.previewView.visibility = android.view.View.GONE
        return true
    }

    /** 상·하단 컨트롤이 상태바/제스처바에 가리지 않도록 인셋만큼 띄웁니다. */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutTopBar.updatePadding(top = bars.top + dp(12))
            binding.layoutBottomBar.updatePadding(bottom = bars.bottom + dp(36))
            insets
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun setOnClickListener() {
        binding.btnGuideBack.setOnClickListener { finish() }

        binding.btnCapture.setOnClickListener {
            // 셔터를 누른 느낌을 주는 짧은 스케일 피드백
            it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            takePhoto()
        }

        binding.btnGallery.setOnClickListener {
            // 💡 1. 갤러리 앱을 열기 위한 인텐트를 생성하되, 특정 URI 주소를 강제로 넣지 않습니다.
            val intent = Intent(Intent.ACTION_MAIN).apply {
                // 이미지 카테고리를 타겟팅
                addCategory(Intent.CATEGORY_APP_GALLERY)
                // 시스템 무관하게 '항상 새로운 태스크(화면)'로 깔끔하게 열리도록 플래그 설정
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            try {
                // 💡 2. 기기에 설치된 진짜 갤러리 앱(삼성 갤러리 등)의 메인 목록 화면을 실행합니다.
                startActivity(intent)
            } catch (e: Exception) {
                // 💡 3. 만약 위 표준 카테고리가 안 먹히는 구형 기기일 경우를 위한 안전한 예외(Fallback) 처리
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                }
                try {
                    startActivity(Intent.createChooser(fallbackIntent, "갤러리 열기"))
                } catch (ex: Exception) {
                    Toast.makeText(this, "갤러리 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("CAMERA_ERROR", "카메라 바인딩 실패: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        binding.btnCapture.isEnabled = false

        binding.flashView.apply {
            alpha = 0f
            visibility = android.view.View.VISIBLE
            animate().alpha(1f).setDuration(100).withEndAction {
                animate().alpha(0f).setDuration(100).withEndAction {
                    visibility = android.view.View.GONE
                }.start()
            }.start()
        }

        val name = "PicView_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            // IS_PENDING = 1로 설정하여 저장이 완료되기 전까지 시스템이 파일을 읽지 못하게 함
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/PicView")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // 저장 완료 후 IS_PENDING = 0으로 변경하여 갤러리에 노출
                val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                output.savedUri?.let { contentResolver.update(it, values, null, null) }

                runOnUiThread { binding.btnCapture.isEnabled = true }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { binding.btnCapture.isEnabled = true }
                Log.e("CAMERA_ERROR", "촬영 실패: ${exception.message}")
            }
        })
    }
}