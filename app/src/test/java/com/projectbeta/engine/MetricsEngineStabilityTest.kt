package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetricsEngineStabilityTest {
    @Test
    fun `perfectly straight constant-velocity climb has zero instability`() {
        val trajectory = Trajectory(
            points = (0..5).map { i -> TrajectoryPoint(i * 1000L, Point3D(0.0, i.toDouble(), 0.0)) },
            units = DistanceUnit.METERS
        )
        val curve = MetricsEngine.computeStabilityCurve(trajectory)
        curve.forEach { assertEquals(0.0, it.instabilityScore, 1e-9) }
    }

    @Test
    fun `lateral wobble increases instability relative to a straight climb`() {
        val straight = Trajectory(
            points = (0..5).map { i -> TrajectoryPoint(i * 1000L, Point3D(0.0, i.toDouble(), 0.0)) },
            units = DistanceUnit.METERS
        )
        val wobbly = Trajectory(
            points = listOf(
                TrajectoryPoint(0, Point3D(0.0, 0.0, 0.0)),
                TrajectoryPoint(1000, Point3D(0.5, 1.0, 0.0)),
                TrajectoryPoint(2000, Point3D(-0.5, 2.0, 0.0)),
                TrajectoryPoint(3000, Point3D(0.5, 3.0, 0.0)),
                TrajectoryPoint(4000, Point3D(-0.5, 4.0, 0.0)),
                TrajectoryPoint(5000, Point3D(0.0, 5.0, 0.0))
            ),
            units = DistanceUnit.METERS
        )
        val straightMax = MetricsEngine.computeStabilityCurve(straight).maxOf { it.instabilityScore }
        val wobblyMax = MetricsEngine.computeStabilityCurve(wobbly).maxOf { it.instabilityScore }
        assertTrue(wobblyMax > straightMax)
    }

    @Test
    fun `steady climb with an uneven frame gap does not spike instability at the gap`() {
        // Simulates TrajectoryBuilder dropping a low-confidence frame: the gap between
        // index 2 (2000ms) and index 3 (5000ms) is 3x the other gaps, but the climber
        // still moves at a perfectly steady 1 unit/sec along a single axis throughout.
        // A time-normalized velocity/acceleration/jerk computation must not mistake the
        // larger raw displacement across that gap for a sudden, unstable movement.
        val trajectory = Trajectory(
            points = listOf(
                TrajectoryPoint(0L, Point3D(0.0, 0.0, 0.0)),
                TrajectoryPoint(1000L, Point3D(0.0, 1.0, 0.0)),
                TrajectoryPoint(2000L, Point3D(0.0, 2.0, 0.0)),
                TrajectoryPoint(5000L, Point3D(0.0, 5.0, 0.0)),
                TrajectoryPoint(6000L, Point3D(0.0, 6.0, 0.0)),
                TrajectoryPoint(7000L, Point3D(0.0, 7.0, 0.0))
            ),
            units = DistanceUnit.METERS
        )
        val curve = MetricsEngine.computeStabilityCurve(trajectory)
        curve.forEach { assertEquals(0.0, it.instabilityScore, 1e-9) }
    }
}
