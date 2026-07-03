package com.projectbeta.engine

data class SpeedSample(val timestampMs: Long, val speedPerSecond: Double)
data class StabilitySample(val timestampMs: Long, val instabilityScore: Double)

object MetricsEngine {
    data class CruxSegment(val startMs: Long, val endMs: Long, val difficultyScore: Double)

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

    fun computeStabilityCurve(trajectory: Trajectory): List<StabilitySample> {
        val points = trajectory.points
        if (points.size < 3) return points.map { StabilitySample(it.timestampMs, 0.0) }

        val primaryDirection = unitVector(points.last().centerOfMass - points.first().centerOfMass)

        val sway = points.indices.map { i ->
            if (i == 0) 0.0 else perpendicularMagnitude(points[i].centerOfMass - points[i - 1].centerOfMass, primaryDirection)
        }

        // TrajectoryBuilder drops low-confidence frames, so gaps between consecutive
        // TrajectoryPoints are not guaranteed to be uniform. Normalize every derivative
        // stage by elapsed time (matching computeSpeedCurve's dtSeconds pattern) so a
        // longer gap after a dropped frame isn't misread as a sudden, unstable movement.
        val dtSeconds = points.indices.map { i ->
            if (i == 0) 0.0 else (points[i].timestampMs - points[i - 1].timestampMs) / 1000.0
        }

        // Extrapolate the pre-start velocity from the first real segment instead of a
        // synthetic zero vector. A zero-vector placeholder would create a spurious jump
        // from 0 to the first real velocity, showing up as fake acceleration/jerk at
        // indices 1-2 even for a perfectly smooth constant-velocity climb.
        val rawVelocities = (1 until points.size).map { i ->
            val dt = dtSeconds[i]
            if (dt > 0.0) (points[i].centerOfMass - points[i - 1].centerOfMass) / dt else Point3D(0.0, 0.0, 0.0)
        }
        val velocities = listOf(rawVelocities.first()) + rawVelocities

        val accelerations = points.indices.map { i ->
            if (i == 0) {
                Point3D(0.0, 0.0, 0.0)
            } else {
                val dt = dtSeconds[i]
                if (dt > 0.0) (velocities[i] - velocities[i - 1]) / dt else Point3D(0.0, 0.0, 0.0)
            }
        }
        val jerk = points.indices.map { i ->
            if (i == 0) {
                0.0
            } else {
                val dt = dtSeconds[i]
                if (dt > 0.0) accelerations[i].distanceTo(accelerations[i - 1]) / dt else 0.0
            }
        }

        val normalizedSway = normalize(sway)
        val normalizedJerk = normalize(jerk)

        return points.indices.map { i ->
            StabilitySample(points[i].timestampMs, normalizedSway[i] + normalizedJerk[i])
        }
    }

    private fun unitVector(v: Point3D): Point3D {
        val length = v.distanceTo(Point3D(0.0, 0.0, 0.0))
        if (length == 0.0) return Point3D(0.0, 0.0, 0.0)
        return Point3D(v.x / length, v.y / length, v.z / length)
    }

    private fun perpendicularMagnitude(v: Point3D, unitDirection: Point3D): Double {
        val dot = v.x * unitDirection.x + v.y * unitDirection.y + v.z * unitDirection.z
        val parallel = Point3D(unitDirection.x * dot, unitDirection.y * dot, unitDirection.z * dot)
        val perpendicular = v - parallel
        return perpendicular.distanceTo(Point3D(0.0, 0.0, 0.0))
    }

    private fun normalize(values: List<Double>): List<Double> {
        val max = values.maxOrNull() ?: 0.0
        if (max == 0.0) return values.map { 0.0 }
        return values.map { it / max }
    }

    // Difficulty formula is an unvalidated hypothesis (per spec) — needs tuning
    // against coach-labeled climbs before the output can be trusted.
    fun detectCrux(
        speedCurve: List<SpeedSample>,
        stabilityCurve: List<StabilitySample>,
        segmentWindowMs: Long = 1000
    ): CruxSegment? {
        if (speedCurve.isEmpty() || stabilityCurve.isEmpty()) return null

        val stabilityByTimestamp = stabilityCurve.associateBy { it.timestampMs }
        val epsilon = 0.01
        val difficultyByTimestamp = speedCurve.mapNotNull { speedSample ->
            val stability = stabilityByTimestamp[speedSample.timestampMs] ?: return@mapNotNull null
            speedSample.timestampMs to (1.0 / (speedSample.speedPerSecond + epsilon) + stability.instabilityScore)
        }
        if (difficultyByTimestamp.isEmpty()) return null

        val sorted = difficultyByTimestamp.sortedBy { it.first }
        var bestStart = sorted.first().first
        var bestEnd = sorted.first().first
        var bestAvg = Double.NEGATIVE_INFINITY

        for (window in sorted.indices) {
            val windowStart = sorted[window].first
            val windowEnd = windowStart + segmentWindowMs
            val inWindow = sorted.filter { it.first in windowStart..windowEnd }
            val avg = inWindow.sumOf { it.second } / inWindow.size
            if (avg > bestAvg) {
                bestAvg = avg
                bestStart = windowStart
                bestEnd = inWindow.last().first
            }
        }

        return CruxSegment(bestStart, bestEnd, bestAvg)
    }
}
