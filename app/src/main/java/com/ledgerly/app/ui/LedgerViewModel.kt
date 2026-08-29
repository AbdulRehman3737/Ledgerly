package com.ledgerly.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ledgerly.app.data.db.BudgetEntity
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.ProfileEntity
import com.ledgerly.app.data.db.TransactionEntity
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.data.repository.ImportReport
import com.ledgerly.app.domain.money.Currencies
import com.ledgerly.app.domain.money.CurrencyInfo
import com.ledgerly.app.domain.model.ThemeMode
import com.ledgerly.app.domain.model.TxType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single shared ViewModel exposing the app's local state and all user actions.
 * Everything is powered by Room flows — data persists immediately and survives restarts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as com.ledgerly.app.LedgerlyApp).container.repository

    val profiles: StateFlow<List<ProfileEntity>> = repo.profiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentProfile: StateFlow<ProfileEntity?> = combine(profiles, repo.currentProfileId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val themeMode: StateFlow<ThemeMode> = repo.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val usedBefore: StateFlow<Boolean> = repo.usedBefore
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val transactions: StateFlow<List<TransactionWithCategory>> = currentProfile
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.transactionsFor(p.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categoriesActive: StateFlow<List<CategoryEntity>> = currentProfile
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.categoriesActive(p.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categoriesAll: StateFlow<List<CategoryEntity>> = currentProfile
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.categoriesAll(p.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val budgets: StateFlow<List<BudgetEntity>> = currentProfile
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.budgetsFor(p.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun currency(): CurrencyInfo = Currencies.fromCode(currentProfile.value?.currencyCode)

    // ---- Settings ----

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setTheme(mode) }

    fun switchProfile(id: Long) = viewModelScope.launch { repo.setActiveProfile(id) }

    // ---- Profiles ----

    fun createProfile(name: String, icon: String, colorArgb: Long, currencyCode: String) =
        viewModelScope.launch { repo.createProfile(name, icon, colorArgb, currencyCode) }

    fun updateProfile(profile: ProfileEntity) = viewModelScope.launch { repo.updateProfile(profile) }

    fun deleteProfile(id: Long) = viewModelScope.launch { repo.deleteProfile(id) }

    // ---- Transactions ----

    fun addTransaction(type: TxType, amountMinor: Long, dateEpochDay: Long, note: String, categoryId: Long?) {
        val profile = currentProfile.value ?: return
        viewModelScope.launch { repo.saveTransaction(profile.id, type, amountMinor, dateEpochDay, note, categoryId) }
    }

    fun updateTransaction(tx: TransactionEntity) = viewModelScope.launch { repo.updateTransaction(tx) }

    fun deleteTransaction(id: Long) = viewModelScope.launch { repo.deleteTransaction(id) }

    fun restoreTransaction(tx: TransactionEntity) = viewModelScope.launch { repo.insertTransactionRaw(tx) }

    // ---- Categories ----

    fun upsertCategory(id: Long?, name: String, type: TxType, icon: String, colorArgb: Long) {
        val profile = currentProfile.value ?: return
        viewModelScope.launch { repo.upsertCategory(profile.id, id, name, type, icon, colorArgb, isSystem = false) }
    }

    fun setCategoryArchived(id: Long, archived: Boolean) =
        viewModelScope.launch { repo.setCategoryArchived(id, archived) }

    // ---- Budgets ----

    fun upsertBudget(categoryId: Long, amountMinor: Long) {
        val profile = currentProfile.value ?: return
        viewModelScope.launch { repo.upsertBudget(profile.id, categoryId, amountMinor) }
    }

    fun deleteBudget(budgetId: Long) = viewModelScope.launch { repo.deleteBudget(budgetId) }

    // ---- Data management ----

    suspend fun exportJson(): String = repo.exportAll()

    suspend fun importJson(raw: String): ImportReport = repo.importJson(raw)

    fun wipeAll() = viewModelScope.launch { repo.wipeAll() }
}