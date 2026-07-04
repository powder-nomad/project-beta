package com.projectbeta.data

import android.content.Context
import com.projectbeta.engine.AnalysisReport
import com.projectbeta.engine.PoseFrame

class ClimbRepository(context: Context) {
    private val dao = ClimbDatabase.getInstance(context).climbDao()

    suspend fun save(
        report: AnalysisReport,
        poseFrames: List<PoseFrame>,
        videoFilePath: String,
        recordedAt: Long = System.currentTimeMillis()
    ): Long {
        val record = ClimbMapper.toClimbRecord(report, poseFrames, videoFilePath, recordedAt)
        return dao.insert(record)
    }

    suspend fun getAll(): List<ClimbRecord> = dao.getAll()

    suspend fun getById(id: Long): ClimbRecord? = dao.getById(id)
}
