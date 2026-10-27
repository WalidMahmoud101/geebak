package com.nabd.money.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.data.Direction
import com.nabd.money.data.FilterTotals
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnFilter
import com.nabd.money.data.countsInTotals
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.FilterBar
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.ProgressBar
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.ScreenTitle
import com.nabd.money.ui.components.StatColumn
import com.nabd.money.ui.components.TxnRow
import com.nabd.money.ui.components.color
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money

/**
 * المصاريف: كل العمليات حسب الفلتر.
 * لما تختار غرض واحد (البيت مثلاً) بيبقى "صندوق": اتضاف كام، اتصرف كام، والباقي.
 */
@Composable
fun LedgerScreen(vm: AppViewModel, onAdd: (Direction, Purpose?) -> Unit, onEdit: (Txn) -> Unit) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val list by vm.filtered.collectAsStateWithLifecycle()
    val prev by vm.previousTotals.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categoriesInRange.collectAsStateWithLifecycle()
    val saved by vm.savedFilters.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()

    val totals = FilterTotals.of(list, vm::txnEgp, filter.companyOnly)
    val names = accounts.associate { it.id to it.name }
    val byDay = list.groupBy { Dates.dayKey(it.timestamp) }.toSortedMap(compareByDescending { it })
    val single = filter.purposes.singleOrNull()

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("المصاريف", "كل جنيه رايح فين وإمتى") }
        item {
            FilterBar(
                filter, accounts, categories, saved,
                onChange = vm::setFilter, onSave = vm::saveFilter, onDeleteSaved = vm::deleteSavedFilter
            )
        }
        item {
            val c = single?.color() ?: NabdColors.Mint
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(single?.icon() ?: Icons.Rounded.ReceiptLong, c, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            filter.companyOnly -> "مصاريف الشركة"
                            single != null -> "صندوق ${single.ar}"
                            else -> "الإجمالي"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${totals.count} عملية", style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim)
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    StatColumn("اتضاف", Money.fmt(totals.income, decimals = false), NabdColors.Mint, Modifier.weight(1f))
                    StatColumn("اتصرف", Money.fmt(totals.spent, decimals = false), NabdColors.Pink, Modifier.weight(1f))
                    StatColumn(
                        "الباقي", Money.fmt(totals.net, decimals = false),
                        if (totals.net >= 0) NabdColors.Text else NabdColors.Danger, Modifier.weight(1f)
                    )
                }
                ComparisonLine(totals, prev, filter)
                if (totals.company > 0 && !filter.companyOnly) {
                    Text(
                        "+ ${Money.fmt(totals.company, decimals = false)} مصاريف شركة لوحدها",
                        style = MaterialTheme.typography.labelSmall, color = NabdColors.Cyan,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                val limit = single?.let { budgets[it.name] }
                if (limit != null && limit > 0 && filter.isMonthly) {
                    Spacer(Modifier.height(12.dp))
                    val frac = (totals.spent / limit).toFloat()
                    ProgressBar(frac, if (frac >= 1f) NabdColors.Danger else if (frac >= 0.8f) NabdColors.Gold else c)
                    Text(
                        "الميزانية ${Money.fmt(limit, decimals = false)} • ${(frac * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = NabdColors.TextDim,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton("مصروف", Modifier.weight(1f), Icons.Rounded.Add) { onAdd(Direction.OUT, single) }
                    PillButton("إضافة مبلغ", Icons.Rounded.ArrowDownward, NabdColors.Mint, Modifier.weight(1f)) {
                        onAdd(Direction.IN, single)
                    }
                }
            }
        }
        if (list.isEmpty()) {
            item {
                EmptyState(
                    if (filter.activeCount > 0) Icons.Rounded.FilterAltOff else Icons.Rounded.ReceiptLong,
                    if (filter.activeCount > 0) "مافيش نتايج للفلتر ده" else "مافيش حركة في الفترة دي",
                    if (filter.activeCount > 0) "جرب تشيل فلتر أو توسع الفترة." else "سجّل مصروف أو استنى الوكلاء يلقطوا عملياتك."
                )
            }
        }
        byDay.forEach { (_, dayTxns) ->
            item(key = "h" + dayTxns.first().timestamp) {
                val dayOut = dayTxns.filter { it.direction == Direction.OUT && (filter.companyOnly || it.countsInTotals()) }
                    .sumOf { vm.txnEgp(it) }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Dates.dayHeader(dayTxns.first().timestamp), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (dayOut > 0) {
                        Text("−" + Money.fmt(dayOut, decimals = false), style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim)
                    }
                }
            }
            items(dayTxns, key = { "t" + it.id }) { t -> TxnRow(t, names[t.accountId]) { onEdit(t) } }
        }
    }
}

/** "أقل بـ 12% من الفترة اللي قبلها" */
@Composable
fun ComparisonLine(now: FilterTotals, prev: FilterTotals?, filter: TxnFilter) {
    if (prev == null || prev.spent <= 0 || filter.period == com.nabd.money.data.Period.ALL) return
    val diff = (now.spent - prev.spent) / prev.spent * 100
    val up = diff > 0
    val text = when {
        kotlin.math.abs(diff) < 1 -> "زي الفترة اللي قبلها تقريباً"
        up -> "↑ ${diff.toInt()}% عن الفترة اللي قبلها"
        else -> "↓ ${(-diff).toInt()}% عن الفترة اللي قبلها"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (up) NabdColors.Gold else NabdColors.Mint,
        modifier = Modifier.padding(top = 8.dp)
    )
}
