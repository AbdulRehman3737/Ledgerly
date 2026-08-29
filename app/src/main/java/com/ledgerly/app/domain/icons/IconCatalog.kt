package com.ledgerly.app.domain.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A curated, fully local icon catalog. Icon identifiers are stored as strings in the
 * database and mapped back to [ImageVector] here — nothing is ever fetched remotely.
 */
data class IconDef(val key: String, val vector: ImageVector, val label: String)

object IconCatalog {

    private fun def(key: String, vector: ImageVector, label: String) = IconDef(key, vector, label)

    // Expense category icons
    val FOOD = def("food", Icons.Filled.Restaurant, "Food")
    val GROCERY = def("grocery", Icons.Filled.ShoppingBasket, "Groceries")
    val TRANSPORT = def("transport", Icons.Filled.DirectionsBus, "Transport")
    val FUEL = def("fuel", Icons.Filled.LocalGasStation, "Fuel")
    val SHOPPING = def("shopping", Icons.Filled.ShoppingBag, "Shopping")
    val BILLS = def("bills", Icons.AutoMirrored.Filled.ReceiptLong, "Bills")
    val RENT = def("rent", Icons.Filled.Home, "Rent")
    val ENTERTAINMENT = def("entertainment", Icons.Filled.Movie, "Entertainment")
    val HEALTH = def("health", Icons.Filled.Favorite, "Health")
    val EDUCATION = def("education", Icons.Filled.School, "Education")
    val TRAVEL = def("travel", Icons.Filled.Flight, "Travel")
    val SUBSCRIPTIONS = def("subscriptions", Icons.Filled.Subscriptions, "Subscriptions")
    val ELECTRONICS = def("electronics", Icons.Filled.Devices, "Electronics")
    val CLOTHING = def("clothing", Icons.Filled.Checkroom, "Clothing")
    val GIFTS = def("gifts", Icons.Filled.CardGiftcard, "Gifts")
    val OTHER = def("other", Icons.Filled.Category, "Other")

    // Income category icons
    val SALARY = def("salary", Icons.Filled.Payments, "Salary")
    val FREELANCE = def("freelance", Icons.Filled.Work, "Freelance")
    val BUSINESS = def("business", Icons.Filled.Storefront, "Business")
    val INVESTMENT = def("investment", Icons.AutoMirrored.Filled.TrendingUp, "Investment")
    val REFUND = def("refund", Icons.AutoMirrored.Filled.Undo, "Refund")

    // Profile avatar icons
    val AV_WALLET = def("avatar_wallet", Icons.Filled.AccountBalanceWallet, "Wallet")
    val AV_BRIEFCASE = def("avatar_briefcase", Icons.Filled.BusinessCenter, "Business")
    val AV_HOME = def("avatar_home", Icons.Filled.Home, "Home")
    val AV_STAR = def("avatar_star", Icons.Filled.Star, "Star")
    val AV_PERSON = def("avatar_person", Icons.Filled.Person, "Personal")
    val AV_CART = def("avatar_cart", Icons.Filled.ShoppingCart, "Cart")
    val AV_PALETTE = def("avatar_palette", Icons.Filled.Palette, "Palette")
    val AV_ROCKET = def("avatar_rocket", Icons.Filled.RocketLaunch, "Rocket")
    val AV_HEART = def("avatar_heart", Icons.Filled.Favorite, "Heart")
    val AV_PETS = def("avatar_pets", Icons.Filled.Pets, "Pets")
    val AV_DIAMOND = def("avatar_diamond", Icons.Filled.Diamond, "Diamond")
    val AV_FLIGHT = def("avatar_flight", Icons.Filled.Flight, "Travel")

    val ALL: List<IconDef> = listOf(
        FOOD, GROCERY, TRANSPORT, FUEL, SHOPPING, BILLS, RENT, ENTERTAINMENT, HEALTH,
        EDUCATION, TRAVEL, SUBSCRIPTIONS, ELECTRONICS, CLOTHING, GIFTS, OTHER,
        SALARY, FREELANCE, BUSINESS, INVESTMENT, REFUND,
    )

    val CATEGORY_PICKER: List<IconDef> = ALL

    val PROFILE_ICONS: List<IconDef> = listOf(
        AV_WALLET, AV_BRIEFCASE, AV_HOME, AV_STAR, AV_PERSON, AV_CART,
        AV_PALETTE, AV_ROCKET, AV_HEART, AV_PETS, AV_DIAMOND, AV_FLIGHT,
    )

    val EXPENSE_DEFAULT_KEYS: List<String> = listOf(
        FOOD.key, GROCERY.key, TRANSPORT.key, FUEL.key, SHOPPING.key, BILLS.key,
        RENT.key, ENTERTAINMENT.key, HEALTH.key, EDUCATION.key, TRAVEL.key,
        SUBSCRIPTIONS.key, ELECTRONICS.key, CLOTHING.key, GIFTS.key, OTHER.key,
    )

    val INCOME_DEFAULT_KEYS: List<String> = listOf(
        SALARY.key, FREELANCE.key, BUSINESS.key, INVESTMENT.key, GIFTS.key, REFUND.key, OTHER.key,
    )

    private val byKeyMap: Map<String, IconDef> = ALL.associateBy { it.key }

    fun byKey(key: String?): IconDef = byKeyMap[key] ?: OTHER

    fun vector(key: String?): ImageVector = byKey(key).vector

    fun label(key: String?): String = byKey(key).label
}