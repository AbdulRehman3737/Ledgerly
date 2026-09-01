package com.ledgerly.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.ProfileEntity
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.icons.IconDef
import com.ledgerly.app.domain.model.TxType
import com.ledgerly.app.ui.theme.ExpenseRed
import com.ledgerly.app.ui.theme.ExpenseRedDark
import com.ledgerly.app.ui.theme.IncomeGreen
import com.ledgerly.app.ui.theme.IncomeGreenDark

fun Int.categoryColor(): Color = Color(this)

@Composable
fun TypeSegmented(
    selected: TxType,
    onChange: (TxType) -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
) {
    val incomeColor = if (darkTheme) IncomeGreenDark else IncomeGreen
    val expenseColor = if (darkTheme) ExpenseRedDark else ExpenseRed
    val container = MaterialTheme.colorScheme.surfaceVariant
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentOption(
            label = "Income",
            active = selected == TxType.INCOME,
            activeColor = incomeColor,
            inactiveText = onContainer,
            onClick = { onChange(TxType.INCOME) },
            modifier = Modifier.weight(1f),
        )
        SegmentOption(
            label = "Expense",
            active = selected == TxType.EXPENSE,
            activeColor = expenseColor,
            inactiveText = onContainer,
            onClick = { onChange(TxType.EXPENSE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentOption(
    label: String,
    active: Boolean,
    activeColor: Color,
    inactiveText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (active) activeColor else Color.Transparent, label = "segBg")
    val fg by animateColorAsState(if (active) Color.White else inactiveText, label = "segFg")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long,
    onSelect: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(categories, key = { it.id }) { cat ->
            CategoryTile(cat, selected = cat.id == selectedId, onClick = { onSelect(cat) })
        }
    }
}

@Composable
fun CategoryTile(
    cat: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(cat.colorArgb.toInt())
    val onColor = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconCircle(
            icon = IconCatalog.vector(cat.icon),
            color = color,
            size = 44.dp,
            iconSize = 22.dp,
            selected = selected,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = cat.name,
            style = MaterialTheme.typography.labelMedium,
            color = onColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun IconCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    selected: Boolean = false,
) {
    val bg = color.copy(alpha = 0.13f)
    val iconColor = color
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(if (selected) 2.dp else 0.dp, if (selected) color else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun IconChoiceGrid(
    icons: List<IconDef>,
    selectedKeys: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        icons.chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { icon ->
                    val selected = icon.key in selectedKeys
                    IconCircle(
                        icon = icon.vector,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 46.dp,
                        iconSize = 22.dp,
                        selected = selected,
                        modifier = Modifier.clickable { onSelect(icon.key) },
                    )
                }
            }
        }
    }
}

@Composable
fun ColorChoiceRow(
    colors: List<Long>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { argb ->
            val c = Color(argb.toInt())
            val active = selected == argb
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (active) 3.dp else 1.dp, if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (c.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FunctionText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text, modifier = modifier, color = color, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
}