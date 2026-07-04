package com.projectbeta.history

/** Lightweight view-model for one history card, precomputed off the main thread when the list
 * loads so the RecyclerView adapter never needs to parse JSON while binding/scrolling. */
data class ClimbCardData(
    val id: Long,
    val recordedAt: Long,
    val videoFilePath: String,
    val peakSpeed: Double,
    val cruxDifficultyScore: Double?,
    val cruxStartMs: Long?,
    val cruxEndMs: Long?,
    val speedSparkline: List<Double>
)
