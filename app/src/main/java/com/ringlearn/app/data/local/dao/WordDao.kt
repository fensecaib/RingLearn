package com.ringlearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ringlearn.app.data.local.entity.WordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT COUNT(*) FROM words")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE isLearnedToday = 1")
    fun observeLearnedTodayCount(): Flow<Int>

    /** 待复习数量：已学过（reviewCount > 0）且已到期（dueAt <= now）且今日未学 */
    @Query("SELECT COUNT(*) FROM words WHERE isLearnedToday = 0 AND reviewCount > 0 AND dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    /** 已掌握：至少复习过 1 次的单词数量 */
    @Query("SELECT COUNT(*) FROM words WHERE reviewCount > 0")
    fun observeMasteredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE isFavorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    @Query("SELECT * FROM words WHERE isLearnedToday = 0 AND reviewCount > 0 AND dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun getDueWords(limit: Int, now: Long): List<WordEntity>

    /** 新词：从未学过 */
    @Query("SELECT * FROM words WHERE isLearnedToday = 0 AND reviewCount = 0 ORDER BY id ASC LIMIT :limit")
    suspend fun getNewWords(limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWord(id: Long): WordEntity?

    @Query("SELECT * FROM words ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomWords(limit: Int): List<WordEntity>

    @Query(
        "SELECT * FROM words WHERE isFavorite = 1 AND " +
            "(word LIKE '%' || :query || '%' OR kana LIKE '%' || :query || '%' OR meaning LIKE '%' || :query || '%') " +
            "ORDER BY id ASC"
    )
    fun observeFavorites(query: String): Flow<List<WordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

    @Update
    suspend fun update(word: WordEntity)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    /** 重置所有学习进度（保留词条本身） */
    @Query(
        "UPDATE words SET repetitions = 0, easeFactor = 2.5, intervalDays = 0, dueAt = 0, " +
            "lastReviewedAt = 0, reviewCount = 0, correctCount = 0, wrongCount = 0, " +
            "isFavorite = 0, isLearnedToday = 0, learnedAt = 0"
    )
    suspend fun resetAllProgress()
}
