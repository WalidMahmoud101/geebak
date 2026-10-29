package com.nabd.money.ui.screens

import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import com.nabd.money.ui.components.PillButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.data.Direction
import com.nabd.money.data.FilterTotals
import com.nabd.money.data.Period
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnFilter
import com.nabd.money.data.countsInTotals
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.FilterBar
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.ProgressBar
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.ScreenTitle
import com.nabd.money.ui.components.TxnRow
import com.nabd.money.ui.components.color
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money

/**
 * العمليات: كل حاجة دخلت وخرجت حسب الفلتر.
 * لما تختار غرض واحد (البيت مثلاً) الملخص بيبقى "صندوق": اتضاف كام، اتصرف كام، والباقي.
 */
@Composable
fun LedgerScreen(vm: AppViewModel, onAdd: (Direction, Purpose?) -> Unit, onEdit: (Txn) -> Unit, onCompany: () -> Unit = {}) {
    val companyPending by vm.companyPending.collectAsStateWithLifecycle()
    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val all by vm.filtered.collectAsStateWithLifecycle()
    // مصاريف الشركة ليها خانتها لوحدها، فمابتظهرش هنا إلا لو طلبتها من الفلتر (التقارير بتفضل شايفاها)
    val filterNow by vm.filter.collectAsStateWithLifecycle()
    val list = if (filterNow.showCompany || filterNow.companyOnly) all else all.filter { !it.reimbursable }
    val prev by vm.previousTotals.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categoriesInRange.collectAsStateWithLifecycle()
    val saved by vm.savedFilters.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()

    val totals = FilterTotals.of(list, vm::txnEgp, filter.companyOnly)
    val names = accounts.associate { it.id to it.name }
    val byDay = list.groupBy { Dates.dayKey(it.timestamp) }.toSortedMap(compareByDescending { it })
    val single = filter.purposes.singleOrNull()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var selecting by remember { mutableStateOf(false) }
    // لو العملية اتغيرت أو اختفت من القايمة نشيلها من التحديد
    val visibleIds = list.map { it.id }.toSet()
    val picked = selected.filter { it in visibleIds }.toSet()
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenTitle("العمليات", if (selecting) "اختار العمليات اللي عايز تخفيها" else "${totals.count} عملية") {
                // زرار واضح بكلمة، علشان الإخفاء مايستخباش
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (selecting) NabdColors.Mint.copy(alpha = 0.15f) else NabdColors.SurfaceHigh)
                        .clickable { selecting = !selecting; if (!selecting) selected = emptySet() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Checklist, null, tint = if (selecting) NabdColors.Mint else NabdColors.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (selecting) "خلصت" else "تحديد: إخفاء أو شركة", style = MaterialTheme.typography.labelMedium, color = if (selecting) NabdColors.Mint else NabdColors.TextDim)
                }
                IconButton(onClick = { onAdd(Direction.OUT, single) }) {
                    Icon(Icons.Rounded.Add, "عملية جديدة", tint = NabdColors.Mint)
                }
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
                if (single != null || filter.companyOnly) {
                    Text(
                        if (filter.companyOnly) "مصاريف الشركة" else "صندوق ${single!!.ar}",
                        style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SummaryCell(if (single != null) "اتضاف" else "الداخل", Money.signed("+", totals.income, decimals = false), NabdColors.Mint, Modifier.weight(1f))
                    CellDivider()
                    SummaryCell(if (single != null) "اتصرف" else "الخارج", Money.signed("−", totals.spent, decimals = false), NabdColors.Text, Modifier.weight(1f))
                    CellDivider()
                    SummaryCell(
                        if (single != null) "الباقي" else "الصافي",
                        Money.signed(if (totals.net >= 0) "+" else "−", kotlin.math.abs(totals.net), decimals = false),
                        if (totals.net >= 0) NabdColors.Mint else NabdColors.Danger, Modifier.weight(1f)
                    )
                }
                ComparisonLine(totals, prev, filter)
                if (hiddenCount > 0 && !filter.onlyHidden) {
                    Text(
                        "مخفي ومحوّل بين حساباتك: $hiddenCount عملية — افتحهم ترجّعهم أو تنقلهم للشركة ›",
                        style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { vm.setFilter(TxnFilter(period = Period.ALL, onlyHidden = true)); selecting = true }.padding(vertical = 4.dp)
                    )
                }
                if (filter.onlyHidden) {
                    Text(
                        "بتشوف المخفي والتحويلات بس. حدد وارجعهم، أو انقلهم لمصاريف الشركة.",
                        style = MaterialTheme.typography.labelMedium, color = NabdColors.Danger, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
                if (companyPending.isNotEmpty() && !filter.companyOnly) {
                    Text(
                        "مصاريف الشركة ${Money.fmt(companyPending.sumOf { vm.txnEgp(it) }, decimals = false)} (${companyPending.size}) في خانتها لوحدها ›",
                        style = MaterialTheme.typography.labelMedium, color = NabdColors.Cyan,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onCompany).padding(vertical = 4.dp)
                    )
                }
                val limit = single?.let { budgets[it.name] }
                if (single != null && limit != null && limit > 0 && filter.isMonthly) {
                    Spacer(Modifier.height(12.dp))
                    val frac = (totals.spent / limit).toFloat()
                    ProgressBar(frac, if (frac >= 1f) NabdColors.Danger else if (frac >= 0.8f) NabdColors.Gold else single.color())
                    Text(
                        "الميزانية ${Money.fmt(limit, decimals = false)} • ${(frac * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = NabdColors.TextDim,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        if (list.isEmpty()) {
            item {
                EmptyState(
                    if (filter.activeCount > 0) Icons.Rounded.FilterAltOff else Icons.Rounded.ReceiptLong,
                    if (filter.activeCount > 0) "مافيش نتايج للفلتر ده" else "مافيش حركة في الفترة دي",
                    if (filter.activeCount > 0) "جرب تشيل فلتر أو توسع الفترة." else "سجّل مصروف أو استنى العمليات تتسجل لوحدها."
                )
            }
        }
        byDay.forEach { (_, dayTxns) ->
            item(key = "d" + dayTxns.first().timestamp) {
                val dayOut = dayTxns.filter { it.direction == Direction.OUT && (filter.companyOnly || it.countsInTotals()) }
                    .sumOf { vm.txnEgp(it) }
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Dates.dayHeader(dayTxns.first().timestamp), style = MaterialTheme.typography.labelLarge,
                            color = NabdColors.TextDim, modifier = Modifier.weight(1f)
                        )
                        if (dayOut > 0) {
                            Text(Money.signed("−", dayOut, decimals = false), style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim)
                        }
                    }
                    GlassCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                        dayTxns.forEach { t ->
                            TxnRow(
                                t, names[t.accountId], selecting = selecting, selected = t.id in picked,
                                onLongClick = { selecting = true; toggle(t.id) },
                            ) { if (selecting) toggle(t.id) else onEdit(t) }
                        }
                    }
                }
            }
        }
    }
    if (selecting) {
        SelectionBar(
            count = picked.size,
            showingHidden = filter.showExcluded || filter.onlyHidden,
            onAll = { selected = visibleIds },
            onCompany = { vm.moveToCompany(picked); selected = emptySet(); selecting = false },
            onHide = { vm.hideTxns(picked, true); selected = emptySet(); selecting = false },
            onRestore = { vm.hideTxns(picked, false); selected = emptySet(); selecting = false },
            onCancel = { selected = emptySet(); selecting = false },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp),
        )
    }
    }
}

