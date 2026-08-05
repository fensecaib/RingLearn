package com.ringlearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ringlearn.app.data.local.entity.ReviewLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insert(log: ReviewLogEntity)

    /** 全部复习时间戳，用于计算连续学习天数 */
    @Query("SELECT reviewedAt FROM review_logs ORDER BY reviewedAt ASC")
    fun observeReviewTimes(): Flow<List<Long>>

    @Query("DELETE FROM review_logs")
    suspend fun clear()
}
