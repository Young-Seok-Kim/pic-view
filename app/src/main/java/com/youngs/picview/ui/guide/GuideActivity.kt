package com.youngs.picview.ui.guide

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngs.picview.R
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.MediaStoreSaver
import com.youngs.picview.databinding.ActivityGuideBinding
import com.youngs.picview.databinding.DialogGuideExampleBinding
import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.pose.GroupSize
import com.youngs.picview.domain.pose.Pose
import com.youngs.picview.domain.pose.PoseRecommender
import com.youngs.picview.domain.pose.PoseScore
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.SpotFactsTable
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.lifecycle.lifecycleScope
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.ui.model.SpotItem
import kotlinx.coroutines.launch
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import com.youngs.picview.domain.pose.Subject

class GuideActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SPOT_NAME = "SPOT_NAME"
        const val EXTRA_SPOT_TYPE = "SPOT_TYPE"

        /** 지금의 빛 구간([LightPhase] 이름). 포즈 추천 순서를 정하는 데 씁니다. */
        const val EXTRA_PHASE = "PHASE"

        /** 촬영한 사진을 방문 기록에 붙이기 위한 장소 식별자. */
        const val EXTRA_CONTENT_ID = "CONTENT_ID"

        /** 시작 구도([GuideOverlayView.GuideType.id]). 없으면 장소 종류로 정합니다. */
        const val EXTRA_GUIDE_ID = "GUIDE_ID"

        /** 첫 안내가 스스로 사라지기까지. 읽기에 넉넉하고 방해되지 않는 선. */
        private const val POSE_HINT_MS = 6000L

    }

    private lateinit var binding: ActivityGuideBinding
    private var imageCapture: ImageCapture? = null

    private lateinit var poseAdapter: PoseAdapter

    /** 이 장소의 방위. 실내면 하늘·역광 포즈를 후순위로 내립니다. */
    private lateinit var facing: Facing

    /** 지금의 빛. 화면이 떠 있는 동안은 바뀌지 않습니다. */
    private lateinit var phase: LightPhase

    private var groupSize: GroupSize = GroupSize.SOLO

    /** 이 화면의 구도. 격자가 이 값을 봅니다. */
    private lateinit var guideType: GuideOverlayView.GuideType

    /**
     * 포즈 목록에서 지금 고른 것.
     *
     * [PoseAdapter] 는 목록을 새로 받을 때마다 1등을 자동으로 고르고
     * 콜백을 부르므로, 화면이 뜨자마자 값이 채워집니다.
     */
    private var selectedPose: PoseScore? = null


    /** 무엇을 찍는가. 인물이면 포즈 목록, 나머지면 촬영 요령이 나옵니다. */
    private var subject: Subject = Subject.PERSON

    /**
     * 앨범에서 사진 한 장 보기.
     *
     * 갤러리 앱을 여는 게 아니라 시스템 사진 선택기를 띄우고, 고른 사진은
     * 시스템 사진 뷰어로 넘깁니다. 예전에는 포토 프레임으로 보냈는데,
     * 남이 다른 날 다른 곳에서 찍은 사진에 이 장소 이름이 붙어 버립니다.
     * 프레임은 내가 남긴 기록(MY 아카이브·일기)에서 꺼낼 때만 씁니다.
     */
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // 볼 앱이 없을 수도 있습니다. 그때 앱이 죽는 것보다 한 줄 알리는 편이 낫습니다.
        runCatching { startActivity(view) }.onFailure {
            Toast.makeText(this, R.string.guide_photo_view_failed, Toast.LENGTH_SHORT).show()
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
            .setPermissions(*requiredPermissions())
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

        // 시선 가이드에서 구도를 보고 넘어왔으면 그 구도로, 아니면 장소 종류로.
        guideType = GuideOverlayView.GuideType.byId(intent.getStringExtra(EXTRA_GUIDE_ID))
            ?: when (spotType) {
                "14" -> GuideOverlayView.GuideType.SYMMETRY // 문화시설 : 건축 대칭
                "39" -> GuideOverlayView.GuideType.CENTER   // 음식점 : 근접 촬영
                else -> GuideOverlayView.GuideType.THIRDS   // 자연·레포츠 등 풍경
            }

        setOnClickListener()

        applyGuideType()
        renderOverlayToggle()
        fitOverlayToVisibleArea()

        setupPoses(spotName, spotType)
        showPoseHintOnce()
    }

    /** 구도를 화면에 반영합니다 — 격자 그림, 상단 알약, 안내 문구. */
    private fun applyGuideType() {
        binding.guideOverlay.guideType = guideType
        binding.btnGuideComposition.text = guideType.label
        binding.tvGuideMessage.text = guideMessage(guideType)
    }

    /**
     * 구도 한 줄 요령. 삼분할·대칭·중앙은 카메라 화면의 옛 문구를, 나머지는
     * 시선 가이드 화면이 쓰는 그 구도의 요령을 그대로 씁니다.
     */
    private fun guideMessage(type: GuideOverlayView.GuideType): String = when (type) {
        GuideOverlayView.GuideType.THIRDS -> getString(R.string.guide_thirds)
        GuideOverlayView.GuideType.SYMMETRY -> getString(R.string.guide_symmetry)
        GuideOverlayView.GuideType.CENTER -> getString(R.string.guide_center)
        else -> SiseonGuide.byId(type.id).tip
    }

    /**
     * 구도 고르기. 카메라를 든 채 목록을 고르는 것이라 긴 설명 없이 이름만 둡니다.
     * 어떤 구도인지는 고르는 즉시 격자가 보여 줍니다.
     */
    private fun pickGuideType() {
        val types = GuideOverlayView.GuideType.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.guide_pick_composition)
            .setSingleChoiceItems(
                types.map { it.label }.toTypedArray(), types.indexOf(guideType)
            ) { dialog, index ->
                dialog.dismiss()
                guideType = types[index]
                applyGuideType()
                // 골랐는데 꺼져 있으면 고른 보람이 없습니다. 켭니다.
                if (!AppPrefs.isGuideOverlayOn(this)) {
                    AppPrefs.setGuideOverlayOn(this, true)
                    renderOverlayToggle()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 상단 바 첫 줄과 하단 컨트롤이 덮는 만큼 격자를 안쪽으로 들입니다.
     * 두 바의 높이는 인셋과 글씨 크기에 따라 달라서 배치가 끝난 뒤에 잽니다.
     */
    private fun fitOverlayToVisibleArea() {
        binding.layoutBottomBar.doOnLayout {
            binding.guideOverlay.setInsets(
                top = binding.layoutTopBar.top + binding.layoutTopBar.paddingTop + dp(40),
                bottom = binding.root.height - binding.layoutBottomBar.top
            )
        }
    }

    /** 격자 버튼과 격자의 켜짐 상태를 맞춥니다. 꺼진 버튼은 반투명입니다. */
    private fun renderOverlayToggle() {
        val on = AppPrefs.isGuideOverlayOn(this)
        binding.guideOverlay.isVisible = on
        binding.btnGuideGrid.alpha = if (on) 1f else 0.45f
    }

    /**
     * 처음 한 번만 "?" 를 가리키는 안내.
     *
     * 아이콘만으로는 그 뒤에 포즈 예시가 있다는 걸 알 수 없습니다.
     * 한 번 보여 주고 나면 다시 뜨지 않습니다 — 두 번째부터는 도움이
     * 아니라 미리보기를 가리는 것이 됩니다.
     *
     * 눌러도 사라지고, 그냥 두면 [POSE_HINT_MS] 뒤에 스스로 사라집니다.
     * 셔터를 누르려는 사람을 기다리게 하지 않기 위해서입니다.
     */
    private fun showPoseHintOnce() {
        if (AppPrefs.isPoseHintSeen(this)) return

        val hint = binding.layoutPoseHint
        hint.isVisible = true
        AppPrefs.setPoseHintSeen(this)

        val dismiss = Runnable {
            if (!hint.isVisible) return@Runnable
            hint.animate().alpha(0f).setDuration(200).withEndAction {
                hint.isVisible = false
                hint.alpha = 1f
            }.start()
        }
        hint.setOnClickListener {
            hint.removeCallbacks(dismiss)
            dismiss.run()
            showExample()
        }
        hint.postDelayed(dismiss, POSE_HINT_MS)
    }

    /**
     * 지금 고른 포즈의 예시 그림.
     *
     * 포즈 칩은 이름과 한 줄 요령까지만 말합니다. "뒤돌아 걷기"가 실제로
     * 어떤 그림인지는 글로는 잘 안 떠오릅니다. 자세와 카메라 자리를 그린
     * 한 장이 그 자리를 메웁니다.
     *
     * 인물이 아닌 피사체(풍경·음식 등)를 고른 상태에서는 포즈 자체가
     * 없으므로, 그 피사체의 촬영 요령을 대신 보여 줍니다.
     */
    private fun showExample() {
        val sheet = BottomSheetDialog(this)
        val view = DialogGuideExampleBinding.inflate(layoutInflater)
        sheet.setContentView(view.root)

        // 인물이면 고른 포즈를, 나머지는 그 피사체의 촬영 요령을 보여 줍니다.
        // 풍경·동물·음식에는 포즈가 없고 "어떻게 담나"가 문제라, 물어야 할
        // 것이 다르면 답도 달라야 합니다.
        // 화면이 막 떴을 때는 아직 콜백이 안 왔을 수 있습니다. 그때는 지금
        // 조건의 1등을 씁니다 — 목록에 보이는 것과 같은 포즈입니다.
        val picked = selectedPose
            ?: PoseRecommender.recommend(phase, facing, groupSize).firstOrNull()

        if (subject == Subject.PERSON && picked != null) {
            view.tvExampleEyebrow.setText(R.string.pose_example_title)
            view.tvExampleNote.setText(R.string.pose_example_note)
            view.tvExampleEmoji.text = picked.pose.emoji
            view.tvExampleTitle.text = picked.pose.label
            view.ivExamplePhoto.setImageResource(picked.pose.artRes)
            view.tvExampleDesc.text = picked.pose.tip
            // 왜 이게 지금 추천인지. 점수만 있으면 근거 없는 숫자로 보입니다.
            view.tvExampleTip.text = picked.reason
        } else {
            // 풍경·동물·음식은 "서는" 게 아니라 "담는" 것이라 말이 다릅니다.
            view.tvExampleEyebrow.setText(R.string.subject_example_title)
            view.tvExampleNote.setText(R.string.subject_example_note)
            view.tvExampleEmoji.text = subject.emoji
            view.tvExampleTitle.text = subject.label
            view.ivExamplePhoto.setImageResource(subject.artRes)
            val tip = subject.tipFor(phase)
            view.tvExampleDesc.isVisible = tip.isNotBlank()
            view.tvExampleDesc.text = tip
            // 지금 빛이 왜 그런 요령을 부르는지 한 줄로 잇습니다.
            view.tvExampleTip.text = "${phase.label} · ${phase.lightCharacter}"
        }

        // 이 장소의 구도가 어떤 것인지도 함께 일러 줍니다.
        val facts = SpotFactsTable.of(
            intent.getStringExtra(EXTRA_SPOT_NAME).orEmpty(),
            intent.getStringExtra(EXTRA_SPOT_TYPE)
        )
        view.layoutExampleTips.removeAllViews()
        SiseonGuide.byId(SiseonGuide.guideIdFor(facts)).tips.forEach { tip ->
            view.layoutExampleTips.addView(
                TextView(this).apply {
                    text = "· $tip"
                    setTextColor(ContextCompat.getColor(this@GuideActivity, R.color.text_secondary))
                    textSize = 13f
                    setPadding(0, dp(3), 0, dp(3))
                }
            )
        }

        sheet.show()
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
            // 예시 버튼이 "지금 고른 그 포즈"를 보여 줘야 하므로 들고 있습니다.
            selectedPose = selected
        }
        binding.rvPoses.adapter = poseAdapter

        buildSubjectChips()

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

    /**
     * 피사체 칩.
     *
     * 코드에서 만드는 이유: 항목이 [Subject] 하나에만 정의돼 있어야
     * 이모지·이름이 한 곳에서 관리됩니다. XML 에 또 적으면 둘이 어긋납니다.
     */
    private fun buildSubjectChips() {
        val group = binding.chipGroupSubject
        group.removeAllViews()

        Subject.entries.forEach { item ->
            val chip = layoutInflater.inflate(
                R.layout.item_subject_chip, group, false
            ) as com.google.android.material.chip.Chip
            chip.text = "${item.emoji} ${item.label}"
            chip.isChecked = item == subject
            chip.setOnClickListener {
                subject = item
                applySubject()
            }
            group.addView(chip)
        }
    }

    /**
     * 고른 피사체에 맞춰 화면을 바꿉니다.
     *
     * 인물일 때만 포즈 목록과 인원 선택이 뜹니다. 풍경을 찍는데 "2인/3~4인"을
     * 고르라고 하면 뜻이 없습니다.
     */
    private fun applySubject() {
        val isPerson = subject == Subject.PERSON

        binding.rvPoses.isVisible = isPerson
        binding.layoutPeopleRow.isVisible = isPerson

        if (isPerson) {
            refreshPoses()
        } else {
            binding.tvGuideMessage.text = subject.tipFor(phase)
        }
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
        binding.btnGuideExample.setOnClickListener { showExample() }
        binding.btnGuideComposition.setOnClickListener { pickGuideType() }
        binding.btnGuideGrid.setOnClickListener {
            AppPrefs.setGuideOverlayOn(this, !AppPrefs.isGuideOverlayOn(this))
            renderOverlayToggle()
        }

        binding.btnCapture.setOnClickListener {
            // 셔터를 누른 느낌을 주는 짧은 스케일 피드백
            it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            takePhoto()
        }

        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    /**
     * 촬영에 필요한 권한.
     *
     * 안드로이드 9 이하에서는 찍은 사진을 MediaStore 에 저장할 때도 쓰기 권한이
     * 필요합니다. 이걸 빼면 셔터는 눌리는데 사진이 어디에도 남지 않습니다.
     * 10 부터는 앱이 자기 사진을 권한 없이 저장할 수 있어 카메라만 받습니다.
     */
    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            // 미리보기 해상도를 기기가 고르게 두면, 카메라2 legacy 계층을 쓰는
            // 구형 기기·에뮬레이터에서 활성 배열보다 큰 크기가 잡혀
            // "previewSize must not be taller than activeArray" 로 죽습니다.
            // 16:9 를 우선 요청해 그런 조합을 피합니다.
            val resolution = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolution)
                .build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(resolution)
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

        // RELATIVE_PATH·IS_PENDING 은 안드로이드 10(API 29)에 생긴 컬럼입니다.
        // 9 이하에서 쓰면 "table files has no column named relative_path" 로
        // insert 가 실패해, 셔터는 눌리는데 사진이 어디에도 남지 않습니다.
        val contentValues = MediaStoreSaver.imageValues(name)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // 다 쓰고 나서 갤러리에 노출합니다(API 29+ 에서만 의미가 있습니다).
                output.savedUri?.let { MediaStoreSaver.publish(this@GuideActivity, it) }

                // 기록을 남긴 뒤에 화면을 닫습니다. 먼저 닫으면 기록을 쓰던
                // 코루틴이 액티비티와 함께 취소돼 사진만 남고 방문은 사라집니다.
                val saved = output.savedUri
                if (saved == null) {
                    runOnUiThread { binding.btnCapture.isEnabled = true }
                    return
                }
                recordVisitWithPhoto(saved) { runOnUiThread { finish() } }
            }
            override fun onError(exception: ImageCaptureException) {
                runOnUiThread { binding.btnCapture.isEnabled = true }
                Log.e("CAMERA_ERROR", "촬영 실패: ${exception.message}")
            }
        })
    }

    /**
     * 촬영한 사진을 방문 기록에 남깁니다.
     *
     * 사진을 찍었다는 건 그 자리에 있었다는 뜻이라, 따로 "다녀왔어요"를 누르지
     * 않아도 방문으로 봅니다. 이렇게 해야 촬영 → 기록 → 일기가 끊기지 않습니다.
     */
    private fun recordVisitWithPhoto(uri: Uri, onDone: () -> Unit = {}) {
        val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
        val name = intent.getStringExtra(EXTRA_SPOT_NAME)
        if (contentId == null || name == null) {
            // 장소를 모르면 기록할 수 없지만, 사진은 이미 갤러리에 남았습니다.
            onDone()
            return
        }

        lifecycleScope.launch {
            runCatching {
                CourseRepository(applicationContext).logCapture(
                    spot = SpotItem(
                        contentId = contentId,
                        contentTypeId = intent.getStringExtra(EXTRA_SPOT_TYPE) ?: "",
                        title = name,
                        addr1 = "",
                        tip = "",
                        imageUrl = "",
                        mapx = "",
                        mapy = ""
                    ),
                    phase = phase,
                    photoUri = uri.toString()
                )
            }.onSuccess {
                Toast.makeText(
                    this@GuideActivity, R.string.guide_photo_logged, Toast.LENGTH_SHORT
                ).show()
            }
            onDone()
        }
    }
}