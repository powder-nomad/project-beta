package com.projectbeta.engine

data class SpeedSample(val timestampMs: Long, val speedPerSecond: Double)

object MetricsEngine {
    fun computeSpeedCurve(trajectory: Trajectory): List<SpeedSample> {
        val points = trajectory.points
        if (points.size < 2) return emptyList()
        return (1 until points.size).map { i ->
            val prev = points[i - 1]
            val curr = points[i]
            val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000.0
            val speed = if (dtSeconds > 0.0) curr.centerOfMass.distanceTo(prev.centerOfMass) / dtSeconds else 0.0
            SpeedSample(curr.timestampMs, speed)
        }
    }
}
