package com.ringlearn.app.data.repository

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/** startOfDay（本地时区零点）的边界用例：午夜归属、跨天、毫秒精度。 */
class StartOfDayTest {

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, ms: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, mo)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, mi)
            set(Calendar.SECOND, s)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis

    private fun startOf(ts: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun noon_is_same_day() {
        val noon = at(2026, 7, 11, 12, 0, 0, 0)
        assertEquals(startOf(noon), startOfDay(noon))
    }

    @Test
    fun midnight_is_same_day() {
        val mid = at(2026, 7, 11, 0, 0, 0, 0)
        assertEquals(mid, startOfDay(mid))
    }

    @Test
    fun just_before_midnight_is_previous_day() {
        val midnight = at(2026, 7, 11, 0, 0, 0, 0)
        val justBefore = midnight - 1L
        assertEquals(startOf(at(2026, 7, 10, 12, 0, 0, 0)), startOfDay(justBefore))
    }

    @Test
    fun end_of_day_is_same_day() {
        val endOfDay = at(2026, 7, 11, 23, 59, 59, 999)
        assertEquals(startOf(at(2026, 7, 11, 12, 0, 0, 0)), startOfDay(endOfDay))
    }
}
