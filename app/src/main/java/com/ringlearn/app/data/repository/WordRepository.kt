package com.ringlearn.app.data.repository

import android.content.Context
import com.ringlearn.app.data.local.dao.ReviewLogDao
import com.ringlearn.app.data.local.dao.WordDao
import com.ringlearn.app.data.local.entity.ReviewLogEntity
import com.ringlearn.app.data.local.entity.WordEntity
import com.ringlearn.app.data.seed.SeedWordParser
import com.ringlearn.app.domain.algorithm.Sm2Scheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/** 单词与学习进度的统一仓库，屏蔽 Room / 本地词库细节。 */
@Singleton
class WordRepository @Inject constructor(
    private val wordDao: WordDao,
    private val reviewLogDao: ReviewLogDao,
    @ApplicationContext private val context: Context
) {

    /** 首页看板统计 */
    data class HomeStats(
        val totalWords: Int = 0,
        val learnedToday: Int = 0,
        /** 待复习（已到期）数量 */
        val dueCount: Int = 0,
        /** 已学过（reviewCount > 0）数量 */
        val masteredCount: Int = 0,
        val favoriteCount: Int = 0,
        /** 连续学习天数 */
        val streakDays: Int = 0,
        /** 新词剩余数量（reviewCount == 0） */
        val newWordCount: Int = 0,
        /** 词库是否已经就绪（种子数据加载完成） */
        val isReady: Boolean = false
    )

    /** 仓库内 scope：单例应用级生命周期（与 HapticManager 同模式），承载共享的分钟级 ticker */
    private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 每分钟推进一次：让“待复习”数量能随单词到期时间自动刷新（而不依赖固定时间戳）。
     * shareIn(replay = 1)：下游 dueCount / learnedTodayCount 共用同一个 60 秒循环
     * （优化前每个订阅者各起一个独立循环），新订阅者立即拿到最新时间戳；无订阅时循环停摆。
     */
    private val nowTicker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }.shareIn(tickerScope, started = SharingStarted.WhileSubscribed(), replay = 1)

    private val dueCount: Flow<Int> = nowTicker.flatMapLatest { wordDao.observeDueCount(it) }

    /** 今日已学新词数：以 60s 粒度随 nowTicker 推进“今日零点”，跨天自动归零 */
    private val learnedTodayCount: Flow<Int> =
        nowTicker.flatMapLatest { wordDao.observeLearnedTodayCount(startOfDay(it)) }

    val homeStats: Flow<HomeStats> = combine(
        combine(
            wordDao.observeTotalCount(),
            learnedTodayCount
        ) { total, learned -> total to learned },
        combine(
            dueCount,
            wordDao.observeMasteredCount()
        ) { due, mastered -> due to mastered },
        combine(
            wordDao.observeFavoriteCount(),
            reviewLogDao.observeReviewTimes()
        ) { fav, reviewTimes -> fav to reviewTimes }
    ) { (total, learned), (due, mastered), (fav, reviewTimes) ->
        HomeStats(
            totalWords = total,
            learnedToday = learned,
            dueCount = due,
            masteredCount = mastered,
            favoriteCount = fav,
            streakDays = computeStreakDays(reviewTimes),
            // 未学过的词 = 总数 - 已学过(reviewCount>0)；已学过但已到期的词不重复计入新词
            newWordCount = (total - mastered - due).coerceAtLeast(0),
            isReady = total > 0
        )
    }

    /** 首次启动时把 assets 内置词库写入 Room（幂等）。 */
    suspend fun ensureSeeded() {
        if (wordDao.count() == 0) {
            val json = withContext(Dispatchers.IO) {
                context.assets.open("jlpt_n2_words.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            wordDao.insertAll(SeedWordParser.parse(json))
        }
    }

    /** 组装一轮学习队列：优先到期复习，再用新词补齐。 */
    suspend fun getStudyRound(limit: Int): List<WordEntity> {
        val now = System.currentTimeMillis()
        val due = wordDao.getDueWords(limit, now)
        if (due.size >= limit) return due
        val newWords = wordDao.getNewWords(limit - due.size)
        return due + newWords
    }

    suspend fun getRandomWords(count: Int): List<WordEntity> = wordDao.getRandomWords(count)

    /** 词库中出现过的全部唯一字符（用于构建手写识别模板，仅词库规模，轻量） */
    suspend fun getAllCharacters(): Set<Char> = withContext(Dispatchers.IO) {
        wordDao.getAllWords()
            .flatMap { (it.word + it.kana).asIterable() }
            .toSet()
    }

    fun observeFavorites(query: String): Flow<List<WordEntity>> = wordDao.observeFavorites(escapeLike(query))

    /** 查词：按 表记/假名/释义 模糊匹配（已做 LIKE 通配符转义） */
    fun observeLookup(query: String): Flow<List<WordEntity>> = wordDao.observeLookup(escapeLike(query))

    /** IME 转换候选：按假名前缀匹配词库（kana 不含 LIKE 通配符，仍做转义兜底） */
    suspend fun searchCandidates(kana: String): List<WordEntity> =
        wordDao.getCandidatesByKana(escapeLike(kana))
    suspend fun setFavorite(wordId: Long, favorite: Boolean) {
        val word = wordDao.getWord(wordId) ?: return
        wordDao.update(word.copy(isFavorite = favorite))
    }

    /**
     * 记录一次复习：用 SM-2 计算新调度状态并落库。
     * @param favorite 是否同时收进生词本（上滑手势）
     */
    suspend fun recordReview(word: WordEntity, quality: Int, favorite: Boolean = false): WordEntity {
        val now = System.currentTimeMillis()
        val sm2 = Sm2Scheduler.review(word, quality, now)
        val updated = word.copy(
            repetitions = sm2.repetitions,
            easeFactor = sm2.easeFactor,
            intervalDays = sm2.intervalDays,
            dueAt = sm2.dueAt,
            lastReviewedAt = now,
            reviewCount = word.reviewCount + 1,
            correctCount = word.correctCount + if (quality >= 3) 1 else 0,
            wrongCount = word.wrongCount + if (quality < 3) 1 else 0,
            isFavorite = word.isFavorite || favorite,
            isLearnedToday = true,
            learnedAt = if (word.learnedAt == 0L) now else word.learnedAt
        )
        wordDao.update(updated)
        reviewLogDao.insert(
            ReviewLogEntity(
                wordId = word.id,
                quality = quality,
                reviewedAt = now,
                intervalDays = sm2.intervalDays,
                easeFactor = sm2.easeFactor
            )
        )
        return updated
    }

    /** 重置所有学习进度（保留词库词条）。 */
    suspend fun resetAllProgress() {
        wordDao.resetAllProgress()
        reviewLogDao.clear()
    }

    /** 连续学习天数：今天或昨天起往前连续有复习记录的天然数。 */
    private fun computeStreakDays(reviewTimes: List<Long>): Int {
        if (reviewTimes.isEmpty()) return 0
        val daySet = reviewTimes.mapTo(mutableSetOf()) { startOfDay(it) }
        val today = startOfDay(System.currentTimeMillis())
        var cursor = if (today in daySet) today else today - DAY_MILLIS
        var streak = 0
        while (cursor in daySet) {
            streak++
            cursor -= DAY_MILLIS
        }
        return streak
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}

/** 当天零点（epoch millis，本地时区）。 */
internal fun startOfDay(epochMillis: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** SQL LIKE 通配符转义（`\` `%` `_`），供查词 / 生词本 / IME 候选查询共用（配合 ESCAPE '\'）。 */
internal fun escapeLike(query: String): String =
    query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")



