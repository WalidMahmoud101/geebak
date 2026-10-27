package com.nabd.money.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.data.Direction
import com.nabd.money.data.FilterTotals
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.countsInTotals
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.BarChart
import com.nabd.money.ui.components.DonutChart
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.FilterBar
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.ProgressBar
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.ScreenTitle
import com.nabd.money.ui.components.StatColumn
import com.nabd.money.ui.components.color
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.CsvExport
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun ReportsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val filter by vm.filter.collectAsStateWithLifecycle()
    val list by vm.filtered.collectAsStateWithLifecycle()
    val prev by vm.previousTotals.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categoriesInRange.collectAsStateWithLifecycle()
    val saved by vm.savedFilters.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    var budgetFor by remember { mutableStateOf<Purpose?>(null) }

    val totals = FilterTotals.of(list, vm::txnEgp, filter.companyOnly)
    // المصروف اللي بيتحسب: الشخصي، أو مصاريف الشركة لو الفلتر عليها
    val out = list.filter { it.direction == Direction.OUT && (if (filter.companyOnly) it.reimbursable else it.countsInTotals()) }
    val (fromDay, toDay) = filter.days()
    val days = filter.dayCount()
    val buckets = chartBuckets(out, fromDay, toDay, vm::txnEgp)
    val categoriesSum = out.groupBy { it.category }.mapValues { e -> e.value.sumOf { vm.txnEgp(it) } }
        .toList().sortedByDescending { it.second }
    val merchants = out.filter { !it.merchant.isNullOrBlank() }.groupBy { it.merchant!! }
        .mapValues { e -> e.value.sumOf { vm.txnEgp(it) } to e.value.size }
        .toList().sortedByDescending { it.second.first }.take(5)

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ScreenTitle("التقارير", "صورة كاملة لأي فترة") {
                IconButton(onClick = {
                    val f = CsvExport.write(ctx, list, accounts, vm::txnEgp, filter.periodLabel())
                    CsvExport.share(ctx, f)
                }) { Icon(Icons.Rounded.FileDownload, "تصدير لـ Excel", tint = NabdColors.Mint) }
            }
        }
        item {
            FilterBar(
                filter, accounts, categories, saved,
                onChange = vm::setFilter, onSave = vm::saveFilter, onDeleteSaved = vm::deleteSavedFilter
            )
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row {
                    StatColumn("الدخل", Money.fmt(totals.income, decimals = false), NabdColors.Mint, Modifier.weight(1f))
                    StatColumn("المصروف", Money.fmt(totals.spent, decimals = false), NabdColors.Pink, Modifier.weight(1f))
                    StatColumn(
                        "الصافي", Money.fmt(totals.net, decimals = false),
                        if (totals.net >= 0) NabdColors.Text else NabdColors.Danger, Modifier.weight(1f)
                    )
                }
                ComparisonLine(totals, prev, filter)
                Spacer(Modifier.height(16.dp))
                Text(buckets.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${Money.fmt(totals.spent / days, decimals = false)} في اليوم • ${totals.count} عملية",
                    style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                )
                Spacer(Modifier.height(10.dp))
                BarChart(buckets.values, Modifier.fillMaxWidth().height(120.dp), highlight = buckets.highlight)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(buckets.first, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
                    Spacer(Modifier.weight(1f))
                    Text(buckets.last, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
                }
            }
        }
        if (list.isEmpty()) {
            item { EmptyState(Icons.Rounded.FileDownload, "مافيش عمليات في الفلتر ده", "غيّر الفترة أو شيل فلتر.") }
        }
        if (out.isNotEmpty()) {
            item {
                PurposeBreakdown(out, vm::txnEgp, "حسب الغرض • دوس للفلترة") { p ->
                    vm.updateFilter { it.copy(purposes = setOf(p)) }
                }
            }
        }
        if (filter.isMonthly && !filter.companyOnly) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("الميزانيات الشهرية", style = MaterialTheme.typography.titleMedium)
                    Text("دوس على أي غرض تحدد ميزانيته", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                    Spacer(Modifier.height(12.dp))
                    Purpose.entries.forEach { p ->
                        val s = out.filter { it.purpose == p }.sumOf { vm.txnEgp(it) }
                        val limit = budgets[p.name]
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { budgetFor = p }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(p.icon(), p.color(), 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(p.ar, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    Money.fmt(s, decimals = false) + (limit?.let { " / " + Money.fmt(it, decimals = false) } ?: ""),
                                    style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            if (limit != null && limit > 0) {
                                val f = (s / limit).toFloat()
                                ProgressBar(f, if (f >= 1f) NabdColors.Danger else if (f >= 0.8f) NabdColors.Gold else p.color())
                            } else {
                                Text("من غير ميزانية", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
                            }
                        }
                    }
                }
            }
        }
        if (categoriesSum.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("حسب التصنيف • دوس للفلترة", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    val max = categoriesSum.first().second
                    val total = categoriesSum.sumOf { it.second }
                    categoriesSum.forEach { (cat, v) ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.updateFilter { it.copy(categories = setOf(cat)) } }
                                .padding(vertical = 6.dp)
                        ) {
                            Row {
                                Text(cat, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${(v / total * 100).toInt()}%  ",
                                    style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim
                                )
                                Text(Money.fmt(v, decimals = false), style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(4.dp))
                            ProgressBar((v / max).toFloat(), NabdColors.Cyan)
                        }
                    }
                }
            }
        }
        if (merchants.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("أكتر أماكن صرفت فيها", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    merchants.forEachIndexed { i, (m, pair) ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.updateFilter { it.copy(query = m) } }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(NabdColors.SurfaceTop),
                                contentAlignment = Alignment.Center
                            ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = NabdColors.Gold) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text("${pair.second} مرة", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                            }
                            Text(Money.fmt(pair.first, decimals = false), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    budgetFor?.let { p ->
        AmountDialog("ميزانية ${p.ar} في الشهر (0 = من غير)", budgets[p.name] ?: 0.0, onDismiss = { budgetFor = null }) {
            vm.setBudget(p, it); budgetFor = null
        }
    }
}

private class Buckets(val title: String, val values: List<Float>, val first: String, val last: String, val highlight: Int?)

/** الرسم بيتقسم حسب طول الفترة: أيام لحد شهر، أسابيع لحد 4 شهور، وبعد كده شهور */
private fun chartBuckets(out: List<Txn>, from: LocalDate, to: LocalDate, egp: (Txn) -> Double): Buckets {
    val days = ChronoUnit.DAYS.between(from, to).toInt() + 1
    val today = LocalDate.now()
    fun dateOf(t: Txn) = Dates.toLocal(t.timestamp).toLocalDate()
    fun short(d: LocalDate) = "${d.dayOfMonth} ${Dates.MONTHS[d.monthValue - 1]}"
    return when {
        days <= 31 -> {
            val v = FloatArray(days)
            out.forEach { t -> val i = ChronoUnit.DAYS.between(from, dateOf(t)).toInt(); if (i in 0 until days) v[i] += egp(t).toFloat() }
            val h = ChronoUnit.DAYS.between(from, today).toInt().takeIf { it in 0 until days }
            Buckets("المصروف اليومي", v.toList(), short(from), short(to), h)
        }
        days <= 124 -> {
            val n = (days + 6) / 7
            val v = FloatArray(n)
            out.forEach { t -> val i = ChronoUnit.DAYS.between(from, dateOf(t)).toInt() / 7; if (i in 0 until n) v[i] += egp(t).toFloat() }
            Buckets("المصروف كل أسبوع", v.toList(), short(from), short(to), null)
        }
        else -> {
            val start = java.time.YearMonth.from(from)
            val n = (ChronoUnit.MONTHS.between(start, java.time.YearMonth.from(to)).toInt() + 1).coerceIn(1, 60)
            val v = FloatArray(n)
            out.forEach { t ->
                val i = ChronoUnit.MONTHS.between(start, java.time.YearMonth.from(dateOf(t))).toInt()
                if (i in 0 until n) v[i] += egp(t).toFloat()
            }
            Buckets("المصروف كل شهر", v.toList(), Dates.monthLabel(start), Dates.monthLabel(java.time.YearMonth.from(to)), null)
        }
    }
}
