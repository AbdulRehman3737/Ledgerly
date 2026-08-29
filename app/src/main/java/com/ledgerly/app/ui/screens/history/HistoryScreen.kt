package com.ledgerly.app.ui.screens.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.money.CurrencyInfo
import com.ledgerly.app.domain.money.Money
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.components.EmptyState
import com.ledgerly.app.ui.components.IconCircle
import com.ledgerly.app.ui.components.ProfileChip
import com.ledgerly.app.ui.components.TransactionRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class TypeFilter(val label: String) { ALL("All"), INCOME("Income"), EXPENSE("Expense") }

private enum class SortMode(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    AMOUNT_DESC("Highest amount"),
    AMOUNT_ASC("Lowest amount"),
}

private data class DayGroup(
    val dateEpochDay: Long,
    val items: List<TransactionWithCategory>,
)

@Composable
fun HistoryScreen(
    vm: LedgerViewModel,
    darkTheme: Boolean,
    onAdd: () -> Unit,
    onEdit: (TransactionWithCategory) -> Unit,
    onProfileClick: () -> Unit,
    snack: (message: String, actionLabel: String?, onAction: (() -> Unit)?) -> Unit,
) {
    val profile by vm.currentProfile.collectAsStateWithLifecycle()
    val allTxs by vm.transactions.collectAsStateWithLifecycle()
    val categoriesAll by vm.categoriesAll.collectAsStateWithLifecycle()
    val currency = vm.currency()

    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf(TypeFilter.ALL) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NEWEST) }
    var month by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }

    var showMonthSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var filtered = allTxs
    filtered = when (typeFilter) {
        TypeFilter.ALL -> filtered
        TypeFilter.INCOME -> filtered.filter { it.transaction.type == TxType.INCOME }
        TypeFilter.EXPENSE -> filtered.filter { it.transaction.type == TxType.EXPENSE }
    }
    if (month != null) {
        filtered = filtered.filter { YearMonth.from(LocalDate.ofEpochDay(it.transaction.dateEpochDay)) == month }
    }
    if (categoryId != null) {
        filtered = filtered.filter { it.transaction.categoryId == categoryId }
    }
    if (query.isNotBlank()) {
        val q = query.trim()
        filtered = filtered.filter {
            it.transaction.note.contains(q, ignoreCase = true) ||
                (it.category?.name?.contains(q, ignoreCase = true) == true)
        }
    }
    filtered = when (sortMode) {
        SortMode.NEWEST -> filtered.sortedWith(compareByDescending<TransactionWithCategory> { it.transaction.dateEpochDay }.thenByDescending { it.transaction.createdAt })
        SortMode.OLDEST -> filtered.sortedWith(compareBy<TransactionWithCategory> { it.transaction.dateEpochDay }.thenBy { it.transaction.createdAt })
        SortMode.AMOUNT_DESC -> filtered.sortedByDescending { it.transaction.amountMinor }
        SortMode.AMOUNT_ASC -> filtered.sortedBy { it.transaction.amountMinor }
    }

    val groups = filtered.groupBy { it.transaction.dateEpochDay }
        .map { (day, list) -> DayGroup(day, list) }
        .sortedByDescending { it.dateEpochDay }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Transactions",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (profile != null) {
                ProfileChip(profile!!, onClick = onProfileClick)
            }
        }
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by note or category") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeFilter.entries.forEach { t ->
                FilterChip(
                    selected = typeFilter == t,
                    onClick = { typeFilter = t },
                    label = { Text(t.label) },
                )
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = { showSortMenu = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                SortMode.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = { sortMode = s; showSortMenu = false },
                    )
                }
            }
            IconButton(onClick = { showCategorySheet = true }) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "Filter category",
                    tint = if (categoryId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val base = month ?: YearMonth.now()
                month = base.minusMonths(1)
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            TextButton(onClick = { showMonthSheet = true }, modifier = Modifier.weight(1f)) {
                Text(
                    month?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: "All time",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = {
                val base = month ?: YearMonth.now()
                month = base.plusMonths(1)
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Spacer(Modifier.height(4.dp))

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = if (allTxs.isEmpty()) "No transactions yet" else "Nothing matches",
                    message = if (allTxs.isEmpty()) {
                        "Tap the + button to add your first income or expense."
                    } else {
                        "Try changing the search or filters."
                    },
                    actionLabel = if (allTxs.isEmpty()) "Add transaction" else null,
                    onAction = if (allTxs.isEmpty()) onAdd else null,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.dateEpochDay }) { group ->
                    DaySection(
                        group = group,
                        currency = currency,
                        darkTheme = darkTheme,
                        onEdit = onEdit,
                        onDelete = { tx ->
                            vm.deleteTransaction(tx.transaction.id)
                            snack("Transaction deleted", "Undo") { vm.restoreTransaction(tx.transaction) }
                        },
                    )
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (showMonthSheet) {
        MonthSheet(
            selected = month,
            onSelect = { month = it; showMonthSheet = false },
            onDismiss = { showMonthSheet = false },
        )
    }
    if (showCategorySheet) {
        CategoryFilterSheet(
            categories = categoriesAll,
            selectedId = categoryId,
            onSelect = { categoryId = it; showCategorySheet = false },
            onClear = { categoryId = null; showCategorySheet = false },
            onDismiss = { showCategorySheet = false },
        )
    }
}

@Composable
private fun DaySection(
    group: DayGroup,
    currency: CurrencyInfo,
    darkTheme: Boolean,
    onEdit: (TransactionWithCategory) -> Unit,
    onDelete: (TransactionWithCategory) -> Unit,
) {
    val net = group.items.sumOf { if (it.transaction.type == TxType.INCOME) it.transaction.amountMinor else -it.transaction.amountMinor }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dayLabel(group.dateEpochDay),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                Money.format(net, currency),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        group.items.forEach { tx ->
            SwipeDismissRow(tx = tx, currency = currency, darkTheme = darkTheme, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDismissRow(
    tx: TransactionWithCategory,
    currency: CurrencyInfo,
    darkTheme: Boolean,
    onEdit: (TransactionWithCategory) -> Unit,
    onDelete: (TransactionWithCategory) -> Unit,
) {
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onDelete(tx)
                    false
                } else {
                    false
                }
            },
        ),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        TransactionRow(tx = tx, currency = currency, darkTheme = darkTheme, onClick = { onEdit(tx) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSheet(
    selected: YearMonth?,
    onSelect: (YearMonth?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                "Filter by month",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val now = YearMonth.now()
            val months = listOf<YearMonth?>(null) + (0L downTo 11L).map { now.minusMonths(it) }
            months.forEach { m ->
                val active = selected == m
                TextButton(
                    onClick = { onSelect(m) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text(
                        m?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: "All time",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterSheet(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Filter by category",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val allOptions = listOf<CategoryEntity?>(null) + categories
            allOptions.forEach { cat ->
                val active = selectedId == cat?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (cat == null) onClear() else onSelect(cat.id) }
                        .padding(horizontal = 24.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cat == null) {
                        Text(
                            "All categories",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        IconCircle(
                            icon = IconCatalog.vector(cat.icon),
                            color = androidx.compose.ui.graphics.Color(cat.colorArgb.toInt()),
                            size = 34.dp,
                            iconSize = 17.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                cat.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (cat.archived) {
                                Text(
                                    "Hidden category",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            if (cat.type == TxType.INCOME) "Income" else "Expense",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun dayLabel(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}