package com.nabd.money.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val df2 = DecimalFormat("#,##0.00", symbols)
    private val df0 = DecimalFormat("#,##0", symbols)
    private val dfCrypto = DecimalFormat("#,##0.########", symbols)

    fun fmt(v: Double, currency: String = "EGP", decimals: Boolean = true): String {
        val f = if (decimals) df2 else df0
        return when (val c = currency.uppercase()) {
            "EGP" -> "${f.format(v)} ج.م"
            "USD" -> "\$" + f.format(v)
            "EUR" -> "€" + f.format(v)
            "BTC", "ETH", "BNB", "SOL" -> "${dfCrypto.format(v)} $c"
            else -> "${f.format(v)} $c"
        }
    }

    /** للأرقام الكبيرة في الرسوم: 12.5K */
    fun compact(v: Double): String = when {
        v >= 1_000_000 -> DecimalFormat("0.#", symbols).format(v / 1_000_000) + "M"
        v >= 1_000 -> DecimalFormat("0.#", symbols).format(v / 1_000) + "K"
        else -> df0.format(v)
    }

    fun parseInput(s: String): Double? =
        com.nabd.money.agents.MoneyParser.normalize(s).replace(",", "").trim().toDoubleOrNull()
}
