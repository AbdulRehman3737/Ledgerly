package com.ledgerly.app.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodTest {

    private val now = LocalDate.of(2026, 8, 29)

    @Test
    fun `this month covers full month`() {
        val p = Periods.of(PeriodType.THIS_MONTH, now = now)
        assertEquals(LocalDate.of(2026, 8, 1), p.start)
        assertEquals(LocalDate.of(2026, 8, 31), p.endInclusive)
        assertTrue(p.contains(LocalDate.of(2026, 8, 15)))
    }

    @Test
    fun `last month is previous two calendar months`() {
        val p = Periods.of(PeriodType.LAST_3_MONTHS, now = now)
        assertEquals(LocalDate.of(2026, 6, 1), p.start)
        assertEquals(LocalDate.of(2026, 8, 31), p.endInclusive)
    }

    @Test
    fun `monthsInside computes month count`() {
        val p = Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))
        assertEquals(3, p.monthsInside())
        assertEquals(3, p.bucketCount())
    }

    @Test
    fun `bucketCount falls back to days`() {
        val p = Period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        assertEquals(1, p.monthsInside())
        assertEquals(31, p.bucketCount())
    }

    @Test
    fun `monthStarts lists covering months`() {
        val p = Period(LocalDate.of(2025, 12, 15), LocalDate.of(2026, 2, 10))
        val starts = Periods.monthStarts(p)
        assertEquals(listOf(
            LocalDate.of(2025, 12, 1),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 1),
        ), starts)
    }

    @Test
    fun `custom period sorts when reversed`() {
        val p = Periods.of(PeriodType.CUSTOM, now = now, customStart = LocalDate.of(2026, 9, 5), customEnd = LocalDate.of(2026, 9, 1))
        assertEquals(LocalDate.of(2026, 9, 1), p.start)
        assertEquals(LocalDate.of(2026, 9, 5), p.endInclusive)
    }

    @Test
    fun `contains works on epoch day`() {
        val p = Period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        assertTrue(p.contains(LocalDate.of(2026, 8, 31).toEpochDay()))
        assertTrue(!p.contains(LocalDate.of(2026, 9, 1).toEpochDay()))
    }
}