package com.nabd.money.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nabd.money.agents.PriceCheck
import com.nabd.money.data.ReviewLine
import com.nabd.money.data.StockCatalog
import com.nabd.money.data.StockItem
import com.nabd.money.data.StockPrice
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.ReceiptSession
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.SelectChip
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import java.time.Instant
import java.time.ZoneOffset

/** أقل من كده الصورة غالباً مش واضحة */
private const val LOW_CONFIDENCE = 45
private const val SUSPICIOUS_JUMP = 50.0

/** مراجعة الفاتورة: القراءة من الصور مش مضمونة، فمفيش حاجة بتتحفظ قبل ما تشوفها وتعدّلها */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewSheet(vm: AppViewModel, session: ReceiptSession, items: List<StockItem>, prices: Map<Long, List<StockPrice>>, onDismiss: () -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<ReviewLine?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { confirmClose = true },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden }),
        containerColor = NabdColors.Surface,
    ) {
        ReceiptReviewContent(
            session, items, prices,
            onPickDate = { pickDate = true }, onLink = { linking = it },
            onSave = { vm.saveReceipt(session) }, onCancel = { confirmClose = true },
            modifier = Modifier.imePadding(),
        )
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = session.date.value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { session.date.value = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("إلغاء") } },
        ) { DatePicker(state = state) }
    }
    linking?.let { l ->
        LinkDialog(l, items, onPick = { session.update(it); linking = null }, onDismiss = { linking = null })
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            containerColor = NabdColors.Surface,
            title = { Text("تسيب الفاتورة من غير حفظ؟", style = MaterialTheme.typography.titleMedium) },
            text = { Text("الأصناف اللي اتقرت هتضيع.", color = NabdColors.TextDim) },
            confirmButton = { TextButton(onClick = { confirmClose = false; onDismiss() }) { Text("سيبها", color = NabdColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("كمّل مراجعة") } },
        )
    }
}

@Composable
fun ReceiptReviewContent(
    session: ReceiptSession,
    items: List<StockItem>,
    prices: Map<Long, List<StockPrice>>,
    onPickDate: () -> Unit,
    onLink: (ReviewLine) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = session.lines
    val good = lines.count { it.valid }
    val sum = lines.sumOf { it.total }
    val draft = session.draft
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("راجع الفاتورة", style = MaterialTheme.typography.titleLarge)
        Text(session.source.note, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
        if (draft.lines.isEmpty() && session.confidence < 100 || session.confidence < LOW_CONFIDENCE) {
            Notice(Icons.Rounded.Warning, NabdColors.Gold, "الصورة مش واضحة", "الأسعار ممكن تكون غلط. لو كتير، صوّر تاني في نور كويس والفاتورة مفرودة وقريبة.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                session.store.value, { session.store.value = it }, modifier = Modifier.weight(1f), singleLine = true,
                shape = RoundedCornerShape(14.dp), label = { Text("المحل") },
            )
            PillButton(Dates.date(Dates.toEpoch(session.date.value)), Icons.Rounded.CalendarMonth, NabdColors.Mint, Modifier.heightIn(min = 52.dp), onClick = onPickDate)
        }
        // المقارنة بالإجمالي المكتوب هي أقوى دليل إن القراية سليمة
        if (lines.isNotEmpty()) {
            val tax = draft.tax ?: 0.0
            when (draft.matchesTotal(sum)) {
                true -> Notice(Icons.Rounded.CheckCircle, NabdColors.Mint, "المجموع مطابق للفاتورة", Money.fmt(sum))
                false -> Notice(
                    Icons.Rounded.Warning, NabdColors.Gold, "المجموع مش مطابق",
                    "الأصناف ${Money.fmt(sum)} والفاتورة ${Money.fmt(draft.subtotal ?: draft.total?.minus(tax) ?: 0.0)}. راجع الأسعار المعلّمة.",
                )
                null -> Text("مجموع الأصناف ${Money.fmt(sum)}", style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim)
            }
        }
        lines.forEach { l ->
            ReviewRow(l, items, prices, onChange = session::update, onRemove = { session.remove(l) }, onLink = { onLink(l) })
        }
        PillButton("ضيف صنف", Icons.Rounded.Add, NabdColors.Mint, Modifier.fillMaxWidth()) { session.addLine() }
        val skipped = lines.size - good
        if (skipped > 0) Text("$skipped سطر ناقص اسم أو سعر، مش هيتحفظ.", style = MaterialTheme.typography.labelMedium, color = NabdColors.Gold)
        if (good > 0) GradientButton("احفظ أسعار $good صنف", Modifier.fillMaxWidth(), Icons.Rounded.CheckCircle, onSave)
        PillButton("إلغاء", null, NabdColors.TextDim, Modifier.fillMaxWidth(), onClick = onCancel)
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = if (NabdColors.isDark) 0.12f else 0.08f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(body, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
        }
    }
}

