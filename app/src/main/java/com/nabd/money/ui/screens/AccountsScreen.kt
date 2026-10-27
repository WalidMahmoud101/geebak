package com.nabd.money.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Txn
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.ScreenTitle
import com.nabd.money.ui.components.SectionHeader
import com.nabd.money.ui.components.TxnRow
import com.nabd.money.ui.components.colors
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money

@Composable
fun AccountsScreen(vm: AppViewModel, onEdit: (Txn) -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    vm.usdRate.collectAsStateWithLifecycle().value
    var adding by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<Long?>(null) }
    val total = accounts.filter { it.includeInTotal }.sumOf { vm.toEgp(it.balance, it.currency) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ScreenTitle("الحسابات", "إجمالي ${Money.fmt(total)}") {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Rounded.Settings, "الإعدادات", tint = NabdColors.TextDim)
                    }
                }
            }
            if (accounts.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.AccountBalanceWallet, "مافيش حسابات لسه",
                        "هتظهر لوحدها من رسائل البنك والإشعارات، أو ضيف حساب بإيدك."
                    )
                }
            }
            AccountType.entries.forEach { type ->
                val group = accounts.filter { it.type == type }
                if (group.isEmpty()) return@forEach
                item(key = "h" + type.name) {
                    val sum = group.sumOf { vm.toEgp(it.balance, it.currency) }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(type.ar, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(Money.fmt(sum, decimals = false), style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                    }
                }
                items(group, key = { it.id }) { a -> AccountRow(a) { openId = a.id } }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            containerColor = NabdColors.Mint,
            contentColor = NabdColors.OnAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 28.dp)
        ) { Icon(Icons.Rounded.Add, "إضافة حساب") }
    }

    if (adding) {
        AddAccountDialog(onDismiss = { adding = false }) { name, type, cur, bal ->
            vm.addAccount(name, type, cur, bal); adding = false
        }
    }
    if (settingsOpen) SettingsDialog(vm) { settingsOpen = false }
    openId?.let { id ->
        accounts.firstOrNull { it.id == id }?.let { a ->
            AccountSheet(vm, a, onDismiss = { openId = null }, onEdit = onEdit)
        }
    }
}

@Composable
private fun AccountRow(a: Account, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(a.colors())),
                contentAlignment = Alignment.Center
            ) { Icon(a.type.icon(), null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (!a.includeInTotal) "برا الإجمالي • " else "") +
                        (if (a.balanceKnown) "مؤكد من البنك" else "تقديري") + " • " + Dates.relative(a.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (a.balanceKnown) NabdColors.TextDim else NabdColors.Gold.copy(alpha = 0.8f)
                )
            }
            Text(
                Money.fmt(a.balance, a.currency),
                fontWeight = FontWeight.Bold,
                color = if (a.balance < 0) NabdColors.Danger else NabdColors.Text
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(vm: AppViewModel, a: Account, onDismiss: () -> Unit, onEdit: (Txn) -> Unit) {
    val txns by vm.txnsFor(a.id).collectAsState(initial = emptyList())
    var editBalance by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(a.colors()))
                        .padding(20.dp)
                ) {
                    Text(a.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(a.type.ar + " • " + a.currency, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(Money.fmt(a.balance, a.currency), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (a.balanceKnown) "آخر رصيد من رسالة البنك" else "رصيد تقديري محسوب من العمليات",
                        color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            item {
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("تعديل الرصيد", Icons.Rounded.Edit, NabdColors.Mint, Modifier.weight(1f)) { editBalance = true }
                    PillButton("إعادة تسمية", Icons.Rounded.Edit, NabdColors.Cyan, Modifier.weight(1f)) { rename = true }
                    PillButton("حذف", Icons.Rounded.Delete, NabdColors.Danger, Modifier.weight(0.7f)) { confirmDelete = true }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NabdColors.SurfaceTop)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("يتحسب في إجمالي فلوسك", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "اقفلها للحسابات الوسيطة زي InstaPay",
                            style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
                        )
                    }
                    androidx.compose.material3.Switch(checked = a.includeInTotal, onCheckedChange = { vm.setIncludeInTotal(a, it) })
                }
            }
            item { SectionHeader("العمليات (${txns.size})") }
            items(txns, key = { it.id }) { t -> TxnRow(t, a.name) { onEdit(t) } }
        }
    }

    if (editBalance) {
        AmountDialog("الرصيد الحالي لـ ${a.name}", a.balance, onDismiss = { editBalance = false }) {
            vm.setBalance(a, it); editBalance = false
        }
    }
    if (rename) {
        TextDialog("اسم الحساب", a.name, onDismiss = { rename = false }) {
            vm.renameAccount(a, it); rename = false
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "حذف ${a.name}؟",
            "هيتحذف الحساب وكل العمليات بتاعته من نبض بس (مش من البنك).",
            onDismiss = { confirmDelete = false }
        ) {
            vm.deleteAccount(a); confirmDelete = false; onDismiss()
        }
    }
}
