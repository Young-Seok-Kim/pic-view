package com.youngs.picview.ui.guide

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.res.ColorStateList
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivitySiseonGuideBinding
import com.youngs.picview.databinding.DialogPhotoSourceBinding
import com.youngs.picview.databinding.DialogSiseonMissionBinding
import com.youngs.picview.databinding.ItemGuidePhotoBinding
import com.youngs.picview.databinding.ItemMissionSlotBinding
import com.youngs.picview.databinding.ItemMissionStepBinding
import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.guide.SiseonGuideItem
import com.youngs.picview.util.MediaStoreSaver
import com.youngs.picview.util.TtsController
import com.youngs.picview.util.applyTopSystemBarInset
import kotlin.math.abs

/**
 * 시선 가이드 — 구도 14종을 한 장씩 넘기며 배우고 바로 써 보는 화면.
 *
 * 웹 시안(GuidePage.tsx)의 안드로이드 판입니다. 구도명 라벨 + 사진
 * 캐러셀, 내 사진 비교, 구도·빛·움직임·체크 탭, 실전 미션(체크 셋 +
 * 세 위치 사진 비교 + 자동 분석)이 시안의 흐름 그대로 들어 있습니다.
 *
 * 홈에서 오면 그 장소의 이름·사진·빛 상태·추천 구도가 함께 넘어와
 * "오늘의 추천 한 장"이 그 장소의 말로 시작합니다.
 */
class SiseonGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySiseonGuideBinding

    private var selectedIndex = 0
    private var contextId = "sunset"
    private var spotTitle: String? = null

    private val checkState = BooleanArray(SiseonGuide.checklist.size)

    /**
     * 미션을 소리로 읽어 줍니다.
     *
     * 촬영 중에는 화면을 볼 손도 눈도 없습니다. 공들여 쓴 안내가 읽히지
     * 않는 가장 큰 이유라, 같은 문장을 귀로도 받게 합니다.
     */
    private val tts by lazy {
        TtsController(this).also { lifecycle.addObserver(it) }
    }

    // ── 미션 상태. 다이얼로그가 닫혀도 이어서 볼 수 있게 화면이 들고 있습니다.
    private var missionBinding: DialogSiseonMissionBinding? = null
    private val missionChecks = BooleanArray(3)
    private val missionPhotos = arrayOfNulls<Uri>(3)
    private var pendingSlot = -1

    private val comparePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            binding.layoutCompareEmpty.isVisible = false
            binding.layoutCompareGrid.isVisible = true
            binding.btnCompareUpload.setText(R.string.siseon_upload_change)
            Glide.with(this).load(uri).centerCrop().into(binding.ivCompareMine)
        }

    private val missionPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val slot = pendingSlot
            pendingSlot = -1
            if (uri == null || slot !in missionPhotos.indices) return@registerForActivityResult
            missionPhotos[slot] = uri
            renderMissionSlots()
        }

    // ── 카메라 촬영. 시스템 카메라가 이 URI 에 직접 쓰고 돌아옵니다.
    private var pendingCameraUri: Uri? = null

    private val missionCamera =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            val slot = pendingSlot
            pendingCameraUri = null
            pendingSlot = -1
            uri ?: return@registerForActivityResult
            if (!success || slot !in missionPhotos.indices) {
                // 촬영을 취소하면 미리 만들어 둔 빈 항목이 갤러리에 남으므로 지웁니다.
                runCatching { contentResolver.delete(uri, null, null) }
                return@registerForActivityResult
            }
            missionPhotos[slot] = uri
            renderMissionSlots()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySiseonGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.layoutGuideHeader.applyTopSystemBarInset()

        spotTitle = intent.getStringExtra(EXTRA_SPOT_TITLE)
        contextId = intent.getStringExtra(EXTRA_CONTEXT) ?: "sunset"
        selectedIndex = SiseonGuide.items.indexOf(
            SiseonGuide.byId(intent.getStringExtra(EXTRA_GUIDE_ID))
        )

        binding.btnGuideBack.setOnClickListener { finish() }
        binding.tvGuideCount.text = getString(R.string.siseon_count, SiseonGuide.items.size)

        setupCarousel()
        setupContextChips()
        setupTools()
        setupChecklist()

        binding.btnCompareUpload.setOnClickListener { comparePicker.launch("image/*") }
        binding.btnShootNow.setOnClickListener { openMission() }
        binding.btnMissionStart.setOnClickListener { openMission() }

        renderSelected()
        renderContext()
    }

    // ───────────────────── 캐러셀 ─────────────────────

    private fun setupCarousel() {
        binding.vpGuide.adapter = PhotoAdapter(SiseonGuide.items)
        binding.vpGuide.setCurrentItem(selectedIndex, false)
        binding.vpGuide.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    selectedIndex = position
                    renderSelected()
                }
            }
        )
        binding.btnGuidePrev.setOnClickListener { moveGuide(-1) }
        binding.btnGuideNext.setOnClickListener { moveGuide(1) }
    }

    private fun moveGuide(direction: Int) {
        val count = SiseonGuide.items.size
        binding.vpGuide.setCurrentItem((selectedIndex + direction + count) % count, true)
    }

    private class PhotoAdapter(private val items: List<SiseonGuideItem>) :
        RecyclerView.Adapter<PhotoAdapter.Holder>() {

        class Holder(val binding: ItemGuidePhotoBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemGuidePhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.binding.ivGuidePhoto.setImageResource(items[position].imageRes)
        }
    }

    // ───────────────────── 구도 선택 반영 ─────────────────────

    private fun renderSelected() {
        val item = SiseonGuide.items[selectedIndex]
        binding.tvGuideEnglish.text = item.english
        binding.tvGuideKorean.text = item.title
        binding.tvGuideDesc.text = item.description
        binding.tvPanelDirection.text = item.direction
        binding.tvPanelTip.text = item.tip
        binding.tvGuideTips.text = item.tips.joinToString("\n") { "• $it" }
        binding.tvCompareGuideCaption.text =
            getString(R.string.siseon_compare_caption_guide, item.title)
        binding.ivCompareGuide.setImageResource(item.imageRes)

        val signal = SiseonGuide.signalOf(item.id)
        binding.tvSignalLabel.text = signal.label
        binding.tvSignalTitle.text = signal.title
        binding.tvSignalValue.text = signal.value
        binding.tvSignalHint.text = signal.hint
        binding.tvSignalDetail.text = signal.detail

        renderTodayLine()
    }

    /**
     * 날씨 탭이 바꾸는 모든 것.
     *
     * 예전에는 여기서 문구 넷만 갈아 끼웠습니다. 그래서 탭을 눌러도
     * 사진과 구도가 그대로라 "필터가 왜 있지"가 됐습니다. 지금은
     * 참고 사진·라벨·볼 것·전용 기능·복습 질문·색까지 함께 바뀝니다.
     */
    private fun renderContext() {
        val context = SiseonGuide.contextById(contextId)
        binding.tvLightLabel.text = context.label
        binding.tvLightTitle.text = context.title
        binding.tvLightHint.text = context.hint
        binding.tvLightDetail.text = context.detail

        val tone = ContextCompat.getColor(this, context.toneRes)

        // ① 참고 사진 + 라벨
        binding.ivWeatherPhoto.setImageResource(context.photoRes)
        binding.tvWeatherLabelEn.text = context.photoLabelEn
        binding.tvWeatherLabelKo.text = context.photoLabelKo
        binding.layoutWeatherLabel.background?.mutate()?.setTint(tone)

        // ② 이 날씨의 컨셉과 볼 것
        binding.tvWeatherConcept.text = "${context.emoji} ${context.chipLabel} · ${context.concept}"
        binding.tvWeatherConcept.setTextColor(tone)
        binding.tvWeatherPoint.text = context.guidePoint

        // ③ 전용 기능 두셋
        binding.chipsWeatherTools.removeAllViews()
        context.tools.forEach { tool ->
            binding.chipsWeatherTools.addView(
                Chip(this).apply {
                    text = tool
                    isCheckable = false
                    isClickable = false
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(this@SiseonGuideActivity, context.toneContainerRes)
                    )
                    setTextColor(tone)
                    chipStrokeWidth = 0f
                }
            )
        }

        // ④ MY FRAME 복습 질문
        binding.tvFrameQuestion.text = "${context.emoji} ${context.frameQuestion}"

        renderTodayLine()
    }

    /**
     * 날씨를 고르면 구도 캐러셀도 그 날씨의 구도로 옮겨 갑니다.
     *
     * 사진만 바뀌고 아래 구도 설명이 그대로면 화면이 두 이야기를 합니다.
     * 사용자가 직접 캐러셀을 넘긴 뒤에는 건드리지 않습니다 — 그때는
     * 구도를 고르는 중이라 발밑이 흔들리면 안 됩니다.
     */
    private fun syncGuideToContext() {
        val target = SiseonGuide.items.indexOfFirst {
            it.id == SiseonGuide.contextById(contextId).guideId
        }
        if (target >= 0 && target != selectedIndex) {
            binding.vpGuide.setCurrentItem(target, true)
        }
    }

    /** 오늘의 추천 한 장. 홈에서 온 장소가 있으면 그 이름으로 말합니다. */
    private fun renderTodayLine() {
        val item = SiseonGuide.items[selectedIndex]
        val context = SiseonGuide.contextById(contextId)
        binding.tvTodayEyebrow.text =
            getString(R.string.siseon_today_eyebrow, context.chipLabel)
        binding.tvTodayLine.text = getString(
            R.string.siseon_today_line,
            spotTitle ?: "지금 있는 곳",
            item.title
        )
        binding.tvTodayMeta.text = "${context.value} · ${context.hint}"
    }

    /**
     * 날씨 탭.
     *
     * 여섯 탭이 같은 색이면 무엇을 고른 상태인지 글자를 읽어야 압니다.
     * 이모지 + 날씨 톤 색으로 이중 코딩해 훑기만 해도 구분되게 합니다.
     */
    private fun setupContextChips() {
        SiseonGuide.contexts.forEach { context ->
            val tone = ContextCompat.getColor(this, context.toneRes)
            val container = ContextCompat.getColor(this, context.toneContainerRes)
            binding.chipsGuideContext.addView(
                Chip(this).apply {
                    id = android.view.View.generateViewId()
                    text = "${context.emoji} ${context.chipLabel}"
                    isCheckable = true
                    isChecked = context.id == contextId
                    tag = context.id
                    // 고른 탭만 그 날씨의 색으로 채웁니다.
                    chipBackgroundColor = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(container, ContextCompat.getColor(this@SiseonGuideActivity, R.color.bg_card))
                    )
                    setTextColor(
                        ColorStateList(
                            arrayOf(
                                intArrayOf(android.R.attr.state_checked),
                                intArrayOf()
                            ),
                            intArrayOf(
                                tone,
                                ContextCompat.getColor(
                                    this@SiseonGuideActivity, R.color.text_secondary
                                )
                            )
                        )
                    )
                    chipStrokeWidth = 1 * resources.displayMetrics.density
                    chipStrokeColor = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(
                            tone,
                            ContextCompat.getColor(this@SiseonGuideActivity, R.color.divider)
                        )
                    )
                    isCheckedIconVisible = false
                }
            )
        }
        binding.chipsGuideContext.setOnCheckedStateChangeListener { group, checked ->
            val chip = checked.firstOrNull()?.let { group.findViewById<Chip>(it) }
            contextId = (chip?.tag as? String) ?: contextId
            renderContext()
            syncGuideToContext()
        }
    }

    // ───────────────────── 구도·빛·움직임·체크 탭 ─────────────────────

    private fun setupTools() {
        binding.toggleGuideTools.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.panelComposition.isVisible = checkedId == R.id.btn_tool_composition
            binding.panelLight.isVisible = checkedId == R.id.btn_tool_light
            binding.panelAction.isVisible = checkedId == R.id.btn_tool_action
            binding.panelCheck.isVisible = checkedId == R.id.btn_tool_check
        }
    }

    private fun setupChecklist() {
        SiseonGuide.checklist.forEachIndexed { index, item ->
            binding.layoutCheckList.addView(
                CheckBox(this).apply {
                    text = item
                    setOnCheckedChangeListener { _, checked ->
                        checkState[index] = checked
                        renderCheckProgress()
                    }
                }
            )
        }
        renderCheckProgress()
    }

    private fun renderCheckProgress() {
        val done = checkState.count { it }
        val total = checkState.size
        val percent = done * 100 / total
        binding.tvCheckPercent.text = "$percent%"
        binding.progressCheck.setProgressCompat(percent, true)
        binding.tvCheckCaption.text = getString(R.string.siseon_check_progress, done, total) +
            " · " + getString(
                if (done == total) R.string.siseon_check_done_note
                else R.string.siseon_check_todo_note
            )
    }

    // ───────────────────── 실전 미션 ─────────────────────

    private fun openMission() {
        val item = SiseonGuide.items[selectedIndex]
        val context = SiseonGuide.contextById(contextId)

        missionChecks.fill(false)
        missionPhotos.fill(null)

        val sheet = DialogSiseonMissionBinding.inflate(layoutInflater)
        missionBinding = sheet

        sheet.tvMissionHeader.text = getString(R.string.mission_header, item.title)
        sheet.tvMissionCtxLabel.text = context.label
        sheet.tvMissionCtxTitle.text = context.title
        sheet.tvMissionGoal.text = context.detail

        // 현장 체크 세 줄 — 구도의 기준점, 지금 빛의 할 일, 비교 한 번 더.
        //
        // 각 줄은 "할 일 한 줄 + 참고 사진 한 장"입니다. 부연은 접어 두고
        // 궁금한 사람만 폅니다. 셋을 다 읽지 않아도 촬영을 시작할 수 있어야
        // 합니다 — 현장에서 글을 읽는 사람은 없습니다.
        val steps = listOf(
            Triple(
                getString(R.string.mission_step_first, item.title), item.tip, item.imageRes
            ),
            Triple(context.title, context.detail, context.photoRes),
            Triple(
                getString(R.string.mission_step_last),
                getString(R.string.mission_step_last_hint),
                0
            )
        )
        sheet.layoutMissionSteps.removeAllViews()
        steps.forEachIndexed { index, (title, detail, photoRes) ->
            val step = ItemMissionStepBinding.inflate(
                layoutInflater, sheet.layoutMissionSteps, false
            )
            step.tvStepTitle.text = title
            step.tvStepDetail.text = detail
            step.ivStepPhoto.isVisible = photoRes != 0
            if (photoRes != 0) step.ivStepPhoto.setImageResource(photoRes)

            step.tvStepMore.setOnClickListener {
                val open = !step.tvStepDetail.isVisible
                step.tvStepDetail.isVisible = open
                step.tvStepMore.setText(
                    if (open) R.string.mission_step_less else R.string.mission_step_more
                )
            }
            step.cbStep.setOnCheckedChangeListener { _, checked ->
                missionChecks[index] = checked
                renderMissionProgress()
            }
            sheet.layoutMissionSteps.addView(step.root)
        }

        // 읽기 대신 듣기. 상황 한 줄과 할 일 세 줄을 이어 읽어 줍니다.
        val script = (listOf(context.detail) + steps.map { it.first })
            .joinToString(" ") { it.trimEnd('.') + "." }

        sheet.btnMissionTts.setOnClickListener { tts.toggle(script) }

        // 재생 중에는 버튼이 "듣기 멈추기"로 바뀝니다. 소리가 나는데
        // 버튼이 그대로면 다시 눌러 겹쳐 재생하려 들게 됩니다.
        tts.speaking.observe(this) { speaking ->
            missionBinding?.btnMissionTts?.setText(
                if (speaking) R.string.mission_listen_stop else R.string.mission_listen
            )
        }

        sheet.layoutMissionSlots.removeAllViews()
        SiseonGuide.missionSlots.forEachIndexed { index, label ->
            val slot = ItemMissionSlotBinding.inflate(layoutInflater, sheet.layoutMissionSlots, false)
            slot.tvSlotNumber.text = "0${index + 1}"
            slot.tvSlotLabel.text = label
            slot.frameSlot.setOnClickListener { showPhotoSourceSheet(index, label) }
            (slot.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                if (index > 0) params.marginStart = resources.getDimensionPixelSize(R.dimen.space_s)
            }
            sheet.layoutMissionSlots.addView(slot.root)
        }

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.setOnDismissListener {
            missionBinding = null
            tts.stop()
        }
        sheet.btnMissionClose.setOnClickListener { dialog.dismiss() }
        sheet.btnMissionComplete.setOnClickListener { dialog.dismiss() }

        renderMissionSlots()
        renderMissionProgress()
        dialog.show()
    }

    private fun renderMissionSlots() {
        val sheet = missionBinding ?: return
        missionPhotos.forEachIndexed { index, uri ->
            val slot = sheet.layoutMissionSlots.getChildAt(index) ?: return@forEachIndexed
            val photo = slot.findViewById<android.widget.ImageView>(R.id.iv_slot_photo)
            val empty = slot.findViewById<android.view.View>(R.id.layout_slot_empty)
            photo.isVisible = uri != null
            empty.isVisible = uri == null
            uri?.let { Glide.with(this).load(it).centerCrop().into(photo) }

            // 사진이 들어오면 그 스텝의 체크가 저절로 켜집니다.
            // 찍었는데 체크까지 손으로 눌러야 하면 '읽고 확인하는 일'이
            // 하나 더 늘어납니다. 행동이 곧 완료가 되게 합니다.
            if (uri != null && !missionChecks[index]) {
                missionChecks[index] = true
                sheet.layoutMissionSteps
                    .getChildAt(index)
                    ?.findViewById<CheckBox>(R.id.cb_step)
                    ?.isChecked = true
            }
        }
        sheet.tvMissionPhotoCount.text =
            getString(R.string.mission_added, missionPhotos.count { it != null })

        if (missionPhotos.all { it != null }) {
            renderMissionFeedback()
        }
        renderMissionProgress()
    }

    private fun renderMissionProgress() {
        val sheet = missionBinding ?: return
        sheet.tvMissionCheckCount.text =
            getString(R.string.mission_checked, missionChecks.count { it })

        val ready = missionChecks.all { it } && missionPhotos.all { it != null }
        sheet.btnMissionComplete.isEnabled = ready
        sheet.btnMissionComplete.setText(
            if (ready) R.string.mission_complete else R.string.mission_incomplete
        )
    }

    // ───────────────────── 사진 넣기 — 촬영 / 갤러리 ─────────────────────

    /**
     * 미션 사진 칸의 두 갈래 선택 시트.
     *
     * 미션은 현장에서 바로 찍는 흐름이라 카메라가 먼저 오지만,
     * 미리 찍어 둔 세 장을 넣는 쓰임도 있어 갤러리도 함께 엽니다.
     */
    private fun showPhotoSourceSheet(slot: Int, label: String) {
        val sheet = DialogPhotoSourceBinding.inflate(layoutInflater)
        sheet.tvSourceSub.text = getString(R.string.mission_source_sub, label)

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet.root)
        sheet.layoutSourceCamera.setOnClickListener {
            dialog.dismiss()
            openMissionCamera(slot)
        }
        sheet.layoutSourceGallery.setOnClickListener {
            dialog.dismiss()
            pendingSlot = slot
            missionPicker.launch("image/*")
        }
        dialog.show()
    }

    /**
     * 시스템 카메라를 엽니다.
     *
     * 매니페스트에 CAMERA 권한이 선언된 앱은 권한 없이 ACTION_IMAGE_CAPTURE 를
     * 던지면 SecurityException 이 나므로, 반드시 권한을 받고 나서 엽니다.
     */
    private fun openMissionCamera(slot: Int) {
        TedPermission.create()
            .setPermissionListener(object : PermissionListener {
                override fun onPermissionGranted() {
                    val uri = newMissionPhotoUri()
                    if (uri == null) {
                        Toast.makeText(
                            this@SiseonGuideActivity,
                            R.string.mission_camera_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    pendingSlot = slot
                    pendingCameraUri = uri
                    missionCamera.launch(uri)
                }

                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                    Toast.makeText(
                        this@SiseonGuideActivity,
                        R.string.mission_camera_denied,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            .setDeniedMessage(getString(R.string.mission_camera_denied))
            .setPermissions(*cameraPermissions())
            .check()
    }

    /** 안드로이드 9 이하는 MediaStore 저장에도 쓰기 권한이 필요합니다. */
    private fun cameraPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    /**
     * 촬영본이 저장될 DCIM/PicView 항목을 미리 만듭니다.
     *
     * IS_PENDING 을 걸면 다른 앱(시스템 카메라)이 그 항목에 쓸 수 없어
     * 촬영이 실패하므로, 여기서는 빼고 바로 보이는 상태로 만듭니다.
     * 취소 시에는 결과 콜백에서 지웁니다.
     */
    private fun newMissionPhotoUri(): Uri? = runCatching {
        val values = MediaStoreSaver
            .imageValues("PicView_mission_${System.currentTimeMillis()}.jpg")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    remove(MediaStore.Images.Media.IS_PENDING)
                }
            }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull()

    // ───────────────────── 세 장 자동 분석 ─────────────────────

    /**
     * 세 사진의 밝기·색온도·밝기 중심을 견줘 말로 바꿉니다.
     * 웹 시안의 buildPhotoFeedback 을 그대로 옮겼습니다 — 96px로 줄인
     * 픽셀만 훑으므로 사진 크기와 무관하게 즉시 끝납니다.
     */
    private fun renderMissionFeedback() {
        val sheet = missionBinding ?: return
        val metrics = missionPhotos.map { uri -> analyze(uri ?: return) }

        val (first, second, third) = metrics
        val brightnessShift = second.brightness - first.brightness
        val finalShift = third.brightness - second.brightness
        val direction = when {
            third.centerX - first.centerX > 0.08 -> "오른쪽"
            third.centerX - first.centerX < -0.08 -> "왼쪽"
            else -> "중앙"
        }
        val warmth = when {
            third.warmth - first.warmth > 0.08 -> "따뜻한 색이 더 강해졌고"
            third.warmth - first.warmth < -0.08 -> "푸른 색감이 더 강해졌고"
            else -> "색감 변화가 크지 않고"
        }
        val brightnessText = when {
            abs(finalShift) < 0.05 -> "밝기가 안정적으로 유지됐어요"
            finalShift > 0 -> "마지막 사진에서 빛이 더 열렸어요"
            else -> "마지막 사진에서 그림자가 더 깊어졌어요"
        }
        val secondLine = when {
            brightnessShift > 0.05 -> "밝아졌어요"
            brightnessShift < -0.05 -> "어두워졌어요"
            else -> "비슷해요"
        }

        sheet.tvMissionFeedback.text = listOf(
            "01 가까이 이동하며 화면의 밝기 중심이 ${direction}으로 이동했어요.",
            "02 두 걸음 가까이 온 사진은 피사체가 더 크게 느껴지고, 첫 사진보다 밝기가 $secondLine.",
            "03 좌우 이동 결과는 $warmth $brightnessText. 균형이 가장 좋은 컷을 대표로 골라보세요."
        ).joinToString("\n")
        sheet.layoutMissionFeedback.isVisible = true
    }

    private data class PhotoMetrics(
        val brightness: Double,
        val warmth: Double,
        val centerX: Double
    )

    private fun analyze(uri: Uri): PhotoMetrics {
        val bitmap = loadScaled(uri) ?: return PhotoMetrics(0.0, 0.0, 0.5)
        var total = 0.0
        var weightedX = 0.0
        var red = 0L
        var blue = 0L
        var count = 0
        val size = bitmap.width.coerceAtMost(bitmap.height)
        var y = 0
        while (y < size) {
            var x = 0
            while (x < size) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val light = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0
                total += light
                weightedX += x * light
                red += r
                blue += b
                count += 1
                x += 4
            }
            y += 4
        }
        if (count == 0 || total == 0.0) return PhotoMetrics(0.0, 0.0, 0.5)
        return PhotoMetrics(
            brightness = total / count,
            warmth = (red - blue).toDouble() / (count * 255.0),
            centerX = weightedX / (total * size)
        )
    }

    private fun loadScaled(uri: Uri): Bitmap? = runCatching {
        contentResolver.openInputStream(uri)?.use { stream ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, bounds)
            val sample = (bounds.outWidth / 96).coerceAtLeast(1)
            contentResolver.openInputStream(uri)?.use { second ->
                BitmapFactory.decodeStream(
                    second, null,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
        }
    }.getOrNull()

    companion object {
        private const val EXTRA_SPOT_TITLE = "spot_title"
        private const val EXTRA_CONTEXT = "context_id"
        private const val EXTRA_GUIDE_ID = "guide_id"

        /**
         * @param spotTitle 홈에서 고른 장소 이름 (없으면 일반 문구)
         * @param contextId 빛 상황 칩 id — [SiseonGuide.contextIdFor]
         * @param guideId   첫 구도 id — [SiseonGuide.guideIdFor]
         */
        fun intent(
            context: Context,
            spotTitle: String? = null,
            contextId: String? = null,
            guideId: String? = null
        ): Intent = Intent(context, SiseonGuideActivity::class.java)
            .putExtra(EXTRA_SPOT_TITLE, spotTitle)
            .putExtra(EXTRA_CONTEXT, contextId)
            .putExtra(EXTRA_GUIDE_ID, guideId)
    }
}
