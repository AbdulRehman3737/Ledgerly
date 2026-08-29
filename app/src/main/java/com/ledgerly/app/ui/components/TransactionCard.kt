package com.ledgerly.app.ui.components

import androidx.compose.foundation.background
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

@Composable
fun TransactionRow(
    tx: TransactionWithCategory,
    currency: CurrencyInfo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    darkTheme: Boolean,
) {
    val category = tx.category
    val color = androidx.compose.ui.graphics.Color(category?.colorArgb?.toInt() ?: 0xFF64748B.toInt())
    val isIncome = tx.transaction.type == com.ledgerly.app.domain.model.TxType.INCOME
    val amountColor = if (isIncome) {
        if (darkTheme) IncomeGreenDark else IncomeGreen
    } else {
        if (darkTheme) ExpenseRedDark else ExpenseRed
    }
    val bg = MaterialTheme.colorScheme.surfaceContainer
    val name = category?.name ?: "Unknown"
    val note = tx.transaction.note

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(
            icon = IconCatalog.vector(category?.icon),
            color = color,
            size = 44.dp,
            iconSize = 22.dp,
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
            style = MaterialTheme.typography.titleSmall,
            color = amountColor,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}