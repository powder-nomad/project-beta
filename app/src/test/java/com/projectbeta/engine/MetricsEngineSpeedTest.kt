package com.projectbeta.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MetricsEngineSpeedTest {
    @Test
    fun `constant vertical movement yields constant speed`() {
        val trajectory = Trajectory(
            points = listOf(
                TrajectoryPoint(0, Point3D(0.0, 0.0, 0.0)),
                TrajectoryPoint(1000, Point3D(0.0, 1.0, 0.0)),
                TrajectoryPoint(2000, Point3D(0.0, 2.0, 0.0))
            ),
            units = DistanceUnit.METERS
        )
        val curve = MetricsEngine.computeSpeedCurve(trajectory)
        assertEquals(2, curve.size)
        assertEquals(1.0, curve[0].speedPerSecond, 1e-9)
        assertEquals(1.0, curve[1].speedPerSecond, 1e-9)
    }

    @Test
    fun `zero movement yields zero speed`() {
        val trajectory = Trajectory(
            points = listOf(
                TrajectoryPoint(0, Point3D(1.0, 1.0, 0.0)),
                TrajectoryPoint(500, Point3D(1.0, 1.0, 0.0))
            ),
            units = DistanceUnit.METERS
        )
        val curve = MetricsEngine.computeSpeedCurve(trajectory)
        assertEquals(0.0, curve[0].speedPerSecond, 1e-9)
    }

    @Test
    fun `single point trajectory yields empty speed curve`() {
        val trajectory = Trajectory(
            points = listOf(TrajectoryPoint(0, Point3D(0.0, 0.0, 0.0))),
            units = DistanceUnit.METERS
        )
        assertEquals(0, MetricsEngine.computeSpeedCurve(trajectory).size)
    }
}
