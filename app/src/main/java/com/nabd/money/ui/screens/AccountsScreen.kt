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
import com.nabd.money.data.Exclusion
import com.nabd.money.data.Institution
import com.nabd.money.data.NetWorth
import com.nabd.money.ui.components.IconBubble
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.CallMerge
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
    val tidyMessage by vm.tidyMessage.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var setBalanceFor by remember { mutableStateOf<Account?>(null) }
    val egp: (Account) -> Double = { vm.toEgp(it.balance, it.currency) }
    val total = NetWorth.total(accounts, egp)
    val institutions = NetWorth.institutions(accounts, egp)
    val needBalance = accounts.filter { NetWorth.exclusion(it, egp(it)) == Exclusion.UNKNOWN }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ScreenTitle("الحسابات", "إجمالي فلوسك ${Money.fmt(total)}") {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Rounded.Settings, "الإعدادات", tint = NabdColors.TextDim)
                    }
                }
            }
            if (needBalance.isNotEmpty()) {
                item { NeedBalanceCard(needBalance.size) { setBalanceFor = needBalance.first() } }
            }
            if (institutions.any { inst -> inst.accounts.count { !it.isCredit } > 1 }) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PillButton("لمّ الكروت والحسابات المكررة", Icons.Rounded.CallMerge, NabdColors.Mint, Modifier.fillMaxWidth()) { vm.tidyAccounts() }
                        tidyMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim) }
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
            items(institutions, key = { it.key }) { inst ->
                InstitutionCard(
                    inst, egp,
                    onOpen = { openId = it.id },
                    onSetBalance = { setBalanceFor = it },
                )
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
    setBalanceFor?.let { a ->
        AmountDialog("رصيدك الحقيقي دلوقتي في ${a.name} (${a.currency})", if (a.balanceKnown) a.balance else 0.0, onDismiss = { setBalanceFor = null }) {
            vm.setBalance(a, it)
            // اللي بعده في القايمة
            setBalanceFor = needBalance.firstOrNull { n -> n.id != a.id && needBalance.indexOf(n) > needBalance.indexOf(a) }
        }
    }
    openId?.let { id ->
        accounts.firstOrNull { it.id == id }?.let { a ->
            AccountSheet(vm, a, accounts, onDismiss = { openId = null }, onEdit = onEdit)
        }
    }
}

@Composable
private fun NeedBalanceCard(count: Int, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Edit, NabdColors.Gold, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$count حساب محتاج رصيده الحقيقي", style = MaterialTheme.typography.titleSmall)
                Text(
                    "اكتبه مرة واحدة، وبعد كده العمليات بتحدّثه لوحدها",
                    style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                )
            }
            Text("ابدأ", style = MaterialTheme.typography.labelLarge, color = NabdColors.Gold)
        }
    }
}

/** كارت لكل بنك أو تطبيق: الإجمالي فوق، والحسابات والكروت جواه */
@Composable
private fun InstitutionCard(
    inst: Institution,
    egp: (Account) -> Double,
    onOpen: (Account) -> Unit,
    onSetBalance: (Account) -> Unit,
) {
    val first = inst.accounts.first()
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(first.colors())),
                contentAlignment = Alignment.Center
            ) { Icon(first.type.icon(), null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(inst.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (inst.accounts.size == 1) first.type.ar else "${inst.accounts.size} حسابات وكروت",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (inst.countedCount > 0) Money.fmt(inst.total) else "—",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    color = if (inst.total < 0) NabdColors.Danger else NabdColors.Text
                )
                Text(
                    if (inst.countedCount > 0) "في إجمالي فلوسك" else "مش محسوب",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inst.countedCount > 0) NabdColors.Mint else NabdColors.TextFaint
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        inst.accounts.forEach { a ->
            val ex = NetWorth.exclusion(a, egp(a))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpen(a) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(a.type.icon(), null, tint = NabdColors.TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        ex?.ar ?: ("مؤكد • " + Dates.relative(a.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (ex) {
                            null -> NabdColors.TextDim
                            Exclusion.UNKNOWN -> NabdColors.Gold
                            else -> NabdColors.TextFaint
                        }
                    )
                }
                if (ex == Exclusion.UNKNOWN) {
                    Text(
                        "اكتب الرصيد",
                        style = MaterialTheme.typography.labelMedium,
                        color = NabdColors.Gold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(NabdColors.Gold.copy(alpha = 0.12f))
                            .clickable { onSetBalance(a) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Money.fmt(a.balance, a.currency),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ex == null) NabdColors.Text else NabdColors.TextFaint
                        )
                        if (a.isCredit) Text("الحد المتاح", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(vm: AppViewModel, a: Account, all: List<Account>, onDismiss: () -> Unit, onEdit: (Txn) -> Unit) {
    val txns by vm.txnsFor(a.id).collectAsState(initial = emptyList())
    var editBalance by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    val ex = NetWorth.exclusion(a, vm.toEgp(a.balance, a.currency))

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
                        when {
                            a.isCredit -> "الحد المتاح في الكارت • مش من فلوسك"
                            !a.balanceKnown -> "تقديري من العمليات • اكتب رصيدك الحقيقي"
                            else -> "مؤكد • " + Dates.relative(a.balanceAt.takeIf { it > 0 } ?: a.updatedAt)
                        },
                        color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            item {
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(if (a.isCredit) "الحد المتاح" else "الرصيد الحقيقي", Icons.Rounded.Edit, NabdColors.Mint, Modifier.weight(1f)) { editBalance = true }
                    PillButton("الاسم", Icons.Rounded.Edit, NabdColors.Cyan, Modifier.weight(0.7f)) { rename = true }
                    PillButton("دمج", Icons.Rounded.CallMerge, NabdColors.Violet, Modifier.weight(0.7f)) { merging = true }
                    PillButton("حذف", Icons.Rounded.Delete, NabdColors.Danger, Modifier.weight(0.7f)) { confirmDelete = true }
                }
            }
            if (ex != null) {
                item {
                    Text(
                        "مش داخل في إجمالي فلوسك: ${ex.ar}",
                        style = MaterialTheme.typography.labelMedium, color = NabdColors.Gold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
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
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NabdColors.SurfaceTop)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("كارت ائتمان", style = MaterialTheme.typography.titleSmall)
                        Text("الرصيد هنا حد البنك، مش فلوسك", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                    }
                    androidx.compose.material3.Switch(checked = a.isCredit, onCheckedChange = { vm.setCredit(a, it) })
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
    if (merging) {
        val targets = all.filter { it.id != a.id }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { merging = false },
            containerColor = NabdColors.Surface,
            title = { Text("دمج ${a.name} في…", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("عملياته هتتنقل للحساب اللي تختاره، وهو هيتشال.", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                    if (targets.isEmpty()) Text("مافيش حسابات تانية", color = NabdColors.TextFaint)
                    targets.forEach { t ->
                        Text(
                            t.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NabdColors.SurfaceTop)
                                .clickable { vm.mergeAccounts(a, t); merging = false; onDismiss() }
                                .padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { merging = false }) { Text("إلغاء") } },
        )
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
