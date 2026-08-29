package com.ledgerly.app.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp

/** Filters user typing so only a valid monetary string is accepted. */
fun sanitizeAmountInput(raw: String, decimals: Int): String {
    if (raw.isEmpty()) return ""
    val sb = StringBuilder()
    var hasDot = false
    var decimalsSeen = 0
    for (c in raw) {
        when {
            c == '.' && !hasDot && decimals > 0 -> {
                hasDot = true
                sb.append(c)
            }
            c.isDigit() && sb.length < 14 -> {
                if (hasDot) {
                    if (decimalsSeen < decimals) {
                        decimalsSeen++
                        sb.append(c)
                    }
                } else {
                    sb.append(c)
                }
            }
        }
    }
    while (sb.length > 1 && sb[0] == '0' && sb.getOrNull(1) != '.') {
        sb.deleteCharAt(0)
    }
    return sb.toString()
}

/**
 * Premium numeric money input. Shows the currency symbol beside a large
 * bare field; typing is filtered to a valid number with the currency's decimals.
 */
@Composable
fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    decimals: Int,
    symbol: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
) {
    val placeholderColor = Color(color.value).copy(alpha = 0.35f)
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.Top) {
        Text(
            text = symbol,
            style = style.copy(color = color.copy(alpha = 0.6f)),
            maxLines = 1,
        )
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(sanitizeAmountInput(it, decimals)) },
            modifier = Modifier.weight(1f),
            textStyle = style.copy(color = color),
            cursorBrush = SolidColor(color),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
            singleLine = true,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = "0", style = style.copy(color = placeholderColor), maxLines = 1)
                }
                inner()
            },
        )
    }
}