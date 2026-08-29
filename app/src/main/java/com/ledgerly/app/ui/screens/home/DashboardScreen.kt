package com.ledgerly.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerly.app.data.db.ProfileEntity
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.domain.money.CurrencyInfo
import com.ledgerly.app.domain.money.Money
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.domain.stats.Statistics
import com.ledgerly.app.domain.time.Period
import com.ledgerly.app.domain.time.PeriodType
import com.ledgerly.app.domain.time.Periods
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.components.DonutChart
import com.ledgerly.app.ui.components.DonutSlice
import com.ledgerly.app.ui.components.EmptyState
import com.ledgerly.app.ui.components.FractionBar
import com.ledgerly.app.ui.components.ProfileChip
import com.ledgerly.app.ui.components.TransactionRow
import com.ledgerly.app.ui.components.animatedLong
import com.ledgerly.app.ui.theme.ExpenseRed
import com.ledgerly.app.ui.theme.ExpenseRedDark
import com.ledgerly.app.ui.theme.IncomeGreen
import com.ledgerly.app.ui.theme.IncomeGreenDark
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    vm: LedgerViewModel,
    darkTheme: Boolean,
    onAdd: () -> Unit,
    onEdit: (TransactionWithCategory) -> Unit,
    onNavigate: (String) -> Unit,
    onProfileClick: () -> Unit,
) {
    val profile by vm.currentProfile.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categoriesAll by vm.categoriesAll.collectAsStateWithLifecycle()

    val currency = vm.currency()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Ledgerly",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (profile != null) {
                    ProfileChip(profile!!, onClick = onProfileClick)
                }
            }
        }

        if (profile != null) {
            item {
                HeroCard(profile!!, txs, currency, darkTheme)
            }

            item {
                ThisMonthCard(txs, currency, darkTheme)
            }

            item {
                TrendCard(txs, currency, darkTheme)
            }

            item {
                RecentTransactionsCard(txs, currency, darkTheme, isNotEmpty = txs.isNotEmpty(), onEdit = onEdit, onSeeAll = { onNavigate("history") })
            }

            if (txs.isNotEmpty()) {
                item {
                    TopCategoriesCard(txs, currency, darkTheme)
                }
            }

            item {
                BudgetsOverviewCard(
                    budgets = budgets,
                    categories = categoriesAll,
                    txs = txs,
                    currency = currency,
                    darkTheme = darkTheme,
                    onNavigate = { onNavigate("budgets") },
                )
            }
        }
    }
}

@Composable
private fun HeroCard(profile: ProfileEntity, txs: List<TransactionWithCategory>, currency: CurrencyInfo, darkTheme: Boolean) {
    val income = Statistics.income(txs)
    val expense = Statistics.expense(txs)
    val balance = income - expense
    val thisIncome = Statistics.income(txs, Periods.of(PeriodType.THIS_MONTH))
    val thisExpense = Statistics.expense(txs, Periods.of(PeriodType.THIS_MONTH))
    val heroStart = if (darkTheme) Color(0xFF052E23) else Color(0xFF056A4A)
    val heroEnd = if (darkTheme) Color(0xFF0E5B41) else Color(0xFF0B8B5E)
    val heroOn = Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(heroStart, heroEnd)))
            .padding(22.dp),
    ) {
        Text(
            "TOTAL BALANCE",
            style = MaterialTheme.typography.labelMedium,
            color = heroOn.copy(alpha = 0.78f),
        )
        Spacer(Modifier.height(4.dp))
        val shownBalance = animatedLong(balance)
        Text(
            text = Money.format(shownBalance, currency),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp),
            color = heroOn,
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(
                label = "Income",
                amount = thisIncome,
                currency = currency,
                color = if (darkTheme) IncomeGreenDark else IncomeGreen,
                modifier = Modifier.weight(1f),
                dark = darkTheme,
            )
            MiniStat(
                label = "Spent",
                amount = thisExpense,
                currency = currency,
                color = if (darkTheme) ExpenseRedDark else ExpenseRed,
                modifier = Modifier.weight(1f),
                dark = darkTheme,
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, amount: Long, currency: CurrencyInfo, color: Color, modifier: Modifier, dark: Boolean) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.92f))
            .padding(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            Money.format(amount, currency),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ThisMonthCard(txs: List<TransactionWithCategory>, currency: CurrencyInfo, darkTheme: Boolean) {
    val period = Periods.of(PeriodType.THIS_MONTH)
    val income = Statistics.income(txs, period)
    val expense = Statistics.expense(txs, period)
    val total = income + expense
    val incomeColor = if (darkTheme) IncomeGreenDark else IncomeGreen
    val expenseColor = if (darkTheme) ExpenseRedDark else ExpenseRed

    SectionCard(title = "This month") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = listOf(
                    DonutSlice(incomeColor, income.toFloat()),
                    DonutSlice(expenseColor, expense.toFloat()),
                ),
                modifier = Modifier.size(96.dp),
                stroke = 16.dp,
                gapDegrees = 4f,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Money.compact(income - expense, currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DonutLegendRow(
                    label = "Income",
                    amount = income,
                    total = total,
                    currency = currency,
                    color = incomeColor,
                    darkTheme = darkTheme,
                )
                DonutLegendRow(
                    label = "Expenses",
                    amount = expense,
                    total = total,
                    currency = currency,
                    color = expenseColor,
                    darkTheme = darkTheme,
                )
            }
        }
    }
}

