package com.example.manualalbumcleaner.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * 支持双指捏合切换列数的网格列表：捏合时 2~5 列实时切换，
 * 单指滚动、点击等行为保持 RecyclerView 的原样。
 */
class PinchGridRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var minColumns = 2
    var maxColumns = 5

    var columns = 2
        private set

    /** 列数变化回调，用于同步适配器的条目布局 */
    var onColumnsChanged: ((Int) -> Unit)? = null

    private var baseColumns = 2
    private var accumulatedScale = 1f
    private var consumingPinch = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (layoutManager !is GridLayoutManager) return false
                if (adapter == null || adapter?.itemCount == 0) return false
                baseColumns = columns
                accumulatedScale = 1f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                accumulatedScale *= detector.scaleFactor
                // 和相册应用一致：捏拢手指 → 缩略图变小、列数变多；张开手指 → 反之
                val target = (baseColumns / accumulatedScale)
                    .roundToInt()
                    .coerceIn(minColumns, maxColumns)
                if (target != columns) applyColumns(target)
                return true
            }
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) consumingPinch = false

        if (layoutManager is GridLayoutManager) {
            scaleDetector.onTouchEvent(ev)
            if (scaleDetector.isInProgress) {
                if (!consumingPinch) {
                    consumingPinch = true
                    cancelOngoingTouch(ev)
                }
                // 捏合进行中吞掉事件，避免列表跟着上下滚动或误触条目
                return true
            }
            if (consumingPinch) {
                // 抬起一根手指后到完全松手之间的残余事件也一并吞掉
                if (ev.actionMasked == MotionEvent.ACTION_UP ||
                    ev.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    consumingPinch = false
                }
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** 捏合开始时给列表发一个 CANCEL，清掉已经按下的条目状态，防止松手后误触发点击 */
    private fun cancelOngoingTouch(source: MotionEvent) {
        val cancel = MotionEvent.obtain(source)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    /** 外部设置列数（例如读取上次保存的偏好） */
    fun setColumns(count: Int) {
        columns = count.coerceIn(minColumns, maxColumns)
        (layoutManager as? GridLayoutManager)?.spanCount = columns
    }

    private fun applyColumns(target: Int) {
        val lm = layoutManager as? GridLayoutManager ?: return
        val anchor = lm.findFirstVisibleItemPosition()
        val anchorOffset = if (anchor == NO_POSITION) 0 else (lm.findViewByPosition(anchor)?.top ?: 0)

        columns = target
        lm.spanCount = target
        onColumnsChanged?.invoke(target)

        if (anchor != NO_POSITION) {
            lm.scrollToPositionWithOffset(anchor, anchorOffset)
        }
    }
}
