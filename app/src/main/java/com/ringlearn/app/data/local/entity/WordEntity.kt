package com.ringlearn.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单词实体。除了词条本身外，还内嵌了 SM-2 算法的调度状态
 * （repetitions / easeFactor / intervalDays / dueAt），
 * 以及今日学习状态与统计字段。
 */
@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 日文表记（含汉字） */
    val word: String,
    /** 假名注音（平假名） */
    val kana: String,
    /** 中文释义 */
    val meaning: String,
    /** 日文例句 */
    val example: String,
    /** 例句中文翻译 */
    val exampleMeaning: String,
    /** JLPT 等级标签，默认 N2 */
    val jlpt: String = "N2",
    /** 是否已收进生词本 */
    val isFavorite: Boolean = false,
    /** SM-2: 连续正确回答次数 */
    val repetitions: Int = 0,
    /** SM-2: 易度因子 EF，初始 2.5，最低 1.3 */
    val easeFactor: Double = 2.5,
    /** SM-2: 当前复习间隔（天） */
    val intervalDays: Int = 0,
    /** SM-2: 下次复习时间戳（epoch millis） */
    val dueAt: Long = 0L,
    /** 上次复习时间戳 */
    val lastReviewedAt: Long = 0L,
    /** 累计复习次数 */
    val reviewCount: Int = 0,
    /** 累计答对次数 */
    val correctCount: Int = 0,
    /** 累计答错次数 */
    val wrongCount: Int = 0,
    /** 今日是否已学习（当天不再重复出现） */
    val isLearnedToday: Boolean = false,
    /** 首次学习时间戳 */
    val learnedAt: Long = 0L
)
