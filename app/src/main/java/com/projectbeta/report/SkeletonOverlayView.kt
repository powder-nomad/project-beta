package com.projectbeta.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.projectbeta.engine.PoseFrame

private const val JOINT_RADIUS_PX = 12f
private const val BONE_STROKE_WIDTH_PX = 6f

/**
 * Transparent overlay drawn on top of a video player showing the 8 tracked joints + bones for
 * whichever [PoseFrame] is nearest the current playback position. The host (ReportActivity)
 * feeds playback position via [updatePlaybackPosition] and video intrinsic size via
 * [updateVideoSize] as the player reports them.
 */
class SkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var poseFrames: List<PoseFrame> = emptyList()
    private var playbackPositionMs: Long = 0L
    private var videoWidth: Float = 0f
    private var videoHeight: Float = 0f

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = BONE_STROKE_WIDTH_PX
    }

    fun setPoseFrames(frames: List<PoseFrame>) {
        poseFrames = frames
        invalidate()
    }

    fun updateVideoSize(width: Float, height: Float) {
        videoWidth = width
        videoHeight = height
        invalidate()
    }

    fun updatePlaybackPosition(positionMs: Long) {
        playbackPositionMs = positionMs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frame = PoseOverlayMath.nearestFrame(poseFrames, playbackPositionMs) ?: return
        val rect = PoseOverlayMath.computeDisplayRect(width.toFloat(), height.toFloat(), videoWidth, videoHeight)
        val positionsByJoint = frame.joints.associate { it.joint to PoseOverlayMath.toViewSpace(it.position, rect) }

        for ((from, to) in SKELETON_BONES) {
            val start = positionsByJoint[from] ?: continue
            val end = positionsByJoint[to] ?: continue
            canvas.drawLine(start.first, start.second, end.first, end.second, bonePaint)
        }
        for ((x, y) in positionsByJoint.values) {
            canvas.drawCircle(x, y, JOINT_RADIUS_PX, jointPaint)
        }
    }
}
