package com.ledgerly.app.data.db

import android.graphics.Color
import com.ledgerly.app.domain.icons.IconCatalog
import com.ledgerly.app.domain.model.TxType

/** Default per-profile category catalog seeded when a new profile is created. */
object Seed {

    data class SeedRow(
        val name: String,
        val type: TxType,
        val icon: String,
        val colorHex: String,
    )

    val DEFAULT_CATEGORIES: List<SeedRow> = listOf(
        SeedRow("Food", TxType.EXPENSE, IconCatalog.FOOD.key, "#F59E0B"),
        SeedRow("Groceries", TxType.EXPENSE, IconCatalog.GROCERY.key, "#22C55E"),
        SeedRow("Transport", TxType.EXPENSE, IconCatalog.TRANSPORT.key, "#0891B2"),
        SeedRow("Fuel", TxType.EXPENSE, IconCatalog.FUEL.key, "#EA580C"),
        SeedRow("Shopping", TxType.EXPENSE, IconCatalog.SHOPPING.key, "#DB2777"),
        SeedRow("Bills", TxType.EXPENSE, IconCatalog.BILLS.key, "#4F46E5"),
        SeedRow("Rent", TxType.EXPENSE, IconCatalog.RENT.key, "#0F766E"),
        SeedRow("Entertainment", TxType.EXPENSE, IconCatalog.ENTERTAINMENT.key, "#8B5CF6"),
        SeedRow("Health", TxType.EXPENSE, IconCatalog.HEALTH.key, "#EF4444"),
        SeedRow("Education", TxType.EXPENSE, IconCatalog.EDUCATION.key, "#2563EB"),
        SeedRow("Travel", TxType.EXPENSE, IconCatalog.TRAVEL.key, "#06B6D4"),
        SeedRow("Subscriptions", TxType.EXPENSE, IconCatalog.SUBSCRIPTIONS.key, "#7C3AED"),
        SeedRow("Electronics", TxType.EXPENSE, IconCatalog.ELECTRONICS.key, "#64748B"),
        SeedRow("Clothing", TxType.EXPENSE, IconCatalog.CLOTHING.key, "#D946EF"),
        SeedRow("Gifts", TxType.EXPENSE, IconCatalog.GIFTS.key, "#F43F5E"),
        SeedRow("Other", TxType.EXPENSE, IconCatalog.OTHER.key, "#475569"),
        SeedRow("Salary", TxType.INCOME, IconCatalog.SALARY.key, "#16A34A"),
        SeedRow("Freelance", TxType.INCOME, IconCatalog.FREELANCE.key, "#06B6D4"),
        SeedRow("Business", TxType.INCOME, IconCatalog.BUSINESS.key, "#F59E0B"),
        SeedRow("Investment", TxType.INCOME, IconCatalog.INVESTMENT.key, "#8B5CF6"),
        SeedRow("Gifts", TxType.INCOME, IconCatalog.GIFTS.key, "#F43F5E"),
        SeedRow("Refund", TxType.INCOME, IconCatalog.REFUND.key, "#2563EB"),
        SeedRow("Other", TxType.INCOME, IconCatalog.OTHER.key, "#14B8A6"),
    )

    fun colorOf(hex: String): Long =
        Color.parseColor(hex).toLong()
}