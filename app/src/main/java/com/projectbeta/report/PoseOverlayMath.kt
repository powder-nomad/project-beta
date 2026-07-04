package com.projectbeta.report

import com.projectbeta.engine.Joint
import com.projectbeta.engine.Point3D
import com.projectbeta.engine.PoseFrame
import kotlin.math.abs

/** The rectangle within a view where the video is actually drawn (accounts for letterboxing). */
data class DisplayRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Bones drawn between tracked joints. Shoulders/hips connect straight to wrists/ankles since
 * elbows and knees aren't part of the 8-joint set MediaPipeJointMapping tracks. */
val SKELETON_BONES: List<Pair<Joint, Joint>> = listOf(
    Joint.LEFT_SHOULDER to Joint.RIGHT_SHOULDER,
    Joint.LEFT_HIP to Joint.RIGHT_HIP,
    Joint.LEFT_SHOULDER to Joint.LEFT_HIP,
    Joint.RIGHT_SHOULDER to Joint.RIGHT_HIP,
    Joint.LEFT_SHOULDER to Joint.LEFT_WRIST,
    Joint.RIGHT_SHOULDER to Joint.RIGHT_WRIST,
    Joint.LEFT_HIP to Joint.LEFT_ANKLE,
    Joint.RIGHT_HIP to Joint.RIGHT_ANKLE
)

/**
 * Pure-Kotlin math behind the skeleton overlay: finding which recorded [PoseFrame] to draw for
 * a given video playback position, and mapping its normalized (0..1) landmark coordinates into
 * the pixel space of the view actually showing the video. Kept free of android.view.View so it
 * can be unit tested without an Android runtime.
 */
object PoseOverlayMath {

    fun nearestFrame(frames: List<PoseFrame>, playbackPositionMs: Long): PoseFrame? =
        frames.minByOrNull { abs(it.timestampMs - playbackPositionMs) }

    /**
     * Video players scale-to-fit by default, which letterboxes when the video and view aspect
     * ratios differ. This computes the actual on-screen rectangle the video occupies so overlay
     * points land on the real content instead of being stretched across the full view.
     */
    fun computeDisplayRect(
        viewWidth: Float,
        viewHeight: Float,
        videoWidth: Float,
        videoHeight: Float
    ): DisplayRect {
        if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0f || videoHeight <= 0f) {
            return DisplayRect(0f, 0f, viewWidth, viewHeight)
        }
        val viewAspect = viewWidth / viewHeight
        val videoAspect = videoWidth / videoHeight
        return if (videoAspect > viewAspect) {
            val displayHeight = viewWidth / videoAspect
            DisplayRect(left = 0f, top = (viewHeight - displayHeight) / 2f, width = viewWidth, height = displayHeight)
        } else {
            val displayWidth = viewHeight * videoAspect
            DisplayRect(left = (viewWidth - displayWidth) / 2f, top = 0f, width = displayWidth, height = viewHeight)
        }
    }

    fun toViewSpace(normalized: Point3D, rect: DisplayRect): Pair<Float, Float> {
        val x = rect.left + normalized.x.toFloat() * rect.width
        val y = rect.top + normalized.y.toFloat() * rect.height
        return x to y
    }
}
