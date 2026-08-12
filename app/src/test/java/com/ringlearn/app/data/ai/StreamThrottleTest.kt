package com.ringlearn.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamThrottleTest {

    @Test
    fun `first emit always allowed`() {
        val t = StreamThrottle(minIntervalMs = 80, minChars = 16)
        assertTrue(t.shouldEmit(nowMs = 1000L, accLen = 1))
    }

    @Test
    fun `interval gates emission`() {
        val t = StreamThrottle(minIntervalMs = 80, minChars = 16)
        assertTrue(t.shouldEmit(nowMs = 1000L, accLen = 1))
        // 30ms 且只新增 4 字符：不应发射
        assertFalse(t.shouldEmit(nowMs = 1030L, accLen = 5))
        // 距上次 100ms：应发射（即使字符增量仍不足）
        assertTrue(t.shouldEmit(nowMs = 1100L, accLen = 10))
    }

    @Test
    fun `char floor allows emit within interval`() {
        val t = StreamThrottle(minIntervalMs = 80, minChars = 16)
        assertTrue(t.shouldEmit(nowMs = 1000L, accLen = 3))
        // 30ms 内但一次性新增 19 字符：应发射
        assertTrue(t.shouldEmit(nowMs = 1030L, accLen = 22))
    }

    @Test
    fun `char floor respects accumulated delta`() {
        val t = StreamThrottle(minIntervalMs = 80, minChars = 16)
        assertTrue(t.shouldEmit(nowMs = 1000L, accLen = 3))
        // 累计 18 -> 新增 15 < 16，且 50ms < 80ms：不发射
        assertFalse(t.shouldEmit(nowMs = 1050L, accLen = 18))
        // 累计 19 -> 新增 16 >= 16：发射
        assertTrue(t.shouldEmit(nowMs = 1050L, accLen = 19))
    }

    @Test
    fun `emission updates baseline`() {
        val t = StreamThrottle(minIntervalMs = 80, minChars = 16)
        assertTrue(t.shouldEmit(nowMs = 1000L, accLen = 10))
        // 发射后以 10 为基准；新增 15 字符（25-10）仍不足
        assertFalse(t.shouldEmit(nowMs = 1050L, accLen = 25))
    }
    @Test
    fun `adaptive interval grows with length and caps`() {
        val t = StreamThrottle()
        assertEquals(80L, t.currentIntervalMs(100))
        assertEquals(100L, t.currentIntervalMs(300))
        assertEquals(160L, t.currentIntervalMs(1000))
        assertEquals(200L, t.currentIntervalMs(10_000))
    }

    @Test
    fun `long text emits at slower cadence`() {
        val t = StreamThrottle()
        assertTrue(t.shouldEmit(nowMs = 1000L, accLen = 1))
        // 大跳变（字符下限满足）→ 立即发射，基准更新到 1000
        assertTrue(t.shouldEmit(nowMs = 1100L, accLen = 1000))
        // 长文本自适应间隔 160ms：50ms 且仅新增 10 字符 → 不发射
        assertFalse(t.shouldEmit(nowMs = 1150L, accLen = 1010))
        // 200ms >= 160ms → 发射
        assertTrue(t.shouldEmit(nowMs = 1300L, accLen = 1020))
    }
}