@Composable
private fun ReviewRow(
    l: ReviewLine,
    items: List<StockItem>,
    prices: Map<Long, List<StockPrice>>,
    onChange: (ReviewLine) -> Unit,
    onRemove: () -> Unit,
    onLink: () -> Unit,
) {
    val linked = items.firstOrNull { it.id == l.itemId }
    val history = linked?.let { prices[it.id] }.orEmpty()
    GlassCard(Modifier.fillMaxWidth(), padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                l.name, { onChange(l.copy(name = it, doubtful = false)) }, modifier = Modifier.weight(1f), singleLine = true,
                shape = RoundedCornerShape(12.dp), label = { Text("الصنف") },
                isError = l.name.isBlank(),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "شيل السطر", tint = NabdColors.TextFaint, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                l.qty, { onChange(l.copy(qty = it)) }, modifier = Modifier.weight(0.7f), singleLine = true, shape = RoundedCornerShape(12.dp),
                label = { Text("الكمية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = l.qtyValue == null,
            )
            OutlinedTextField(
                l.price, { onChange(l.copy(price = it, doubtful = false)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp),
                label = { Text("سعر الوحدة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = l.priceValue == null,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onLink).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (linked != null) Icons.Rounded.Link else stockIcon(l.category), null, tint = if (linked != null) NabdColors.Mint else stockColor(l.category), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (linked != null) linked.name else "جديد في ${l.category}",
                    style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // فرق كبير عن آخر سعر: الأغلب قراية غلط مش غلاء
            val jump = l.priceValue?.let { kotlin.math.abs(PriceCheck.check(it, history).percent) >= SUSPICIOUS_JUMP } == true
            if (l.doubtful || jump) {
                Text("راجعه", style = MaterialTheme.typography.labelSmall, color = NabdColors.Gold, modifier = Modifier.padding(horizontal = 6.dp))
            }
            l.priceValue?.let { p -> PriceBadge(PriceCheck.check(p, history)) }
        }
    }
}

/** ربط سطر الفاتورة بصنف موجود، أو يتسجل جديد في التصنيف اللي تختاره */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkDialog(l: ReviewLine, items: List<StockItem>, onPick: (ReviewLine) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val key = StockCatalog.key(q)
    val shown = items.filter { key.isEmpty() || it.key.contains(key) }.take(30)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text("ده نفس صنف عندك؟", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.isNotEmpty()) {
                    OutlinedTextField(q, { q = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), label = { Text("دوّر") })
                    shown.forEach { i ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(l.copy(itemId = i.id, category = i.category)) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBubble(stockIcon(i.category), stockColor(i.category), 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(i.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(color = NabdColors.Stroke)
                }
                Text("صنف جديد في", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (StockCatalog.builtIn + items.map { it.category } + StockCatalog.carrefour).distinct().forEach { c ->
                        SelectChip(c, l.itemId == null && l.category == c, stockColor(c), stockIcon(c)) { onPick(l.copy(itemId = null, category = c)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
