package com.nabd.money.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val MONTHS = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )
    private val DAYS = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

    fun toLocal(ts: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)

    fun time(ts: Long): String {
        val d = toLocal(ts)
        val h = if (d.hour % 12 == 0) 12 else d.hour % 12
        return "%d:%02d %s".format(Locale.US, h, d.minute, if (d.hour < 12) "ص" else "م")
    }

    fun dayHeader(ts: Long): String {
        val d = toLocal(ts).toLocalDate()
        val today = LocalDate.now(zone)
        return when (d) {
            today -> "النهاردة"
            today.minusDays(1) -> "امبارح"
            else -> "${DAYS[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
        }
    }

    fun date(ts: Long): String {
        val d = toLocal(ts)
        return "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}"
    }

    fun full(ts: Long) = "${date(ts)} • ${time(ts)}"

    fun monthLabel(ym: YearMonth) = "${MONTHS[ym.monthValue - 1]} ${ym.year}"

    fun relative(ts: Long?): String {
        if (ts == null || ts == 0L) return "لسه"
        val diff = System.currentTimeMillis() - ts
        val min = diff / 60_000
        return when {
            min < 1 -> "دلوقتي"
            min < 60 -> "من $min د"
            min < 60 * 24 -> "من ${min / 60} س"
            else -> "من ${min / (60 * 24)} يوم"
        }
    }

    fun toEpoch(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayKey(ts: Long): Long = toLocal(ts).toLocalDate().toEpochDay()

    fun monthRange(ym: YearMonth): Pair<Long, Long> {
        val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from to to
    }

    fun dayRange(ts: Long): Pair<Long, Long> {
        val d = toLocal(ts).toLocalDate()
        val from = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from to to
    }
}
