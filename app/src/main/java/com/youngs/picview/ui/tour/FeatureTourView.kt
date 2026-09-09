package com.youngs.picview.ui.tour

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import com.youngs.picview.R
import com.youngs.picview.databinding.ViewFeatureTourCardBinding

/**
 * 첫 실행 버튼 안내(코치마크).
 *
 * 화면을 어둡게 덮고 지금 설명하는 버튼 자리만 밝게 뚫어, 그 옆에
 * "이걸 누르면 이렇게 돼요" 한 장을 붙입니다. 온보딩이 앱이 무엇인지
 * 말했다면 이것은 어디를 누르면 무엇이 되는지를 말합니다.
 *
 * 설명 대상은 [Step.target] 로 그때그때 찾습니다. 탭 Fragment 의 뷰는
 * 안내가 시작될 때야 만들어져 있어서 미리 붙잡아 둘 수 없습니다.
 * 대상이 스크롤 밖에 있으면 먼저 보이는 자리까지 끌어올린 뒤 그립니다.
 *
 * 덮개 아래로는 터치를 흘리지 않습니다. 안내 중에 실제 버튼이 눌려
 * 화면이 바뀌면 다음 안내가 가리킬 자리가 사라집니다.
 */
class FeatureTourView(context: Context) : FrameLayout(context) {

    /**
     * 안내 한 장.
     *
     * @param target 밝게 뚫을 뷰. null 이거나 아직 크기가 없으면 그 장은 건너뜁니다.
     * @param also 같이 뚫을 뷰. 제목과 목록처럼 나란한 형제를 한 자리로 묶을 때 씁니다.
     *   없거나 아직 비어 있으면 [target] 만 뚫습니다.
     */
    data class Step(
        val title: String,
        val body: String,
        val target: () -> View?,
        val also: () -> View? = { null }
    )

    private val card = ViewFeatureTourCardBinding.inflate(LayoutInflater.from(context), this, false)

