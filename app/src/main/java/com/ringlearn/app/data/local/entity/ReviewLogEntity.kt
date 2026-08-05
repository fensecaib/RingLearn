package com.ringlearn.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 复习日志。每次对单词进行复习（滑动卡片）都会写入一条记录，
 * 用于统计连续学习天数等数据。
 */
@Entity(tableName = "review_logs")
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val wordId: Long,
    /** SM-2 评分 0..5 */
    val quality: Int,
    /** 复习时间戳 */
    val reviewedAt: Long,
    /** 本次更新后的间隔（天） */
    val intervalDays: Int,
    /** 本次更新后的易度因子 */
    val easeFactor: Double
)
