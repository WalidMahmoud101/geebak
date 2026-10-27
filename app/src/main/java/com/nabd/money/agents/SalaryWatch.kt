package com.nabd.money.agents

import com.nabd.money.data.SalaryConfig
import com.nabd.money.util.Dates
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

/** منطق وكيل المرتب: مين بيدفعلك، ميعاد القبض، واتأخر قد إيه */
object SalaryWatch {
    const val ID = "salary"

    private val spaces = Regex("\\s+")
    private fun norm(s: String) = MoneyParser.normalize(s).lowercase().replace(spaces, " ").trim()

    fun matchesPayer(payer: String, merchant: String?, text: String): Boolean {
        if (payer.isBlank()) return false
        val p = norm(payer)
        return norm(text).contains(p) || (merchant != null && norm(merchant).contains(p))
    }

    fun expectedDate(payday: Int, ym: YearMonth): LocalDate = ym.atDay(min(payday, ym.lengthOfMonth()))

    data class Status(
        val payday: Int,
        val expected: LocalDate,
        /** المرتب بتاع الدورة دي وصل خلاص */
        val received: Boolean,
        val daysLate: Int,
        val daysLeft: Int,
        val lastAmount: Double,
        val lastAt: Long,
    )

    fun status(cfg: SalaryConfig, today: LocalDate = LocalDate.now()): Status? {
        if (!cfg.enabled || cfg.payday !in 1..31) return null
        val thisMonth = expectedDate(cfg.payday, YearMonth.from(today))
        val lastDate = if (cfg.lastAt > 0) Dates.toLocal(cfg.lastAt).toLocalDate() else null
        // المرتب ممكن ينزل بدري لحد 10 أيام (قبل العيد مثلاً)
        val received = lastDate != null && !lastDate.isBefore(thisMonth.minusDays(10))
        return when {
            received -> {
                val next = expectedDate(cfg.payday, YearMonth.from(today).plusMonths(1))
                Status(cfg.payday, next, true, 0, ChronoUnit.DAYS.between(today, next).toInt(), cfg.lastAmount, cfg.lastAt)
            }
            today.isAfter(thisMonth) ->
                Status(cfg.payday, thisMonth, false, ChronoUnit.DAYS.between(thisMonth, today).toInt(), 0, cfg.lastAmount, cfg.lastAt)
            else ->
                Status(cfg.payday, thisMonth, false, 0, ChronoUnit.DAYS.between(today, thisMonth).toInt(), cfg.lastAmount, cfg.lastAt)
        }
    }

    /** يوم القبض = الوسيط بتاع أيام المرتبات اللي فاتت، علشان شهر متأخر مايبوظش الميعاد */
    fun learnDay(days: List<Int>): Int {
        if (days.isEmpty()) return 0
        val s = days.sorted()
        return s[s.size / 2]
    }
}
