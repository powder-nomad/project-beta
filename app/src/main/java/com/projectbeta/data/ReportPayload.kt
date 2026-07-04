package com.projectbeta.data

import com.projectbeta.engine.AnalysisReport
import com.projectbeta.engine.PoseFrame
import kotlinx.serialization.Serializable

/**
 * Everything needed to redraw a past climb's report screen: the computed metrics plus the
 * raw per-frame pose landmarks the skeleton overlay needs (AnalysisReport itself only carries
 * the aggregated trajectory/curves, not per-joint positions).
 */
@Serializable
data class ReportPayload(
    val report: AnalysisReport,
    val poseFrames: List<PoseFrame>
)
