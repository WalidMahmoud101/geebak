package com.nabd.money.data

import com.nabd.money.agents.ArabicNumbers
import com.nabd.money.util.Dates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Period(val ar: String) {
    TODAY("النهاردة"),
    YESTERDAY("امبارح"),
    THIS_WEEK("الأسبوع ده"),
    LAST_7("آخر 7 أيام"),
    THIS_MONTH("الشهر ده"),
    LAST_MONTH("الشهر اللي فات"),
    LAST_30("آخر 30 يوم"),
    LAST_3_MONTHS("آخر 3 شهور"),
    LAST_6_MONTHS("آخر 6 شهور"),
    THIS_YEAR("السنة دي"),
    ALL("كل الوقت"),
    MONTH("شهر"),
    CUSTOM("فترة مخصصة"),
}

/** "الكاش" في فلتر الحسابات = عمليات من غير حساب */
const val CASH_ACCOUNT_ID = -1L

/** عمليات مستبعدة من الإجمالي بس مهمة تبان في العمليات: استرداد الشركة والعهدة (فلوس الشركة اللي معاك) */
val ALWAYS_SHOWN = setOf("استرداد مصاريف الشركة", "عهدة من الشركة", "رد للشركة")

/**
 * فلتر واحد للمصاريف والتقارير: الفترة، النوع، الغرض، التصنيف، الحساب، المصدر، المبلغ، والبحث.
 * كل اللي جوه الفلتر بيتحسب في الإجمالي اللي ظاهر، ومافيش حاجة بتدخل في بعضها.
 */
