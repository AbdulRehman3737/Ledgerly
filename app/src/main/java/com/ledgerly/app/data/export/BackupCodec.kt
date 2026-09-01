package com.ledgerly.app.data.export

import com.ledgerly.app.data.db.BudgetEntity
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.ProfileEntity
import com.ledgerly.app.data.db.TransactionEntity
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.model.BudgetPeriod
import com.ledgerly.app.domain.model.TxType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Pure JSON codec for the offline backup format. All parsing/validation happens
 * here so it can be unit tested on the JVM.
 */

data class ExportProfile(
    val profile: ProfileEntity,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
)

class BackupException(message: String) : Exception(message)

data class ParsedCategory(val name: String, val type: TxType, val icon: String, val colorArgb: Long, val isSystem: Boolean, val archived: Boolean)

data class ParsedTransaction(
    val categoryKey: String,
    val type: TxType,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val note: String,
    val createdAt: Long,
)

data class ParsedBudget(val categoryKey: String, val amountMinor: Long, val period: BudgetPeriod)

data class ParsedProfile(
    val name: String,
    val icon: String,
    val colorArgb: Long,
    val currencyCode: String,
    val createdAt: Long,
    val categories: List<ParsedCategory>,
    val transactions: List<ParsedTransaction>,
    val budgets: List<ParsedBudget>,
)

data class ParsedBackup(
    val profiles: List<ParsedProfile>,
    var warnings: MutableList<String> = mutableListOf(),
)

object BackupCodec {

    private const val FILE_VERSION = 1

    /** Reserved category key marking a budget that spans ALL monthly spending. */
    const val OVERALL_KEY = "OVERALL"

    private val MIN_DAY = LocalDate.of(1970, 1, 1).toEpochDay()
    private val MAX_DAY = LocalDate.of(2200, 12, 31).toEpochDay()

    fun categoryKey(type: TxType, name: String): String = type.name + "|" + name

    fun encode(profiles: List<ExportProfile>): String {
        val root = JSONObject()
        root.put("ledgerly_backup", true)
        root.put("file_version", FILE_VERSION)
        root.put("exported_at", System.currentTimeMillis())
        val arr = JSONArray()
        for (ep in profiles) {
            val p = ep.profile
            val profile = JSONObject()
            profile.put("name", p.name)
            profile.put("icon", p.icon)
            profile.put("color", p.colorArgb)
            profile.put("currency", p.currencyCode)
            profile.put("created_at", p.createdAt)

            val idToKey = ep.categories.associate { it.id to categoryKey(it.type, it.name) }
            val fallbackKey = categoryKey(TxType.EXPENSE, "Other")

            val categories = JSONArray()
            for (c in ep.categories.sortedBy { it.id }) {
                val j = JSONObject()
                j.put("name", c.name)
                j.put("type", c.type.name)
                j.put("icon", c.icon)
                j.put("color", c.colorArgb)
                j.put("system", c.isSystem)
                j.put("archived", c.archived)
                categories.put(j)
            }
            profile.put("categories", categories)

            val txs = JSONArray()
            for (t in ep.transactions.sortedBy { it.id }) {
                val j = JSONObject()
                j.put("category", idToKey[t.categoryId] ?: fallbackKey)
                j.put("type", t.type.name)
                j.put("amount", t.amountMinor)
                j.put("date", t.dateEpochDay)
                j.put("note", t.note)
                j.put("created_at", t.createdAt)
                txs.put(j)
            }
            profile.put("transactions", txs)

            val budgets = JSONArray()
            for (b in ep.budgets.sortedBy { it.id }) {
                val j = JSONObject()
                j.put("category", if (b.categoryId == null) OVERALL_KEY else (idToKey[b.categoryId] ?: fallbackKey))
                j.put("amount", b.amountMinor)
                j.put("period", b.period.name)
                budgets.put(j)
            }
            profile.put("budgets", budgets)

            arr.put(profile)
        }
        root.put("profiles", arr)
        return root.toString(2)
    }

