package com.projectbeta.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per completed analysis. Columns here are exactly what the history list needs to
 * render and sort cards without touching [reportJson] — the full report (curves, trajectory,
 * pose frames) is deserialized lazily, only when a specific climb's Report screen is opened.
 */
@Entity(tableName = "climb_records")
data class ClimbRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAt: Long,
    val videoFilePath: String,
    val avgSpeed: Double,
    val peakSpeed: Double,
    val cruxStartMs: Long?,
    val cruxEndMs: Long?,
    val cruxDifficultyScore: Double?,
    val reportJson: String
)
