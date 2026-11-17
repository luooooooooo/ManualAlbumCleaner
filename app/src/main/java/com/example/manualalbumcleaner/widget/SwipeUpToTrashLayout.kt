package com.example.manualalbumcleaner.widget

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.min

/**
 * 只负责"向上抛"这一个手势的容器：向左/向右的滑动仍然交给内部的 ViewPager2 处理，
 * 因此左右翻页保留系统的原生惯性手感，上滑则由这里接管并联动回收站提示。
 */
class SwipeUpToTrashLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 跟随手指移动的视图，一般是 ViewPager2 */
    var dragTarget: View? = null

    /** 上滑时逐渐显现的回收站目标提示 */
    var trashTarget: View? = null

    var dragEnabled = true

    /** 拖拽进度：0 起步，1 达到删除阈值，可超过 1 */
    var onDragProgress: ((Float) -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDragCancel: (() -> Unit)? = null
    var onTrashed: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 6

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var animating = false
    private var hapticDone = false
    private var thresholdPx = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!dragEnabled || animating) return false
        val target = dragTarget ?: return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragging = false
                hapticDone = false
                thresholdPx = target.height.toFloat() * THRESHOLD_RATIO
                releaseVelocityTracker()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                if (dragging) return true
                val dy = ev.y - downY
                val dx = ev.x - downX
                // 竖向位移明显大于横向，且方向向上，才认定是"上滑"
                if (dy < -touchSlop && abs(dy) > abs(dx) * VERTICAL_BIAS) {
                    dragging = true
                    onDragStart?.invoke()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                releaseVelocityTracker()
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(ev)

        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> applyDrag(ev.y - downY)

            MotionEvent.ACTION_UP -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                val dy = ev.y - downY
                dragging = false
                releaseVelocityTracker()
                if (dy < -thresholdPx || (dy < -touchSlop * 2 && vy < -minimumFlingVelocity)) {
                    commitTrash()
                } else {
                    cancelDrag()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                releaseVelocityTracker()
                cancelDrag()
            }
        }
        return true
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun applyDrag(rawDy: Float) {
        val target = dragTarget ?: return
        val dy = min(0f, rawDy)
        val progress = if (thresholdPx > 0f) abs(dy) / thresholdPx else 0f
        val visual = progress.coerceAtMost(1f)

        target.translationY = dy
        val scale = 1f - SCALE_DROP * visual
        target.scaleX = scale
        target.scaleY = scale
        target.alpha = 1f - 0.35f * visual

        trashTarget?.let {
            it.alpha = visual * visual
            val targetScale = 0.7f + 0.3f * visual
            it.scaleX = targetScale
            it.scaleY = targetScale
        }

        onDragProgress?.invoke(progress)

        if (progress >= 1f && !hapticDone) {
            hapticDone = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun commitTrash() {
        val target = dragTarget ?: return
        animating = true
        trashTarget?.animate()
            ?.alpha(0f)
            ?.scaleX(0.7f)
            ?.scaleY(0.7f)
            ?.setDuration(140)
            ?.start()

        target.animate()
            .translationY(-height.toFloat() * 1.1f)
            .alpha(0f)
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(220)
            .setInterpolator(AccelerateInterpolator(1.6f))
            .withEndAction {
                resetVisuals()
                animating = false
                onTrashed?.invoke()
            }
            .start()
    }

    private fun cancelDrag() {
        val target = dragTarget ?: return
        animating = true
        trashTarget?.animate()?.alpha(0f)?.setDuration(180)?.start()

        target.animate()
            .translationY(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260)
            .setInterpolator(OvershootInterpolator(0.85f))
            .withEndAction {
                resetVisuals()
                animating = false
                onDragCancel?.invoke()
            }
            .start()
    }

    private fun resetVisuals() {
        dragTarget?.let {
            it.translationY = 0f
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
        }
        trashTarget?.let {
            it.alpha = 0f
            it.scaleX = 0.7f
            it.scaleY = 0.7f
        }
    }

    private companion object {
        /** 上滑超过约 1/4 屏高即视为删除 */
        const val THRESHOLD_RATIO = 0.26f

        /** 竖向位移达到横向的 1.5 倍才拦截，避免和左右翻页抢手势 */
        const val VERTICAL_BIAS = 1.5f

        const val SCALE_DROP = 0.10f
    }
}
