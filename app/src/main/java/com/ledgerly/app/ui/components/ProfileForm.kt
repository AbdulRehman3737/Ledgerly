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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ledgerly.app.domain.colors.Palette
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.money.Currencies
import com.ledgerly.app.domain.money.CurrencyInfo

@Composable
fun CurrencyChipRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Currencies.all.forEach { cur ->
            val active = cur.code == selected
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(cur.code) },
            ) {
                Text(
                    cur.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
fun ProfileForm(
    initialName: String,
    initialIcon: String,
    initialColor: Long,
    initialCurrency: String,
    modifier: Modifier = Modifier,
    currencyEnabled: Boolean = true,
    onSubmit: (name: String, icon: String, colorArgb: Long, currencyCode: String) -> Unit,
    submitLabel: String,
    onValidateName: () -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var icon by rememberSaveable(initialIcon) { mutableStateOf(initialIcon) }
    var color by rememberSaveable(initialColor) { mutableStateOf(initialColor) }
    var currency by rememberSaveable(initialCurrency) { mutableStateOf(initialCurrency) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Avatar preview centered
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            val profilePreview = com.ledgerly.app.data.db.ProfileEntity(
                id = -1, name = name, icon = icon, colorArgb = color, currencyCode = currency, createdAt = 0,
            )
            ProfileAvatar(profilePreview, 72.dp)
        }
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Profile name") },
            placeholder = { Text("e.g. Personal, Business, Family") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Text(
            "Choose an avatar",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconChoiceGrid(
            icons = IconCatalog.PROFILE_ICONS,
            selectedKeys = listOf(icon),
            onSelect = { icon = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Pick a color",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorChoiceRow(
            colors = Palette.PROFILE_COLORS,
            selected = color,
            onSelect = { color = it },
            modifier = Modifier.fillMaxWidth(),
        )

        if (currencyEnabled) {
            Text(
                "Currency",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Used to format all amounts in this profile. Tracked locally — no exchange rates used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CurrencyChipRow(selected = currency, onSelect = { currency = it })
        }

        androidx.compose.material3.Button(
            onClick = {
                if (name.isBlank()) {
                    onValidateName()
                    return@Button
                }
                onSubmit(name.trim(), icon, color, currency)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(submitLabel)
        }
    }
}