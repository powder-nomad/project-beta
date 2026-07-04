package com.projectbeta.pipeline

import com.projectbeta.engine.Joint
import com.projectbeta.engine.JointObservation
import com.projectbeta.engine.Point3D
import com.projectbeta.engine.PoseFrame
import com.projectbeta.pose.PoseEstimator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class FakePoseEstimator(private val frames: List<PoseFrame>) : PoseEstimator {
    override fun estimate(videoFilePath: String): List<PoseFrame> = frames
}

class AnalysisPipelineTest {
    @Test
    fun `runs the full pipeline from pose frames to a report`() {
        val frames = (0..4).map { i ->
            PoseFrame(
                timestampMs = i * 1000L,
                joints = listOf(
                    JointObservation(Joint.LEFT_HIP, Point3D(0.0, i.toDouble(), 0.0), 0.9, false),
                    JointObservation(Joint.RIGHT_HIP, Point3D(0.0, i.toDouble(), 0.0), 0.9, false)
                )
            )
        }
        val pipeline = AnalysisPipeline(FakePoseEstimator(frames))

        val result = pipeline.run("unused/path.mp4", scaleMetersPerUnit = null)

        assertEquals(5, result.report.trajectory.points.size)
        assertEquals(4, result.report.speedCurve.size)
        assertNotNull(result.report.crux)
        assertEquals(frames, result.poseFrames)
    }
}
