package com.ledgerly.app.data.repository

import com.ledgerly.app.data.db.AppSettingEntity
import com.ledgerly.app.data.db.BudgetEntity
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.LedgerDatabase
import com.ledgerly.app.data.db.ProfileEntity
import com.ledgerly.app.data.db.Seed
import com.ledgerly.app.data.db.TransactionEntity
import com.ledgerly.app.data.db.TransactionWithCategory
import com.ledgerly.app.data.export.BackupCodec
import com.ledgerly.app.data.export.ExportProfile
import com.ledgerly.app.data.export.ParsedBackup
import com.ledgerly.app.data.export.ParsedCategory
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.money.Currencies
import com.ledgerly.app.domain.model.BudgetPeriod
import com.ledgerly.app.domain.model.ThemeMode
import com.ledgerly.app.domain.model.TxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ImportReport(
    val importedProfiles: Int,
    val addedCategories: Int,
    val addedTransactions: Int,
    val addedBudgets: Int,
    val warnings: List<String>,
) {
    val isSuccess: Boolean get() = importedProfiles > 0
}

class LedgerRepository(private val db: LedgerDatabase) {

    companion object {
        const val KEY_CURRENT_PROFILE = "current_profile_id"
        const val KEY_THEME = "theme_mode"
        const val KEY_USED = "used_before"
    }

    private val profileDao = db.profileDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    private val settingsDao = db.settingsDao()

    // ---- Observables ----

    val profiles: Flow<List<ProfileEntity>> = profileDao.observeAll()

    val currentProfileId: Flow<Long?> = settingsDao.observe(KEY_CURRENT_PROFILE)
        .map { it?.value?.toLongOrNull() }

