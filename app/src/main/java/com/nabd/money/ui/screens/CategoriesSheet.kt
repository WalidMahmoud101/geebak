package com.nabd.money.ui.screens

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
            tax.rules.forEach { r -> RuleRow(r) { vm.deleteRule(r) } }
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
    if (addingRule) NewRuleDialog(onDismiss = { addingRule = false }) { k, c, p -> vm.addRule(k, c, p); addingRule = false }
}

@Composable
private fun RuleRow(r: UserRule, onDelete: () -> Unit) {
    val p = r.purpose ?: Categorizer.purposeOf(r.category)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NabdColors.SurfaceTop)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("«${r.keyword}»", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (r.name?.let { "اسمها «$it» • " } ?: "") + r.category + (p?.let { " • ${it.ar}" } ?: ""),
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
