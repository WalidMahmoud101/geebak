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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.data.Account
import com.nabd.money.data.CashFlow
import com.nabd.money.data.Direction
import com.nabd.money.data.FilterTotals
import com.nabd.money.data.FlowLine
import com.nabd.money.data.NetWorth
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.countsInTotals
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.BarChart
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.FilterBar
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.ProgressBar
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.ScreenTitle
import com.nabd.money.ui.components.color
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.CsvExport
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private val TABS = listOf("الداخل والخارج", "الحسابات", "تحليل")

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
    var tab by rememberSaveable { mutableStateOf(0) }
    var budgetFor by remember { mutableStateOf<Purpose?>(null) }

    val flow = CashFlow.of(list, vm::txnEgp)
    val totals = FilterTotals.of(list, vm::txnEgp, filter.companyOnly)

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenTitle("التقارير") {
                IconButton(onClick = {
                    CsvExport.share(ctx, CsvExport.write(ctx, list, accounts, vm::txnEgp, filter.periodLabel()))
                }) { Icon(Icons.Rounded.FileDownload, "تصدير لـ Excel", tint = NabdColors.TextDim) }
            }
        }
        item {
            FilterBar(
                filter, accounts, categories, saved,
                onChange = vm::setFilter, onSave = vm::saveFilter, onDeleteSaved = vm::deleteSavedFilter
            )
        }
        item { Tabs(tab) { tab = it } }

        if (list.isEmpty()) {
            item { EmptyState(Icons.Rounded.FileDownload, "مافيش عمليات في الفترة دي", "غيّر الفترة أو شيل فلتر.") }
        } else when (tab) {
            0 -> {
                item { NetSummary(flow, totals, prev, filter) }
                item { FlowSection("الداخل", flow.inflow, flow.totalIn, true) { line -> vm.updateFilter { it.copy(categories = setOf(line.label), direction = Direction.IN) } } }
                item { FlowSection("الخارج", flow.outflow, flow.totalOut, false) { line ->
                    vm.updateFilter { f -> if (line.purpose != null && line.children.isNotEmpty()) f.copy(purposes = setOf(line.purpose)) else f.copy(categories = setOf(line.label)) }
                } }
                if (flow.transfers > 0 || flow.companyOut > 0 || flow.companyRefund > 0) {
                    item { OutsideSection(flow) }
                }
            }
            1 -> item { AccountsFlow(flow, accounts, vm) }
            else -> {
                item { ChartCard(list, filter, totals, vm) }
                item {
                    val out = list.filter { it.direction == Direction.OUT && (if (filter.companyOnly) it.reimbursable else it.countsInTotals()) }
                    PurposeBreakdown(out, vm::txnEgp, "حسب الغرض") { p -> vm.updateFilter { it.copy(purposes = setOf(p)) } }
                }
                if (filter.isMonthly && !filter.companyOnly) item { BudgetsCard(list, budgets, vm) { budgetFor = it } }
                item { TopMerchants(list, vm) }
            }
        }
    }

    budgetFor?.let { p ->
        AmountDialog("ميزانية ${p.ar} في الشهر (0 = من غير)", budgets[p.name] ?: 0.0, onDismiss = { budgetFor = null }) {
            vm.setBudget(p, it); budgetFor = null
        }
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NabdColors.SurfaceTop)
            .padding(4.dp)
    ) {
        TABS.forEachIndexed { i, t ->
            val on = i == selected
            Text(
                t,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) NabdColors.Text else NabdColors.TextDim,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) NabdColors.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

/** الصافي فوق: الداخل − الخارج، ووفرت كام في المية */
@Composable
private fun NetSummary(flow: CashFlow, totals: FilterTotals, prev: FilterTotals?, filter: com.nabd.money.data.TxnFilter) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SummaryCell("الداخل", Money.signed("+", flow.totalIn, decimals = false), NabdColors.Mint, Modifier.weight(1f))
            CellDivider()
            SummaryCell("الخارج", Money.signed("−", flow.totalOut, decimals = false), NabdColors.Text, Modifier.weight(1f))
            CellDivider()
            SummaryCell(
                "الصافي", Money.signed(if (flow.net >= 0) "+" else "−", kotlin.math.abs(flow.net), decimals = false),
                if (flow.net >= 0) NabdColors.Mint else NabdColors.Danger, Modifier.weight(1f)
            )
        }
        flow.savingsRate?.let { r ->
            Spacer(Modifier.height(14.dp))
            ProgressBar((1 - r).toFloat().coerceIn(0f, 1f), if (r >= 0) NabdColors.Mint else NabdColors.Danger)
            Text(
                if (r >= 0) "صرفت ${((1 - r) * 100).toInt()}% من دخلك ووفرت ${(r * 100).toInt()}%"
                else "صرفت أكتر من دخلك بـ ${Money.fmt(-flow.net, decimals = false)}",
                style = MaterialTheme.typography.labelMedium,
                color = NabdColors.TextDim,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        ComparisonLine(totals, prev, filter)
    }
}