@Composable
private fun DonutLegendRow(label: String, amount: Long, total: Long, currency: CurrencyInfo, color: Color, darkTheme: Boolean) {
    val fraction = if (total > 0) amount.toFloat() / total.toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(Money.format(amount, currency), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        FractionBar(fraction = fraction, color = color, height = 7.dp)
    }
}

@Composable
private fun TrendCard(txs: List<TransactionWithCategory>, currency: CurrencyInfo, darkTheme: Boolean) {
    val period = Periods.of(PeriodType.THIS_MONTH)
    val days = Statistics.dailySeries(txs, period)
    val points = days.map { it.net.toFloat() }
    val color = if (darkTheme) IncomeGreenDark else IncomeGreen

    SectionCard(title = "Net trend this month") {
        if (points.isEmpty() || points.all { it == 0f }) {
            Text(
                "No activity to chart yet this month.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            com.ledgerly.app.ui.components.TrendLineChart(points = points, chartHeight = 120.dp, lineColor = color)
            Spacer(Modifier.height(10.dp))
            Text(
                period.start.format(DateTimeFormatter.ofPattern("d MMM")) + " — " + period.endInclusive.format(DateTimeFormatter.ofPattern("d MMM")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentTransactionsCard(
    txs: List<TransactionWithCategory>,
    currency: CurrencyInfo,
    darkTheme: Boolean,
    isNotEmpty: Boolean,
    onEdit: (TransactionWithCategory) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (!isNotEmpty) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "No transactions yet",
                message = "Tap the + button to log your first income or expense. It will appear here instantly.",
                actionLabel = "Add transaction",
                onAction = null,
            )
        }
        return
    }
    val recent = txs.take(5)
    SectionCard(title = "Recent transactions", onAction = onSeeAll, actionLabel = "See all") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { tx ->
                TransactionRow(
                    tx = tx,
                    currency = currency,
                    darkTheme = darkTheme,
                    onClick = { onEdit(tx) },
                )
            }
        }
    }
}

@Composable
private fun TopCategoriesCard(txs: List<TransactionWithCategory>, currency: CurrencyInfo, darkTheme: Boolean) {
    val top = Statistics.topCategories(txs, TxType.EXPENSE, 5, Periods.of(PeriodType.THIS_MONTH))
    SectionCard(title = "Top spending this month") {
        if (top.isEmpty()) {
            Text(
                "No expenses this month.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val max = top.first().amountMinor.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            top.forEach { stat ->
                val color = Color(stat.category.colorArgb.toInt())
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stat.category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(
                            Money.format(stat.amountMinor, currency),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    FractionBar(fraction = stat.amountMinor.toFloat() / max.toFloat(), color = color, height = 8.dp)
                }
            }
        }
    }
}

@Composable
private fun BudgetsOverviewCard(
    budgets: List<com.ledgerly.app.data.db.BudgetEntity>,
    categories: List<com.ledgerly.app.data.db.CategoryEntity>,
    txs: List<TransactionWithCategory>,
    currency: CurrencyInfo,
    darkTheme: Boolean,
    onNavigate: () -> Unit,
) {
    if (budgets.isEmpty()) {
        SectionCard(title = "Budgets") {
            Text(
                "Set monthly limits for the categories that matter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNavigate) { Text("Create a budget") }
        }
        return
    }
    val catById = categories.associateBy { it.id }
    val period = Periods.of(PeriodType.THIS_MONTH)
    SectionCard(title = "Budgets", onAction = onNavigate, actionLabel = "Manage") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            budgets.take(3).forEach { budget ->
                val cat = catById[budget.categoryId] ?: return@forEach
                val spent = Statistics.spentForCategory(txs, budget.categoryId, period)
                val fraction = spent.toFloat() / budget.amountMinor.toFloat()
                val over = spent > budget.amountMinor
                val color = when {
                    over -> MaterialTheme.colorScheme.error
                    fraction > 0.8f -> com.ledgerly.app.ui.theme.AmberWarn
                    else -> Color(cat.colorArgb.toInt())
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text(
                            Money.format(spent, currency) + " / " + Money.format(budget.amountMinor, currency),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FractionBar(fraction = fraction.coerceIn(0f, 1f), color = color, height = 8.dp)
                }
            }
        }
    }
}