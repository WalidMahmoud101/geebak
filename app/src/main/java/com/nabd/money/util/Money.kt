package com.nabd.money.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    private val symbols = DecimalFormatSymbols(Locale.US)
    private val df2 = DecimalFormat("#,##0.00", symbols)
    private val df0 = DecimalFormat("#,##0", symbols)
    private val dfCrypto = DecimalFormat("#,##0.########", symbols)

    // الرقم وعلامته جوه "عزل" من الشمال لليمين، علشان في النص العربي الـ − والـ + مايتنقلوش لآخر الرقم
    private const val LRI = "\u2066"
    private const val PDI = "\u2069"

    fun fmt(v: Double, currency: String = "EGP", decimals: Boolean = true): String = signed("", v, currency, decimals)

    /** مبلغ بعلامة: signed("−", 250.0) = "−250.00 ج.م" مقروءة صح في العربي */
    fun signed(sign: String, v: Double, currency: String = "EGP", decimals: Boolean = true): String {
        val f = if (decimals) df2 else df0
        val neg = v < 0 && sign.isEmpty()
        val s = if (neg) "−" else sign
        val n = if (neg) -v else v
        return when (val c = currency.uppercase()) {
            "EGP" -> "$LRI$s${f.format(n)}$PDI ج.م"
            "USD" -> "$LRI$s\$${f.format(n)}$PDI"
            "EUR" -> "$LRI$s€${f.format(n)}$PDI"
            "BTC", "ETH", "BNB", "SOL" -> "$LRI$s${dfCrypto.format(n)} $c$PDI"
            else -> "$LRI$s${f.format(n)} $c$PDI"
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