/** شريط الإجراءات لما تحدد عمليات: إخفاء من الحساب، أو رجّعها */
@Composable
private fun SelectionBar(
    count: Int, showingHidden: Boolean,
    onAll: () -> Unit, onHide: () -> Unit, onRestore: () -> Unit, onCompany: () -> Unit, onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NabdColors.SurfaceTop)
            .border(1.dp, NabdColors.Stroke, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (count == 0) "ماحددتش حاجة" else "اتحدد $count", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("الكل", style = MaterialTheme.typography.labelLarge, color = NabdColors.Mint,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(horizontal = 8.dp, vertical = 4.dp))
            Text("إلغاء", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showingHidden) {
                PillButton("رجّعها", Icons.Rounded.Visibility, NabdColors.Mint, Modifier.weight(1f)) { if (count > 0) onRestore() }
            } else {
                PillButton("اخفيها", Icons.Rounded.VisibilityOff, NabdColors.Danger, Modifier.weight(1f)) { if (count > 0) onHide() }
            }
            // على حساب الشركة: دايماً ظاهر، من أي عمليات حددتها (المصروف بس)
            PillButton("على حساب الشركة", Icons.Rounded.BusinessCenter, NabdColors.Cyan, Modifier.weight(1.4f)) { if (count > 0) onCompany() }
        }
        Text(
            if (showingHidden) "«رجّعها» بترجعها تتحسب وتظهر في العمليات. «على حساب الشركة» بتنقل المصروف لخانة مصاريف الشركة (الدخل مابيتنقلش)."
            else "«على حساب الشركة» بتشيل المصروف من العمليات وتنقله لخانة الشركة. «اخفيها» مابتتحسبش خصم ولا إضافة، وتلاقيها بسطر «مخفي» فوق.",
            style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
        )
    }
}

@Composable
fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

@Composable
fun CellDivider() {
    Box(Modifier.width(1.dp).height(30.dp).background(NabdColors.Stroke))
}

/** "↑ 12% عن الفترة اللي قبلها" */
@Composable
fun ComparisonLine(now: FilterTotals, prev: FilterTotals?, filter: TxnFilter) {
    if (prev == null || prev.spent <= 0 || filter.period == Period.ALL) return
    val diff = (now.spent - prev.spent) / prev.spent * 100
    val up = diff > 0
    val text = when {
        kotlin.math.abs(diff) < 1 -> "المصروف زي الفترة اللي قبلها تقريباً"
        up -> "المصروف ↑ ${diff.toInt()}% عن الفترة اللي قبلها"
        else -> "المصروف ↓ ${(-diff).toInt()}% عن الفترة اللي قبلها"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (up) NabdColors.Gold else NabdColors.Mint,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    )
}
