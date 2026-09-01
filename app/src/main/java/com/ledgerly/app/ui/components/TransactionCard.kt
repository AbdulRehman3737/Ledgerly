package com.ledgerly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.money.CurrencyInfo
import com.ledgerly.app.domain.money.Money
import com.ledgerly.app.ui.theme.ExpenseRed
import com.ledgerly.app.ui.theme.ExpenseRedDark
import com.ledgerly.app.ui.theme.IncomeGreen
import com.ledgerly.app.ui.theme.IncomeGreenDark
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TransactionRow(
    tx: TransactionWithCategory,
    currency: CurrencyInfo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    darkTheme: Boolean,
    bordered: Boolean = true,
    showDate: Boolean = false,
) {
    val isIncome = tx.transaction.type == com.ledgerly.app.domain.model.TxType.INCOME
    val accent = if (isIncome) {
        if (darkTheme) IncomeGreenDark else IncomeGreen
    } else {
        if (darkTheme) ExpenseRedDark else ExpenseRed
    }
    val bg = if (isIncome) {
        if (darkTheme) Color(0xFF12311F) else Color(0xFFE9F3E8)
    } else {
        if (darkTheme) Color(0xFF331B18) else Color(0xFFF7E7E0)
    }
    val shape = RoundedCornerShape(10.dp)
    val name = tx.category?.name ?: "Unknown"
    val note = tx.transaction.note

    val surfaceModifier = if (bordered) {
        Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, accent.copy(alpha = if (isIncome) 0.45f else 0.4f), shape)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(surfaceModifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (bordered) 12.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(
            icon = IconCatalog.vector(tx.category?.icon),
            color = accent,
            size = 36.dp,
            iconSize = 18.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showDate) {
                Text(
                    text = dayLabel(tx.transaction.dateEpochDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (note.isNotBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = if (isIncome) Money.format(Money.abs(tx.transaction.amountMinor), currency)
            else "-" + Money.format(Money.abs(tx.transaction.amountMinor), currency),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
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