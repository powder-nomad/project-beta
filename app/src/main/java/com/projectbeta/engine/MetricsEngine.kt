package com.projectbeta.engine

import kotlin.math.sqrt

data class SpeedSample(val timestampMs: Long, val speedPerSecond: Double)
data class StabilitySample(val timestampMs: Long, val instabilityScore: Double)

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

    fun computeStabilityCurve(trajectory: Trajectory): List<StabilitySample> {
        val points = trajectory.points
        if (points.size < 3) return points.map { StabilitySample(it.timestampMs, 0.0) }

        val primaryDirection = unitVector(points.last().centerOfMass - points.first().centerOfMass)

        val sway = points.indices.map { i ->
            if (i == 0) 0.0 else perpendicularMagnitude(points[i].centerOfMass - points[i - 1].centerOfMass, primaryDirection)
        }

        val velocities = points.indices.map { i ->
            if (i == 0) {
                if (points.size > 1) points[1].centerOfMass - points[0].centerOfMass else Point3D(0.0, 0.0, 0.0)
            } else {
                points[i].centerOfMass - points[i - 1].centerOfMass
            }
        }
        val accelerations = velocities.indices.map { i ->
            if (i == 0) Point3D(0.0, 0.0, 0.0) else velocities[i] - velocities[i - 1]
        }
        val jerk = accelerations.indices.map { i ->
            if (i == 0) 0.0 else accelerations[i].distanceTo(accelerations[i - 1])
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
}
