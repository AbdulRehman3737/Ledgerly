package com.ledgerly.app.ui.approot

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.screens.add.AddTransactionSheet
import com.ledgerly.app.ui.screens.budgets.BudgetsScreen
import com.ledgerly.app.ui.screens.history.HistoryScreen
import com.ledgerly.app.ui.screens.home.DashboardScreen
import com.ledgerly.app.ui.screens.onboarding.OnboardingScreen
import com.ledgerly.app.ui.screens.settings.SettingsScreen
import com.ledgerly.app.ui.screens.stats.StatsScreen
import kotlinx.coroutines.launch

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec("home", "Home", Icons.Filled.Home),
    TabSpec("history", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
    TabSpec("stats", "Analytics", Icons.Filled.PieChart),
    TabSpec("budgets", "Budgets", Icons.Filled.Savings),
    TabSpec("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun LedgerlyAppRoot(vm: LedgerViewModel, darkTheme: Boolean) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val current by vm.currentProfile.collectAsStateWithLifecycle()
    val usedBefore by vm.usedBefore.collectAsStateWithLifecycle()

    if (current == null || profiles.isEmpty()) {
        OnboardingScreen(vm = vm, showIntro = !usedBefore, darkTheme = darkTheme)
    } else {
        MainScaffold(vm = vm, darkTheme = darkTheme)
    }
}

@Composable
fun MainScaffold(vm: LedgerViewModel, darkTheme: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var sheetEditTx by remember { mutableStateOf<TransactionWithCategory?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val currentProfile by vm.currentProfile.collectAsStateWithLifecycle()

    fun openEditor(tx: TransactionWithCategory?) {
        sheetEditTx = tx
        showAddSheet = true
    }

    fun notifySaved() {
        scope.launch {
            snackbarHostState.showSnackbar(
                if (sheetEditTx != null) "Transaction updated" else "Transaction added",
            )
        }
    }

    fun snack(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == "home" || currentRoute == "history") {
                FloatingActionButton(
                    onClick = { openEditor(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                DashboardScreen(
                    vm = vm,
                    darkTheme = darkTheme,
                    onAdd = { openEditor(null) },
                    onEdit = { openEditor(it) },
                    onNavigate = { route -> navController.navigate(route) },
                    onProfileClick = { showProfileSheet = true },
                )
            }
            composable("history") {
                HistoryScreen(
                    vm = vm,
                    darkTheme = darkTheme,
                    onAdd = { openEditor(null) },
                    onEdit = { openEditor(it) },
                    onProfileClick = { showProfileSheet = true },
                    snack = { message, label, action -> snack(message, label, action) },
                )
            }
            composable("stats") {
                StatsScreen(vm = vm, darkTheme = darkTheme, onProfileClick = { showProfileSheet = true })
            }
            composable("budgets") {
                BudgetsScreen(vm = vm, darkTheme = darkTheme, onProfileClick = { showProfileSheet = true })
            }
            composable("settings") {
                SettingsScreen(vm = vm, darkTheme = darkTheme, onProfileClick = { showProfileSheet = true })
            }
        }
    }

    if (showAddSheet) {
        AddTransactionSheet(
            vm = vm,
            darkTheme = darkTheme,
            editing = sheetEditTx,
            onDismiss = { showAddSheet = false },
            onSaved = { notifySaved() },
        )
    }

    if (showProfileSheet) {
        com.ledgerly.app.ui.components.ProfileSwitcherSheet(
            profiles = profiles,
            currentId = currentProfile?.id ?: -1,
            onSelect = {
                vm.switchProfile(it)
                showProfileSheet = false
            },
            onManage = {
                showProfileSheet = false
                navController.navigate("settings")
            },
            onDismiss = { showProfileSheet = false },
        )
    }
}