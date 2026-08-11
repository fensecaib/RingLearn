package com.ringlearn.app.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextStatsTest {

    @Test
    fun `empty stats`() {
        assertEquals("0 轮 · 0 chars", formatContextStats(ContextStats(0, 0)))
    }

    @Test
    fun `thousands formatted with k`() {
        assertEquals("12 轮 · 3.2k chars", formatContextStats(ContextStats(12, 3200)))
    }

    @Test
    fun `millions formatted with M`() {
        assertEquals("3 轮 · 1.5M chars", formatContextStats(ContextStats(3, 1_500_000)))
    }

    @Test
    fun `below 1000 plain number`() {
        assertEquals("1 轮 · 999 chars", formatContextStats(ContextStats(1, 999)))
    }
}
