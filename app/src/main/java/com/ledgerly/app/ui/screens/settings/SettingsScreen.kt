package com.ledgerly.app.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.domain.colors.Palette
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.money.Currencies
import com.ledgerly.app.domain.model.ThemeMode
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.components.ColorChoiceRow
import com.ledgerly.app.ui.components.ConfirmDialog
import com.ledgerly.app.ui.components.EmptyState
import com.ledgerly.app.ui.components.IconChoiceGrid
import com.ledgerly.app.ui.components.IconCircle
import com.ledgerly.app.ui.components.TypeSegmented
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: LedgerViewModel,
    darkTheme: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by vm.currentProfile.collectAsStateWithLifecycle()
    val categoriesAll by vm.categoriesAll.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val currency = vm.currency()

    var categoryForm by remember { mutableStateOf<CategoryFormState?>(null) }
    var wipeTarget by remember { mutableStateOf(false) }

    val snack: (String) -> Unit = { msg ->
        Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(vm.exportJson().toByteArray(Charsets.UTF_8))
                true
            } ?: false
            snack(if (ok) "Backup exported" else "Export failed")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (raw.isEmpty()) {
                snack("Could not read the selected file")
                return@launch
            }
            val report = vm.importJson(raw)
            if (report.isSuccess) {
                snack("Imported ${report.importedProfiles} profile(s), ${report.addedTransactions} transaction(s), ${report.addedBudgets} budget(s)")
            } else {
                snack(report.warnings.firstOrNull() ?: "Import failed")
            }
        }
    }

    val xlsxExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = vm.exportXlsx()
            val ok = if (data.isEmpty()) false else context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(data)
                true
            } ?: false
            snack(if (ok) "Spreadsheet exported" else "Export failed")
        }
    }

    val xlsxImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            if (bytes.isEmpty()) {
                snack("Could not read the selected file")
                return@launch
            }
            val report = vm.importXlsx(bytes)
            if (report.isSuccess) {
                snack("Imported ${report.addedTransactions} transaction(s), ${report.addedCategories} category(ies)")
            } else {
                snack(report.warnings.firstOrNull() ?: "Import failed")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                .padding(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Appearance ----
            SectionCard(
                icon = Icons.Filled.Palette,
                title = "Appearance",
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.DARK to "Dark",
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { vm.setTheme(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                },
            )

            // ---- Categories ----
            val activeCats = categoriesAll.filter { !it.archived }.sortedWith(
                compareBy<CategoryEntity> { if (it.type == TxType.EXPENSE) 0 else 1 }.thenBy { it.name.lowercase() }
            )
            val archivedCats = categoriesAll.filter { it.archived }
            SectionCard(
                icon = Icons.Filled.Category,
                title = "Categories",
                content = {
                    if (activeCats.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Category,
                            title = "No categories",
                            message = "Create categories to organize income and expenses.",
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            activeCats.forEach { cat ->
                                CategoryManageRow(
                                    cat = cat,
                                    archived = false,
                                    onEdit = { categoryForm = CategoryFormState(cat) },
                                    onToggle = { vm.setCategoryArchived(cat.id, true) },
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { categoryForm = CategoryFormState(null) },
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add category")
                    }

                    if (archivedCats.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Hidden categories",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            archivedCats.forEach { cat ->
                                CategoryManageRow(
                                    cat = cat,
                                    archived = true,
                                    onEdit = { categoryForm = CategoryFormState(cat) },
                                    onToggle = { vm.setCategoryArchived(cat.id, false) },
                                )
                            }
                        }
                        Text(
                            "Past transactions keep their category. Archived categories are hidden from new transactions and budgets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            // ---- Currency ----
            SectionCard(title = "Currency") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Amounts are displayed in (" + currency.code + ") " + currency.name + ".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Currencies.all.forEach { c ->
                            FilterChip(
                                selected = c.code == currency.code,
                                onClick = { vm.setCurrency(c.code) },
                                label = { Text(c.code) },
                            )
                        }
                    }
                }
            }

            // ---- Data ----
            SectionCard(
                icon = Icons.Filled.Storage,
                title = "Data",
                content = {
                    Text(
                        "Everything is stored locally on this device — no account, no internet, no tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))

                    Text("Spreadsheet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Transactions in the MoneyManager format (Category, Note, Amount, Currency, Type, Account, Date, Photos).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val base = (current?.name ?: "ledgerly").replace(Regex("[^\\p{L}\\p{N}_ -]"), "").replace(' ', '_').trim('_').ifBlank { "ledgerly" }
                                val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                                xlsxExportLauncher.launch("MoneyManager_${base}_$date.xlsx")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export .xlsx")
                        }
                        FilledTonalButton(
                            onClick = {
                                xlsxImportLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/octet-stream",
                                        "*/*",
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import .xlsx")
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(14.dp))

                    Text("Full backup", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Every profile, category, budget and transaction as a single JSON file for restore or migration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { exportLauncher.launch("ledgerly_backup.json") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export .json")
                        }
                        FilledTonalButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import .json")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { wipeTarget = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Erase all data")
                    }
                },
            )

            // ---- About ----
            SectionCard(title = "About") {
                Text(
                    "Ledgerly v1.0\nA private, offline money tracker. Your data never leaves your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    categoryForm?.let { state ->
        CategoryFormSheet(
            editing = state.category,
            darkTheme = darkTheme,
            vm = vm,
            onDismiss = { categoryForm = null },
            onDuplicate = { snack("A category with that name already exists") },
            onInvalid = { snack("Enter a category name") },
        )
    }

    if (wipeTarget) {
        ConfirmDialog(
            title = "Erase everything?",
            message = "All profiles, transactions, categories, budgets and settings will be permanently deleted. Consider exporting a backup first.",
            confirmText = "Erase",
            onConfirm = { vm.wipeAll() },
            onDismiss = { wipeTarget = false },
        )
    }
}

private data class CategoryFormState(val category: CategoryEntity?)

@Composable
private fun SectionCard(
    icon: ImageVector? = null,
    title: String,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun CategoryManageRow(
    cat: CategoryEntity,
    archived: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
) {
    val color = Color(cat.colorArgb.toInt())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(IconCatalog.vector(cat.icon), color, size = 34.dp, iconSize = 17.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(cat.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                (if (cat.type == TxType.INCOME) "Income" else "Expense") + if (cat.isSystem) " · Default" else " · Custom",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text("Edit") }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (archived) Icons.Filled.Restore else Icons.Filled.Archive,
                contentDescription = if (archived) "Restore" else "Hide",
                tint = if (archived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFormSheet(
    editing: CategoryEntity?,
    darkTheme: Boolean,
    vm: LedgerViewModel,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onInvalid: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by rememberSaveable(editing?.name) { mutableStateOf(editing?.name ?: "") }
    var type by rememberSaveable(editing?.type?.name) { mutableStateOf(editing?.type ?: TxType.EXPENSE) }
    var icon by rememberSaveable(editing?.icon) { mutableStateOf(editing?.icon ?: IconCatalog.ALL.first().key) }
    var color by rememberSaveable(editing?.colorArgb ?: -1L) { mutableStateOf(editing?.colorArgb ?: Palette.CATEGORY_COLORS.first()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (editing == null) "New category" else "Edit \"${editing!!.name}\"",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (editing == null) {
                TypeSegmented(selected = type, onChange = { type = it }, darkTheme = darkTheme, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category name") },
                placeholder = { Text("e.g. Dining out, Freelance, Rent") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            )
            Text(
                "Choose an icon",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconChoiceGrid(icons = IconCatalog.ALL, selectedKeys = listOf(icon), onSelect = { icon = it }, modifier = Modifier.fillMaxWidth())
            Text(
                "Pick a color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ColorChoiceRow(colors = Palette.CATEGORY_COLORS, selected = color, onSelect = { color = it }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val cleaned = name.trim()
                    if (cleaned.isBlank()) {
                        onInvalid()
                        return@Button
                    }
                    vm.upsertCategory(editing?.id, cleaned, type, icon, color)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (editing == null) "Create category" else "Save changes")
            }
        }
    }
}