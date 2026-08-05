package com.ringlearn.app.domain.algorithm

import com.ringlearn.app.data.local.entity.WordEntity
import kotlin.math.roundToInt

/**
 * SM-2 (SuperMemo 2) 间隔重复算法实现。
 *
 * 输入：单词当前调度状态 + 本次记忆质量评分 quality (0..5)
 * 输出：新的调度状态（repetitions / easeFactor / intervalDays / dueAt）
 *
 * 规则：
 *  - quality >= 3（回答正确）：
 *      repetitions 依次为 1、2 时间隔为 1 天、6 天；
 *      之后间隔 = 上次间隔 × 易度因子 EF（四舍五入）。
 *  - quality < 3（回答错误）：repetitions 归零，间隔重置为 1 天（次日再复习）。
 *  - EF = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))，且 EF 不低于 1.3。
 */
object Sm2Scheduler {

    const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    const val DEFAULT_EASE_FACTOR = 2.5
    const val MIN_EASE_FACTOR = 1.3

    data class Sm2Result(
        val repetitions: Int,
        val easeFactor: Double,
        val intervalDays: Int,
        val dueAt: Long
    )

    /**
     * @param current 单词当前调度状态
     * @param quality 0..5 的记忆质量评分
     * @param now     当前时间戳（便于测试注入）
     */
    fun review(current: WordEntity, quality: Int, now: Long = System.currentTimeMillis()): Sm2Result {
        val q = quality.coerceIn(0, 5)
        var repetitions = current.repetitions
        var ease = current.easeFactor.ifNaN(DEFAULT_EASE_FACTOR)
        var interval = current.intervalDays

        if (q >= 3) {
            repetitions += 1
            interval = when (repetitions) {
                1 -> 1
                2 -> 6
                else -> (interval.toDouble() * ease).roundToInt().coerceAtLeast(1)
            }
        } else {
            repetitions = 0
            interval = 1
        }

        ease += (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        ease = ease.coerceAtLeast(MIN_EASE_FACTOR)

        return Sm2Result(
            repetitions = repetitions,
            easeFactor = ease,
            intervalDays = interval,
            dueAt = now + interval * DAY_MILLIS
        )
    }

    /** 将 App 内的手势映射为 SM-2 质量评分 */
    fun qualityFor(known: Boolean): Int = if (known) 5 else 2

    private fun Double.ifNaN(fallback: Double): Double = if (this.isNaN() || this.isInfinite()) fallback else this
}
