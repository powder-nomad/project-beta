package com.projectbeta.data

import com.projectbeta.engine.AnalysisReport
import com.projectbeta.engine.PoseFrame
import kotlinx.serialization.json.Json

/**
 * Pure conversion logic between the engine's [AnalysisReport]/[PoseFrame] types and the
 * Room-persisted [ClimbRecord]. Kept free of Room/Android so it's unit-testable on the JVM
 * without an in-memory database or device.
 */
object ClimbMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toClimbRecord(
        report: AnalysisReport,
        poseFrames: List<PoseFrame>,
        videoFilePath: String,
        recordedAt: Long
    ): ClimbRecord {
        val payload = ReportPayload(report, poseFrames)
        return ClimbRecord(
            recordedAt = recordedAt,
            videoFilePath = videoFilePath,
            avgSpeed = report.averageSpeed,
            peakSpeed = report.peakSpeed,
            cruxStartMs = report.crux?.startMs,
            cruxEndMs = report.crux?.endMs,
            cruxDifficultyScore = report.crux?.difficultyScore,
            reportJson = json.encodeToString(ReportPayload.serializer(), payload)
        )
    }

    fun toReportPayload(record: ClimbRecord): ReportPayload =
        json.decodeFromString(ReportPayload.serializer(), record.reportJson)
}
