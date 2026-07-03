package com.projectbeta.engine

object TrajectoryBuilder {
    fun build(
        frames: List<PoseFrame>,
        scaleMetersPerUnit: Double?,
        minConfidence: Double = 0.5,
        smoothingWindow: Int = 5
    ): Trajectory {
        val rawPoints = frames.mapNotNull { frame ->
            val qualifying = frame.joints.filter { it.confidence >= minConfidence }
            if (qualifying.isEmpty()) return@mapNotNull null
            val avg = Point3D(
                x = qualifying.sumOf { it.position.x } / qualifying.size,
                y = qualifying.sumOf { it.position.y } / qualifying.size,
                z = qualifying.sumOf { it.position.z } / qualifying.size
            )
            TrajectoryPoint(frame.timestampMs, avg)
        }

        val smoothed = smooth(rawPoints, smoothingWindow)

        val scaled = if (scaleMetersPerUnit != null) {
            smoothed.map {
                it.copy(
                    centerOfMass = Point3D(
                        it.centerOfMass.x * scaleMetersPerUnit,
                        it.centerOfMass.y * scaleMetersPerUnit,
                        it.centerOfMass.z * scaleMetersPerUnit
                    )
                )
            }
        } else {
            smoothed
        }

        val units = if (scaleMetersPerUnit != null) DistanceUnit.METERS else DistanceUnit.BODY_HEIGHTS
        return Trajectory(scaled, units)
    }

    private fun smooth(points: List<TrajectoryPoint>, window: Int): List<TrajectoryPoint> {
        if (window <= 1 || points.size < 2) return points
        val half = window / 2
        return points.indices.map { i ->
            val start = (i - half).coerceAtLeast(0)
            val end = (i + half).coerceAtMost(points.size - 1)
            val slice = points.subList(start, end + 1)
            val avg = Point3D(
                x = slice.sumOf { it.centerOfMass.x } / slice.size,
                y = slice.sumOf { it.centerOfMass.y } / slice.size,
                z = slice.sumOf { it.centerOfMass.z } / slice.size
            )
            TrajectoryPoint(points[i].timestampMs, avg)
        }
    }
}
