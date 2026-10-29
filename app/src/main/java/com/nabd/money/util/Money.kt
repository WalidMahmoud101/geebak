package com.nabd.money.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    /** طريقة عرض المبالغ: AUTO = بعملة كل حساب، EGP = الكل بالجنيه، USD = الكل بالدولار */
    var mode by androidx.compose.runtime.mutableStateOf("AUTO")
    var rate by androidx.compose.runtime.mutableStateOf(50.0)

    private val convertible = setOf("EGP", "USD", "USDT", "USDC", "EUR", "GBP", "SAR", "AED")

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
        var currency = currency
        var v = v
        if ((mode == "EGP" || mode == "USD") && currency.uppercase() in convertible && currency.uppercase() != mode) {
            val egp = com.nabd.money.data.Fx.toEgp(v, currency, rate)
            v = if (mode == "USD") egp / rate else egp
            currency = mode
        }
        return plain(sign, v, currency, decimals)
    }

    /** نفس التنسيق من غير أي تحويل (للمحوّل وشاشة العملات) */
    fun plain(sign: String, v: Double, currency: String, decimals: Boolean = true): String {
        // القروش بتظهر دايماً (13,228.84)، مش الجنيه بس. الباراميتر decimals فضل للتوافق ومابقاش بيخفي القروش
        val f = df2
        val neg = v < 0 && sign.isEmpty()
        val s = if (neg) "−" else sign
        val n = if (neg) -v else v
        return when (val c = currency.uppercase()) {
            "EGP" -> "$LRI$s${f.format(n)}$PDI ج.م"
            "USD" -> "$LRI$s\$${f.format(n)}$PDI"
            "EUR" -> "$LRI$s€${f.format(n)}$PDI"
            "GBP" -> "$LRI$s£${f.format(n)}$PDI"
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
