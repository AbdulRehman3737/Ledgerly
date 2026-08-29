package com.ledgerly.app.domain.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class PeriodType(val displayName: String) {
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_YEAR("This year"),
    CUSTOM("Custom range"),
}

data class Period(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    fun contains(epochDay: Long): Boolean =
        epochDay >= start.toEpochDay() && epochDay <= endInclusive.toEpochDay()

    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    fun monthsInside(): Int {
        val from = YearMonth.from(start)
        val to = YearMonth.from(endInclusive)
        return (to.year * 12 + to.monthValue) - (from.year * 12 + from.monthValue) + 1
    }

    fun bucketCount(): Int = when {
        monthsInside() > 1 -> monthsInside()
        else -> endInclusive.toEpochDay().toInt() - start.toEpochDay().toInt() + 1
    }
}

object Periods {

    fun of(
        type: PeriodType,
        now: LocalDate = LocalDate.now(),
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null,
    ): Period {
        return when (type) {
            PeriodType.THIS_WEEK -> {
                val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                Period(monday, monday.plusDays(6))
            }
            PeriodType.THIS_MONTH -> Period(now.withDayOfMonth(1), now.with(TemporalAdjusters.lastDayOfMonth()))
            PeriodType.LAST_MONTH -> {
                val lm = YearMonth.from(now).minusMonths(1)
                Period(lm.atDay(1), lm.atEndOfMonth())
            }
            PeriodType.LAST_3_MONTHS -> Period(now.minusMonths(2).withDayOfMonth(1), now.with(TemporalAdjusters.lastDayOfMonth()))
            PeriodType.THIS_YEAR -> Period(now.withDayOfYear(1), now.with(TemporalAdjusters.lastDayOfYear()))
            PeriodType.CUSTOM -> {
                val s = customStart ?: now.minusDays(29)
                val e = customEnd ?: now
                if (s.isAfter(e)) Period(e, s) else Period(s, e)
            }
        }
    }

    /** Month start dates covering [period] (<= 24 buckets). */
    fun monthStarts(period: Period): List<LocalDate> {
        val first = YearMonth.from(period.start)
        val last = YearMonth.from(period.endInclusive)
        var cur = first
        val out = mutableListOf<LocalDate>()
        while (!cur.isAfter(last) && out.size < 24) {
            out += cur.atDay(1)
            cur = cur.plusMonths(1)
        }
        return out
    }
}