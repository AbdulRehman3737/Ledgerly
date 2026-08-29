package com.ledgerly.app.domain.colors

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color

/**
 * A fixed local palette of category and profile colors. Stored as packed ARGB long
 * values so they survive round trips through the database and JSON export.
 */
object Palette {

    val CATEGORY_COLORS: List<Long> = listOf(
        color(0xFF0EA5A4), color(0xFF2563EB), color(0xFF7C3AED), color(0xFFDB2777),
        color(0xFFEA580C), color(0xFF84CC16), color(0xFF0D9488), color(0xFFF59E0B),
        color(0xFFEF4444), color(0xFF0891B2), color(0xFF16A34A), color(0xFFEAB308),
        color(0xFF8B5CF6), color(0xFF14B8A6), color(0xFFF43F5E), color(0xFF475569),
    )

    val PROFILE_COLORS: List<Long> = listOf(
        color(0xFF0EA5A4), color(0xFF2563EB), color(0xFF7C3AED), color(0xFFDB2777),
        color(0xFFEA580C), color(0xFF16A34A), color(0xFFEAB308), color(0xFF0891B2),
    )

    fun color(argb: Long): Long = argb.toLong()

    fun fromArgb(argb: Long): Color = Color(argb.toInt())

    fun longToCsv(argb: Long): String = String.format("#%08X", argb)

    fun csvToLong(hex: String): Long? = try { AndroidColor.parseColor(hex).toLong() } catch (e: IllegalArgumentException) { null }
}