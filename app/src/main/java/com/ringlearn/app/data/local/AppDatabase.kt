package com.ringlearn.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ringlearn.app.data.local.dao.ReviewLogDao
import com.ringlearn.app.data.local.dao.WordDao
import com.ringlearn.app.data.local.entity.ReviewLogEntity
import com.ringlearn.app.data.local.entity.WordEntity

@Database(
    entities = [WordEntity::class, ReviewLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun reviewLogDao(): ReviewLogDao
}
