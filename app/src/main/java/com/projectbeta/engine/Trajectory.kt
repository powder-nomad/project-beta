package com.projectbeta.engine

import kotlinx.serialization.Serializable

@Serializable
enum class DistanceUnit { METERS, BODY_HEIGHTS }

@Serializable
data class TrajectoryPoint(
    val timestampMs: Long,
    val centerOfMass: Point3D
)

@Serializable
data class Trajectory(
    val points: List<TrajectoryPoint>,
    val units: DistanceUnit
)
