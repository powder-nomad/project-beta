package com.projectbeta.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

private const val LINE_STROKE_WIDTH_PX = 4f

/** Minimal line-only preview of a value series for history cards — intentionally not a full
 * MPAndroidChart, since rendering a full chart per RecyclerView row is unnecessary overhead
 * for a small preview. Tapping the card opens ReportActivity for the full-size charts. */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Double> = emptyList()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5")
        style = Paint.Style.STROKE
        strokeWidth = LINE_STROKE_WIDTH_PX
    }

    fun setValues(newValues: List<Double>) {
        values = newValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2 || width == 0 || height == 0) return

        val max = values.max()
        val min = values.min()
        val range = (max - min).let { if (it > 0.0) it else 1.0 }
        val stepX = width / (values.size - 1).toFloat()

        fun yFor(value: Double) = height - ((value - min) / range * height).toFloat()

        var previousX = 0f
        var previousY = yFor(values[0])
        for (i in 1 until values.size) {
            val x = i * stepX
            val y = yFor(values[i])
            canvas.drawLine(previousX, previousY, x, y, linePaint)
            previousX = x
            previousY = y
        }
    }
}