/** قسم الداخل أو الخارج: كل سطر بمبلغه ونسبته، والخارج بيتفتح على التصنيفات */
@Composable
private fun FlowSection(title: String, lines: List<FlowLine>, total: Double, income: Boolean, onFilter: (FlowLine) -> Unit) {
    var open by remember { mutableStateOf(setOf<String>()) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                Money.signed(if (income) "+" else "−", total, decimals = false),
                style = MaterialTheme.typography.titleMedium,
                color = if (income) NabdColors.Mint else NabdColors.Text
            )
        }
        if (lines.isEmpty()) {
            Text(if (income) "مافيش دخل في الفترة دي" else "مافيش مصاريف", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(6.dp))
        lines.forEachIndexed { i, line ->
            if (i > 0) HorizontalDivider(color = NabdColors.Stroke)
            val expandable = line.children.size > 1
            val isOpen = line.key in open
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { if (expandable) open = if (isOpen) open - line.key else open + line.key else onFilter(line) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (line.purpose != null) {
                    IconBubble(line.purpose.icon(), line.purpose.color(), 34.dp)
                } else {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (income) NabdColors.Mint else NabdColors.TextDim))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${line.count} عملية • ${if (total > 0) (line.amount / total * 100).toInt() else 0}%",
                        style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
                    )
                }
                Text(Money.fmt(line.amount, decimals = false), style = MaterialTheme.typography.titleSmall)
                if (expandable) {
                    Icon(
                        if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
                        tint = NabdColors.TextFaint, modifier = Modifier.padding(start = 4.dp).size(20.dp)
                    )
                }
            }
            if (isOpen) {
                line.children.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onFilter(c) }
                            .padding(start = 44.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.label, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim, modifier = Modifier.weight(1f))
                        Text(Money.fmt(c.amount, decimals = false), style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** حاجات اتحركت بس مابتغيرش فلوسك */
@Composable
private fun OutsideSection(flow: CashFlow) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("برا الحساب", style = MaterialTheme.typography.titleSmall)
        Text("فلوس اتحركت بس مابتغيرش اللي معاك", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
        Spacer(Modifier.height(8.dp))
        if (flow.transfers > 0) OutsideLine("تحويلات بين حساباتك", "${flow.transfersCount} تحويل", flow.transfers)
        if (flow.companyOut > 0) OutsideLine("مصاريف الشركة", "${flow.companyCount} عملية هتستردها", flow.companyOut)
        if (flow.companyRefund > 0) OutsideLine("اللي رجع من الشركة", "استرداد", flow.companyRefund)
    }
}

@Composable
private fun OutsideLine(label: String, sub: String, amount: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
        }
        Text(Money.fmt(amount, decimals = false), style = MaterialTheme.typography.titleSmall, color = NabdColors.TextDim)
    }
}

