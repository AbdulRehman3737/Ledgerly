package com.ledgerly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerly.app.domain.model.ThemeMode
import com.ledgerly.app.ui.LedgerViewModel
import com.ledgerly.app.ui.approot.LedgerlyAppRoot
import com.ledgerly.app.ui.theme.LedgerlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: LedgerViewModel = viewModel()
            val theme by vm.themeMode.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LedgerlyTheme(darkTheme = dark) {
                LedgerlyAppRoot(vm = vm, darkTheme = dark)
            }
        }
    }
}