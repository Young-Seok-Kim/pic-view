package com.youngs.picview.ui.guide

import android.Manifest
import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.youngs.picview.databinding.ActivityGuideBinding

class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        setupGuideType()
        binding.btnCapture.setOnClickListener { takePhoto() }
    }

    private fun checkPermissions() {
        TedPermission.create()
            .setPermissionListener(object : PermissionListener {
                override fun onPermissionGranted() { startCamera() }
                override fun onPermissionDenied(list: MutableList<String>?) { finish() }
            })
            .setPermissions(Manifest.permission.CAMERA)
            .check()
    }

    private fun setupGuideType() {
        val spotName = intent.getStringExtra("SPOT_NAME") ?: ""
        binding.guideOverlay.guideType = when {
            spotName.contains("산") || spotName.contains("정원") -> GuideOverlayView.GuideType.THIRDS
            spotName.contains("향교") || spotName.contains("기념탑") -> GuideOverlayView.GuideType.CENTER
            else -> GuideOverlayView.GuideType.DEFAULT
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                setupZoomGesture()
            } catch (e: Exception) {
                Log.e("CAMERA", "실패: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupZoomGesture() {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                camera?.cameraControl?.setZoomRatio(currentZoom * detector.scaleFactor)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(this, listener)
        binding.previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        binding.btnCapture.isEnabled = false

        // 플래시 효과
        binding.flashView.apply {
            visibility = android.view.View.VISIBLE
            alpha = 0f
            animate().alpha(1f).setDuration(100).withEndAction {
                animate().alpha(0f).setDuration(100).withEndAction { visibility = android.view.View.GONE }.start()
            }.start()
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "PicView_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/PicView")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                runOnUiThread {
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(this@GuideActivity, "저장 완료!", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onError(e: ImageCaptureException) {
                runOnUiThread { binding.btnCapture.isEnabled = true }
            }
        })
    }
}