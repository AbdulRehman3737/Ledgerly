package com.ledgerly.app.ui.screens.add

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.domain.money.Money
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.components.AmountInput
import com.ledgerly.app.ui.components.CategoryGrid
import com.ledgerly.app.ui.components.DatePickerDialog
import com.ledgerly.app.ui.components.TypeSegmented
import com.ledgerly.app.ui.theme.ExpenseRed
import com.ledgerly.app.ui.theme.ExpenseRedDark
import com.ledgerly.app.ui.theme.IncomeGreen
import com.ledgerly.app.ui.theme.IncomeGreenDark
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    vm: LedgerViewModel,
    darkTheme: Boolean,
    editing: TransactionWithCategory?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currency = vm.currency()

    var type by rememberSaveable { mutableStateOf(editing?.transaction?.type ?: TxType.EXPENSE) }
    var amount by rememberSaveable(editing?.transaction?.id) { mutableStateOf(if (editing != null) Money.toInputString(editing.transaction.amountMinor, currency.decimals) else "") }
    var selectedCategoryId by rememberSaveable(editing?.transaction?.id) { mutableStateOf(editing?.category?.id ?: 0L) }
    var note by rememberSaveable(editing?.transaction?.id) { mutableStateOf(editing?.transaction?.note ?: "") }
    var date by rememberSaveable(editing?.transaction?.id) { mutableStateOf(LocalDate.ofEpochDay(editing?.transaction?.dateEpochDay ?: LocalDate.now().toEpochDay())) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val categories = vm.categoriesActive.collectAsStateWithLifecycle().value.filter { it.type == type }
    val validSelectedId = if (selectedCategoryId != 0L && categories.any { it.id == selectedCategoryId }) selectedCategoryId else 0L
    val accent = when (type) {
        TxType.INCOME -> if (darkTheme) IncomeGreenDark else IncomeGreen
        TxType.EXPENSE -> if (darkTheme) ExpenseRedDark else ExpenseRed
    }

    fun submit() {
        if (amount.isBlank()) {
            error = "Enter an amount"
            return
        }
        val minor = Money.parse(amount, currency.decimals)
        if (minor == null || minor <= 0) {
            error = "Enter a valid amount"
            return
        }
        error = null
        if (editing != null) {
            vm.updateTransaction(
                editing.transaction.copy(
                    type = type,
                    amountMinor = minor,
                    categoryId = if (validSelectedId != 0L) validSelectedId else editing.transaction.categoryId,
                    dateEpochDay = date.toEpochDay(),
                    note = note,
                ),
            )
        } else {
            vm.addTransaction(
                type = type,
                amountMinor = minor,
                dateEpochDay = date.toEpochDay(),
                note = note,
                categoryId = validSelectedId.takeIf { it != 0L },
            )
        }
        onSaved()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (editing != null) "Edit transaction" else "New transaction",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            TypeSegmented(selected = type, onChange = { type = it }, darkTheme = darkTheme)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = if (darkTheme) 0.14f else 0.08f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                AmountInput(
                    value = amount,
                    onValueChange = { amount = it },
                    decimals = currency.decimals,
                    symbol = currency.symbol + " ",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp),
                    color = accent,
                    modifier = Modifier.fillMaxWidth(),
                    onSubmit = { submit() },
                )
            }

            Text(
                text = if (type == TxType.INCOME) "Income category" else "Expense category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CategoryGrid(
                categories = categories,
                selectedId = validSelectedId,
                onSelect = { selectedCategoryId = it.id },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .then(Modifier.clickable { showDatePicker = true })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a note (optional)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            if (error != null) {
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text("Cancel")
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent)
                        .clickable { submit() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (editing != null) "Save changes" else "Add " + (if (type == TxType.INCOME) "income" else "expense"),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = date,
            onDateSelected = { date = it },
            onDismiss = { showDatePicker = false },
        )
    }
}