    fun parse(raw: String): ParsedBackup {
        if (raw.isBlank()) throw BackupException("File is empty.")
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw BackupException("Not valid JSON: ${e.message}")
        }
        if (!root.optBoolean("ledgerly_backup", false)) {
            throw BackupException("This file is not a Ledgerly backup.")
        }
        val warningList = mutableListOf<String>()
        val profilesJson = root.optJSONArray("profiles") ?: JSONArray()
        val parsedProfiles = mutableListOf<ParsedProfile>()
        for (i in 0 until profilesJson.length()) {
            val pj = profilesJson.optJSONObject(i) ?: continue
            parsedProfiles += parseProfile(pj, warningList)
        }
        val backup = ParsedBackup(parsedProfiles, warningList)
        if (backup.profiles.isEmpty()) warningList += "No profiles found in backup file."
        return backup
    }

    private fun parseProfile(pj: JSONObject, warnings: MutableList<String>): ParsedProfile {
        val name = pj.optString("name", "").trim()
        if (name.isEmpty()) warnings += "Skipped a profile with an empty name."
        val currency = pj.optString("currency", "")
        if (currency.isEmpty()) warnings += "Profile '$name' has no currency; using default."

        val cats = mutableListOf<ParsedCategory>()
        val catsJson = pj.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until catsJson.length()) {
            val cj = catsJson.optJSONObject(i) ?: continue
            val cName = cj.optString("name", "").trim()
            if (cName.isEmpty()) { warnings += "Skipped an unnamed category in '$name'."; continue }
            val type = safeType(cj.optString("type", ""))
            if (type == null) { warnings += "Skipped category '$cName' in '$name' (bad type)."; continue }
            cats += ParsedCategory(
                name = cName,
                type = type,
                icon = cj.optString("icon", IconCatalog.OTHER.key).ifBlank { IconCatalog.OTHER.key },
                colorArgb = cj.optLong("color", 0x0EA5A4L),
                isSystem = cj.optBoolean("system", false),
                archived = cj.optBoolean("archived", false),
            )
        }

        val txs = mutableListOf<ParsedTransaction>()
        val txJson = pj.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txJson.length()) {
            val tj = txJson.optJSONObject(i) ?: continue
            val catKey = tj.optString("category", "").trim()
            val type = safeType(tj.optString("type", "")) ?: continue
            val amount = tj.optLong("amount", -1).takeIf { it >= 0 } ?: continue
            val date = tj.optLong("date", -1)
            if (catKey.isEmpty()) { warnings += "Skipped a transaction with no category in '$name'."; continue }
            if (date < MIN_DAY || date > MAX_DAY) { warnings += "Skipped a transaction in '$name' with an invalid date."; continue }
            txs += ParsedTransaction(
                categoryKey = catKey,
                type = type,
                amountMinor = amount,
                dateEpochDay = date,
                note = tj.optString("note", ""),
                createdAt = tj.optLong("created_at", System.currentTimeMillis()),
            )
        }

        val budgets = mutableListOf<ParsedBudget>()
        val budgetsJson = pj.optJSONArray("budgets") ?: JSONArray()
        for (i in 0 until budgetsJson.length()) {
            val bj = budgetsJson.optJSONObject(i) ?: continue
            val catKey = bj.optString("category", "").trim()
            val amount = bj.optLong("amount", -1).takeIf { it > 0 } ?: continue
            val periodName = bj.optString("period", BudgetPeriod.MONTHLY.name)
            val period = try { BudgetPeriod.valueOf(periodName) } catch (e: Exception) { BudgetPeriod.MONTHLY }
            if (catKey.isEmpty()) continue
            budgets += ParsedBudget(catKey, amount, period)
        }

        return ParsedProfile(
            name = name.ifEmpty { "Imported" },
            icon = pj.optString("icon", IconCatalog.AV_WALLET.key).ifBlank { IconCatalog.AV_WALLET.key },
            colorArgb = pj.optLong("color", 0x0EA5A4L),
            currencyCode = currency.ifEmpty { "USD" },
            createdAt = pj.optLong("created_at", System.currentTimeMillis()),
            categories = cats,
            transactions = txs,
            budgets = budgets,
        )
    }

    private fun safeType(value: String): TxType? = when (value) {
        TxType.INCOME.name -> TxType.INCOME
        TxType.EXPENSE.name -> TxType.EXPENSE
        else -> null
    }
}