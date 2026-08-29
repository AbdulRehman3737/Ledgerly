package com.ledgerly.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.ledgerly.app.domain.money.CurrencyInfo
import com.ledgerly.app.domain.money.Money
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.ui.theme.ExpenseRed
import com.ledgerly.app.ui.theme.ExpenseRedDark
import com.ledgerly.app.ui.theme.IncomeGreen
import com.ledgerly.app.ui.theme.IncomeGreenDark

@Composable
fun animatedLong(
    target: Long,
    animSpec: AnimationSpec<Float> = tween(durationMillis = 650, easing = FastOutSlowInEasing),
): Long {
    val animated by animateFloatAsState(target.toFloat(), animationSpec = animSpec, label = "moneyCount")
    return animated.toLong().coerceIn(kotlin.math.min(0L, target), kotlin.math.max(0L, target))
}

@Composable
fun txTypeColor(type: TxType, darkTheme: Boolean): Color {
    val target = when (type) {
        TxType.INCOME -> if (darkTheme) IncomeGreenDark else IncomeGreen
        TxType.EXPENSE -> if (darkTheme) ExpenseRedDark else ExpenseRed
    }
    val color by animateColorAsState(target, label = "txColor")
    return color
}

@Composable
fun MoneyAmountText(
    amountMinor: Long,
    currency: CurrencyInfo,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color? = null,
    animate: Boolean = true,
) {
    val shown = if (animate) animatedLong(amountMinor) else amountMinor
    val text = Money.format(shown, currency)
    val resolved = color ?: MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.Text(
        text = text,
        style = style,
        color = resolved,
        modifier = modifier,
        maxLines = 1,
    )
}