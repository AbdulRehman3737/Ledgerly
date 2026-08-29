package com.ledgerly.app.ui.screens.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerly.app.data.db.BudgetEntity
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.money.Money
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.domain.stats.Statistics
import com.ledgerly.app.domain.time.PeriodType
import com.ledgerly.app.domain.time.Periods
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.components.ConfirmDialog
import com.ledgerly.app.ui.components.DonutChart
import com.ledgerly.app.ui.components.DonutSlice
import com.ledgerly.app.ui.components.EmptyState
import com.ledgerly.app.ui.components.FractionBar
import com.ledgerly.app.ui.components.IconCircle
import com.ledgerly.app.ui.components.ProfileChip
import com.ledgerly.app.ui.components.animatedLong
import com.ledgerly.app.ui.theme.AmberWarn

private data class BudgetUi(
    val budget: BudgetEntity,
    val category: CategoryEntity,
    val spent: Long,
)

@Composable
fun BudgetsScreen(
    vm: LedgerViewModel,
    darkTheme: Boolean,
    onProfileClick: () -> Unit,
) {
    val profile by vm.currentProfile.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categories by vm.categoriesAll.collectAsStateWithLifecycle()
    val currency = vm.currency()

    var budgetEditor by remember { mutableStateOf<BudgetUi?>(null) }
    var deleteTarget by remember { mutableStateOf<BudgetUi?>(null) }

    val catById = categories.associateBy { it.id }
    val activeCatIds = categories.filter { !it.archived }.map { it.id }.toSet()
    val period = Periods.of(PeriodType.THIS_MONTH)
    val list = budgets.mapNotNull { b ->
        val cat = catById[b.categoryId] ?: return@mapNotNull null
        BudgetUi(b, cat, Statistics.spentForCategory(txs, b.categoryId, period))
    }.sortedBy { it.category.name.lowercase() }

    val visible = list.filter { it.category.id in activeCatIds }
    val archived = list.filter { it.category.id !in activeCatIds }

    val totalLimits = visible.sumOf { it.budget.amountMinor }
    val totalSpent = visible.sumOf { it.spent.coerceAtMost(it.budget.amountMinor) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Budgets",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (profile != null) ProfileChip(profile!!, onClick = onProfileClick)
            }
        }

        item { Spacer(Modifier.height(2.dp)) }

        if (visible.isEmpty() && archived.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(
                        icon = Icons.Filled.Savings,
                        title = "No budgets yet",
                        message = "Set a monthly limit for any expense category and track how close you are.",
                        actionLabel = "Create a budget",
                        onAction = {
                            val first = categories.firstOrNull { it.type == TxType.EXPENSE && !it.archived }
                            if (first != null) budgetEditor = BudgetUi(BudgetEntity(0, 0, first.id, 0, com.ledgerly.app.domain.model.BudgetPeriod.MONTHLY, 0), first, 0)
                        },
                    )
                }
            }
        } else {
            item {
                BudgetSummaryCard(totalLimits, totalSpent, currency)
            }

            items(visible, key = { it.budget.id }) { item ->
                BudgetCard(
                    item = item,
                    currency = currency,
                    onChange = { budgetEditor = item },
                    onDelete = { deleteTarget = item },
                )
            }

            if (archived.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            "Hidden categories",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Budgets for archived categories are paused until you restore them in Settings → Categories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    val first = categories.firstOrNull { it.type == TxType.EXPENSE && !it.archived }
                    if (first != null) budgetEditor = BudgetUi(BudgetEntity(0, 0, first.id, 0, com.ledgerly.app.domain.model.BudgetPeriod.MONTHLY, 0), first, 0)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Set a budget")
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }

    budgetEditor?.let { edit ->
        BudgetSheet(
            categories = categories.filter { it.type == TxType.EXPENSE && !it.archived },
            initial = edit,
            vm = vm,
            onDismiss = { budgetEditor = null },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete budget?",
            message = "Remove the monthly budget for ${target.category.name}? Your transactions are not affected.",
            confirmText = "Delete",
            onConfirm = { vm.deleteBudget(target.budget.id) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun BudgetSummaryCard(totalLimits: Long, totalSpent: Long, currency: com.ledgerly.app.domain.money.CurrencyInfo) {
    val fraction = if (totalLimits > 0) totalSpent.toFloat() / totalLimits.toFloat() else 0f
    val color = when {
        fraction > 1f -> MaterialTheme.colorScheme.error
        fraction > 0.8f -> AmberWarn
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = listOf(DonutSlice(color, totalSpent.toFloat()), DonutSlice(MaterialTheme.colorScheme.surfaceVariant, (totalLimits - totalSpent).coerceAtLeast(0).toFloat())),
                modifier = Modifier.size(84.dp),
                stroke = 14.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.0f%%".format(fraction.coerceIn(0f, 1f) * 100),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("This month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val spent = animatedLong(totalSpent)
                Text(
                    Money.format(spent, currency),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "of " + Money.format(totalLimits, currency) + " budgeted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FractionBar(fraction = fraction.coerceIn(0f, 1f), color = color, height = 10.dp)
    }
}

@Composable
private fun BudgetCard(
    item: BudgetUi,
    currency: com.ledgerly.app.domain.money.CurrencyInfo,
    onChange: () -> Unit,
    onDelete: () -> Unit,
) {
    val cat = item.category
    val spent = item.spent
    val limit = item.budget.amountMinor
    val fraction = spent.toFloat() / limit.toFloat()
    val over = spent > limit
    val color = when {
        over -> MaterialTheme.colorScheme.error
        fraction > 0.8f -> AmberWarn
        else -> Color(cat.colorArgb.toInt())
    }
    val remaining = limit - spent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(
                icon = IconCatalog.vector(cat.icon),
                color = Color(cat.colorArgb.toInt()),
                size = 42.dp,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cat.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Monthly budget", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                Money.format(limit, currency),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
        FractionBar(fraction = fraction.coerceIn(0f, 1f), color = color, height = 9.dp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (over) "Over by " + Money.format(-remaining, currency) else "Remaining " + Money.format(remaining, currency),
                style = MaterialTheme.typography.labelMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                Money.format(spent, currency) + " / " + Money.format(limit, currency),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onChange) {
                Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Text("Delete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(
    categories: List<CategoryEntity>,
    initial: BudgetUi,
    vm: LedgerViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val currency = vm.currency()
    var selectedCategoryId by rememberSaveable(initial.budget.categoryId) { mutableStateOf(initial.budget.categoryId) }
    var amount by rememberSaveable(initial.budget.amountMinor) { mutableStateOf(if (initial.budget.amountMinor > 0) Money.toInputString(initial.budget.amountMinor, currency.decimals) else "") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (initial.budget.amountMinor > 0) "Edit budget" else "Set a budget",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                categories.forEach { cat ->
                    val selected = selectedCategoryId == cat.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable { selectedCategoryId = cat.id }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconCircle(
                            icon = IconCatalog.vector(cat.icon),
                            color = Color(cat.colorArgb.toInt()),
                            size = 34.dp,
                            iconSize = 17.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (selected) {
                            Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = com.ledgerly.app.ui.components.sanitizeAmountInput(it, currency.decimals) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Monthly amount ($currency.code)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            if (error != null) {
                Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    val minor = Money.parse(amount, currency.decimals)
                    if (minor == null || minor <= 0) {
                        error = "Enter a valid monthly amount"
                        return@Button
                    }
                    vm.upsertBudget(selectedCategoryId, minor)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Save budget")
            }
        }
    }
}