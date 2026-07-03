package com.projectbeta.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CalibrationOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply { color = 0xFF00FF00.toInt(); strokeWidth = 6f }
    private var topTapY: Float? = null
    private var bottomTapY: Float? = null
    var onCalibrationComplete: ((pixelDistance: Double) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (topTapY == null) {
            topTapY = event.y
        } else if (bottomTapY == null) {
            bottomTapY = event.y
            onCalibrationComplete?.invoke(kotlin.math.abs(bottomTapY!! - topTapY!!).toDouble())
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        topTapY?.let { canvas.drawLine(0f, it, width.toFloat(), it, paint) }
        bottomTapY?.let { canvas.drawLine(0f, it, width.toFloat(), it, paint) }
    }
}