data class TxnFilter(
    val period: Period = Period.THIS_MONTH,
    /** للـ MONTH: الشهر المختار */
    val month: YearMonth? = null,
    /** للـ CUSTOM: أول يوم وآخر يوم (epochDay) */
    val fromDay: Long? = null,
    val toDay: Long? = null,
    val direction: Direction? = null,
    val purposes: Set<Purpose> = emptySet(),
    val categories: Set<String> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
    val sources: Set<TxnSource> = emptySet(),
    val query: String = "",
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val companyOnly: Boolean = false,
    /** إظهار التحويلات بين حساباتك والحاجات المستبعدة من الإجمالي */
    val showExcluded: Boolean = false,
    /** مصاريف الشركة بتظهر في خانة الشركة بس، ومابتتخلطش بالعمليات إلا لو طلبت */
    val showCompany: Boolean = false,
    /** المخفي والتحويلات الداخلية بس (علشان ترجّعها أو تنقلها للشركة) */
    val onlyHidden: Boolean = false,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun days(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> = when (period) {
        Period.TODAY -> today to today
        Period.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
        Period.THIS_WEEK -> {
            // الأسبوع في مصر بيبدأ السبت
            var d = today
            while (d.dayOfWeek != DayOfWeek.SATURDAY) d = d.minusDays(1)
            d to today
        }
        Period.LAST_7 -> today.minusDays(6) to today
        Period.THIS_MONTH -> today.withDayOfMonth(1) to today
        Period.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
        Period.LAST_30 -> today.minusDays(29) to today
        Period.LAST_3_MONTHS -> today.minusMonths(3).plusDays(1) to today
        Period.LAST_6_MONTHS -> today.minusMonths(6).plusDays(1) to today
        Period.THIS_YEAR -> today.withDayOfYear(1) to today
        Period.ALL -> LocalDate.of(2000, 1, 1) to today
        Period.MONTH -> (month ?: YearMonth.from(today)).let { it.atDay(1) to minOf(it.atEndOfMonth(), today) }
        Period.CUSTOM -> {
            val f = fromDay?.let(LocalDate::ofEpochDay) ?: today.withDayOfMonth(1)
            val t = toDay?.let(LocalDate::ofEpochDay) ?: today
            if (f <= t) f to t else t to f
        }
    }

    fun range(today: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val (f, t) = days(today)
        return f.atStartOfDay(zone).toInstant().toEpochMilli() to
            t.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }

    fun dayCount(today: LocalDate = LocalDate.now()): Int {
        val (f, t) = days(today)
        return (ChronoUnit.DAYS.between(f, t) + 1).toInt().coerceAtLeast(1)
    }

    val isMonthly get() = period == Period.THIS_MONTH || period == Period.LAST_MONTH || period == Period.MONTH

    /** الفترة اللي قبلها بنفس الطول، للمقارنة */
    fun previous(today: LocalDate = LocalDate.now()): TxnFilter {
        if (isMonthly) {
            val (f, _) = days(today)
            val ym = YearMonth.from(f).minusMonths(1)
            // الشهر ده لحد النهاردة بيتقارن بنفس الأيام من الشهر اللي فات
            if (period == Period.THIS_MONTH) {
                val end = minOf(ym.atEndOfMonth(), ym.atDay(1).plusDays((dayCount(today) - 1).toLong()))
                return copy(period = Period.CUSTOM, fromDay = ym.atDay(1).toEpochDay(), toDay = end.toEpochDay())
            }
            return copy(period = Period.MONTH, month = ym)
        }
        val (f, t) = days(today)
        val len = ChronoUnit.DAYS.between(f, t) + 1
        return copy(period = Period.CUSTOM, fromDay = f.minusDays(len).toEpochDay(), toDay = f.minusDays(1).toEpochDay())
    }

    /** الأسهم: الشهر/الفترة اللي قبل أو بعد */
    fun shift(delta: Int, today: LocalDate = LocalDate.now()): TxnFilter {
        if (period == Period.ALL) return this
        val (f, t) = days(today)
        if (isMonthly) {
            val ym = YearMonth.from(f).plusMonths(delta.toLong())
            if (ym > YearMonth.from(today)) return this
            return copy(period = if (ym == YearMonth.from(today)) Period.THIS_MONTH else Period.MONTH, month = ym)
        }
        if (period == Period.THIS_YEAR || (period == Period.CUSTOM && f.dayOfYear == 1 && (t == f.withDayOfYear(f.lengthOfYear()) || t == today))) {
            val y = f.year + delta
            if (y > today.year) return this
            if (y == today.year) return copy(period = Period.THIS_YEAR)
            val start = LocalDate.of(y, 1, 1)
            return copy(period = Period.CUSTOM, fromDay = start.toEpochDay(), toDay = start.withDayOfYear(start.lengthOfYear()).toEpochDay())
        }
        val len = ChronoUnit.DAYS.between(f, t) + 1
        val nf = f.plusDays(len * delta)
        if (nf > today) return this
        val nt = minOf(t.plusDays(len * delta), today)
        return copy(period = Period.CUSTOM, fromDay = nf.toEpochDay(), toDay = nt.toEpochDay())
    }

    fun periodLabel(today: LocalDate = LocalDate.now()): String = when (period) {
        Period.MONTH -> Dates.monthLabel(month ?: YearMonth.from(today))
        Period.THIS_MONTH -> Dates.monthLabel(YearMonth.from(today))
        Period.LAST_MONTH -> Dates.monthLabel(YearMonth.from(today).minusMonths(1))
        Period.CUSTOM -> {
            val (f, t) = days(today)
            if (f == t) dayLabel(f) else "${dayLabel(f)} ← ${dayLabel(t)}"
        }
        else -> period.ar
    }

    private fun dayLabel(d: LocalDate) = "${d.dayOfMonth} ${Dates.MONTHS[d.monthValue - 1]}" +
        if (d.year != LocalDate.now().year) " ${d.year}" else ""

    /** عدد الفلاتر الشغالة غير الفترة */
    val activeCount: Int
        get() = listOf(
            direction != null, purposes.isNotEmpty(), categories.isNotEmpty(), accountIds.isNotEmpty(),
            sources.isNotEmpty(), query.isNotBlank(), minAmount != null || maxAmount != null, companyOnly, showExcluded, showCompany, onlyHidden,
        ).count { it }

    fun clearedFilters() = TxnFilter(period = period, month = month, fromDay = fromDay, toDay = toDay)

    fun matches(t: Txn, egp: Double): Boolean {
        if (onlyHidden) { if (!t.excluded || t.category in setOf("استرداد مصاريف الشركة", "عهدة من الشركة", "رد للشركة", "خصم من العهدة")) return false }
        else if (!showExcluded && t.excluded && t.category !in ALWAYS_SHOWN) return false
        if (companyOnly && !t.reimbursable) return false
        if (direction != null && t.direction != direction) return false
        if (purposes.isNotEmpty() && purposes.none { t.belongsTo(it) }) return false
        if (categories.isNotEmpty() && t.category !in categories) return false
        if (accountIds.isNotEmpty() && (t.accountId ?: CASH_ACCOUNT_ID) !in accountIds) return false
        if (sources.isNotEmpty() && t.source !in sources) return false
        val amount = if (egp > 0) egp else t.amount
        if (minAmount != null && amount < minAmount) return false
        if (maxAmount != null && amount > maxAmount) return false
        if (query.isNotBlank()) {
            val q = ArabicNumbers.norm(query.trim())
            val hay = ArabicNumbers.norm(
                listOfNotNull(t.merchant, t.note, t.category, t.purpose.ar, t.rawText).joinToString(" ")
            )
            if (q.split(' ').filter { it.isNotBlank() }.any { !hay.contains(it) }) return false
        }
        return true
    }
}

/** إجماليات الفلتر: الدخل والمصروف الشخصي، ومصاريف الشركة لوحدها */
data class FilterTotals(
    val income: Double,
    val spent: Double,
    val company: Double,
    val count: Int,
) {
    val net get() = income - spent

    companion object {
        fun of(list: List<Txn>, egp: (Txn) -> Double, companyOnly: Boolean = false) = FilterTotals(
            income = list.filter { it.direction == Direction.IN && it.countsInTotals() }.sumOf(egp),
            // في وضع "مصاريف الشركة بس" المصروف = مصاريف الشركة
            spent = list.filter { it.direction == Direction.OUT && (if (companyOnly) it.reimbursable else it.countsInTotals()) }.sumOf(egp),
            company = list.filter { it.reimbursable }.sumOf(egp),
            count = list.size,
        )
    }
}
