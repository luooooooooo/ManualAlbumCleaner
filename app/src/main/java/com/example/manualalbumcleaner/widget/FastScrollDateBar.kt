package com.example.manualalbumcleaner.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 列表右侧的快速定位手柄：上下拖动直接跳到对应位置，
 * 拖动过程中在拇指旁显示当前那一张的日期。
 */
class FastScrollDateBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 索引 → 日期文案 */
    var labelProvider: ((Int) -> CharSequence?)? = null

    /** 拖动时回调目标索引 */
    var onSeek: ((Int) -> Unit)? = null

    var recyclerView: RecyclerView? = null
        set(value) {
            field?.removeOnScrollListener(scrollListener)
            field = value
            value?.addOnScrollListener(scrollListener)
            syncFromScroll()
        }

    private var dragging = false
    private var fraction = 0f
    private var bubbleVisible = false
    private var bubbleLabel: CharSequence = ""
    private var bubbleCenterY = 0f

    private val density = resources.displayMetrics.density
    private val trackWidth = 3f * density
    private val thumbWidth = 7f * density
    private val thumbHeight = 44f * density
    private val bubblePadH = 10f * density
    private val bubblePadV = 6f * density
    private val bubbleGap = 6f * density
    /** 轨道距离右边距 */
    private val trackInset = 14f * density
    /** 只有最右侧这条区域响应手势，左边区域让给缩略图点击 */
    private val touchZone = 44f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@FastScrollDateBar, TRACK_COLOR)
        alpha = 90
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@FastScrollDateBar, THUMB_COLOR)
    }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@FastScrollDateBar, BUBBLE_COLOR)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@FastScrollDateBar, BUBBLE_TEXT_COLOR)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics
        )
    }

    private val rect = RectF()
    private val hideRunnable = Runnable {
        bubbleVisible = false
        invalidate()
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!dragging) syncFromScroll()
        }
    }

    private fun itemCount(): Int = recyclerView?.adapter?.itemCount ?: 0

    /** 数据变化后重新同步拇指位置 */
    fun refresh() {
        syncFromScroll()
    }

    private fun trackTop(): Float = paddingTop.toFloat()

    private fun trackBottom(): Float = (height - paddingBottom).toFloat()

    private fun syncFromScroll() {
        val count = itemCount()
        if (count < 2) {
            invalidate()
            return
        }
        val lm = (recyclerView?.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)
        val first = lm?.findFirstVisibleItemPosition() ?: 0
        fraction = (first.toFloat() / (count - 1)).coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = itemCount()
        if (count < 2) return

        val top = trackTop()
        val bottom = trackBottom()
        if (bottom <= top) return

        val cx = width - trackInset
        // 轨道
        rect.set(cx - trackWidth / 2f, top, cx + trackWidth / 2f, bottom)
        val radius = trackWidth / 2f
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        // 拇指
        val thumbH = minOf(thumbHeight, bottom - top)
        val travel = max(0f, (bottom - top) - thumbH)
        val thumbY = top + travel * fraction
        rect.set(cx - thumbWidth / 2f, thumbY, cx + thumbWidth / 2f, thumbY + thumbH)
        canvas.drawRoundRect(rect, thumbWidth / 2f, thumbWidth / 2f, thumbPaint)

        if (!bubbleVisible || bubbleLabel.isEmpty()) return

        // 日期气泡，贴在拇指左侧
        val textWidth = textPaint.measureText(bubbleLabel.toString())
        val fm = textPaint.fontMetrics
        val bubbleW = textWidth + bubblePadH * 2
        val bubbleH = (fm.descent - fm.ascent) + bubblePadV * 2
        val right = cx - thumbWidth / 2f - bubbleGap
        val left = max(2f * density, right - bubbleW)
        val centerY = if (bubbleCenterY > 0f) bubbleCenterY else thumbY + thumbH / 2f
        val bubbleTop = centerY - bubbleH / 2f
        rect.set(left, bubbleTop, right, bubbleTop + bubbleH)
        canvas.drawRoundRect(rect, bubbleH / 2f, bubbleH / 2f, bubblePaint)

        val baseline = centerY - (fm.ascent + fm.descent) / 2f
        canvas.drawText(bubbleLabel, 0, bubbleLabel.length, left + bubblePadH, baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (itemCount() < 2) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // 左侧空白区不拦截，交给下面的缩略图网格
            if (event.x < width - touchZone) return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                removeCallbacks(hideRunnable)
                updateFromTouch(event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) updateFromTouch(event.y)
                return dragging
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                postDelayed(hideRunnable, 700)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(y: Float) {
        val count = itemCount()
        if (count < 2) return
        val top = trackTop()
        val bottom = trackBottom()
        val span = (bottom - top).coerceAtLeast(1f)
        fraction = ((y - top) / span).coerceIn(0f, 1f)

        val index = (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
        bubbleLabel = labelProvider?.invoke(index)?.toString().orEmpty()
        bubbleVisible = bubbleLabel.isNotEmpty()
        bubbleCenterY = top + (bottom - top) * fraction

        onSeek?.invoke(index)
        invalidate()
    }

    private companion object {
        val TRACK_COLOR = com.google.android.material.R.attr.colorOnSurfaceVariant
        val THUMB_COLOR = com.google.android.material.R.attr.colorPrimary
        val BUBBLE_COLOR = com.google.android.material.R.attr.colorOnSurface
        val BUBBLE_TEXT_COLOR = com.google.android.material.R.attr.colorSurface
    }
}
