package com.nabd.money.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nabd.money.data.Account
import com.nabd.money.data.CASH_ACCOUNT_ID
import com.nabd.money.data.Direction
import com.nabd.money.data.Period
import com.nabd.money.data.Purpose
import com.nabd.money.data.SavedFilter
import com.nabd.money.data.TxnFilter
import com.nabd.money.data.TxnSource
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

private enum class Panel { PERIOD, PURPOSE, CATEGORY, ACCOUNT, SOURCE, AMOUNT, SAVED }

/**
 * شريط الفلترة: الفترة (يوم، أسبوع، شهر، من تاريخ لتاريخ…) + فلاتر النوع والغرض والتصنيف والحساب والمصدر والمبلغ والبحث.
 */
@Composable
fun FilterBar(
    filter: TxnFilter,
    accounts: List<Account>,
    categories: List<String>,
    saved: List<SavedFilter>,
    onChange: (TxnFilter) -> Unit,
    onSave: (String) -> Unit,
    onDeleteSaved: (SavedFilter) -> Unit,
) {
    var panel by remember { mutableStateOf<Panel?>(null) }
    var sheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // الفترة + أسهم قبل/بعد
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NabdColors.SurfaceHigh),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onChange(filter.shift(-1)) }, enabled = filter.period != Period.ALL) {
                    Icon(Icons.Rounded.ChevronRight, "اللي قبلها", tint = NabdColors.TextDim)
                }
                Text(
                    filter.periodLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { panel = Panel.PERIOD }
                        .padding(vertical = 10.dp)
                )
                val canNext = filter.shift(1) != filter
                IconButton(onClick = { onChange(filter.shift(1)) }, enabled = canNext) {
                    Icon(Icons.Rounded.ChevronLeft, "اللي بعدها", tint = if (canNext) NabdColors.TextDim else NabdColors.Stroke)
                }
            }
            // زرار فلترة واحد
            val n = filter.activeCount
            Row(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (n > 0) NabdColors.Mint.copy(alpha = if (NabdColors.isDark) 0.18f else 0.12f) else NabdColors.SurfaceHigh)
                    .clickable { sheet = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Tune, null, tint = if (n > 0) NabdColors.Mint else NabdColors.Text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (n > 0) "فلترة ($n)" else "فلترة",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (n > 0) NabdColors.Mint else NabdColors.Text
                )
            }
        }
        // الفلاتر الشغالة بس، وتتشال بلمسة
        val chips = activeChips(filter, accounts)
        if (chips.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chips.forEach { (label, clear) ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(NabdColors.SurfaceTop)
                            .clickable { onChange(clear(filter)) }
                            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Close, "شيل", tint = NabdColors.TextDim, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    if (sheet) {
        FilterSheet(
            filter, accounts, categories, saved,
            onDismiss = { sheet = false },
            onApply = { onChange(it); sheet = false },
            onOpenSaved = { sheet = false; panel = Panel.SAVED },
        )
    }
    when (panel) {
        Panel.PERIOD -> PeriodSheet(filter, onDismiss = { panel = null }) { onChange(it); panel = null }
        Panel.SAVED -> SavedSheet(saved, filter, onDismiss = { panel = null }, onApply = { onChange(it.filter); panel = null }, onSave = onSave, onDelete = onDeleteSaved)
        else -> Unit
    }
}

private fun activeChips(f: TxnFilter, accounts: List<Account>): List<Pair<String, (TxnFilter) -> TxnFilter>> = buildList {
    f.direction?.let { add((if (it == Direction.OUT) "مصروف بس" else "دخل بس") to { x: TxnFilter -> x.copy(direction = null) }) }
    f.purposes.forEach { p -> add(p.ar to { x: TxnFilter -> x.copy(purposes = x.purposes - p) }) }
    f.categories.forEach { c -> add(c to { x: TxnFilter -> x.copy(categories = x.categories - c) }) }
    f.accountIds.forEach { id ->
        val name = if (id == CASH_ACCOUNT_ID) "كاش" else accounts.firstOrNull { it.id == id }?.name ?: "حساب"
        add(name to { x: TxnFilter -> x.copy(accountIds = x.accountIds - id) })
    }
    f.sources.forEach { s -> add(s.ar to { x: TxnFilter -> x.copy(sources = x.sources - s) }) }
    if (f.minAmount != null || f.maxAmount != null) add(amountLabel(f) to { x: TxnFilter -> x.copy(minAmount = null, maxAmount = null) })
    if (f.query.isNotBlank()) add("«${f.query}»" to { x: TxnFilter -> x.copy(query = "") })
    if (f.companyOnly) add("مصاريف الشركة" to { x: TxnFilter -> x.copy(companyOnly = false) })
    if (f.showExcluded) add("+ التحويلات الداخلية" to { x: TxnFilter -> x.copy(showExcluded = false) })
}

/** كل الفلاتر في شاشة واحدة */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filter: TxnFilter,
    accounts: List<Account>,
    categories: List<String>,
    saved: List<SavedFilter>,
    onDismiss: () -> Unit,
    onApply: (TxnFilter) -> Unit,
    onOpenSaved: () -> Unit,
) {
    var f by remember { mutableStateOf(filter) }
    fun str(v: Double?) = v?.toBigDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty()
    var min by remember { mutableStateOf(str(filter.minAmount)) }
    var max by remember { mutableStateOf(str(filter.maxAmount)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("فلترة", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { f = f.clearedFilters(); min = ""; max = "" }) { Text("مسح الكل", color = NabdColors.TextDim) }
            }
            OutlinedTextField(
                value = f.query, onValueChange = { f = f.copy(query = it) },
                placeholder = { Text("بحث في الوصف والملاحظات") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )
            Section("النوع") {
                SelectChip("الكل", f.direction == null) { f = f.copy(direction = null) }
                SelectChip("مصروف", f.direction == Direction.OUT, NabdColors.Pink) { f = f.copy(direction = Direction.OUT) }
                SelectChip("دخل", f.direction == Direction.IN) { f = f.copy(direction = Direction.IN) }
            }
            Section("الغرض") {
                Purpose.entries.forEach { p ->
                    SelectChip(p.ar, p in f.purposes, p.color(), p.icon()) {
                        f = f.copy(purposes = if (p in f.purposes) f.purposes - p else f.purposes + p)
                    }
                }
            }
            Section("التصنيف") {
                categories.forEach { c ->
                    SelectChip(c, c in f.categories) { f = f.copy(categories = if (c in f.categories) f.categories - c else f.categories + c) }
                }
            }
            Section("الحساب") {
                (listOf(CASH_ACCOUNT_ID to "كاش") + accounts.map { it.id to it.name }).forEach { (id, name) ->
                    SelectChip(name, id in f.accountIds) {
                        f = f.copy(accountIds = if (id in f.accountIds) f.accountIds - id else f.accountIds + id)
                    }
                }
            }
            Section("المصدر") {
                TxnSource.entries.forEach { src ->
                    SelectChip(src.ar, src in f.sources) { f = f.copy(sources = if (src in f.sources) f.sources - src else f.sources + src) }
                }
            }
            Text("المبلغ بالجنيه", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    min, { min = it }, label = { Text("من") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    max, { max = it }, label = { Text("لحد") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)
                )
            }
            Section("كمان") {
                SelectChip("مصاريف الشركة بس", f.companyOnly, NabdColors.Cyan, Icons.Rounded.BusinessCenter) { f = f.copy(companyOnly = !f.companyOnly) }
                SelectChip("وريني التحويلات الداخلية", f.showExcluded, NabdColors.TextDim, Icons.Rounded.VisibilityOff) { f = f.copy(showExcluded = !f.showExcluded) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("تطبيق", Modifier.weight(1f)) {
                    onApply(f.copy(minAmount = Money.parseInput(min), maxAmount = Money.parseInput(max)))
                }
                PillButton(if (saved.isEmpty()) "احفظه" else "المحفوظة", Icons.Rounded.Bookmark, NabdColors.Mint, Modifier.weight(0.7f)) {
                    onApply(f.copy(minAmount = Money.parseInput(min), maxAmount = Money.parseInput(max)))
                    onOpenSaved()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

private fun countLabel(title: String, n: Int, single: String?) = when {
    n == 0 -> title
    n == 1 && single != null -> single
    else -> "$title ($n)"
}

private fun amountLabel(f: TxnFilter): String = when {
    f.minAmount != null && f.maxAmount != null -> "${Money.compact(f.minAmount)} – ${Money.compact(f.maxAmount)}"
    f.minAmount != null -> "أكتر من ${Money.compact(f.minAmount)}"
    f.maxAmount != null -> "أقل من ${Money.compact(f.maxAmount)}"
    else -> "المبلغ"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PeriodSheet(filter: TxnFilter, onDismiss: () -> Unit, onPick: (TxnFilter) -> Unit) {
    var pickDay by remember { mutableStateOf(false) }
    var pickRange by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("الفترة", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigOption("يوم معين", Icons.Rounded.Event, Modifier.weight(1f)) { pickDay = true }
                BigOption("من تاريخ لتاريخ", Icons.Rounded.DateRange, Modifier.weight(1f)) { pickRange = true }
            }
            Label("سريع")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Period.TODAY, Period.YESTERDAY, Period.THIS_WEEK, Period.LAST_7, Period.THIS_MONTH, Period.LAST_MONTH,
                    Period.LAST_30, Period.LAST_3_MONTHS, Period.LAST_6_MONTHS, Period.THIS_YEAR, Period.ALL,
                ).forEach { p ->
                    SelectChip(p.ar, filter.period == p) { onPick(filter.copy(period = p, month = null, fromDay = null, toDay = null)) }
                }
            }
            Label("الشهور")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until 12).map { YearMonth.from(today).minusMonths(it.toLong()) }.forEach { ym ->
                    val sel = (filter.period == Period.MONTH && filter.month == ym) || (filter.period == Period.THIS_MONTH && ym == YearMonth.from(today))
                    SelectChip(
                        Dates.MONTHS[ym.monthValue - 1] + if (ym.year != today.year) " ${ym.year}" else "",
                        sel, NabdColors.Cyan
                    ) { onPick(filter.copy(period = Period.MONTH, month = ym)) }
                }
            }
            Label("السنين")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until 3).map { today.year - it }.forEach { y ->
                    SelectChip("$y", false, NabdColors.Violet) {
                        onPick(
                            if (y == today.year) filter.copy(period = Period.THIS_YEAR)
                            else filter.copy(
                                period = Period.CUSTOM,
                                fromDay = LocalDate.of(y, 1, 1).toEpochDay(),
                                toDay = LocalDate.of(y, 12, 31).toEpochDay()
                            )
                        )
                    }
                }
            }
        }
    }

    if (pickDay) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        onPick(filter.copy(period = Period.CUSTOM, fromDay = d, toDay = d))
                    }
                    pickDay = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { pickDay = false }) { Text("إلغاء") } },
        ) { DatePicker(state = state) }
    }
    if (pickRange) {
        val (f, t) = filter.days()
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = f.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = t.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = state.selectedStartDateMillis
                        if (s != null) {
                            val e = state.selectedEndDateMillis ?: s
                            fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                            onPick(filter.copy(period = Period.CUSTOM, fromDay = day(s), toDay = day(e)))
                        }
                        pickRange = false
                    },
                    enabled = state.selectedStartDateMillis != null
                ) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("إلغاء") } },
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f),
                title = { Text("اختار أول يوم وآخر يوم", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            )
        }
    }
}

