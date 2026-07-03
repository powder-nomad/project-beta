package com.projectbeta.engine

enum class DistanceUnit { METERS, BODY_HEIGHTS }

data class TrajectoryPoint(
    val timestampMs: Long,
    val centerOfMass: Point3D
)

data class Trajectory(
    val points: List<TrajectoryPoint>,
    val units: DistanceUnit
)
