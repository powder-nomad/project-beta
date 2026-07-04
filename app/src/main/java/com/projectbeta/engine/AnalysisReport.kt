package com.projectbeta.engine

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisReport(
    val trajectory: Trajectory,
    val speedCurve: List<SpeedSample>,
    val stabilityCurve: List<StabilitySample>,
    val crux: MetricsEngine.CruxSegment?,
    val averageSpeed: Double,
    val peakSpeed: Double
)

fun MetricsEngine.buildReport(trajectory: Trajectory): AnalysisReport {
    val speedCurve = MetricsEngine.computeSpeedCurve(trajectory)
    val stabilityCurve = MetricsEngine.computeStabilityCurve(trajectory)
    val crux = MetricsEngine.detectCrux(speedCurve, stabilityCurve)
    val average = if (speedCurve.isEmpty()) 0.0 else speedCurve.sumOf { it.speedPerSecond } / speedCurve.size
    val peak = speedCurve.maxOfOrNull { it.speedPerSecond } ?: 0.0
    return AnalysisReport(trajectory, speedCurve, stabilityCurve, crux, average, peak)
}
