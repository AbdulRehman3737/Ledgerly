package com.ledgerly.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerly.app.domain.money.Money
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.domain.stats.CatStat
import com.ledgerly.app.domain.stats.Statistics
import com.ledgerly.app.domain.time.Period
import com.ledgerly.app.domain.time.PeriodType
import com.ledgerly.app.domain.time.Periods
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.components.BarGroup
import com.ledgerly.app.ui.components.DatePickerDialog
import com.ledgerly.app.ui.components.DonutChart
import com.ledgerly.app.ui.components.DonutSlice
import com.ledgerly.app.ui.components.EmptyState
import com.ledgerly.app.ui.components.FractionBar
import com.ledgerly.app.ui.components.GroupedBarChart
import com.ledgerly.app.ui.components.ProfileChip
import com.ledgerly.app.ui.components.TrendLineChart
import com.ledgerly.app.ui.theme.ExpenseRedDark
import com.ledgerly.app.ui.theme.IncomeGreenDark
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(
    vm: LedgerViewModel,
    darkTheme: Boolean,
    onProfileClick: () -> Unit,
) {
    val profile by vm.currentProfile.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val currency = vm.currency()

    var periodType by rememberSaveable { mutableStateOf(PeriodType.THIS_MONTH) }
    var customStartDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEndDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    val customStart = customStartDay?.let { LocalDate.ofEpochDay(it) }
    val customEnd = customEndDay?.let { LocalDate.ofEpochDay(it) }
    val period = Periods.of(periodType, customStart = customStart, customEnd = customEnd)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Analytics",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (profile != null) {
                ProfileChip(profile!!, onClick = onProfileClick)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodType.entries.forEach { t ->
                FilterChip(
                    selected = periodType == t,
                    onClick = { periodType = t },
                    label = { Text(t.displayName) },
                )
            }
        }

        if (periodType == PeriodType.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) {
                    Text("Start: " + (customStart?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "today-29d"))
                }
                TextButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) {
                    Text("End: " + (customEnd?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "today"))
                }
            }
        }

        if (txs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.BarChart,
                title = "No data to analyze",
                message = "Once you add transactions, your charts and breakdowns will appear here.",
                modifier = Modifier.padding(top = 48.dp),
            )
        } else {
            SummaryCard(period = period, txs = txs, currency)
            ComparisonCard(period = period, txs = txs, currency = currency, darkTheme = darkTheme)
            CategoryCard(
                title = "Spending by category",
                typeLabel = "Expenses",
                stats = Statistics.categoryBreakdown(txs, TxType.EXPENSE, period),
                currency = currency,
            )
            CategoryCard(
                title = "Income by category",
                typeLabel = "Income",
                stats = Statistics.categoryBreakdown(txs, TxType.INCOME, period),
                currency = currency,
            )
            NetTrendCard(period = period, txs = txs, currency = currency, darkTheme = darkTheme)
        }
        Spacer(Modifier.height(120.dp))
    }

    if (pickStart) {
        DatePickerDialog(
            initialDate = customStart ?: LocalDate.now().minusDays(29),
            onDateSelected = { customStartDay = it.toEpochDay() },
            onDismiss = { pickStart = false },
        )
    }
    if (pickEnd) {
        DatePickerDialog(
            initialDate = customEnd ?: LocalDate.now(),
            onDateSelected = { customEndDay = it.toEpochDay() },
            onDismiss = { pickEnd = false },
        )
    }
}

@Composable
private fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
private fun SummaryCard(period: Period, txs: List<com.ledgerly.app.data.db.TransactionWithCategory>, currency: com.ledgerly.app.domain.money.CurrencyInfo) {
    val income = Statistics.income(txs, period)
    val expense = Statistics.expense(txs, period)
    val net = income - expense
    StatsCard(title = "Summary · " + rangeLabel(period)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SummaryCell("Income", income, IncomeGreenDark, currency, Modifier.weight(1f))
            SummaryCell("Expenses", expense, ExpenseRedDark, currency, Modifier.weight(1f))
            SummaryCell("Net", net, if (net >= 0) IncomeGreenDark else ExpenseRedDark, currency, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, amountMinor: Long, color: Color, currency: com.ledgerly.app.domain.money.CurrencyInfo, modifier: Modifier = Modifier) {
    val shown = com.ledgerly.app.ui.components.animatedLong(amountMinor)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            Money.format(shown, currency),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun ComparisonCard(period: Period, txs: List<com.ledgerly.app.data.db.TransactionWithCategory>, currency: com.ledgerly.app.domain.money.CurrencyInfo, darkTheme: Boolean) {
    val months = Periods.monthStarts(period).map { YearMonth.from(it) }
    val series = Statistics.monthlySeries(txs, months)
    val incomeColor = if (darkTheme) IncomeGreenDark else com.ledgerly.app.ui.theme.IncomeGreen
    val expenseColor = if (darkTheme) ExpenseRedDark else com.ledgerly.app.ui.theme.ExpenseRed
    val groups = series.map { BarGroup(it.label(), listOf(it.income.toFloat(), it.expense.toFloat()), listOf(incomeColor, expenseColor)) }
    StatsCard(title = "Income vs expenses") {
        if (groups.isEmpty()) {
            Text("No data in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            GroupedBarChart(
                groups = groups,
                chartHeight = 180.dp,
                showValues = groups.size <= 8,
                valueCompact = { Money.compact(it.toLong(), currency) },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LegendDot(incomeColor, "Income")
                LegendDot(expenseColor, "Expenses")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(0.dp))
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryCard(title: String, typeLabel: String, stats: List<CatStat>, currency: com.ledgerly.app.domain.money.CurrencyInfo) {
    StatsCard(title = title) {
        if (stats.isEmpty()) {
            Text("No $typeLabel in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@StatsCard
        }
        val top = stats.take(6)
        val total = top.sumOf { it.amountMinor }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = top.map { DonutSlice(Color(it.category.colorArgb.toInt()), it.amountMinor.toFloat()) },
                modifier = Modifier.size(104.dp),
                stroke = 15.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Money.compact(total, currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEach { stat ->
                    val color = Color(stat.category.colorArgb.toInt())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    stat.category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                Text(
                                    Money.format(stat.amountMinor, currency),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FractionBar(fraction = if (total > 0) stat.amountMinor.toFloat() / total.toFloat() else 0f, color = color, height = 5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetTrendCard(period: Period, txs: List<com.ledgerly.app.data.db.TransactionWithCategory>, currency: com.ledgerly.app.domain.money.CurrencyInfo, darkTheme: Boolean) {
    val points: List<Float>
    val label: String
    if (period.monthsInside() > 1) {
        val months = Periods.monthStarts(period).map { YearMonth.from(it) }
        points = Statistics.monthlySeries(txs, months).map { it.net.toFloat() }
        label = "Net per month"
    } else {
        points = Statistics.dailySeries(txs, period).map { it.net.toFloat() }
        label = "Net per day"
    }
    StatsCard(title = label) {
        if (points.isEmpty()) {
            Text("No data in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TrendLineChart(
                points = points,
                chartHeight = 130.dp,
                lineColor = if (darkTheme) com.ledgerly.app.ui.theme.IncomeGreenDark else com.ledgerly.app.ui.theme.IncomeGreen,
            )
            Text(
                rangeLabel(period),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun rangeLabel(period: Period): String =
    period.start.format(DateTimeFormatter.ofPattern("d MMM yyyy")) + " — " + period.endInclusive.format(DateTimeFormatter.ofPattern("d MMM yyyy"))