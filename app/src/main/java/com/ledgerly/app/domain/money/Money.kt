package com.ledgerly.app.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money is stored as [Long] minor units (hundredths) so that no floating point
 * arithmetic ever happens on monetary values. All calculations are integer based.
 */
object Money {

    /** Store scale: one major unit == 100 minor units. */
    const val SCALE: Long = 100L

    fun toMinor(amount: Double): Long = (amount * SCALE).toLong()

    private fun decimalFormat(decimals: Int): DecimalFormat {
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = when (decimals) {
            0 -> "#,##0"
            1 -> "#,##0.0"
            2 -> "#,##0.00"
            else -> "#,##0." + "0".repeat(decimals)
        }
        return DecimalFormat(pattern, symbols)
    }

    /** Formats a minor-unit value with currency symbol, e.g. "$1,234.56" or "Rs 1,234". */
    fun format(amountMinor: Long, currency: CurrencyInfo): String {
        val major = BigDecimal(amountMinor)
            .divide(BigDecimal(SCALE), currency.decimals, RoundingMode.HALF_UP)
        val body = decimalFormat(currency.decimals).format(major.toPlainString().toDoubleOrNull() ?: 0.0)
        return if (currency.symbol.length == 1) currency.symbol + body else currency.symbol + " " + body
    }

    /** Formats with an explicit +/- sign used for income/expense coloring. */
    fun formatSigned(amountMinor: Long, currency: CurrencyInfo, positive: Boolean): String {
        val sign = if (amountMinor < 0) "-" else if (positive) "+" else "-"
        return sign + format(abs(amountMinor), currency)
    }

    fun abs(v: Long): Long = if (v < 0) -v else v

    /**
     * Parses user typed input like "1,234.56" or "500" into minor units.
     * Returns null when the input is invalid (empty, malformed, > 2 decimal places).
     */
    fun parse(input: String, decimals: Int = 2): Long? {
        val cleaned = input.replace(",", " ").trim().replace(" ", "")
        if (cleaned.isEmpty() || cleaned.all { it == '.' }) return null
        val regex = Regex("""^\d+\.?\d{0,$decimals}$""")
        if (!regex.matches(cleaned)) return null
        val value = BigDecimal(cleaned)
        if (value.signum() < 0) return null
        val minor = value
            .multiply(BigDecimal(SCALE))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        if (minor > 999_999_999_999_99L) return null
        return minor
    }

    /** Raw numeric string without symbol or grouping, used inside the input field. */
    fun toInputString(amountMinor: Long, decimals: Int = 2): String {
        val major = BigDecimal(amountMinor).divide(BigDecimal(SCALE), decimals, RoundingMode.HALF_UP)
        val d = decimalFormat(decimals)
        return d.format(major.toPlainString().toDoubleOrNull() ?: 0.0).replace(",", "")
    }

    /** Compact representation for chart axis labels, e.g. 12.5k, 3.2M. */
    fun compact(amountMinor: Long, currency: CurrencyInfo): String {
        val major = BigDecimal(amountMinor).divide(BigDecimal(SCALE), 2, RoundingMode.HALF_UP).toPlainString()
        val value = major.toDoubleOrNull() ?: 0.0
        val abs = kotlin.math.abs(value)
        val suffix: String
        val scaled: Double
        when {
            abs >= 1_000_000_000 -> { scaled = value / 1_000_000_000; suffix = "B" }
            abs >= 1_000_000 -> { scaled = value / 1_000_000; suffix = "M" }
            abs >= 1_000 -> { scaled = value / 1_000; suffix = "k" }
            else -> { scaled = value; suffix = "" }
        }
        val body = if (suffix.isEmpty()) {
            decimalFormat(currency.decimals).format(scaled).trimEnd('0', '.')
        } else {
            DecimalFormat("#.#").format(scaled)
        }
        return if (currency.symbol.length == 1) currency.symbol + body + suffix else currency.symbol + " " + body + suffix
    }
}