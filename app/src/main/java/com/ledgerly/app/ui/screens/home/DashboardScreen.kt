package com.ledgerly.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerly.app.data.db.BudgetEntity
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.domain.money.CurrencyInfo
import com.ledgerly.app.domain.money.Money
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.domain.stats.CatStat
import com.ledgerly.app.domain.stats.Statistics
import com.ledgerly.app.domain.time.Period
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.components.DonutChart
import com.ledgerly.app.ui.components.DonutSlice
import com.ledgerly.app.ui.components.EmptyState
import com.ledgerly.app.ui.components.FractionBar
import com.ledgerly.app.ui.components.LedgerHeroPanel
import com.ledgerly.app.ui.components.LedgerHeader
import com.ledgerly.app.ui.components.LedgerRule
import com.ledgerly.app.ui.components.LedgerSection
import com.ledgerly.app.ui.components.TransactionRow
import com.ledgerly.app.ui.components.animatedLong
import com.ledgerly.app.ui.theme.AmberWarn
import com.ledgerly.app.ui.theme.AmberWarnDark
import com.ledgerly.app.ui.theme.ExpenseRed
import com.ledgerly.app.ui.theme.ExpenseRedDark
import com.ledgerly.app.ui.theme.IncomeGreen
import com.ledgerly.app.ui.theme.IncomeGreenDark
import com.ledgerly.app.ui.theme.LightPrimary
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    vm: LedgerViewModel,
    darkTheme: Boolean,
    onAdd: () -> Unit,
    onEdit: (TransactionWithCategory) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val profile by vm.currentProfile.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categoriesAll by vm.categoriesAll.collectAsStateWithLifecycle()
    val currency = vm.currency()

    val now = YearMonth.now()
    var selectedMonthKey by rememberSaveable { mutableStateOf(now.toString()) }
    var showMonthSheet by remember { mutableStateOf(false) }
    val selectedMonth = YearMonth.parse(selectedMonthKey)
    val period = Period(selectedMonth.atDay(1), selectedMonth.atEndOfMonth())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MonthHeader(
                selected = selectedMonth,
                onPrev = { selectedMonthKey = selectedMonth.minusMonths(1).toString() },
                onNext = { selectedMonthKey = selectedMonth.plusMonths(1).toString() },
                onLabelClick = { showMonthSheet = true },
            )
        }

        if (profile != null) {
            item {
                BalanceCard(
                    selectedMonth = selectedMonth,
                    period = period,
                    txs = txs,
                    budgets = budgets,
                    currency = currency,
                )
            }

            item {
                RecentActivityCard(
                    txs = txs,
                    currency = currency,
                    darkTheme = darkTheme,
                    onAdd = onAdd,
                    onEdit = onEdit,
                    onSeeAll = { onNavigate("history") },
                )
            }

            item {
                MonthSummaryCard(
                    selectedMonth = selectedMonth,
                    period = period,
                    txs = txs,
                    currency = currency,
                    darkTheme = darkTheme,
                )
            }

            item {
                BudgetsOverviewCard(
                    period = period,
                    budgets = budgets,
                    categories = categoriesAll,
                    txs = txs,
                    currency = currency,
                    darkTheme = darkTheme,
                    onNavigate = { onNavigate("budgets") },
                )
            }

            val top = Statistics.topCategories(txs, TxType.EXPENSE, 5, period)
            if (top.isNotEmpty()) {
                item {
                    TopCategoriesCard(top = top, currency = currency)
                }
            }
        }
    }

    if (showMonthSheet) {
        MonthPickerSheet(
            selected = selectedMonth,
            onSelect = { selectedMonthKey = it.toString(); showMonthSheet = false },
            onDismiss = { showMonthSheet = false },
        )
    }
}

