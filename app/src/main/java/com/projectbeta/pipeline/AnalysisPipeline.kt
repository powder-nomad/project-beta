package com.projectbeta.pipeline

import com.projectbeta.engine.AnalysisReport
import com.projectbeta.engine.MetricsEngine
import com.projectbeta.engine.TrajectoryBuilder
import com.projectbeta.engine.buildReport
import com.projectbeta.pose.PoseEstimator

class AnalysisPipeline(private val poseEstimator: PoseEstimator) {
    fun run(videoFilePath: String, scaleMetersPerUnit: Double?): AnalysisReport {
        val frames = poseEstimator.estimate(videoFilePath)
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit)
        return MetricsEngine.buildReport(trajectory)
    }
}
