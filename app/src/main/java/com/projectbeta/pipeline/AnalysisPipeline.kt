package com.projectbeta.pipeline

import com.projectbeta.engine.AnalysisReport
import com.projectbeta.engine.MetricsEngine
import com.projectbeta.engine.PoseFrame
import com.projectbeta.engine.TrajectoryBuilder
import com.projectbeta.engine.buildReport
import com.projectbeta.pose.PoseEstimator

data class PipelineResult(val report: AnalysisReport, val poseFrames: List<PoseFrame>)

class AnalysisPipeline(private val poseEstimator: PoseEstimator) {
    fun run(videoFilePath: String, scaleMetersPerUnit: Double?): PipelineResult {
        val frames = poseEstimator.estimate(videoFilePath)
        if (frames.isEmpty()) {
            throw IllegalStateException(
                "No frames could be decoded from this video. It may use a codec or " +
                    "resolution unsupported by this device (e.g. 4K/HDR video)."
            )
        }
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit)
        val report = MetricsEngine.buildReport(trajectory)
        return PipelineResult(report, frames)
    }
}
