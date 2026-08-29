package com.ledgerly.app.domain.stats

import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.domain.time.Period
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class MonthPoint(
    val yearMonth: YearMonth,
    val income: Long = 0,
    val expense: Long = 0,
) {
    val net: Long get() = income - expense
    fun label(): String = yearMonth.format(DateTimeFormatter.ofPattern("MMM"))
}

data class DayPoint(
    val dayEpochDay: Long,
    val income: Long = 0,
    val expense: Long = 0,
) {
    val net: Long get() = income - expense
}

data class CatStat(
    val category: CategoryEntity,
    val amountMinor: Long,
)

/**
 * All aggregation logic is pure Kotlin operating on locally stored rows.
 * No data ever leaves the device.
 */
object Statistics {

    fun total(txs: List<TransactionWithCategory>, type: TxType, period: Period? = null): Long {
        var sum = 0L
        for (t in txs) {
            if (t.transaction.type != type) continue
            if (period != null && !period.contains(t.transaction.dateEpochDay)) continue
            sum += t.transaction.amountMinor
        }
        return sum
    }

    fun income(txs: List<TransactionWithCategory>, period: Period? = null): Long =
        total(txs, TxType.INCOME, period)

    fun expense(txs: List<TransactionWithCategory>, period: Period? = null): Long =
        total(txs, TxType.EXPENSE, period)

    fun net(txs: List<TransactionWithCategory>, period: Period? = null): Long =
        income(txs, period) - expense(txs, period)

    fun categoryBreakdown(txs: List<TransactionWithCategory>, type: TxType, period: Period? = null): List<CatStat> {
        val map = LinkedHashMap<Long, CatStat>()
        for (t in txs) {
            if (t.transaction.type != type) continue
            if (period != null && !period.contains(t.transaction.dateEpochDay)) continue
            val cat = t.category ?: continue
            val existing = map[cat.id]
            if (existing != null) {
                map[cat.id] = existing.copy(amountMinor = existing.amountMinor + t.transaction.amountMinor)
            } else {
                map[cat.id] = CatStat(cat, t.transaction.amountMinor)
            }
        }
        return map.values.sortedByDescending { it.amountMinor }
    }

    fun topCategories(txs: List<TransactionWithCategory>, type: TxType, n: Int, period: Period? = null): List<CatStat> =
        categoryBreakdown(txs, type, period).take(n)

    fun monthlySeries(txs: List<TransactionWithCategory>, months: List<YearMonth>): List<MonthPoint> {
        val map = HashMap<YearMonth, LongArray>()
        for (m in months) map[m] = longArrayOf(0, 0)
        for (t in txs) {
            val ym = java.time.LocalDate.ofEpochDay(t.transaction.dateEpochDay).let { YearMonth.from(it) }
            val arr = map[ym] ?: continue
            if (t.transaction.type == TxType.INCOME) arr[0] += t.transaction.amountMinor else arr[1] += t.transaction.amountMinor
        }
        return months.map { MonthPoint(it, map[it]!![0], map[it]!![1]) }
    }

    fun dailySeries(txs: List<TransactionWithCategory>, period: Period): List<DayPoint> {
        val days = period.start.toEpochDay()..period.endInclusive.toEpochDay()
        val map = HashMap<Long, LongArray>()
        for (d in days) map[d] = longArrayOf(0, 0)
        for (t in txs) {
            val e = map[t.transaction.dateEpochDay] ?: continue
            if (t.transaction.type == TxType.INCOME) e[0] += t.transaction.amountMinor else e[1] += t.transaction.amountMinor
        }
        return days.map { DayPoint(it, map[it]!![0], map[it]!![1]) }
    }

    fun spentForCategory(txs: List<TransactionWithCategory>, categoryId: Long, period: Period? = null): Long {
        var sum = 0L
        for (t in txs) {
            if (t.transaction.type != TxType.EXPENSE) continue
            if (t.transaction.categoryId != categoryId) continue
            if (period != null && !period.contains(t.transaction.dateEpochDay)) continue
            sum += t.transaction.amountMinor
        }
        return sum
    }
}