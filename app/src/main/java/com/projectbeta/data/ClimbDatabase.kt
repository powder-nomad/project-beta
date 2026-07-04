package com.projectbeta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

private const val DATABASE_NAME = "project-beta-climbs.db"

@Database(entities = [ClimbRecord::class], version = 1, exportSchema = false)
abstract class ClimbDatabase : RoomDatabase() {
    abstract fun climbDao(): ClimbDao

    companion object {
        @Volatile private var instance: ClimbDatabase? = null

        fun getInstance(context: Context): ClimbDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClimbDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}
