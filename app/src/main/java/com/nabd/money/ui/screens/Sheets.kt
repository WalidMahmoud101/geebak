package com.nabd.money.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material3.HorizontalDivider
import com.nabd.money.ui.components.agentTint
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.agents.Categorizer
import com.nabd.money.agents.Draft
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnSource
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.PurposeChips
import com.nabd.money.ui.components.SelectChip
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTxnSheet(
    initialDirection: Direction,
    initialPurpose: Purpose?,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    initialCompany: Boolean = false,
    draft: Draft? = null,
    onNewCategory: (String, Purpose) -> Unit = { _, _ -> },
    defaultAccountId: Long? = null,
    onSave: (Double, Direction, Purpose, String, String?, Long?, Long, String?, Boolean, String?, Int) -> Unit,
) {
    var quantity by remember { mutableStateOf(1) }
    val draftTime = draft?.let { Dates.toLocal(it.timestamp) }
    var company by remember { mutableStateOf(draft?.company ?: initialCompany) }
    var direction by remember { mutableStateOf(draft?.direction ?: initialDirection) }
    var amountText by remember {
        mutableStateOf(draft?.amount?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: "")
    }
    var merchant by remember { mutableStateOf(draft?.merchant.orEmpty()) }
    var purpose by remember { mutableStateOf(draft?.purpose ?: initialPurpose ?: Purpose.HOME) }
    var purposeTouched by remember { mutableStateOf(draft != null || initialPurpose != null) }
    var category by remember {
        mutableStateOf(
            draft?.category ?: if (initialDirection == Direction.IN) "مصروف البيت"
            else when (initialPurpose) {
                Purpose.CAR -> "وقود"
                Purpose.OUTING -> "مطاعم وكافيهات"
                Purpose.WORK -> "أدوات شغل"
                else -> "بقالة"
            }
        )
    }
    var pickedCurrency by remember { mutableStateOf(draft?.currency?.takeIf { it != "EGP" }) }
    var accountId by remember { mutableStateOf(draft?.accountId ?: defaultAccountId?.takeIf { id -> accounts.any { it.id == id } }) }
    var date by remember { mutableStateOf(draftTime?.toLocalDate() ?: LocalDate.now()) }
    var time by remember {
        mutableStateOf((draftTime?.toLocalTime() ?: LocalTime.now()).withSecond(0).withNano(0))
    }
    var note by remember { mutableStateOf(draft?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val amount = Money.parseInput(amountText)
    val color = if (direction == Direction.OUT) NabdColors.Pink else NabdColors.Mint

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (direction == Direction.OUT) "تسجيل مصروف" else "إضافة مبلغ",
                style = MaterialTheme.typography.titleLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DirectionTab("خصم / مصروف", Icons.Rounded.ArrowUpward, NabdColors.Pink, direction == Direction.OUT, Modifier.weight(1f)) {
                    direction = Direction.OUT
                }
                DirectionTab("إضافة / إيداع", Icons.Rounded.ArrowDownward, NabdColors.Mint, direction == Direction.IN, Modifier.weight(1f)) {
                    direction = Direction.IN
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("المبلغ") },
                suffix = { Text(pickedCurrency ?: accounts.firstOrNull { it.id == accountId }?.currency ?: "EGP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            // العملة: افتراضياً عملة الحساب، وتقدر تغيرها (والرصيد بيتحول بسعر الصرف)
            val accCur = accounts.firstOrNull { it.id == accountId }?.currency ?: "EGP"
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EGP" to "جنيه ج.م", "USD" to "دولار \$", "USDT" to "USDT", "EUR" to "يورو €").forEach { (c, label) ->
                    SelectChip(label, (pickedCurrency ?: accCur) == c, NabdColors.Gold) { pickedCurrency = if (c == accCur) null else c }
                }
            }
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text(if (direction == Direction.OUT) "اتصرف على إيه؟ (سوبر ماركت، بنزين...)" else "منين؟ (مرتب، تحويل...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            AccountPicker(direction, accounts, accountId) { accountId = it }

            Label("للبيت ولا للشغل ولا للعربية؟")
            PurposeChips(purpose, includeAll = false) { p ->
                if (p != null) {
                    purpose = p; purposeTouched = true
                }
            }

            Label("التصنيف")
            var newCat by remember { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = Categorizer.categoriesFor(direction).let { l -> if (category in l) l else l + category }
                options.forEach { c ->
                    SelectChip(c, c == category, NabdColors.Cyan) {
                        category = c
                        if (!purposeTouched) Categorizer.suggestPurpose(c)?.let { purpose = it }
                    }
                }
                SelectChip("+ تصنيف جديد", false, NabdColors.Mint) { newCat = true }
            }
            if (newCat) {
                TextDialog("اسم التصنيف الجديد", "", onDismiss = { newCat = false }) { name ->
                    onNewCategory(name, purpose)
                    category = name
                    newCat = false
                }
            }

            Label("إمتى؟")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    "${date.dayOfMonth} ${Dates.MONTHS[date.monthValue - 1]} ${date.year}",
                    Icons.Rounded.CalendarMonth, NabdColors.Text, Modifier.weight(1f)
                ) { pickDate = true }
                val ts = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                PillButton(Dates.time(ts), Icons.Rounded.Schedule, NabdColors.Text, Modifier.weight(1f)) { pickTime = true }
            }

            if (direction == Direction.OUT) {
                CompanyToggle(company) {
                    company = it
                    if (it) { purpose = Purpose.WORK; purposeTouched = true } else quantity = 1
                }
                if (company) {
                    // الكمية: المبلغ فوق = سعر الوحدة، والإجمالي = السعر × الكمية (بيظهر في فاتورة الشركة)
                    QuantityStepper(quantity, amount, pickedCurrency ?: accounts.firstOrNull { it.id == accountId }?.currency ?: "EGP") { quantity = it }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("ملاحظة (اختياري)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            GradientButton(
                if (amount != null && amount > 0) "حفظ ${Money.fmt(amount)}" else "اكتب المبلغ",
                Modifier.fillMaxWidth(),
                Icons.Rounded.Check
            ) {
                val a = amount ?: return@GradientButton
                if (a <= 0) return@GradientButton
                val ts = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val q = if (company && direction == Direction.OUT) quantity else 1
                onSave(
                    a * q, direction, purpose, category, merchant.trim().ifBlank { null }, accountId, ts,
                    note.trim().ifBlank { null }, company && direction == Direction.OUT, pickedCurrency, q
                )
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickDate = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("إلغاء") } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("إلغاء") } },
            text = { TimePicker(state = state) },
            containerColor = NabdColors.Surface,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NabdColors.SurfaceHigh)
            .border(1.dp, NabdColors.Stroke, RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CompanyToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (checked) NabdColors.Cyan.copy(alpha = 0.12f) else NabdColors.SurfaceHigh)
            .border(1.dp, if (checked) NabdColors.Cyan else NabdColors.Stroke, shape)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("على حساب الشركة", style = MaterialTheme.typography.titleSmall)
            Text(
                "دفعته من جيبي وهسترده من الشركة/المكتب",
                style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
}

@Composable
private fun DirectionTab(
    text: String, icon: ImageVector, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.18f) else NabdColors.SurfaceHigh)
            .border(1.dp, if (selected) color else NabdColors.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) color else NabdColors.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (selected) color else NabdColors.TextDim, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTxnSheet(
    txn: Txn,
    accountName: String?,
    onDismiss: () -> Unit,
    onSave: (Txn, hide: Boolean?, quantity: Int) -> Unit,
    onDelete: (Txn) -> Unit,
    onNewCategory: (String, Purpose) -> Unit = { _, _ -> },
    onRule: (keyword: String, category: String, purpose: Purpose) -> Unit = { _, _, _ -> },
    onMessageRule: (keyword: String, category: String, purpose: Purpose, name: String?) -> Unit = { _, _, _, _ -> },
    ruleMatchCount: (String) -> Int = { 0 },
    rules: List<com.nabd.money.data.UserRule> = emptyList(),
    onAddToRule: (com.nabd.money.data.UserRule, String) -> Unit = { _, _ -> },
) {
    var purpose by remember { mutableStateOf(txn.purpose) }
    // مقفولة من الأول: التعديل بيشمل العملية دي بس، إلا لو انت طلبت تطبقه على كل نفس الاسم
    var applyAll by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(txn.category) }
    var merchant by remember { mutableStateOf(txn.merchant.orEmpty()) }
    var note by remember { mutableStateOf(txn.note.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var company by remember { mutableStateOf(txn.reimbursable) }
    var counted by remember { mutableStateOf(!txn.excluded) }
    var qty by remember { mutableStateOf(txn.quantity.coerceAtLeast(1)) }
    val out = txn.direction == Direction.OUT
    val agent = AgentRegistry.byId(txn.agentId)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Money.signed(if (out) "−" else "+", txn.amount, txn.currency),
                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    color = if (out) NabdColors.Text else NabdColors.Mint
                )
                Text(
                    "${accountName ?: "كاش"} • ${Dates.full(txn.timestamp)}",
                    style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                )
                Text(
                    "${txn.source.ar} • ${agent?.name ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = agent?.let { agentTint(it.color) } ?: NabdColors.TextDim
                )
                txn.balanceAfter?.let {
                    Text("الباقي بعد العملية: ${Money.fmt(it, txn.currency)}", style = MaterialTheme.typography.labelMedium, color = NabdColors.Cyan)
                }
            }
            txn.rawText?.let { raw ->
                Text(
                    raw,
                    style = MaterialTheme.typography.bodySmall,
                    color = NabdColors.TextDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NabdColors.SurfaceHigh)
                        .padding(12.dp)
                )
            }
            OutlinedTextField(
                value = merchant, onValueChange = { merchant = it }, label = { Text("الوصف / التاجر") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
            )
            Label("الغرض")
            PurposeChips(purpose, includeAll = false) { p -> if (p != null) purpose = p }
            if (!out && txn.source != TxnSource.MANUAL) {
                Text(
                    "ده دخل ملتقط: بيتحسب دخل مش فلوس في صندوق البيت أو الشغل. الصناديق بتتملى بس لما تضيف فلوس بإيدك.",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
                )
            }
            Label("التصنيف")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = Categorizer.categoriesFor(txn.direction).let { l -> if (category in l) l else l + category }
                options.forEach { c ->
                    SelectChip(c, c == category, NabdColors.Cyan) {
                        category = c
                        // الغرض بيتقترح من التصنيف للمصروف بس، الدخل غرضه "أخرى" إلا لو حطيته في صندوق بإيدك
                        if (out) Categorizer.suggestPurpose(c)?.let { if (!company) purpose = it }
                    }
                }
                SelectChip("+ تصنيف جديد", false, NabdColors.Mint) { newCat = true }
            }
            if (newCat) {
                TextDialog("اسم التصنيف الجديد", "", onDismiss = { newCat = false }) { name ->
                    onNewCategory(name, purpose)
                    category = name
                    newCat = false
                }
            }
            // التعديل يبقى قاعدة ثابتة: كل عمليات المكان ده تتصنف كده
            // لما تعلّم "على حساب الشركة" الغرض بيتحول لـ "الشغل" لوحده، وده مش تعديل منك يستاهل قاعدة لكل المكان
            val purposeByCompany = company != txn.reimbursable
            val changed = category != txn.category || (purpose != txn.purpose && !purposeByCompany)
            val keyword = merchant.trim()
            if (changed && keyword.length >= 2) {
                val others = ruleMatchCount(keyword)
                ToggleRow(
                    "طبّق على كل «$keyword»",
                    when {
                        others == 0 -> "مفيش عمليات تانية بنفس الاسم. الجاي (${if (out) "مصروف" else "دخل"} بس) هيتصنف $category • ${purpose.ar}"
                        else -> "هتتغير $others عملية تانية بنفس الاسم واللي جاي هيتصنف $category • ${purpose.ar}" +
                            if (others > 15) "\nده رقم كبير، اتأكد إن الاسم مميز" else ""
                    },
                    applyAll
                ) { applyAll = it }
            }
            if (out) CompanyToggle(company) {
                company = it
                if (it) { purpose = Purpose.WORK; counted = true } // مصروف الشركة بيتحسب في خانته، مش مخفي
            }
            if (out && company) {
                // سعر الوحدة ثابت، والإجمالي بيتغير بالكمية
                val unit = txn.amount / txn.quantity.coerceAtLeast(1)
                QuantityStepper(qty, unit, txn.currency, totalOnly = true) { qty = it }
            }
            // إخفاء من الحساب: مابتتحسبش خصم ولا إضافة، ورصيد الكاش والحسابات اليدوي بيرجع كأنها ماحصلتش
            ToggleRow(
                "اخفيها من الحساب",
                if (counted) "العملية دي داخلة في الدخل والمصروف" else "مخفية: مابتتحسبش خصم ولا إضافة. ترجعها من هنا أو من الفلتر",
                !counted
            ) { counted = !it }
            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("ملاحظة") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
            )
            txn.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
                MessageRuleSection(raw, merchant.trim(), category, purpose, Categorizer.categoriesFor(txn.direction).let { l -> if (category in l) l else l + category }, rules.filter { it.dir == null || it.dir == txn.direction }, onAddToRule, onMessageRule)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("حفظ", Modifier.weight(1f), Icons.Rounded.Check) {
                    val kw = merchant.trim()
                    if (applyAll && kw.length >= 2 && changed) {
                        onRule(kw, category, purpose)
                    }
                    onSave(
                        txn.copy(
                            purpose = purpose, category = category,
                            merchant = merchant.trim().ifBlank { null }, note = note.trim().ifBlank { null },
                            reimbursable = company && out,
                            reimbursedAt = if (company && out) txn.reimbursedAt else null,
                        ),
                        // الإخفاء بيتنفذ بعد الحفظ علشان رصيد الحساب يتظبط
                        if (counted != !txn.excluded) !counted else null,
                        if (company && out) qty else 1
                    )
                }
                PillButton("حذف", Icons.Rounded.Delete, NabdColors.Danger, Modifier.weight(0.6f)) { confirmDelete = true }
            }
            if (txn.source != TxnSource.MANUAL) {
                Text(
                    "حذف عملية ملتقطة مش هيغير رصيد الحساب.",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
                )
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("حذف العملية؟", "هتتشال من نبض بس.", onDismiss = { confirmDelete = false }) {
            confirmDelete = false; onDelete(txn)
        }
    }
}

