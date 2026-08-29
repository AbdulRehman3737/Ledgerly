package com.ledgerly.app.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    private val usd = Currencies.USD
    private val pkr = Currencies.PKR

    @Test
    fun `toMinor keeps two decimal precision`() {
        assertEquals(12345L, Money.toMinor(123.45))
        assertEquals(100L, Money.toMinor(1.0))
    }

    @Test
    fun `format uses symbol and grouping`() {
        assertEquals("\$1,234.56", Money.format(123456, usd))
        assertEquals("Rs 1,235", Money.format(123456, pkr))
        assertEquals("\$0.00", Money.format(0, usd))
    }

    @Test
    fun `format handles negative by absolute rounding`() {
        assertEquals("\$-5.00", Money.format(-500, usd))
    }

    @Test
    fun `formatSigned adds explicit sign`() {
        assertEquals("+\$10.00", Money.formatSigned(1000, usd, positive = true))
        assertEquals("-\$10.00", Money.formatSigned(1000, usd, positive = false))
        assertEquals("-\$10.00", Money.formatSigned(-1000, usd, positive = false))
    }

    @Test
    fun `parse handles commas dots and decimals`() {
        assertEquals(123456L, Money.parse("1,234.56"))
        assertEquals(50000L, Money.parse("500"))
        assertEquals(50000L, Money.parse("500.00"))
        assertEquals(125L, Money.parse("1.25"))
    }

    @Test
    fun `parse rejects malformed input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse(".."))
    }

    @Test
    fun `parse respects currency decimals`() {
        assertEquals(1234L, Money.parse("12.34", decimals = 2))
        assertNull(Money.parse("12.345", decimals = 2))
        assertEquals(1200L, Money.parse("12", decimals = 0))
    }

    @Test
    fun `toInputString round trips`() {
        assertEquals("3.25", Money.toInputString(325, usd.decimals))
        assertEquals("15", Money.toInputString(1500, pkr.decimals))
    }

    @Test
    fun `compact formats abbreviations`() {
        assertEquals("\$1.2k", Money.compact(120_000, usd))
        assertEquals("\$5", Money.compact(500, usd))
    }

    @Test
    fun `abs works for negative and zero`() {
        assertEquals(42L, Money.abs(-42L))
        assertEquals(0L, Money.abs(0L))
    }
}