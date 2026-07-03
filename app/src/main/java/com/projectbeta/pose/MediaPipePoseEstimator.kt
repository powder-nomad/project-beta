package com.projectbeta.pose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.projectbeta.engine.JointObservation
import com.projectbeta.engine.Point3D
import com.projectbeta.engine.PoseFrame
import java.io.File

private const val POSE_MODEL_ASSET_PATH = "pose_landmarker_full.task"
private const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
private const val DEFAULT_FRAME_INTERVAL_US = 33_000L // ~30 fps sampling

class MediaPipePoseEstimator(private val context: Context) : PoseEstimator {

    override fun estimate(videoFilePath: String): List<PoseFrame> {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(POSE_MODEL_ASSET_PATH)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
            .build()

        PoseLandmarker.createFromOptions(context, options).use { landmarker ->
            val frames = mutableListOf<PoseFrame>()
            VideoFrameSource(File(videoFilePath)).forEachFrame { bitmap, timestampMs ->
                val result = landmarker.detectForVideo(
                    BitmapImageBuilder(bitmap).build(),
                    timestampMs
                )
                val landmarks = result.landmarks().firstOrNull() ?: return@forEachFrame
                val joints = landmarks.mapIndexedNotNull { index, landmark ->
                    val joint = MediaPipeJointMapping.toJoint(index) ?: return@mapIndexedNotNull null
                    JointObservation(
                        joint = joint,
                        position = Point3D(landmark.x().toDouble(), landmark.y().toDouble(), 0.0),
                        confidence = landmark.visibility().orElse(0.0f).toDouble(),
                        hasDepth = false
                    )
                }
                frames.add(PoseFrame(timestampMs, joints))
            }
            return frames
        }
    }
}

/**
 * Decodes an MP4 into per-frame [Bitmap] + timestamp (ms) pairs using
 * [MediaMetadataRetriever.getFrameAtTime], sampling at a fixed interval.
 *
 * This is plain Android boilerplate with no MediaPipe-specific logic; it exists solely to
 * feed [MediaPipePoseEstimator] a sequence of frames to run pose detection on.
 */
private class VideoFrameSource(private val videoFile: File) {

    fun forEachFrame(
        frameIntervalUs: Long = DEFAULT_FRAME_INTERVAL_US,
        action: (bitmap: Bitmap, timestampMs: Long) -> Unit
    ) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val durationUs = durationMs * 1_000L

            var timeUs = 0L
            while (timeUs <= durationUs) {
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    action(bitmap, timeUs / 1_000L)
                }
                timeUs += frameIntervalUs
            }
        } finally {
            retriever.release()
        }
    }
}