@Composable
private fun MonthHeader(
    selected: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLabelClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        TextButton(onClick = onLabelClick, modifier = Modifier.weight(1f)) {
            Text(
                selected.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun BalanceCard(
    selectedMonth: YearMonth,
    period: Period,
    txs: List<TransactionWithCategory>,
    budgets: List<BudgetEntity>,
    currency: CurrencyInfo,
) {
    // All-time balance — the absolute total amount left, across the whole ledger.
    val allTimeIncome = Statistics.income(txs)
    val allTimeExpense = Statistics.expense(txs)
    val allTimeTotal = (allTimeIncome - allTimeExpense).coerceAtLeast(0)

    // This month's figures.
    val thisIncome = Statistics.income(txs, period)
    val thisExpense = Statistics.expense(txs, period)
    val overallBudget = budgets.firstOrNull { it.categoryId == null }?.amountMinor
    val hasOverall = overallBudget != null && overallBudget > 0
    val monthLeft = if (hasOverall) (overallBudget!! - thisExpense) else (thisIncome - thisExpense)

    val heroOn = Color.White
    val heroBg = LightPrimary
    val monthLabel = selectedMonth.format(DateTimeFormatter.ofPattern("MMM yyyy")).uppercase()

    LedgerHeroPanel(background = heroBg) {
        Text(
            "Balance · all time",
            style = MaterialTheme.typography.labelLarge,
            color = heroOn.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = Money.format(animatedLong(allTimeTotal), currency),
            style = MaterialTheme.typography.displayLarge,
            color = heroOn,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasOverall) "Left to spend · $monthLabel  ${Money.format(monthLeft, currency)}"
            else "THIS MONTH · $monthLabel  ${Money.format(monthLeft, currency)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (monthLeft < 0) Color(0xFFFFC2B4) else heroOn.copy(alpha = 0.7f),
        )
        if (hasOverall) {
            Spacer(Modifier.height(12.dp))
            FractionBar(
                fraction = thisExpense.toFloat() / overallBudget!!.toFloat(),
                color = if (thisExpense > overallBudget) Color(0xFFFF9E8C) else Color(0xFFBFF0D8),
                height = 4.dp,
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = heroOn.copy(alpha = 0.2f))
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            BalanceStat("Income · all time", allTimeIncome, currency, Modifier.weight(1f))
            BalanceStat("Spent · all time", allTimeExpense, currency, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BalanceStat(label: String, amount: Long, currency: CurrencyInfo, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.65f),
        )
        Text(
            Money.format(amount, currency),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun RecentActivityCard(
    txs: List<TransactionWithCategory>,
    currency: CurrencyInfo,
    darkTheme: Boolean,
    onAdd: () -> Unit,
    onEdit: (TransactionWithCategory) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (txs.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "No transactions yet",
            message = "Tap the + button to log your first income or expense. It will appear here.",
            actionLabel = "Add transaction",
            onAction = onAdd,
        )
        return
    }
    LedgerSection {
        LedgerHeader(title = "Recent activity", actionLabel = "See all", onAction = onSeeAll)
        Column {
            txs.take(5).forEachIndexed { index, tx ->
                TransactionRow(
                    tx = tx,
                    currency = currency,
                    darkTheme = darkTheme,
                    onClick = { onEdit(tx) },
                    bordered = false,
                    showDate = true,
                )
                if (index < 4) LedgerRule()
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(
    selectedMonth: YearMonth,
    period: Period,
    txs: List<TransactionWithCategory>,
    currency: CurrencyInfo,
    darkTheme: Boolean,
) {
    val income = Statistics.income(txs, period)
    val expense = Statistics.expense(txs, period)
    val total = income + expense
    val incomeColor = if (darkTheme) IncomeGreenDark else IncomeGreen
    val expenseColor = if (darkTheme) ExpenseRedDark else ExpenseRed
    val hasActivity = total > 0

    LedgerSection {
        LedgerHeader(title = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
        if (!hasActivity) {
            Text(
                "No income or spending in ${selectedMonth.format(DateTimeFormatter.ofPattern("MMMM")).lowercase()}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@LedgerSection
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = listOf(
                    DonutSlice(incomeColor, income.toFloat()),
                    DonutSlice(expenseColor, expense.toFloat()),
                ),
                modifier = Modifier.size(92.dp),
                stroke = 14.dp,
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DonutLegendRow("Income", income, total, currency, incomeColor)
                DonutLegendRow("Expenses", expense, total, currency, expenseColor)
            }
        }
    }
}

@Composable
private fun DonutLegendRow(label: String, amount: Long, total: Long, currency: CurrencyInfo, color: Color) {
    val fraction = if (total > 0) amount.toFloat() / total.toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(Money.format(amount, currency), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        FractionBar(fraction = fraction, color = color, height = 3.dp)
    }
}

@Composable
private fun TopCategoriesCard(top: List<CatStat>, currency: CurrencyInfo) {
    LedgerSection {
        LedgerHeader(title = "Biggest spending")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val max = top.first().amountMinor.coerceAtLeast(1)
            top.forEach { stat ->
                val color = Color(stat.category.colorArgb.toInt())
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    FractionBar(fraction = stat.amountMinor.toFloat() / max.toFloat(), color = color, height = 4.dp)
                }
            }
        }
    }
}

@Composable
private fun BudgetsOverviewCard(
    period: Period,
    budgets: List<BudgetEntity>,
    categories: List<CategoryEntity>,
    txs: List<TransactionWithCategory>,
    currency: CurrencyInfo,
    darkTheme: Boolean,
    onNavigate: () -> Unit,
) {
    if (budgets.isEmpty()) {
        LedgerSection {
            LedgerHeader(title = "Budgets", actionLabel = "Create", onAction = onNavigate)
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
    LedgerSection {
        LedgerHeader(title = "Budgets", actionLabel = "Manage", onAction = onNavigate)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            budgets.take(3).forEach { budget ->
                val catId = budget.categoryId ?: return@forEach
                val cat = catById[catId] ?: return@forEach
                val spent = Statistics.spentForCategory(txs, catId, period)
                val fraction = spent.toFloat() / budget.amountMinor.toFloat()
                val over = spent > budget.amountMinor
                val color = when {
                    over -> MaterialTheme.colorScheme.error
                    fraction > 0.8f -> if (darkTheme) AmberWarnDark else AmberWarn
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
                    FractionBar(fraction = fraction.coerceIn(0f, 1f), color = color, height = 4.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPickerSheet(
    selected: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val now = YearMonth.now()
    val months = (0L downTo 11L).map { now.minusMonths(it) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Choose month",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            months.forEach { m ->
                val active = selected == m
                TextButton(
                    onClick = { onSelect(m) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text(
                        m.format(DateTimeFormatter.ofPattern("MMMM yyyy")) + if (m == now) "  · Current" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}