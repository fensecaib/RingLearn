package com.ringlearn.app.data.ai

/**
 * 流式 UI 更新节流：把「每 token 一次」的增量合并为「距上次发射 ≥ 间隔 或 累计新增 ≥ 字符数下限」
 * 才允许发射，显著降低主线程重组/Markdown 重解析频率（弱机流式期帧卡顿的主因）。
 *
 * 间隔采用**自适应**：随累积文本长度增长（[intervalPerStepMs]/[charsPerStep]），上限 [maxIntervalMs]——
 * 短回复保持 ~80ms 灵敏，长回复最高 200ms（流式文本平滑；行内渲染成本低）。
 * 调用方负责在流正常结束后强制 flush 最终文本。
 *
 * 非线程安全：仅在同一协程（仓库 Mutex 保护）内使用。
 */
internal class StreamThrottle(
    private val minIntervalMs: Long = 80L,
    private val minChars: Int = 16,
    private val intervalPerStepMs: Long = 20L,
    private val charsPerStep: Int = 250,
    private val maxIntervalMs: Long = 200L
) {
    private var lastEmitAtMs: Long = 0L
    private var lastEmittedLen: Int = 0

    /** 当前自适应间隔（毫秒）：随累积长度 [accLen] 增长。 */
    internal fun currentIntervalMs(accLen: Int): Long =
        (minIntervalMs + (accLen / charsPerStep) * intervalPerStepMs).coerceAtMost(maxIntervalMs)

    /**
     * 判断累计长度 [accLen]（单调不减）是否值得发射，并在发射时记录本次状态。
     * 首次调用总是返回 true（首个增量立即上屏）。
     */
    fun shouldEmit(nowMs: Long, accLen: Int): Boolean {
        if (lastEmittedLen == 0) {
            lastEmitAtMs = nowMs
            lastEmittedLen = accLen
            return true
        }
        val intervalOk = nowMs - lastEmitAtMs >= currentIntervalMs(accLen)
        val charsOk = accLen - lastEmittedLen >= minChars + accLen / charsPerStep
        if (intervalOk || charsOk) {
            lastEmitAtMs = nowMs
            lastEmittedLen = accLen
            return true
        }
        return false
    }
}
