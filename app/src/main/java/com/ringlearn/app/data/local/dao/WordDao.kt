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

    /** 今日已学新词：首次学习时间（learnedAt）落在今日零点之后 */
    @Query("SELECT COUNT(*) FROM words WHERE learnedAt >= :startOfToday")
    fun observeLearnedTodayCount(startOfToday: Long): Flow<Int>

    /** 待复习数量：已学过（reviewCount > 0）且已到期（dueAt <= now） */
    @Query("SELECT COUNT(*) FROM words WHERE reviewCount > 0 AND dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    /** 已掌握：至少复习过 1 次的单词数量 */
    @Query("SELECT COUNT(*) FROM words WHERE reviewCount > 0")
    fun observeMasteredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE isFavorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    @Query("SELECT * FROM words WHERE reviewCount > 0 AND dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun getDueWords(limit: Int, now: Long): List<WordEntity>

    /** 新词：从未复习过（reviewCount == 0） */
    @Query("SELECT * FROM words WHERE reviewCount = 0 ORDER BY id ASC LIMIT :limit")
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

    /** 查词：按 表记/假名/释义 模糊匹配（LIKE 已做 % _ 转义，配合 ESCAPE '\\'） */
    @Query(
        "SELECT * FROM words WHERE " +
            "(word LIKE '%' || :q || '%' ESCAPE '\\' OR kana LIKE '%' || :q || '%' ESCAPE '\\' OR meaning LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY CASE " +
            "WHEN word = :q THEN 0 " +
            "WHEN kana = :q THEN 1 " +
            "WHEN word LIKE :q || '%' ESCAPE '\\' THEN 2 " +
            "ELSE 3 END, id ASC LIMIT 100"
    )
    fun observeLookup(q: String): Flow<List<WordEntity>>

    /** IME 转换候选：按假名前缀匹配，优先完全匹配，再按 JLPT 等级与词库顺序（最多 20 条） */
    @Query(
        "SELECT * FROM words WHERE kana LIKE :kana || '%' ESCAPE '\\' " +
            "ORDER BY CASE WHEN kana = :kana THEN 0 ELSE 1 END, " +
            "CASE jlpt WHEN 'N2' THEN 0 WHEN 'N1' THEN 1 ELSE 2 END, id ASC LIMIT 20"
    )
    suspend fun getCandidatesByKana(kana: String): List<WordEntity>

    @Query("SELECT * FROM words")
    suspend fun getAllWords(): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

    @Update
    suspend fun update(word: WordEntity)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    /** 词库中全部 (词, 假名) 键，供增量种子去重（仅词库规模，轻量）。 */
    @Query("SELECT word, kana FROM words")
    suspend fun getWordKeys(): List<WordKey>

    /** 指定 JLPT 等级的词条数（判断该等级是否已种入）。 */
    @Query("SELECT COUNT(*) FROM words WHERE jlpt = :level")
    suspend fun countByJlpt(level: String): Int

    /** 重置所有学习进度（保留词条本身） */
    @Query(
        "UPDATE words SET repetitions = 0, easeFactor = 2.5, intervalDays = 0, dueAt = 0, " +
            "lastReviewedAt = 0, reviewCount = 0, correctCount = 0, wrongCount = 0, " +
            "isFavorite = 0, isLearnedToday = 0, learnedAt = 0"
    )
    suspend fun resetAllProgress()
}

/** (word, kana) 轻量投影，避免增量种子去重时加载整行。 */
data class WordKey(val word: String, val kana: String)





