package com.ledgerly.app.domain.stats

import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.TransactionEntity
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.domain.time.Period
import com.ledgerly.app.domain.time.PeriodType
import com.ledgerly.app.domain.time.Periods
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatisticsTest {

    private fun cat(id: Long, name: String, type: TxType) =
        CategoryEntity(id = id, profileId = 1, name = name, type = type, icon = "x", colorArgb = 1, isSystem = false)

    private fun tx(id: Long, catId: Long, type: TxType, amount: Long, date: LocalDate) =
        TransactionWithCategory(
            TransactionEntity(
                id = id, profileId = 1, categoryId = catId, type = type,
                amountMinor = amount, dateEpochDay = date.toEpochDay(), createdAt = 0,
            ),
            null,
        )

    @Test
    fun `income and expense are summed`() {
        val txs = listOf(
            tx(1, 1, TxType.INCOME, 1000, LocalDate.of(2026, 8, 10)),
            tx(2, 2, TxType.EXPENSE, 400, LocalDate.of(2026, 8, 11)),
            tx(3, 2, TxType.EXPENSE, 100, LocalDate.of(2026, 8, 12)),
        )
        assertEquals(1000L, Statistics.income(txs))
        assertEquals(500L, Statistics.expense(txs))
        assertEquals(500L, Statistics.net(txs))
    }

    @Test
    fun `period filter excludes outside range`() {
        val txs = listOf(
            tx(1, 1, TxType.INCOME, 1000, LocalDate.of(2026, 7, 30)),
            tx(2, 1, TxType.INCOME, 2000, LocalDate.of(2026, 8, 5)),
        )
        val august = Periods.of(PeriodType.THIS_MONTH, now = LocalDate.of(2026, 8, 29))
        assertEquals(2000L, Statistics.income(txs, august))
    }

    @Test
    fun `spentForCategory tallies only that expense`() {
        val food = cat(1, "Food", TxType.EXPENSE)
        val txs = listOf(
            TransactionWithCategory(
                TransactionEntity(id = 1, profileId = 1, categoryId = 1, type = TxType.EXPENSE, amountMinor = 300, dateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(), createdAt = 0),
                food,
            ),
            tx(2, 2, TxType.EXPENSE, 500, LocalDate.of(2026, 8, 2)),
        )
        assertEquals(300L, Statistics.spentForCategory(txs, 1, Periods.of(PeriodType.THIS_MONTH, now = LocalDate.of(2026, 8, 29))))
    }

    @Test
    fun `categoryBreakdown groups and sorts`() {
        val a = cat(1, "A", TxType.EXPENSE)
        val b = cat(2, "B", TxType.EXPENSE)
        val txs = listOf(
            TransactionWithCategory(TransactionEntity(1, 1, 1, TxType.EXPENSE, 100, 0, createdAt = 0), a),
            TransactionWithCategory(TransactionEntity(2, 1, 2, TxType.EXPENSE, 900, 0, createdAt = 0), b),
            TransactionWithCategory(TransactionEntity(3, 1, 1, TxType.EXPENSE, 200, 0, createdAt = 0), a),
        )
        val stats = Statistics.categoryBreakdown(txs, TxType.EXPENSE)
        assertEquals(listOf(2L, 1L), stats.map { it.category.id })
        assertEquals(300L, stats[1].amountMinor)
    }

    @Test
    fun `monthlySeries aligns to given months`() {
        val txs = listOf(
            tx(1, 1, TxType.INCOME, 1000, LocalDate.of(2026, 1, 15)),
            tx(2, 2, TxType.EXPENSE, 300, LocalDate.of(2026, 2, 10)),
            tx(3, 1, TxType.INCOME, 500, LocalDate.of(2026, 3, 1)),
        )
        val months = listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3))
        val series = Statistics.monthlySeries(txs, months)
        assertEquals(1000L, series[0].income)
        assertEquals(300L, series[1].expense)
        assertEquals(500L, series[2].income)
        assertEquals(1000L, series[0].net)
    }
}