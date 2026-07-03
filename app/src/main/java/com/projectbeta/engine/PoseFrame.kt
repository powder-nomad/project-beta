package com.projectbeta.engine

enum class Joint {
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_HIP, RIGHT_HIP,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_ANKLE, RIGHT_ANKLE
}

data class JointObservation(
    val joint: Joint,
    val position: Point3D,
    val confidence: Double,
    val hasDepth: Boolean
)

data class PoseFrame(
    val timestampMs: Long,
    val joints: List<JointObservation>
)
