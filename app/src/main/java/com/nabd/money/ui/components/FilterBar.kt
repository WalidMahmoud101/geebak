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
    var searching by remember { mutableStateOf(filter.query.isNotBlank()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // الفترة + أسهم قبل/بعد
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NabdColors.Surface)
                .border(1.dp, NabdColors.Stroke, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onChange(filter.shift(-1)) }, enabled = filter.period != Period.ALL) {
                Icon(Icons.Rounded.ChevronRight, "اللي قبلها", tint = NabdColors.TextDim)
            }
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { panel = Panel.PERIOD }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = NabdColors.Mint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(filter.periodLabel(), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            val canNext = filter.shift(1) != filter
            IconButton(onClick = { onChange(filter.shift(1)) }, enabled = canNext) {
                Icon(Icons.Rounded.ChevronLeft, "اللي بعدها", tint = if (canNext) NabdColors.TextDim else NabdColors.Stroke)
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(
                when (filter.direction) {
                    Direction.OUT -> "مصروف بس"
                    Direction.IN -> "دخل بس"
                    null -> "مصروف ودخل"
                },
                Icons.Rounded.SwapVert, filter.direction != null
            ) {
                onChange(filter.copy(direction = when (filter.direction) {
                    null -> Direction.OUT
                    Direction.OUT -> Direction.IN
                    Direction.IN -> null
                }))
            }
            FilterPill(countLabel("الغرض", filter.purposes.size, filter.purposes.singleOrNull()?.ar), Icons.Rounded.Tune, filter.purposes.isNotEmpty()) { panel = Panel.PURPOSE }
            FilterPill(countLabel("التصنيف", filter.categories.size, filter.categories.singleOrNull()), Icons.Rounded.Category, filter.categories.isNotEmpty()) { panel = Panel.CATEGORY }
            FilterPill(
                countLabel("الحساب", filter.accountIds.size, filter.accountIds.singleOrNull()?.let { id -> if (id == CASH_ACCOUNT_ID) "كاش" else accounts.firstOrNull { it.id == id }?.name }),
                Icons.Rounded.AccountBalanceWallet, filter.accountIds.isNotEmpty()
            ) { panel = Panel.ACCOUNT }
            FilterPill(countLabel("المصدر", filter.sources.size, filter.sources.singleOrNull()?.ar), Icons.Rounded.Inbox, filter.sources.isNotEmpty()) { panel = Panel.SOURCE }
            FilterPill(amountLabel(filter), Icons.Rounded.Tune, filter.minAmount != null || filter.maxAmount != null) { panel = Panel.AMOUNT }
            FilterPill("بحث", Icons.Rounded.Search, filter.query.isNotBlank()) { searching = !searching }
            FilterPill("مصاريف الشركة", Icons.Rounded.BusinessCenter, filter.companyOnly) { onChange(filter.copy(companyOnly = !filter.companyOnly)) }
            FilterPill("التحويلات الداخلية", Icons.Rounded.VisibilityOff, filter.showExcluded) { onChange(filter.copy(showExcluded = !filter.showExcluded)) }
            FilterPill(if (saved.isEmpty()) "احفظ الفلتر" else "المحفوظة (${saved.size})", Icons.Rounded.Bookmark, false) { panel = Panel.SAVED }
            if (filter.activeCount > 0) {
                FilterPill("مسح (${filter.activeCount})", Icons.Rounded.FilterAltOff, false, NabdColors.Danger) {
                    searching = false
                    onChange(filter.clearedFilters())
                }
            }
        }

        if (searching) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { onChange(filter.copy(query = it)) },
                placeholder = { Text("دوّر في الوصف والملاحظات…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    IconButton(onClick = { onChange(filter.copy(query = "")); searching = false }) {
                        Icon(Icons.Rounded.Close, "قفل")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
        }
    }

    when (panel) {
        Panel.PERIOD -> PeriodSheet(filter, onDismiss = { panel = null }) { onChange(it); panel = null }
        Panel.PURPOSE -> ChoiceSheet(
            "الغرض", Purpose.entries.map { it.ar to it }, filter.purposes,
            icon = { it.icon() }, color = { it.color() }, onDismiss = { panel = null }
        ) { onChange(filter.copy(purposes = it)) }
        Panel.CATEGORY -> ChoiceSheet(
            "التصنيف", categories.map { it to it }, filter.categories, onDismiss = { panel = null }
        ) { onChange(filter.copy(categories = it)) }
        Panel.ACCOUNT -> ChoiceSheet(
            "الحساب", listOf("كاش / بدون حساب" to CASH_ACCOUNT_ID) + accounts.map { it.name to it.id }, filter.accountIds,
            onDismiss = { panel = null }
        ) { onChange(filter.copy(accountIds = it)) }
        Panel.SOURCE -> ChoiceSheet(
            "المصدر", TxnSource.entries.map { it.ar to it }, filter.sources, onDismiss = { panel = null }
        ) { onChange(filter.copy(sources = it)) }
        Panel.AMOUNT -> AmountSheet(filter, onDismiss = { panel = null }) { onChange(it); panel = null }
        Panel.SAVED -> SavedSheet(saved, filter, onDismiss = { panel = null }, onApply = { onChange(it.filter); panel = null }, onSave = onSave, onDelete = onDeleteSaved)
        null -> Unit
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

@Composable
private fun FilterPill(text: String, icon: ImageVector, active: Boolean, tint: Color = NabdColors.Mint, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (active) tint.copy(alpha = 0.14f) else NabdColors.Surface)
            .border(1.dp, if (active) tint.copy(alpha = 0.7f) else NabdColors.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) tint else NabdColors.TextDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) tint else NabdColors.Text, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<String, T>>,
    selected: Set<T>,
    icon: ((T) -> ImageVector)? = null,
    color: ((T) -> Color)? = null,
    onDismiss: () -> Unit,
    onChange: (Set<T>) -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    ModalBottomSheet(
        onDismissRequest = { onChange(current); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (current.isNotEmpty()) TextButton(onClick = { current = emptySet() }) { Text("الكل") }
            }
            Text("اختار واحد أو أكتر", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (label, v) ->
                    SelectChip(label, v in current, color?.invoke(v) ?: NabdColors.Mint, icon?.invoke(v)) {
                        current = if (v in current) current - v else current + v
                    }
                }
            }
            GradientButton("تطبيق", Modifier.fillMaxWidth()) { onChange(current); onDismiss() }
        }
    }
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
private fun AmountSheet(filter: TxnFilter, onDismiss: () -> Unit, onApply: (TxnFilter) -> Unit) {
    fun str(v: Double?) = v?.toBigDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty()
    var min by remember { mutableStateOf(str(filter.minAmount)) }
    var max by remember { mutableStateOf(str(filter.maxAmount)) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NabdColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("المبلغ (بالجنيه)", style = MaterialTheme.typography.titleLarge)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100.0, 500.0, 1000.0, 5000.0).forEach { v ->
                    SelectChip("أكتر من ${Money.compact(v)}", false) { min = str(v); max = "" }
                }
            }
            GradientButton("تطبيق", Modifier.fillMaxWidth()) {
                onApply(filter.copy(minAmount = Money.parseInput(min), maxAmount = Money.parseInput(max)))
            }
        }
    }
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
