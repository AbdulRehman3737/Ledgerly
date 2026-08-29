package com.ledgerly.app.domain.money

/**
 * Locally defined currency information. No exchange rates, no network — these are
 * purely display settings chosen by the user.
 */
data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
)

object Currencies {
    val PKR = CurrencyInfo("PKR", "Pakistani Rupee", "Rs", 0)
    val USD = CurrencyInfo("USD", "US Dollar", "$", 2)
    val EUR = CurrencyInfo("EUR", "Euro", "EUR", 2)
    val GBP = CurrencyInfo("GBP", "British Pound", "GBP", 2)
    val AED = CurrencyInfo("AED", "UAE Dirham", "AED", 2)
    val SAR = CurrencyInfo("SAR", "Saudi Riyal", "SAR", 2)

    val all: List<CurrencyInfo> = listOf(PKR, USD, EUR, GBP, AED, SAR)

    fun fromCode(code: String?): CurrencyInfo =
        all.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: USD

    fun fromName(name: String?): CurrencyInfo =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: USD
}