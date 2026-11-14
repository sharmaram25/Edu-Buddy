package com.edubuddy.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Money is always stored as a whole number of paise (1 ₹ = 100 paise) so
 * sums never drift the way floating-point rupees do.
 */
object Money {
    /** Hard upper bound for a single entry: ₹1,00,00,000 (one crore). */
    const val MAX_PAISE: Long = 1_00_00_000L * 100

    sealed interface ParseResult {
        data class Ok(val paise: Long) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    /** Parses user input like "150", "1,250.50", "₹ 99.9". */
    fun parse(input: String): ParseResult {
        val cleaned = input.trim().replace("₹", "").replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return ParseResult.Error("Enter an amount")
        val value = cleaned.toBigDecimalOrNull() ?: return ParseResult.Error("Not a valid number")
        if (value.signum() <= 0) return ParseResult.Error("Amount must be more than zero")
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) {
            return ParseResult.Error("Use at most 2 decimal places")
        }
        val paise = value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).toLong()
        if (paise > MAX_PAISE) return ParseResult.Error("That amount is too large")
        return ParseResult.Ok(paise)
    }

    /**
     * Indian digit grouping (1,25,000): last three digits, then pairs. Done by hand because
     * NumberFormat's en-IN support differs between the JVM and Android versions.
     */
    internal fun groupIndian(whole: Long): String {
        val s = whole.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val pairs = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$pairs,$last3"
    }

    /** "₹1,250" or "₹1,250.50": decimals only when non-zero. */
    fun format(paise: Long, showSign: Boolean = false): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        val whole = abs / 100
        val fraction = abs % 100
        val body = "₹" + groupIndian(whole) + if (fraction != 0L) "." + fraction.toString().padStart(2, '0') else ""
        return when {
            negative -> "−$body"
            showSign && paise > 0 -> "+$body"
            else -> body
        }
    }

    /** Whole rupees, rounded down: for estimates like "₹90/day" where paise are noise. */
    fun formatWhole(paise: Long): String = format(paise / 100 * 100)

    /** Compact form for charts and tight spaces: ₹950, ₹1.2K, ₹3.4L. */
    fun formatCompact(paise: Long): String {
        val rupees = paise / 100.0
        val abs = kotlin.math.abs(rupees)
        val sign = if (rupees < 0) "−" else ""
        return when {
            abs >= 1_00_000 -> sign + "₹" + trim(abs / 1_00_000) + "L"
            abs >= 1_000 -> sign + "₹" + trim(abs / 1_000) + "K"
            else -> sign + "₹" + abs.toLong()
        }
    }

    /** Value suitable for pre-filling an edit field: "1250" or "1250.5". */
    fun toInput(paise: Long): String =
        BigDecimal.valueOf(paise, 2).stripTrailingZeros().toPlainString()

    private fun trim(v: Double): String {
        val s = String.format(Locale.US, "%.1f", v)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}
