package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrajectoryBuilderTest {
    private fun frame(timestampMs: Long, y: Double, confidence: Double = 0.9) = PoseFrame(
        timestampMs = timestampMs,
        joints = listOf(
            JointObservation(Joint.LEFT_HIP, Point3D(0.0, y, 0.0), confidence, hasDepth = false),
            JointObservation(Joint.RIGHT_HIP, Point3D(0.0, y, 0.0), confidence, hasDepth = false)
        )
    )

    @Test
    fun `builds one trajectory point per frame using average joint position`() {
        val frames = listOf(frame(0, 1.0), frame(33, 2.0))
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit = null, smoothingWindow = 1)
        assertEquals(2, trajectory.points.size)
        assertEquals(1.0, trajectory.points[0].centerOfMass.y, 1e-9)
        assertEquals(2.0, trajectory.points[1].centerOfMass.y, 1e-9)
    }

    @Test
    fun `frames with only low-confidence joints are dropped, not fabricated`() {
        val frames = listOf(
            frame(0, 1.0, confidence = 0.9),
            frame(33, 5.0, confidence = 0.1),
            frame(66, 3.0, confidence = 0.9)
        )
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit = null, minConfidence = 0.5, smoothingWindow = 1)
        assertEquals(2, trajectory.points.size)
        assertEquals(0L, trajectory.points[0].timestampMs)
        assertEquals(66L, trajectory.points[1].timestampMs)
    }

    @Test
    fun `applies real-world scale and reports meters when calibrated`() {
        val frames = listOf(frame(0, 1.0), frame(33, 1.0))
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit = 2.0, smoothingWindow = 1)
        assertEquals(DistanceUnit.METERS, trajectory.units)
        assertEquals(2.0, trajectory.points[0].centerOfMass.y, 1e-9)
    }

    @Test
    fun `falls back to body-heights unit when no calibration provided`() {
        val frames = listOf(frame(0, 1.0))
        val trajectory = TrajectoryBuilder.build(frames, scaleMetersPerUnit = null, smoothingWindow = 1)
        assertEquals(DistanceUnit.BODY_HEIGHTS, trajectory.units)
    }
}
