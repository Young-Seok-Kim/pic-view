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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityGuideBinding

class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding
    private var imageCapture: ImageCapture? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedImageUri: Uri? = result.data?.data
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        val spotName = intent.getStringExtra("SPOT_NAME") ?: ""
        val guideType = when {
            spotName.contains("산") || spotName.contains("정원") -> GuideOverlayView.GuideType.THIRDS
            spotName.contains("향교") || spotName.contains("기념탑") -> GuideOverlayView.GuideType.CENTER
            else -> GuideOverlayView.GuideType.DEFAULT
        }
        setOnClickListener()

        binding.guideOverlay.guideType = guideType
        binding.tvGuideMessage.text = when (guideType) {
            GuideOverlayView.GuideType.THIRDS -> "선이 교차하는 지점에 피사체를 배치하세요"
            GuideOverlayView.GuideType.CENTER -> "피사체를 중앙 박스 안에 맞추세요"
            else -> "중앙 원 안에 피사체를 배치하세요"
        }

    }

    private fun setOnClickListener() {
        binding.btnCapture.setOnClickListener { takePhoto() }

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