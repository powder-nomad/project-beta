package com.projectbeta.data

import com.projectbeta.engine.AnalysisReport
import com.projectbeta.engine.DistanceUnit
import com.projectbeta.engine.Joint
import com.projectbeta.engine.JointObservation
import com.projectbeta.engine.MetricsEngine
import com.projectbeta.engine.Point3D
import com.projectbeta.engine.PoseFrame
import com.projectbeta.engine.SpeedSample
import com.projectbeta.engine.StabilitySample
import com.projectbeta.engine.Trajectory
import com.projectbeta.engine.TrajectoryPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ClimbMapperTest {

    private fun sampleReport(crux: MetricsEngine.CruxSegment?): AnalysisReport = AnalysisReport(
        trajectory = Trajectory(
            points = listOf(TrajectoryPoint(0L, Point3D(0.0, 0.0, 0.0)), TrajectoryPoint(1000L, Point3D(0.0, 1.0, 0.0))),
            units = DistanceUnit.METERS
        ),
        speedCurve = listOf(SpeedSample(1000L, 1.0)),
        stabilityCurve = listOf(StabilitySample(1000L, 0.2)),
        crux = crux,
        averageSpeed = 1.0,
        peakSpeed = 1.5
    )

    private fun samplePoseFrames(): List<PoseFrame> = listOf(
        PoseFrame(
            timestampMs = 0L,
            joints = listOf(JointObservation(Joint.LEFT_WRIST, Point3D(0.1, 0.2, 0.0), 0.9, false))
        ),
        PoseFrame(
            timestampMs = 1000L,
            joints = listOf(JointObservation(Joint.LEFT_WRIST, Point3D(0.15, 0.25, 0.0), 0.85, false))
        )
    )

    @Test
    fun `toClimbRecord copies queryable summary fields from the report`() {
        val crux = MetricsEngine.CruxSegment(startMs = 500L, endMs = 1500L, difficultyScore = 3.2)
        val record = ClimbMapper.toClimbRecord(
            report = sampleReport(crux),
            poseFrames = samplePoseFrames(),
            videoFilePath = "/videos/climb1.mp4",
            recordedAt = 42_000L
        )

        assertEquals(42_000L, record.recordedAt)
        assertEquals("/videos/climb1.mp4", record.videoFilePath)
        assertEquals(1.0, record.avgSpeed)
        assertEquals(1.5, record.peakSpeed)
        assertEquals(500L, record.cruxStartMs)
        assertEquals(1500L, record.cruxEndMs)
        assertEquals(3.2, record.cruxDifficultyScore)
    }

    @Test
    fun `toClimbRecord stores null crux fields when no crux was detected`() {
        val record = ClimbMapper.toClimbRecord(
            report = sampleReport(crux = null),
            poseFrames = emptyList(),
            videoFilePath = "/videos/climb2.mp4",
            recordedAt = 0L
        )

        assertNull(record.cruxStartMs)
        assertNull(record.cruxEndMs)
        assertNull(record.cruxDifficultyScore)
    }

    @Test
    fun `report and pose frames round-trip through JSON unchanged`() {
        val crux = MetricsEngine.CruxSegment(startMs = 500L, endMs = 1500L, difficultyScore = 3.2)
        val originalReport = sampleReport(crux)
        val originalFrames = samplePoseFrames()

        val record = ClimbMapper.toClimbRecord(originalReport, originalFrames, "/videos/climb3.mp4", 7L)
        val payload = ClimbMapper.toReportPayload(record)

        assertEquals(originalReport, payload.report)
        assertEquals(originalFrames, payload.poseFrames)
    }
}
