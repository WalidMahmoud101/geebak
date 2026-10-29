package com.nabd.money.ui.screens

import com.nabd.money.ui.components.PurposeChips
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.agents.Categorizer
import com.nabd.money.data.Purpose
import com.nabd.money.data.UserRule
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.SelectChip
import com.nabd.money.ui.components.color
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.NabdColors

/**
 * التصنيفات والقواعد:
 * - كل تصنيف وغرضه الثابت (ترفيه ← الخروجات)، وتضيف تصنيفات من عندك
 * - قواعد: كلمة ← تصنيف وغرض، والوكلاء يصنفوا بيها لوحدهم
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val tax by vm.taxonomy.collectAsStateWithLifecycle()
    var purposeFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingRule by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<UserRule?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("التصنيفات والقواعد", style = MaterialTheme.typography.titleLarge)

            // ---------- القواعد ----------
            Text("قواعدك", style = MaterialTheme.typography.titleMedium)
            Text(
                "أي عملية فيها الكلمة دي تتصنف كده لوحدها، القديمة والجديدة.",
                style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
            )
            if (tax.rules.isEmpty()) {
                Text(
                    "لسه مافيش. لما تعدّل تصنيف عملية، هتلاقي اختيار «طبّق على كل…».",
                    style = MaterialTheme.typography.labelMedium, color = NabdColors.TextFaint
                )
            }
            tax.rules.forEach { r -> RuleRow(r, onEdit = { editingRule = r }) { vm.deleteRule(r) } }
            PillButton("قاعدة جديدة", Icons.Rounded.Add, NabdColors.Mint, Modifier.fillMaxWidth()) { addingRule = true }

            HorizontalDivider(color = NabdColors.Stroke, modifier = Modifier.padding(vertical = 6.dp))

            // ---------- التصنيفات ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("التصنيفات", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text("+ جديد") }
            }
            Text(
                "دوس على الغرض علشان تثبّته. التغيير بيتطبق على كل عمليات التصنيف ده.",
                style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
            )
            Categorizer.allCategories().forEach { c ->
                val custom = c in tax.customCategories
                val p = Categorizer.purposeOf(c)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (p != null) p.color().copy(alpha = 0.12f) else NabdColors.SurfaceTop)
                            .clickable { purposeFor = c }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (p != null) {
                            Icon(p.icon(), null, tint = p.color(), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(p?.ar ?: "من غير غرض", style = MaterialTheme.typography.labelMedium, color = p?.color() ?: NabdColors.TextDim)
                    }
                    if (custom) {
                        IconButton(onClick = { renaming = c }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Edit, "تعديل", tint = NabdColors.TextDim, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { vm.deleteCategory(c) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, "حذف", tint = NabdColors.TextFaint, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Spacer(Modifier.width(64.dp))
                    }
                }
            }
        }
    }

    purposeFor?.let { c ->
        PurposePickerDialog("غرض «$c»", Categorizer.purposeOf(c), onDismiss = { purposeFor = null }) { p ->
            vm.setCategoryPurpose(c, p); purposeFor = null
        }
    }
    renaming?.let { c ->
        TextDialog("اسم التصنيف", c, onDismiss = { renaming = null }) { n -> vm.renameCategory(c, n); renaming = null }
    }
    if (adding) NewCategoryDialog(onDismiss = { adding = false }) { name, p -> vm.addCategory(name, p); adding = false }
    editingRule?.let { r ->
        RuleEditDialog(r, onDismiss = { editingRule = null }, onDelete = { vm.deleteRule(r); editingRule = null }) { updated ->
            vm.updateRule(r, updated); editingRule = null
        }
    }
    if (addingRule) NewRuleDialog(onDismiss = { addingRule = false }) { k, c, p -> vm.addRule(k, c, p); addingRule = false }
}

@Composable
private fun RuleRow(r: UserRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    val p = r.purpose ?: Categorizer.purposeOf(r.category)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NabdColors.SurfaceTop)
            .clickable(onClick = onEdit)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.parts.joinToString(" أو ") { "«$it»" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (r.name?.let { "اسمها «$it» • " } ?: "") + (r.dir?.let { if (it == com.nabd.money.data.Direction.IN) "للدخل بس • " else "للمصروف بس • " } ?: "") + r.category + (p?.let { " • ${it.ar}" } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = p?.color() ?: NabdColors.TextDim
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Close, "حذف القاعدة", tint = NabdColors.TextFaint) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurposeChips(selected: Purpose?, onPick: (Purpose) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Purpose.entries.forEach { p -> SelectChip(p.ar, p == selected, p.color(), p.icon()) { onPick(p) } }
    }
}

@Composable
private fun PurposePickerDialog(title: String, current: Purpose?, onDismiss: () -> Unit, onPick: (Purpose) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { PurposeChips(current, onPick) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun NewCategoryDialog(onDismiss: () -> Unit, onSave: (String, Purpose?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf<Purpose?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text("تصنيف جديد", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم (مثلاً: جيم، دروس العيال)") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Text("غرضه الثابت", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                PurposeChips(purpose) { purpose = it }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name, purpose) }) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewRuleDialog(onDismiss: () -> Unit, onSave: (String, String, Purpose?) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var newCategory by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf<Purpose?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text("قاعدة جديدة", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    keyword, { keyword = it }, label = { Text("لو العملية فيها كلمة…") },
                    placeholder = { Text("مثلاً: Costa أو جيم أو اسم شخص") },
                    singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                Text("تتصنف", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Categorizer.allCategories().forEach { c ->
                        SelectChip(c, c == category, NabdColors.Cyan) {
                            category = c; newCategory = ""
                            if (purpose == null) purpose = Categorizer.purposeOf(c)
                        }
                    }
                }
                OutlinedTextField(
                    newCategory, { newCategory = it; if (it.isNotBlank()) category = null },
                    label = { Text("أو اكتب تصنيف جديد") }, singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                Text("للغرض", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                PurposeChips(purpose) { purpose = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = newCategory.trim().ifBlank { category }
                if (keyword.isNotBlank() && c != null) onSave(keyword, c, purpose)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}


/** تعديل قاعدة: الأجزاء (زوّد أو شيل في أي وقت)، الاسم، التصنيف، الغرض، النوع، وتدور في إيه */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditDialog(rule: UserRule, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (UserRule) -> Unit) {
    var parts by remember { mutableStateOf(rule.parts) }
    var newPart by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(rule.name.orEmpty()) }
    var category by remember { mutableStateOf(rule.category) }
    var purpose by remember { mutableStateOf(rule.purpose) }
    var dir by remember { mutableStateOf(rule.dir) }
    var onMessage by remember { mutableStateOf(rule.onMessage) }
    fun addPart() {
        val p = newPart.trim()
        if (p.length >= 2 && p !in parts) parts = parts + p
        newPart = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text("تعديل القاعدة", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("لو العملية فيها أي جزء من دول", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    parts.forEach { p ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(NabdColors.Mint.copy(alpha = 0.14f))
                                .clickable { parts = parts - p }.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p, style = MaterialTheme.typography.labelLarge, color = NabdColors.Mint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Close, "شيل", tint = NabdColors.Mint, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                if (parts.isEmpty()) Text("لازم جزء واحد على الأقل.", style = MaterialTheme.typography.labelSmall, color = NabdColors.Danger)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newPart, { newPart = it }, modifier = Modifier.weight(1f), label = { Text("زوّد جزء") },
                        placeholder = { Text("كلمة، اسم، أو # لأي رقم") }, singleLine = true, shape = RoundedCornerShape(14.dp)
                    )
                    PillButton("زوّد", Icons.Rounded.Add, NabdColors.Mint) { addPart() }
                }
                Text(
                    "شيل أي جزء بالضغط عليه. اللي اتصنف قبل كده بيفضل زي ما هو، والتعديل بيتطبق على اللي بيطابق دلوقتي واللي جاي.",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
                )
                OutlinedTextField(
                    name, { name = it }, label = { Text("الاسم (اختياري)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                Text("التصنيف", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (Categorizer.allCategories().let { l -> if (category in l) l else l + category }).forEach { c ->
                        SelectChip(c, c == category, NabdColors.Cyan) { category = c }
                    }
                }
                Text("الغرض", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                PurposeChips(purpose) { purpose = it }
                Text("للدخل ولا للمصروف", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip("الاتنين", dir == null, NabdColors.Gold) { dir = null }
                    SelectChip("مصروف بس", dir == com.nabd.money.data.Direction.OUT, NabdColors.Gold) { dir = com.nabd.money.data.Direction.OUT }
                    SelectChip("دخل بس", dir == com.nabd.money.data.Direction.IN, NabdColors.Gold) { dir = com.nabd.money.data.Direction.IN }
                }
                Text("بتدور في", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip("الرسالة كلها", onMessage, NabdColors.Gold) { onMessage = true }
                    SelectChip("اسم التاجر بس", !onMessage, NabdColors.Gold) { onMessage = false }
                }
                TextButton(onClick = onDelete) { Text("احذف القاعدة", color = NabdColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val all = if (newPart.trim().length >= 2 && newPart.trim() !in parts) parts + newPart.trim() else parts
                rule.copy(category = category, purpose = purpose, name = name.trim().ifBlank { null }, dir = dir, onMessage = onMessage)
                    .withParts(all)?.let(onSave)
            }, enabled = parts.isNotEmpty() || newPart.trim().length >= 2) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
