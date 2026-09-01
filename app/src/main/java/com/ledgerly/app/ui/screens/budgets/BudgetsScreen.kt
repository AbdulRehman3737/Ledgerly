package com.ledgerly.app.ui.screens.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.ledgerly.app.ui.components.animatedLong
import com.ledgerly.app.ui.theme.AmberWarn

private data class BudgetUi(
    val budget: BudgetEntity,
    val category: CategoryEntity?,
    val spent: Long,
) {
    // Overall budget uses categoryId == null and is shown first with a ledger "grand total" treatment.
    val isOverall: Boolean get() = category == null
}

@Composable
fun BudgetsScreen(
    vm: LedgerViewModel,
    darkTheme: Boolean,
) {
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categories by vm.categoriesAll.collectAsStateWithLifecycle()
    val currency = vm.currency()

    var budgetEditor by remember { mutableStateOf<BudgetUi?>(null) }
    var deleteTarget by remember { mutableStateOf<BudgetUi?>(null) }

    val catById = categories.associateBy { it.id }
    val activeCatIds = categories.filter { !it.archived }.map { it.id }.toSet()
    val period = Periods.of(PeriodType.THIS_MONTH)
    val monthExpense = Statistics.expense(txs, period)

    val overall = budgets.firstOrNull { it.categoryId == null }?.let {
        BudgetUi(it, null, monthExpense)
    }

    val list = budgets.mapNotNull { b ->
        val catId = b.categoryId ?: return@mapNotNull null
        val cat = catById[catId] ?: return@mapNotNull null
        BudgetUi(b, cat, Statistics.spentForCategory(txs, b.categoryId ?: 0L, period))
    }.sortedBy { it.category!!.name.lowercase() }

    val visible = list.filter { it.category!!.id in activeCatIds }
    val archived = list.filter { it.category!!.id !in activeCatIds }

    // Totals include the overall cap; overall spent counts ALL month spending (not capped per category).
    val catLimits = visible.sumOf { it.budget.amountMinor }
    val catSpent = visible.sumOf { it.spent.coerceAtMost(it.budget.amountMinor) }
    val overallLimit = overall?.budget?.amountMinor ?: 0L
    val overallSpentCapped = (overall?.spent ?: 0L).coerceAtMost(overallLimit)
    val totalLimits = catLimits + overallLimit
    val totalSpent = catSpent + overallSpentCapped

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
            }
        }

        item { Spacer(Modifier.height(2.dp)) }

        if (overall == null && visible.isEmpty() && archived.isEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(
                        icon = Icons.Filled.Savings,
                        title = "No budgets yet",
                        message = "Set a monthly overall cap, or a limit for any expense category, and track how close you are.",
                        actionLabel = "Create a budget",
                        onAction = {
                            val first = categories.firstOrNull { it.type == TxType.EXPENSE && !it.archived }
                            budgetEditor = if (first == null) BudgetUi(BudgetEntity(0, 0, null, 0, com.ledgerly.app.domain.model.BudgetPeriod.MONTHLY, 0), null, 0)
                            else BudgetUi(BudgetEntity(0, 0, first.id, 0, com.ledgerly.app.domain.model.BudgetPeriod.MONTHLY, 0), first, 0)
                        },
                    )
                }
            }
        } else {
            item {
                BudgetSummaryCard(totalLimits, totalSpent, currency)
            }

            if (overall != null) {
                item {
                    BudgetCard(
                        item = overall,
                        currency = currency,
                        onChange = { budgetEditor = overall },
                        onDelete = { deleteTarget = overall },
                    )
                }
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
                    budgetEditor = BudgetUi(BudgetEntity(0, 0, first?.id, 0, com.ledgerly.app.domain.model.BudgetPeriod.MONTHLY, 0), first, 0)
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
            message = if (target.isOverall) "Remove your overall monthly budget? Your transactions are not affected." else "Remove the monthly budget for ${target.category!!.name}? Your transactions are not affected.",
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
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
            .padding(16.dp),
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
                Text("This month".uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val isOverall = item.isOverall
    val color = when {
        over -> MaterialTheme.colorScheme.error
        fraction > 0.8f -> AmberWarn
        isOverall -> MaterialTheme.colorScheme.primary
        else -> Color(cat!!.colorArgb.toInt())
    }
    val remaining = limit - spent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (isOverall) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isOverall) {
                IconCircle(
                    icon = Icons.Filled.Savings,
                    color = color,
                    size = 42.dp,
                    iconSize = 21.dp,
                )
            } else {
                IconCircle(
                    icon = IconCatalog.vector(cat!!.icon),
                    color = Color(cat.colorArgb.toInt()),
                    size = 42.dp,
                    iconSize = 21.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isOverall) "Overall" else cat!!.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(if (isOverall) "ALL SPENDING · MONTHLY" else "MONTHLY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun BudgetSheet(
    categories: List<CategoryEntity>,
    initial: BudgetUi,
    vm: LedgerViewModel,
    onDismiss: () -> Unit,
) {
    val currency = vm.currency()
    var selectedCategoryId by rememberSaveable(initial.budget.categoryId) { mutableStateOf(initial.budget.categoryId) }
    var amount by rememberSaveable(initial.budget.amountMinor) { mutableStateOf(if (initial.budget.amountMinor > 0) Money.toInputString(initial.budget.amountMinor, currency.decimals) else "") }
    var error by remember { mutableStateOf<String?>(null) }
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally),
            )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedCategoryId == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .clickable {
                            selectedCategoryId = null
                            amountFocus.requestFocus()
                            keyboard?.show()
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconCircle(
                        icon = Icons.Filled.Savings,
                        color = MaterialTheme.colorScheme.primary,
                        size = 34.dp,
                        iconSize = 17.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overall", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("A cap for ALL monthly spending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selectedCategoryId == null) {
                        Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                categories.forEach { cat ->
                    val selected = selectedCategoryId == cat.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable {
                                selectedCategoryId = cat.id
                                amountFocus.requestFocus()
                                keyboard?.show()
                            }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus),
                label = { Text("Monthly amount ($currency.code)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Cancel")
            }
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
                    val catId = selectedCategoryId
                    if (catId == null) vm.upsertOverallBudget(minor) else vm.upsertBudget(catId, minor)
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