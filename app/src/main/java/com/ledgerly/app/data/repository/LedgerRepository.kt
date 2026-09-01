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
import com.ledgerly.app.data.export.XlsxCodec
import com.ledgerly.app.data.export.XlsxRow
import com.ledgerly.app.domain.colors.Palette
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
        const val LEDGER_PROFILE_ID = 1L
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

    /** The id of the single ledger row, reusing whatever row already exists on a migrated DB. */
    private suspend fun ledgerId(): Long {
        profileDao.observeAll().first().firstOrNull()?.let { return it.id }
        return LEDGER_PROFILE_ID
    }

    /** Ensures the single ledger row exists, seeding default categories on first run. Idempotent. */
    suspend fun ensureInitialized(currencyCode: String) {
        val existing = profileDao.observeAll().first().firstOrNull()
        if (existing != null) {
            if (currencyCode.isNotBlank() && !currencyCode.equals(existing.currencyCode, ignoreCase = true)) {
                profileDao.update(existing.copy(currencyCode = Currencies.fromCode(currencyCode).code))
            }
            settingsDao.put(AppSettingEntity(KEY_USED, "1"))
            return
        }
        val id = profileDao.insert(
            ProfileEntity(
                id = LEDGER_PROFILE_ID,
                name = "Ledger",
                icon = IconCatalog.AV_WALLET.key,
                colorArgb = 0x0F5A45L,
                currencyCode = Currencies.fromCode(currencyCode).code,
                createdAt = System.currentTimeMillis(),
            )
        )
        if (id == LEDGER_PROFILE_ID) seedCategories(LEDGER_PROFILE_ID)
        settingsDao.put(AppSettingEntity(KEY_USED, "1"))
    }

    suspend fun setCurrency(code: String) {
        val profile = profileDao.getById(ledgerId()) ?: return
        profileDao.update(profile.copy(currencyCode = Currencies.fromCode(code).code))
    }

    // ---- Profiles ----

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

    suspend fun upsertOverallBudget(profileId: Long, amountMinor: Long): Long {
        require(amountMinor > 0) { "Budget amount must be positive" }
        val existing = budgetDao.getOverall(profileId)
        val entity = BudgetEntity(
            id = existing?.id ?: 0,
            profileId = profileId,
            categoryId = null,
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

    suspend fun deleteOverallBudget(profileId: Long) = budgetDao.deleteOverall(profileId)

    suspend fun deleteBudget(budgetId: Long) {
        val entity = budgetDao.getById(budgetId) ?: return
        budgetDao.delete(entity)
    }

    // ---- Export / Import ----

    suspend fun exportAll(): String {
        val id = ledgerId()
        val ledger = profileDao.getById(id) ?: return "{}"
        val bundle = listOf(
            ExportProfile(
                profile = ledger,
                categories = categoryDao.observeAll(id).first(),
                transactions = transactionDao.observeAll(id).first().map { it.transaction },
                budgets = budgetDao.observeAll(id).first(),
            )
        )
        return BackupCodec.encode(bundle)
    }

    suspend fun importJson(raw: String): ImportReport = applyImport(BackupCodec.parse(raw))

    /** Builds an .xlsx spreadsheet of every transaction in [profileId], matching the MoneyManager layout. */
    suspend fun exportXlsx(profileId: Long): ByteArray {
        val profile = profileDao.getById(profileId) ?: return ByteArray(0)
        val categories = categoryDao.observeAll(profileId).first().associateBy { it.id }
        val transactions = transactionDao.observeAll(profileId).first()
        val rows = transactions.map { twc ->
            XlsxRow(
                category = twc.category?.name ?: "Other",
                note = twc.transaction.note,
                amountMinor = twc.transaction.amountMinor,
                currencyCode = profile.currencyCode,
                type = twc.transaction.type,
                dateEpochDay = twc.transaction.dateEpochDay,
            )
        }
        return XlsxCodec.encode(rows)
    }

    /** Imports an .xlsx spreadsheet into [profileId], reusing matching categories and creating new ones. */
    suspend fun importXlsx(profileId: Long, bytes: ByteArray): ImportReport {
        val parsed = XlsxCodec.parse(bytes)
        var addedCategories = 0
        var addedTransactions = 0

        if (parsed.rows.isNotEmpty()) {
            val existing = categoryDao.getActive(profileId)
            val idByKey = mutableMapOf<String, Long>()
            val profileCurrency = profileDao.getById(profileId)?.currencyCode

            for (r in parsed.rows) {
                val key = r.type.name + "|" + r.categoryName
                val categoryId = existing.firstOrNull {
                    it.type == r.type && it.name.equals(r.categoryName, ignoreCase = true)
                }?.id ?: idByKey[key] ?: run {
                    val newId = categoryDao.insert(
                        CategoryEntity(
                            profileId = profileId,
                            name = r.categoryName,
                            type = r.type,
                            icon = if (r.type == TxType.EXPENSE) IconCatalog.OTHER.key else IconCatalog.SALARY.key,
                            colorArgb = Palette.CATEGORY_COLORS[(r.categoryName.hashCode() and 0x7fffffff) % Palette.CATEGORY_COLORS.size],
                            isSystem = false,
                        )
                    )
                    if (newId != 0L) {
                        idByKey[key] = newId
                        addedCategories++
                    }
                    newId
                }
                if (categoryId <= 0L) continue
                transactionDao.insert(
                    TransactionEntity(
                        profileId = profileId,
                        categoryId = categoryId,
                        type = r.type,
                        amountMinor = r.amountMinor,
                        dateEpochDay = r.dateEpochDay,
                        note = r.note,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                addedTransactions++
            }

            if (profileCurrency != null) {
                for (r in parsed.rows) {
                    val code = Currencies.fromCode(r.currencyCode).code
                    if (!code.equals(profileCurrency, ignoreCase = true)) {
                        parsed.warnings += "Row ${r.rowNumber}: currency '$code' differs from profile currency; amounts added as-is."
                    }
                }
            }
        } else {
            parsed.warnings += "No importable rows found in the spreadsheet."
        }

        return ImportReport(
            importedProfiles = if (addedTransactions > 0) 1 else 0,
            addedCategories = addedCategories,
            addedTransactions = addedTransactions,
            addedBudgets = 0,
            warnings = parsed.warnings,
        )
    }

    private suspend fun applyImport(backup: ParsedBackup): ImportReport {
        ensureInitialized("") // guarantee the single ledger row exists
        val ledgerId = ledgerId()
        var addedCategories = 0
        var addedTransactions = 0
        var addedBudgets = 0

        if (backup.profiles.size > 1) {
            backup.warnings += "This backup contains ${backup.profiles.size} profiles, but Ledgerly now keeps a single ledger. Only the first profile was imported."
        }
        val profile = backup.profiles.firstOrNull()
            ?: return ImportReport(0, 0, 0, 0, backup.warnings)

        val idByKey = mutableMapOf<String, Long>()
        for (c in profile.categories) {
            val key = BackupCodec.categoryKey(c.type, c.name)
            if (idByKey.containsKey(key)) continue
            val newId = categoryDao.insert(toEntity(ledgerId, c))
            if (newId != 0L) idByKey[key] = newId
        }

        for (t in profile.transactions) {
            val catId = idByKey[t.categoryKey]
                ?: resolveImportCategory(ledgerId, t.type, idByKey)
                ?: continue
            transactionDao.insert(
                TransactionEntity(
                    profileId = ledgerId,
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
            val categoryId = when {
                b.categoryKey.equals(BackupCodec.OVERALL_KEY, ignoreCase = true) -> null
                else -> idByKey[b.categoryKey]
            } ?: continue
            budgetDao.insert(
                BudgetEntity(
                    profileId = ledgerId,
                    categoryId = categoryId,
                    amountMinor = b.amountMinor,
                    period = b.period,
                    createdAt = System.currentTimeMillis(),
                )
            )
            addedBudgets++
        }
        return ImportReport(
            importedProfiles = 1,
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
        settingsDao.put(AppSettingEntity(KEY_USED, ""))
    }
}