    val themeMode: Flow<ThemeMode> = settingsDao.observe(KEY_THEME).map {
        when (it?.value) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val usedBefore: Flow<Boolean> = settingsDao.observe(KEY_USED).map { it != null }

    fun transactionsFor(profileId: Long): Flow<List<TransactionWithCategory>> =
        transactionDao.observeAll(profileId)

    fun categoriesActive(profileId: Long): Flow<List<CategoryEntity>> =
        categoryDao.observeActive(profileId)

    fun categoriesAll(profileId: Long): Flow<List<CategoryEntity>> =
        categoryDao.observeAll(profileId)

    fun budgetsFor(profileId: Long): Flow<List<BudgetEntity>> =
        budgetDao.observeAll(profileId)

    // ---- Settings ----

    suspend fun setTheme(mode: ThemeMode) {
        settingsDao.put(AppSettingEntity(KEY_THEME, mode.name))
    }

    suspend fun setActiveProfile(id: Long) {
        settingsDao.put(AppSettingEntity(KEY_CURRENT_PROFILE, id.toString()))
    }

    // ---- Profiles ----

    suspend fun createProfile(name: String, icon: String, colorArgb: Long, currencyCode: String): Long {
        val taken = profileDao.observeAll().first().map { it.name.trim() }.toMutableSet()
        var finalName = name.trim().ifEmpty { "Profile" }
        var suffix = 2
        while (taken.contains(finalName)) {
            finalName = "${name.trim()} ($suffix)"
            suffix++
        }
        val profile = ProfileEntity(
            name = finalName,
            icon = icon.ifBlank { IconCatalog.AV_WALLET.key },
            colorArgb = colorArgb,
            currencyCode = Currencies.fromCode(currencyCode).code,
            createdAt = System.currentTimeMillis(),
        )
        val id = profileDao.insert(profile)
        if (id != 0L) {
            seedCategories(id)
            settingsDao.put(AppSettingEntity(KEY_CURRENT_PROFILE, id.toString()))
            settingsDao.put(AppSettingEntity(KEY_USED, "1"))
        }
        return id
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        profileDao.update(profile)
    }

    suspend fun deleteProfile(id: Long) {
        val profile = profileDao.getById(id) ?: return
        profileDao.delete(profile)
        val remaining = profileDao.observeAll().first()
        if (remaining.isNotEmpty()) {
            if (profileDao.getById(remaining.first().id) != null) {
                settingsDao.put(AppSettingEntity(KEY_CURRENT_PROFILE, remaining.first().id.toString()))
            }
        } else {
            settingsDao.put(AppSettingEntity(KEY_CURRENT_PROFILE, ""))
        }
    }

    private suspend fun seedCategories(profileId: Long) {
        for (row in Seed.DEFAULT_CATEGORIES) {
            categoryDao.insert(
                CategoryEntity(
                    profileId = profileId,
                    name = row.name,
                    type = row.type,
                    icon = row.icon,
                    colorArgb = Seed.colorOf(row.colorHex),
                    isSystem = true,
                )
            )
        }
    }

    // ---- Transactions ----

    suspend fun saveTransaction(
        profileId: Long,
        type: TxType,
        amountMinor: Long,
        dateEpochDay: Long,
        note: String,
        categoryId: Long?,
    ): Long {
        val category = resolveCategory(profileId, type, categoryId)
        return transactionDao.insert(
            TransactionEntity(
                profileId = profileId,
                categoryId = category.id,
                type = type,
                amountMinor = amountMinor,
                dateEpochDay = dateEpochDay,
                note = note.trim(),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateTransaction(tx: TransactionEntity) {
        transactionDao.update(tx)
    }

    suspend fun deleteTransaction(id: Long) {
        val tx = transactionDao.getById(id) ?: return
        transactionDao.delete(tx)
    }

    /** Re-inserts a transaction (used by delete-undo). Places a fresh row with same data. */
    suspend fun insertTransactionRaw(tx: TransactionEntity): Long {
        val cat = categoryDao.getById(tx.categoryId) ?: return -1
        if (cat.profileId != tx.profileId) return -1
        return transactionDao.insert(tx.copy(id = 0))
    }

    private suspend fun resolveCategory(profileId: Long, type: TxType, categoryId: Long?): CategoryEntity {
        if (categoryId != null && categoryId > 0) {
            val cat = categoryDao.getById(categoryId)
            if (cat != null && cat.profileId == profileId && cat.type == type && !cat.archived) return cat
        }
        val byType = categoryDao.getByType(profileId, type)
        val other = byType.firstOrNull { it.name.equals("Other", ignoreCase = true) }
        if (other != null) return other
        if (byType.isNotEmpty()) return byType.first()
        val created = CategoryEntity(
            profileId = profileId,
            name = "Other",
            type = type,
            icon = IconCatalog.OTHER.key,
            colorArgb = if (type == TxType.EXPENSE) 0x475569L else 0x14B8A6L,
            isSystem = true,
        )
        val id = categoryDao.insert(created)
        return created.copy(id = id)
    }

    // ---- Categories ----

    suspend fun upsertCategory(
        profileId: Long,
        id: Long?,
        name: String,
        type: TxType,
        icon: String,
        colorArgb: Long,
        isSystem: Boolean,
    ): Long {
        val clean = name.trim()
        val safeIcon = IconCatalog.byKey(icon).key
        if (id != null && id > 0) {
            val existing = categoryDao.getById(id) ?: return -1
            categoryDao.update(existing.copy(name = clean.ifBlank { existing.name }, icon = safeIcon, colorArgb = colorArgb))
            return id
        }
        var finalName = clean.ifBlank { "Unnamed" }
        var suffix = 2
        while (categoryDao.countByName(profileId, finalName, type) > 0) {
            finalName = "${clean.ifBlank { "Unnamed" }} ($suffix)"
            suffix++
        }
        return categoryDao.insert(
            CategoryEntity(
                profileId = profileId,
                name = finalName,
                type = type,
                icon = safeIcon,
                colorArgb = colorArgb,
                isSystem = isSystem,
            )
        )
    }

    suspend fun categoryNameAvailable(profileId: Long, name: String, type: TxType): Boolean =
        categoryDao.countByName(profileId, name.trim(), type) == 0

    suspend fun setCategoryArchived(id: Long, archived: Boolean) {
        val cat = categoryDao.getById(id) ?: return
        categoryDao.setArchived(id, archived)
        if (archived) budgetDao.deleteForCategory(cat.profileId, id)
    }

    // ---- Budgets ----

    suspend fun upsertBudget(profileId: Long, categoryId: Long, amountMinor: Long): Long {
        require(amountMinor > 0) { "Budget amount must be positive" }
        val existing = budgetDao.getForCategory(profileId, categoryId)
        val entity = BudgetEntity(
            id = existing?.id ?: 0,
            profileId = profileId,
            categoryId = categoryId,
            amountMinor = amountMinor,
            period = BudgetPeriod.MONTHLY,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        return if (existing != null) {
            budgetDao.update(entity)
            entity.id
        } else {
            budgetDao.insert(entity)
        }
    }

    suspend fun deleteBudget(budgetId: Long) {
        val entity = budgetDao.getById(budgetId) ?: return
        budgetDao.delete(entity)
    }

    // ---- Export / Import ----

    suspend fun exportAll(): String {
        val profiles = profileDao.observeAll().first()
        val bundle = profiles.map { p ->
            ExportProfile(
                profile = p,
                categories = categoryDao.observeAll(p.id).first(),
                transactions = transactionDao.observeAll(p.id).first().map { it.transaction },
                budgets = budgetDao.observeAll(p.id).first(),
            )
        }
        return BackupCodec.encode(bundle)
    }

    suspend fun importJson(raw: String): ImportReport = applyImport(BackupCodec.parse(raw))

    private suspend fun applyImport(backup: ParsedBackup): ImportReport {
        val taken = profileDao.observeAll().first().map { it.name.trim() }.toMutableSet()
        var imported = 0
        var addedCategories = 0
        var addedTransactions = 0
        var addedBudgets = 0

        for (profile in backup.profiles) {
            var finalName = profile.name.trim()
            var suffix = 2
            while (taken.contains(finalName)) {
                finalName = "${profile.name.trim()} ($suffix)"
                suffix++
            }
            taken += finalName

            val newProfileId = profileDao.insert(
                ProfileEntity(
                    name = finalName,
                    icon = profile.icon,
                    colorArgb = profile.colorArgb,
                    currencyCode = Currencies.fromCode(profile.currencyCode).code,
                    createdAt = if (profile.createdAt <= 0) System.currentTimeMillis() else profile.createdAt,
                )
            )
            if (newProfileId == 0L) continue
            imported++

            val idByKey = mutableMapOf<String, Long>()
            for (c in profile.categories) {
                val key = BackupCodec.categoryKey(c.type, c.name)
                if (idByKey.containsKey(key)) continue
                val newId = categoryDao.insert(toEntity(newProfileId, c))
                if (newId != 0L) idByKey[key] = newId
            }

            for (t in profile.transactions) {
                val catId = idByKey[t.categoryKey]
                    ?: resolveImportCategory(newProfileId, t.type, idByKey)
                    ?: continue
                transactionDao.insert(
                    TransactionEntity(
                        profileId = newProfileId,
                        categoryId = catId,
                        type = t.type,
                        amountMinor = t.amountMinor,
                        dateEpochDay = t.dateEpochDay,
                        note = t.note,
                        createdAt = t.createdAt,
                    )
                )
                addedTransactions++
            }

            for (b in profile.budgets) {
                val catId = idByKey[b.categoryKey] ?: continue
                budgetDao.insert(
                    BudgetEntity(
                        profileId = newProfileId,
                        categoryId = catId,
                        amountMinor = b.amountMinor,
                        period = b.period,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                addedBudgets++
            }
        }
        return ImportReport(
            importedProfiles = imported,
            addedCategories = addedCategories,
            addedTransactions = addedTransactions,
            addedBudgets = addedBudgets,
            warnings = backup.warnings,
        )
    }

    private suspend fun resolveImportCategory(
        profileId: Long,
        type: TxType,
        idByKey: MutableMap<String, Long>,
    ): Long? {
        val otherKey = BackupCodec.categoryKey(type, "Other")
        idByKey[otherKey]?.let { return it }
        val newId = categoryDao.insert(
            CategoryEntity(
                profileId = profileId,
                name = "Other",
                type = type,
                icon = IconCatalog.OTHER.key,
                colorArgb = if (type == TxType.EXPENSE) 0x475569L else 0x14B8A6L,
                isSystem = true,
            )
        )
        if (newId != 0L) idByKey[otherKey] = newId
        return newId
    }

    private fun toEntity(profileId: Long, c: ParsedCategory): CategoryEntity =
        CategoryEntity(
            profileId = profileId,
            name = c.name,
            type = c.type,
            icon = c.icon,
            colorArgb = c.colorArgb,
            isSystem = c.isSystem,
            archived = c.archived,
        )

    suspend fun wipeAll() {
        val profiles = profileDao.observeAll().first()
        for (p in profiles) profileDao.delete(p)
        settingsDao.put(AppSettingEntity(KEY_CURRENT_PROFILE, ""))
        settingsDao.put(AppSettingEntity(KEY_USED, ""))
    }
}