@Composable
fun AmountDialog(title: String, initial: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (initial == 0.0) "" else initial.toBigDecimal().stripTrailingZeros().toPlainString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { Money.parseInput(text.ifBlank { "0" })?.let(onConfirm) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, color = NabdColors.TextDim) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("حذف", color = NabdColors.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAccountDialog(onDismiss: () -> Unit, onConfirm: (String, AccountType, String, Double, String?, String?) -> Unit) {
    var preset by remember { mutableStateOf<com.nabd.money.agents.AccountPreset?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var currency by remember { mutableStateOf("EGP") }
    var balance by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text("حساب جديد", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("اختار حساب جاهز", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.nabd.money.agents.AccountPresets.all.forEach { p ->
                        SelectChip(p.label, preset == p, NabdColors.Mint, p.type.icon()) {
                            if (preset == p) { preset = null } else {
                                preset = p; name = p.label; type = p.type; currency = p.currency
                            }
                        }
                    }
                }
                preset?.let { Text(it.note, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("الاسم (مثلاً: بنك تاني، كاش البيت)") },
                    singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                if (preset == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountType.entries.forEach { t -> SelectChip(t.ar, t == type, NabdColors.Mint, t.icon()) { type = t } }
                    }
                }
                Text("العملة", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("EGP" to "جنيه ج.م", "USD" to "دولار \$", "USDT" to "USDT", "EUR" to "يورو €").forEach { (c, label) ->
                        SelectChip(label, c == currency, NabdColors.Gold) { currency = c }
                    }
                }
                if (preset?.askLast4 == true) {
                    OutlinedTextField(
                        value = last4, onValueChange = { v -> last4 = v.filter { it.isDigit() }.take(4) },
                        label = { Text("آخر 4 أرقام الكارت (علشان نربط الإشعارات)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                OutlinedTextField(
                    value = balance, onValueChange = { balance = it }, label = { Text("المبلغ اللي معاك فيه دلوقتي ($currency)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val l4 = last4.takeIf { it.length == 4 }
                    val label = if (l4 != null && !name.contains(l4)) "${name.trim()} •••• $l4" else name.trim()
                    onConfirm(label, type, currency, Money.parseInput(balance) ?: 0.0, preset?.agentId, l4)
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var categories by remember { mutableStateOf(false) }
    var backup by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var currencies by remember { mutableStateOf(false) }
    if (currencies) {
        CurrencySheet(vm) { currencies = false }
        return
    }
    if (reset) {
        ResetSheet(vm) { reset = false }
        return
    }
    if (categories) {
        CategoriesSheet(vm) { categories = false }
        return
    }
    if (backup) {
        BackupSheet(vm) { backup = false }
        return
    }
    val s = vm.settings
    var low by remember { mutableStateOf(s.lowBalance.toInt().toString()) }
    var large by remember { mutableStateOf(s.largeSpend.toInt().toString()) }
    var notify by remember { mutableStateOf(s.notifyCaptures) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text("الإعدادات", style = MaterialTheme.typography.titleMedium) },
        text = {
            // بيتحرك لتحت على الشاشات الصغيرة، والتحديث فوق علشان يبان على طول
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val update by vm.update.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الإصدار ${com.nabd.money.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when (val u = update) {
                                is com.nabd.money.util.UpdateState.Checking -> "بيدور على تحديث…"
                                is com.nabd.money.util.UpdateState.UpToDate -> "عندك آخر نسخة"
                                is com.nabd.money.util.UpdateState.Available -> "في نسخة جديدة: ${u.release.name}"
                                is com.nabd.money.util.UpdateState.Downloading -> "بينزل ${(u.progress * 100).toInt()}%"
                                is com.nabd.money.util.UpdateState.Ready -> "جاهز للتثبيت"
                                is com.nabd.money.util.UpdateState.Error -> u.message
                                else -> "التحديثات بتوصل لوحدها"
                            },
                            style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
                        )
                    }
                    val u = update
                    if (u is com.nabd.money.util.UpdateState.Available) {
                        TextButton(onClick = { vm.downloadAndInstall(u.release) }) { Text("حدّث") }
                    } else if (u is com.nabd.money.util.UpdateState.Ready) {
                        TextButton(onClick = { com.nabd.money.util.Updater.install(vm.getApplication(), u.file) }) { Text("ثبّت") }
                    } else {
                        TextButton(onClick = { vm.checkForUpdate(manual = true) }) { Text("اتحقق") }
                    }
                }
                HorizontalDivider(color = NabdColors.Stroke)
                val rateNow by vm.usdRate.collectAsState()
                PillButton("العملات وسعر الصرف  (1$ = ${"%.2f".format(java.util.Locale.US, rateNow)} ج.م)", Icons.Rounded.CurrencyExchange, NabdColors.Gold, Modifier.fillMaxWidth()) { currencies = true }
                OutlinedTextField(
                    value = low, onValueChange = { low = it }, label = { Text("حارس الرصيد: نبهني لو الرصيد أقل من") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = large, onValueChange = { large = it }, label = { Text("كاشف المصاريف: مبلغ كبير لو أكتر من") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
                PillButton("التصنيفات والقواعد", Icons.Rounded.Category, NabdColors.Mint, Modifier.fillMaxWidth()) { categories = true }
                PillButton("النسخة الاحتياطية", Icons.Rounded.CloudDone, NabdColors.Mint, Modifier.fillMaxWidth()) { backup = true }
                PillButton("ابدأ من جديد", Icons.Rounded.RestartAlt, NabdColors.Danger, Modifier.fillMaxWidth()) { reset = true }
                Text("المبالغ تظهر بـ", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AUTO" to "عملة كل حساب", "EGP" to "جنيه ج.م", "USD" to "دولار \$").forEach { (k, label) ->
                        SelectChip(label, Money.mode == k) { s.displayCurrency = k }
                    }
                }
                Text("الشكل", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                val mode by vm.themeMode.collectAsState()
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.nabd.money.ui.theme.ThemeMode.entries.forEach { m ->
                        SelectChip(m.ar, m == mode) { vm.setTheme(m) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("إشعار مع كل عملية ملتقطة", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = notify, onCheckedChange = { notify = it })
                }
                Text(
                    "بياناتك المالية على الموبايل بس. النت بيستخدم للتحديثات وبس.",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Money.parseInput(low)?.let { s.lowBalance = it }
                Money.parseInput(large)?.let { s.largeSpend = it }
                s.notifyCaptures = notify
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}


/** من أنهي حساب؟ الكاش (الجيب) أول اختيار، وبعده حساباتك. بيوري رصيد الحساب المختار */
@Composable
private fun AccountPicker(direction: Direction, accounts: List<Account>, selected: Long?, onSelect: (Long?) -> Unit) {
    val cash = accounts.firstOrNull { it.type == AccountType.CASH && it.agentId == com.nabd.money.agents.AgentRegistry.MANUAL_ID && it.accountKey == com.nabd.money.agents.AgentEngine.CASH_KEY }
    val shown = accounts.filter { it != cash }
    val current = accounts.firstOrNull { it.id == selected } ?: cash
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(if (direction == Direction.OUT) "دفعت من أنهي حساب؟" else "دخلت في أنهي حساب؟")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("كاش (جيبي)", selected == null || selected == cash?.id, NabdColors.Gold, AccountType.CASH.icon()) { onSelect(cash?.id) }
            shown.forEach { a ->
                SelectChip(a.name, selected == a.id, NabdColors.Gold, a.type.icon()) { onSelect(a.id) }
            }
        }
        val hint = when {
            current == null || !current.balanceKnown -> if (current?.type == AccountType.CASH || current == null) "من فلوس جيبك. اكتب رصيد الكاش من الحسابات علشان يتحسب في إجمالي فلوسك" else null
            else -> "رصيد ${current.name} دلوقتي ${Money.fmt(current.balance, current.currency)}"
        }
        hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim) }
    }
}


/** الكمية 1، 2، 3…: بتضرب سعر الوحدة وتوريك الإجمالي */
@Composable
fun QuantityStepper(quantity: Int, unitPrice: Double?, currency: String, totalOnly: Boolean = false, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NabdColors.SurfaceTop).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("الكمية", style = MaterialTheme.typography.titleSmall)
            Text(
                if (unitPrice != null && unitPrice > 0)
                    "${Money.plain("", unitPrice, currency)} × $quantity = ${Money.plain("", unitPrice * quantity, currency)}"
                else if (totalOnly) "" else "المبلغ فوق = سعر الوحدة الواحدة",
                style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton("−", enabled = quantity > 1) { onChange(quantity - 1) }
            Text("$quantity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepButton("+", enabled = quantity < 999) { onChange(quantity + 1) }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
            .background(if (enabled) NabdColors.Mint.copy(alpha = 0.15f) else NabdColors.Stroke.copy(alpha = 0.4f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) NabdColors.Mint else NabdColors.TextFaint) }
}


/**
 * الرسالة الأصلية: حدد جزء منها (اضغط مطولاً واسحب)، وحط له اسم وتصنيف.
 * كل رسالة فيها الجزء ده، اللي فاتت واللي جاية، بتاخد الاسم والتصنيف لوحدها.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageRuleSection(
    raw: String, merchant: String, category: String, purpose: Purpose, categories: List<String>,
    rules: List<com.nabd.money.data.UserRule>, onAddToRule: (com.nabd.money.data.UserRule, String) -> Unit,
    onRule: (keyword: String, category: String, purpose: Purpose, name: String?) -> Unit,
) {
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(raw)) }
    var dialog by remember { mutableStateOf(false) }
    var addToRule by remember { mutableStateOf(false) }
    val picked = field.text.substring(field.selection.min.coerceIn(0, field.text.length), field.selection.max.coerceIn(0, field.text.length)).trim()

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NabdColors.SurfaceTop).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("الرسالة الأصلية", style = MaterialTheme.typography.titleSmall)
        Text("اضغط مطولاً على كلمة واسحب علشان تحدد الجزء اللي بيميّز العملية دي.", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
        OutlinedTextField(
            value = field, onValueChange = { field = it.copy(text = raw) }, readOnly = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        PillButton(
            if (picked.isEmpty()) "حدد جزء من الرسالة الأول" else "اعمل قاعدة من «${picked.take(24)}${if (picked.length > 24) "…" else ""}»",
            Icons.Rounded.Check, if (picked.isEmpty()) NabdColors.TextFaint else NabdColors.Mint, Modifier.fillMaxWidth()
        ) { if (picked.length >= 2) dialog = true }
        if (rules.isNotEmpty()) {
            PillButton(
                "ضيف الجزء المحدد لقاعدة موجودة",
                Icons.Rounded.Check, if (picked.length >= 2) NabdColors.Cyan else NabdColors.TextFaint, Modifier.fillMaxWidth()
            ) { if (picked.length >= 2) addToRule = true }
        }
    }
    if (addToRule) {
        AlertDialog(
            onDismissRequest = { addToRule = false },
            containerColor = NabdColors.Surface,
            title = { Text("ضيف «${picked.take(30)}» لأنهي قاعدة؟", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rules.forEach { r ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NabdColors.SurfaceTop)
                                .clickable { onAddToRule(r, picked); addToRule = false }.padding(12.dp)
                        ) {
                            Text(r.name ?: r.parts.first(), style = MaterialTheme.typography.titleSmall)
                            Text(r.parts.joinToString(" أو ") { "«$it»" }, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                            Text(r.category, style = MaterialTheme.typography.labelSmall, color = NabdColors.Cyan)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addToRule = false }) { Text("إلغاء") } },
        )
    }

    if (dialog) {
        var snippet by remember { mutableStateOf(picked) }
        var anyNumber by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf(merchant) }
        var cat by remember { mutableStateOf(category) }
        var purp by remember { mutableStateOf(purpose) }
        val hasDigits = snippet.any { it.isDigit() }
        val keyword = if (anyNumber && hasDigits) snippet.replace(Regex("""\d+(?:[.,]\d+)*"""), "#") else snippet
        AlertDialog(
            onDismissRequest = { dialog = false },
            containerColor = NabdColors.Surface,
            title = { Text("قاعدة من الرسالة", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = snippet, onValueChange = { snippet = it }, label = { Text("لو الرسالة فيها") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )
                    if (hasDigits) {
                        ToggleRow("الأرقام بتتغير", "مثلاً رقم التليفون أو المبلغ: القاعدة بتشتغل مع أي رقم ($keyword)", anyNumber) { anyNumber = it }
                    }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("اسمها يبقى") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )
                    Text("التصنيف", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { c -> SelectChip(c, c == cat, NabdColors.Cyan) { cat = c } }
                    }
                    Text("الغرض", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                    PurposeChips(purp, includeAll = false) { p -> if (p != null) purp = p }
                    Text(
                        "هتتطبق على كل العمليات اللي فاتت اللي فيها الجزء ده، وعلى الرسايل الجاية.",
                        style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (keyword.trim().length >= 2) onRule(keyword.trim(), cat, purp, name.trim().ifBlank { null })
                    dialog = false
                }) { Text("احفظ القاعدة") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("إلغاء") } },
        )
    }
}