@Composable
private fun BigOption(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(NabdColors.SurfaceTop)
            .border(1.dp, NabdColors.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBubble(icon, NabdColors.Mint, 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedSheet(
    saved: List<SavedFilter>,
    current: TxnFilter,
    onDismiss: () -> Unit,
    onApply: (SavedFilter) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (SavedFilter) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NabdColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("الفلاتر المحفوظة", style = MaterialTheme.typography.titleLarge)
            if (saved.isEmpty()) {
                Text("احفظ الفلتر الحالي باسم، وارجعله بلمسة.", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
            }
            saved.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NabdColors.SurfaceTop)
                        .clickable { onApply(s) }
                        .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Bookmark, null, tint = NabdColors.Mint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall)
                        Text(s.filter.periodLabel(), style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                    }
                    IconButton(onClick = { onDelete(s) }) { Icon(Icons.Rounded.Close, "حذف", tint = NabdColors.TextFaint) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it }, placeholder = { Text("اسم الفلتر (مثلاً: بنزين السنة دي)") },
                    singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                )
                PillButton("حفظ", Icons.Rounded.Bookmark, NabdColors.Mint) {
                    if (name.isNotBlank()) { onSave(name.trim()); name = "" }
                }
            }
            if (current.activeCount == 0) {
                Text("الفلتر الحالي فيه الفترة بس.", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
            }
        }
    }
}
