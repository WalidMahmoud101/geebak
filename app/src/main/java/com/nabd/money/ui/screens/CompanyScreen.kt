package com.nabd.money.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.data.Account
import com.nabd.money.data.Txn
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.SectionHeader
import com.nabd.money.ui.components.SelectChip
import com.nabd.money.ui.components.StatColumn
import com.nabd.money.ui.theme.HeroGradient
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money

/**
 * مصاريف الشركة: كل مبلغ دفعته من جيبك للشركة/المكتب.
 * الإجمالي المستحق ليك، وتفاصيل كل واحدة، ولما تستلم الفلوس تصفّرهم من القايمة.
 */
@Composable
fun CompanyScreen(vm: AppViewModel, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Txn) -> Unit) {
    BackHandler(onBack = onBack)
    val pending by vm.companyPending.collectAsStateWithLifecycle()
    val done by vm.companyReimbursed.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val salary by vm.salary.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var receiving by remember { mutableStateOf<List<Txn>?>(null) }

    val pendingTotal = pending.sumOf { vm.txnEgp(it) }
    val doneTotal = done.sumOf { vm.txnEgp(it) }
    val chosen = pending.filter { it.id in selected }
    val names = accounts.associate { it.id to it.name }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowForward, "رجوع") }
                    Column(Modifier.weight(1f)) {
                        Text("مصاريف الشركة", style = MaterialTheme.typography.headlineSmall)
                        Text("اللي دفعته للشغل وهتسترده", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                    }
                }
            }
            item {
                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(HeroGradient)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), shape).padding(20.dp)
                ) {
                    Text("الشركة مديونالك بـ", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                    Text(Money.fmt(pendingTotal), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = NabdColors.Cyan)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        StatColumn("عمليات مستحقة", "${pending.size}", NabdColors.Text, Modifier.weight(1f))
                        StatColumn("اتسلم قبل كده", Money.fmt(doneTotal, decimals = false), NabdColors.Mint, Modifier.weight(1f))
                    }
                    if (pending.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        GradientButton("استلمت الفلوس كلها", Modifier.fillMaxWidth(), Icons.Rounded.DoneAll) {
                            receiving = pending
                        }
                    }
                    if (salary.enabled) {
                        Text(
                            "لو ${salary.payer} حوّلت نفس المبلغ، القايمة بتتصفر لوحدها.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NabdColors.TextDim,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
            if (chosen.isNotEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "محدد ${chosen.size} • ${Money.fmt(chosen.sumOf { vm.txnEgp(it) })}",
                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
                            )
                            PillButton("استلمتهم", Icons.Rounded.Check, NabdColors.Mint) { receiving = chosen }
                        }
                    }
                }
            }
            item { SectionHeader("لسه ماستلمتهاش (${pending.size})") }
            if (pending.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.BusinessCenter, "مافيش مصاريف مستحقة",
                        "سجّل اللي بتدفعه للشغل علشان تسترده."
                    )
                }
            }
            items(pending, key = { "p" + it.id }) { t ->
                CompanyRow(
                    t, names[t.accountId], checked = t.id in selected, done = false,
                    onCheck = { c -> selected = if (c) selected + t.id else selected - t.id },
                    onClick = { onEdit(t) }
                )
            }
            if (done.isNotEmpty()) {
                item { SectionHeader("اتسلمت (${done.size})") }
                done.groupBy { Dates.dayKey(it.reimbursedAt ?: 0) }.forEach { (_, group) ->
                    item(key = "d" + group.first().id) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "استلمت ${Money.fmt(group.sumOf { vm.txnEgp(it) }, decimals = false)} • ${Dates.date(group.first().reimbursedAt ?: 0)}",
                                style = MaterialTheme.typography.labelLarge, color = NabdColors.Mint, modifier = Modifier.weight(1f)
                            )
                            Text(
                                "تراجع", style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable { vm.setReimbursed(group.map { it.id }, false) }.padding(6.dp)
                            )
                        }
                    }
                    items(group, key = { "r" + it.id }) { t ->
                        CompanyRow(t, names[t.accountId], checked = true, done = true,
                            onCheck = { c -> if (!c) vm.setReimbursed(listOf(t.id), false) }, onClick = { onEdit(t) })
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            containerColor = NabdColors.Cyan,
            contentColor = NabdColors.OnAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 28.dp)
        ) { Icon(Icons.Rounded.Add, "مصروف شركة جديد") }
    }

    receiving?.let { items ->
        ReceiveDialog(items, accounts, vm::txnEgp, onDismiss = { receiving = null }) { accountId, record ->
            vm.receiveCompanyMoney(items, accountId, record)
            selected = emptySet()
            receiving = null
        }
    }
}

@Composable
private fun CompanyRow(
    t: Txn, accountName: String?, checked: Boolean, done: Boolean,
    onCheck: (Boolean) -> Unit, onClick: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), padding = 10.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked, onCheckedChange = onCheck,
                colors = CheckboxDefaults.colors(checkedColor = NabdColors.Mint, checkmarkColor = NabdColors.OnAccent)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    t.merchant ?: t.category,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) NabdColors.TextDim else NabdColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${Dates.full(t.timestamp)} • ${accountName ?: "كاش"}" + (t.note?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                Money.fmt(t.amount, t.currency),
                fontWeight = FontWeight.Bold,
                color = if (done) NabdColors.TextDim else NabdColors.Cyan,
                textDecoration = if (done) TextDecoration.LineThrough else null
            )
        }
    }
}

@Composable
private fun ReceiveDialog(
    items: List<Txn>, accounts: List<Account>, egp: (Txn) -> Double,
    onDismiss: () -> Unit, onConfirm: (Long?, Boolean) -> Unit,
) {
    var record by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text("استلمت ${Money.fmt(items.sumOf(egp))}؟", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${items.size} عملية هتتشال من القايمة المستحقة.", color = NabdColors.TextDim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("سجّل المبلغ كفلوس داخلة", modifier = Modifier.weight(1f))
                    Switch(checked = record, onCheckedChange = { record = it })
                }
                if (record) {
                    Text("استلمتها فين؟", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SelectChip("كاش", accountId == null, NabdColors.Gold) { accountId = null }
                        accounts.forEach { a -> SelectChip(a.name, accountId == a.id, NabdColors.Gold) { accountId = a.id } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(accountId, record) }) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** كارت مختصر في الرئيسية */
@Composable
fun CompanyCard(pendingTotal: Double, count: Int, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.BusinessCenter, NabdColors.Cyan)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("مصاريف الشركة", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (count == 0) "سجل اللي بتدفعه للشغل علشان تسترده" else "$count عملية مستحقة ليك",
                    style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                )
            }
            Text(Money.fmt(pendingTotal, decimals = false), fontWeight = FontWeight.Bold, color = NabdColors.Cyan)
        }
    }
}