/** لكل حساب: دخل كام وخرج كام في الفترة */
@Composable
private fun AccountsFlow(flow: CashFlow, accounts: List<Account>, vm: AppViewModel) {
    val byId = accounts.associateBy { it.id }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("حركة كل حساب", style = MaterialTheme.typography.titleMedium)
        Text("كل اللي دخل وخرج من الحساب في الفترة، حتى التحويلات بين حساباتك", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("الحساب", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint, modifier = Modifier.weight(1.4f))
            Text("داخل", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("خارج", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        flow.perAccount.forEachIndexed { i, af ->
            if (i > 0) HorizontalDivider(color = NabdColors.Stroke)
            val a = af.accountId?.let { byId[it] }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.updateFilter { it.copy(accountIds = setOf(af.accountId ?: com.nabd.money.data.CASH_ACCOUNT_ID)) } }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1.4f)) {
                    Text(a?.name ?: "كاش", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "الصافي " + Money.signed(if (af.net >= 0) "+" else "−", kotlin.math.abs(af.net), decimals = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (af.net >= 0) NabdColors.Mint else NabdColors.TextDim
                    )
                }
                Text(
                    if (af.inflow > 0) Money.signed("+", af.inflow, decimals = false) else "—",
                    style = MaterialTheme.typography.labelLarge, color = NabdColors.Mint,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End
                )
                Text(
                    if (af.outflow > 0) Money.signed("−", af.outflow, decimals = false) else "—",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun ChartCard(list: List<Txn>, filter: com.nabd.money.data.TxnFilter, totals: FilterTotals, vm: AppViewModel) {
    val out = list.filter { it.direction == Direction.OUT && (if (filter.companyOnly) it.reimbursable else it.countsInTotals()) }
    val (from, to) = filter.days()
    val days = filter.dayCount()
    val b = chartBuckets(out, from, to, vm::txnEgp)
    GlassCard(Modifier.fillMaxWidth()) {
        Text(b.title, style = MaterialTheme.typography.titleMedium)
        Text(
            "${Money.fmt(totals.spent / days, decimals = false)} في المتوسط كل يوم",
            style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
        )
        Spacer(Modifier.height(14.dp))
        BarChart(b.values, Modifier.fillMaxWidth().height(110.dp), highlight = b.highlight)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(b.first, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
            Spacer(Modifier.weight(1f))
            Text(b.last, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
        }
    }
}

@Composable
private fun BudgetsCard(list: List<Txn>, budgets: Map<String, Double>, vm: AppViewModel, onEdit: (Purpose) -> Unit) {
    val out = list.filter { it.direction == Direction.OUT && it.countsInTotals() }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("الميزانيات", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Purpose.entries.filter { it != Purpose.OTHER }.forEach { p ->
            val s = out.filter { it.purpose == p }.sumOf { vm.txnEgp(it) }
            val limit = budgets[p.name]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onEdit(p) }
                    .padding(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(p.icon(), null, tint = p.color(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(p.ar, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (limit != null && limit > 0) "${Money.fmt(s, decimals = false)} من ${Money.fmt(limit, decimals = false)}" else "حدد ميزانية",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (limit != null && limit > 0) NabdColors.Text else NabdColors.TextFaint
                    )
                }
                if (limit != null && limit > 0) {
                    Spacer(Modifier.height(6.dp))
                    val f = (s / limit).toFloat()
                    ProgressBar(f, if (f >= 1f) NabdColors.Danger else if (f >= 0.8f) NabdColors.Gold else p.color())
                }
            }
        }
    }
}

@Composable
private fun TopMerchants(list: List<Txn>, vm: AppViewModel) {
    val merchants = list.filter { it.direction == Direction.OUT && it.countsInTotals() && !it.merchant.isNullOrBlank() }
        .groupBy { it.merchant!! }
        .mapValues { e -> e.value.sumOf { vm.txnEgp(it) } to e.value.size }
        .toList().sortedByDescending { it.second.first }.take(5)
    if (merchants.isEmpty()) return
    GlassCard(Modifier.fillMaxWidth()) {
        Text("أكتر أماكن صرفت فيها", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        merchants.forEachIndexed { i, (m, pair) ->
            if (i > 0) HorizontalDivider(color = NabdColors.Stroke)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.updateFilter { it.copy(query = m) } }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextFaint, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(m, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pair.second} مرة", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                }
                Text(Money.fmt(pair.first, decimals = false), style = MaterialTheme.typography.titleSmall)
            }
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
            Buckets("المصروف كل يوم", v.toList(), short(from), short(to), h)
        }
        days <= 124 -> {
            val n = (days + 6) / 7
            val v = FloatArray(n)
            out.forEach { t -> val i = ChronoUnit.DAYS.between(from, dateOf(t)).toInt() / 7; if (i in 0 until n) v[i] += egp(t).toFloat() }
            Buckets("المصروف كل أسبوع", v.toList(), short(from), short(to), null)
        }
        else -> {
            val start = YearMonth.from(from)
            val n = (ChronoUnit.MONTHS.between(start, YearMonth.from(to)).toInt() + 1).coerceIn(1, 60)
            val v = FloatArray(n)
            out.forEach { t ->
                val i = ChronoUnit.MONTHS.between(start, YearMonth.from(dateOf(t))).toInt()
                if (i in 0 until n) v[i] += egp(t).toFloat()
            }
            Buckets("المصروف كل شهر", v.toList(), Dates.monthLabel(start), Dates.monthLabel(YearMonth.from(to)), null)
        }
    }
}
