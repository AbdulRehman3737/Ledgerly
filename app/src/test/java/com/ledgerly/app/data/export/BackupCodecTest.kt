package com.ledgerly.app.data.export

import com.ledgerly.app.data.db.BudgetEntity
import com.ledgerly.app.data.db.CategoryEntity
import com.ledgerly.app.data.db.ProfileEntity
import com.ledgerly.app.data.db.TransactionEntity
import com.ledgerly.app.domain.model.BudgetPeriod
import com.ledgerly.app.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {

    private fun profile() = ProfileEntity(name = "Personal", icon = "avatar_wallet", colorArgb = 0xFF0866FF, currencyCode = "USD", createdAt = 100)

    private fun categories() = listOf(
        CategoryEntity(id = 1, profileId = 1, name = "Food", type = TxType.EXPENSE, icon = "food", colorArgb = 0xFF00FF00, isSystem = true),
        CategoryEntity(id = 2, profileId = 1, name = "Salary", type = TxType.INCOME, icon = "salary", colorArgb = 0xFF0000FF, isSystem = true),
    )

    private fun transactions() = listOf(
        TransactionEntity(id = 1, profileId = 1, categoryId = 1, type = TxType.EXPENSE, amountMinor = 1234, dateEpochDay = 20000, note = "Lunch", createdAt = 200),
    )

    private fun budgets() = listOf(
        BudgetEntity(id = 1, profileId = 1, categoryId = 1, amountMinor = 50_000, period = BudgetPeriod.MONTHLY, createdAt = 300),
    )

    @Test
    fun `encode then parse round trips`() {
        val export = ExportProfile(profile(), categories(), transactions(), budgets())
        val json = BackupCodec.encode(listOf(export))
        val parsed = BackupCodec.parse(json)

        assertEquals(1, parsed.profiles.size)
        val p = parsed.profiles.first()
        assertEquals("Personal", p.name)
        assertEquals("USD", p.currencyCode)
        assertEquals(2, p.categories.size)
        assertEquals("Food", p.categories.first().name)
        assertEquals(1, p.transactions.size)
        assertEquals(1234L, p.transactions.first().amountMinor)
    }

    @Test
    fun `overall budget round trips with reserved key`() {
        val overall = BudgetEntity(id = 2, profileId = 1, categoryId = null, amountMinor = 90_000, period = BudgetPeriod.MONTHLY, createdAt = 301)
        val export = ExportProfile(profile(), categories(), transactions(), listOf(overall))
        val json = BackupCodec.encode(listOf(export))
        val parsed = BackupCodec.parse(json)

        assertEquals(1, parsed.profiles.first().budgets.size)
        assertEquals(BackupCodec.OVERALL_KEY, parsed.profiles.first().budgets.first().categoryKey)
    }

    @Test
    fun `categories referenced by type-pipe-name key`() {
        val export = ExportProfile(profile(), categories(), transactions(), budgets())
        val json = BackupCodec.encode(listOf(export))
        val parsed = BackupCodec.parse(json)

        val tx = parsed.profiles.first().transactions.first()
        assertEquals("EXPENSE|Food", tx.categoryKey)
        assertEquals(1, parsed.profiles.first().budgets.size)
        assertEquals("EXPENSE|Food", parsed.profiles.first().budgets.first().categoryKey)
    }

    @Test(expected = BackupException::class)
    fun `rejects non backup json`() {
        BackupCodec.parse("""{"hello":"world"}""")
    }

    @Test(expected = BackupException::class)
    fun `rejects empty input`() {
        BackupCodec.parse("   ")
    }

    @Test
    fun `invalid transactions are skipped with warnings`() {
        val bad = """
            {
              "ledgerly_backup": true,
              "file_version": 1,
              "profiles": [
                {
                  "name": "P",
                  "icon": "avatar_wallet",
                  "color": 1,
                  "currency": "USD",
                  "created_at": 1,
                  "categories": [{"name":"Food","type":"EXPENSE","icon":"food","color":1,"system":true,"archived":false}],
                  "transactions": [
                    {"category":"EXPENSE|Food","type":"EXPENSE","amount":100,"date":999999999999,"note":"bad date"},
                    {"category":"EXPENSE|Food","type":"EXPENSE","amount":50,"date":20000,"note":"ok"}
                  ],
                  "budgets": []
                }
              ]
            }
        """.trimIndent()
        val parsed = BackupCodec.parse(bad)
        assertEquals(1, parsed.profiles.size)
        assertEquals(1, parsed.profiles.first().transactions.size)
        assertEquals("ok", parsed.profiles.first().transactions.first().note)
        assertEquals(true, parsed.warnings.isNotEmpty())
    }
}