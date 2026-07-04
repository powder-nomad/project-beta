package com.projectbeta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ClimbDao {
    @Insert
    suspend fun insert(record: ClimbRecord): Long

    @Query("SELECT * FROM climb_records ORDER BY recordedAt DESC")
    suspend fun getAll(): List<ClimbRecord>

    @Query("SELECT * FROM climb_records WHERE id = :id")
    suspend fun getById(id: Long): ClimbRecord?
}
