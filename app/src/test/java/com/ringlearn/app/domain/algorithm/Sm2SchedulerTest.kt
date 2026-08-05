package com.ringlearn.app.domain.algorithm

import com.ringlearn.app.data.local.entity.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SM-2 算法核心行为验证 */
class Sm2SchedulerTest {

    private val now = 1_700_000_000_000L

    private fun freshWord() = WordEntity(
        id = 1L,
        word = "環境",
        kana = "かんきょう",
        meaning = "环境",
        example = "環境が悪い。",
        exampleMeaning = "环境不好。"
    )

    @Test
    fun firstCorrectAnswerSchedulesOneDayLater() {
        val result = Sm2Scheduler.review(freshWord(), quality = 5, now = now)
        assertEquals(1, result.repetitions)
        assertEquals(1, result.intervalDays)
        assertEquals(now + Sm2Scheduler.DAY_MILLIS, result.dueAt)
        assertTrue(result.easeFactor > 2.5) // quality=5 会提升 EF
    }

    @Test
    fun secondCorrectAnswerSchedulesSixDaysLater() {
        val first = Sm2Scheduler.review(freshWord(), quality = 5, now = now)
        val word = freshWord().copy(
            repetitions = first.repetitions,
            easeFactor = first.easeFactor,
            intervalDays = first.intervalDays
        )
        val second = Sm2Scheduler.review(word, quality = 5, now = now)
        assertEquals(2, second.repetitions)
        assertEquals(6, second.intervalDays)
    }

    @Test
    fun intervalGrowsByEaseFactorOnLaterReviews() {
        var word = freshWord()
        var lastInterval = 0
        repeat(5) {
            val r = Sm2Scheduler.review(word, quality = 4, now = now)
            word = word.copy(
                repetitions = r.repetitions,
                easeFactor = r.easeFactor,
                intervalDays = r.intervalDays
            )
            lastInterval = r.intervalDays
        }
        assertTrue(lastInterval > 6)
    }

    @Test
    fun wrongAnswerResetsRepetitionsAndSchedulesOneDay() {
        val first = Sm2Scheduler.review(freshWord(), quality = 5, now = now)
        val word = freshWord().copy(
            repetitions = first.repetitions,
            easeFactor = first.easeFactor,
            intervalDays = first.intervalDays
        )
        val wrong = Sm2Scheduler.review(word, quality = 2, now = now)
        assertEquals(0, wrong.repetitions)
        assertEquals(1, wrong.intervalDays)
        assertTrue(wrong.easeFactor < first.easeFactor)
    }

    @Test
    fun easeFactorNeverDropsBelowMin() {
        var word = freshWord()
        repeat(10) {
            val r = Sm2Scheduler.review(word, quality = 0, now = now)
            word = word.copy(easeFactor = r.easeFactor, repetitions = r.repetitions, intervalDays = r.intervalDays)
            assertTrue(r.easeFactor >= 1.3)
        }
    }

    @Test
    fun qualityMappingForGestures() {
        assertEquals(5, Sm2Scheduler.qualityFor(true))
        assertEquals(2, Sm2Scheduler.qualityFor(false))
    }
}
