package com.ledgerly.app.ui.screens.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerly.app.domain.colors.Palette
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.money.Currencies
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.components.ProfileForm
import com.ledgerly.app.ui.theme.LightPrimary

@Composable
fun OnboardingScreen(vm: LedgerViewModel, showIntro: Boolean, darkTheme: Boolean) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                showIntro && step == 0 -> WelcomeStep(
                    darkTheme = darkTheme,
                    onNext = { step = 1 },
                )
                else -> CreateProfileStep(
                    vm = vm,
                    onBack = if (showIntro && step == 1) ({ step = 0 }) else null,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(darkTheme: Boolean, onNext: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(primary.copy(alpha = if (darkTheme) 0.28f else 0.16f), MaterialTheme.colorScheme.background))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Your money,\nfriendly again.",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Ledgerly is a private money tracker that works completely on your device. No accounts, no internet, no ads — just clean numbers.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            FeatureRow(Icons.Filled.CloudOff, "100% offline", "Works in airplane mode. Nothing is uploaded anywhere.")
            FeatureRow(Icons.Filled.Lock, "Your data stays yours", "Stored only on this device with instant local persistence.")
            FeatureRow(Icons.Filled.AccountBalanceWallet, "Multiple profiles", "Personal, Business, Family — kept fully separate.")

            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Get started", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreateProfileStep(vm: LedgerViewModel, onBack: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("Back") }
        }
        Text(
            "Create your first profile",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Profiles keep everything separate — perfect for Personal, Business and Family finances.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        ProfileForm(
            initialName = "",
            initialIcon = IconCatalog.AV_WALLET.key,
            initialColor = Palette.PROFILE_COLORS.first(),
            initialCurrency = Currencies.USD.code,
            onSubmit = { name, icon, color, currency ->
                vm.createProfile(name, icon, color, currency)
            },
            submitLabel = "Create profile",
            onValidateName = {},
        )
        Spacer(Modifier.height(24.dp))
    }
}