    private val scrimPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.scrim_strong) }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = ContextCompat.getColor(context, R.color.golden_500)
    }

    /** 지금 뚫린 자리. 비어 있으면 아직 아무 장도 안 그린 것입니다. */
    private val hole = RectF()
    private val holeRadius = dp(16f)
    private val holePadding = dp(6f)
    private val cardGap = dp(14f)
    private val sidePadding = resources.getDimensionPixelSize(R.dimen.screen_padding)

    private var steps: List<Step> = emptyList()
    private var index = -1
    private var onDone: (() -> Unit)? = null

    val isShowing: Boolean get() = parent != null

    init {
        setWillNotDraw(false)
        // 구멍을 CLEAR 로 뚫으려면 이 뷰가 제 레이어를 가져야 합니다.
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = true
        addView(card.root, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        card.root.isVisible = false

        card.btnTourNext.setOnClickListener { next() }
        card.btnTourSkip.setOnClickListener { finish() }
    }

    /**
     * 안내를 시작합니다. [host] 위에 덮개를 얹고 첫 장을 그립니다.
     *
     * @param onDone 끝까지 봤든 건너뛰었든, 덮개가 걷힐 때 한 번 불립니다.
     */
    fun start(host: ViewGroup, steps: List<Step>, onDone: () -> Unit) {
        // 대상이 없는 장은 처음부터 뺍니다. 그래야 "3 / 6"의 분모가 실제로
        // 보여 줄 장수와 맞습니다. 크기와 표시 여부는 스크롤과 무관합니다.
        val usable = steps.filter { it.target()?.isUsable() == true }
        if (usable.isEmpty()) {
            onDone()
            return
        }
        this.steps = usable
        this.onDone = onDone
        index = -1
        host.addView(
            this, ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        alpha = 0f
        animate().alpha(1f).setDuration(220).start()
        // 카드 폭을 이 뷰의 폭으로 재므로 한 번 배치된 뒤에 첫 장을 그립니다.
        doOnLayout { next() }
    }

    /** 뒤로 가기 등으로 밖에서 닫을 때. */
    fun dismiss() = finish()

    private fun next() {
        // 대상이 없는 장은 소리 없이 넘어갑니다. 목록이 아직 안 왔거나
        // 모드가 달라 그 버튼이 없는 화면일 수 있습니다.
        while (true) {
            index++
            if (index >= steps.size) {
                finish()
                return
            }
            // 시작 때 걸렀지만 그 사이 화면이 바뀌었을 수 있어 한 번 더 봅니다.
            val step = steps[index]
            val target = step.target()?.takeIf { it.isUsable() } ?: continue
            reveal(target, step.also()?.takeIf { it.isUsable() })
            return
        }
    }

    private fun View.isUsable(): Boolean = width > 0 && height > 0 && isShown

    private fun finish() {
        if (parent == null) return
        val done = onDone
        onDone = null
        animate().alpha(0f).setDuration(180).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
            done?.invoke()
        }.start()
    }

    /**
     * 대상을 화면 안으로 끌어온 뒤 구멍과 카드를 그 자리에 맞춥니다.
     *
     * 스크롤이 끝나고 한 번 그려진 다음에 위치를 재야 해서 post 로 미룹니다.
     */
    private fun reveal(target: View, also: View?) {
        // 둘을 같이 보여야 하면 아래쪽(목록)을 먼저 끌어올리고 위쪽(제목)을
        // 맞춥니다. 순서가 반대면 목록이 화면 밖에 남을 수 있습니다.
        var moved = also?.requestRectangleOnScreen(Rect(0, 0, also.width, also.height), true) ?: false
        moved = target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true) || moved
        if (moved) target.post { place(target, also) } else place(target, also)
    }

    private fun place(target: View, also: View?) {
        if (parent == null) return
        val step = steps.getOrNull(index) ?: return

        hole.set(boundsOf(target))
        also?.let { hole.union(boundsOf(it)) }
        hole.inset(-holePadding, -holePadding)

        card.tvTourTitle.text = step.title
        card.tvTourBody.text = step.body
        card.tvTourCount.text = context.getString(R.string.tour_count, index + 1, steps.size)
        card.btnTourNext.setText(
            if (index == steps.lastIndex) R.string.tour_done else R.string.tour_next
        )

        // 카드는 화면 폭에서 좌우 여백을 뺀 만큼 씁니다. 구멍이 아래쪽이면
        // 위에, 위쪽이면 아래에 붙여 설명이 대상을 가리지 않게 합니다.
        val cardWidth = width - sidePadding * 2
        card.root.measure(
            MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val cardHeight = card.root.measuredHeight
        // 덮개는 상태바·제스처바 아래까지 깔리므로 카드는 그 안쪽에만 둡니다.
        val bars = ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        val minTop = sidePadding + (bars?.top ?: 0)
        val maxBottom = height - sidePadding - (bars?.bottom ?: 0)
        val above = hole.centerY() > height / 2f
        val cardTop = if (above) {
            (hole.top - cardGap - cardHeight).coerceAtLeast(minTop.toFloat())
        } else {
            (hole.bottom + cardGap).coerceAtMost((maxBottom - cardHeight).toFloat())
        }
        card.root.layoutParams = LayoutParams(cardWidth, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = sidePadding
            topMargin = cardTop.toInt()
        }

        card.root.isVisible = true
        card.root.alpha = 0f
        card.root.animate().alpha(1f).setDuration(160).start()
        invalidate()
    }

    /** 대상의 자리를 이 뷰의 좌표로. */
    private fun boundsOf(view: View): RectF {
        val mine = IntArray(2).also { getLocationInWindow(it) }
        val theirs = IntArray(2).also { view.getLocationInWindow(it) }
        val left = (theirs[0] - mine[0]).toFloat()
        val top = (theirs[1] - mine[1]).toFloat()
        return RectF(left, top, left + view.width, top + view.height)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (hole.isEmpty) return
        canvas.drawRoundRect(hole, holeRadius, holeRadius, clearPaint)
        canvas.drawRoundRect(hole, holeRadius, holeRadius, ringPaint)
    }

    /** 덮개 어디를 눌러도 다음 장으로. 카드의 버튼은 제 리스너가 먼저 받습니다. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val inCard = event.x >= card.root.left && event.x <= card.root.right &&
                event.y >= card.root.top && event.y <= card.root.bottom
            if (!inCard) next()
        }
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
