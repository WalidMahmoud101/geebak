package com.nabd.money.ui.screens

import com.nabd.money.ui.components.agentTint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.data.Account
import com.nabd.money.data.Direction
import com.nabd.money.data.Insight
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.Perms
import com.nabd.money.ui.components.DonutChart
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.PulseLine
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.SectionHeader
import com.nabd.money.ui.components.TxnRow
import com.nabd.money.ui.components.agentIcon
import com.nabd.money.ui.components.color
import com.nabd.money.ui.components.colors
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.BrandGradient
import com.nabd.money.ui.theme.HeroGradient
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import com.nabd.money.util.UpdateState
import com.nabd.money.ui.theme.ThemeMode
import com.nabd.money.ui.components.ProgressBar
import com.nabd.money.data.countsInTotals
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SystemUpdate

@Composable
fun HomeScreen(
    vm: AppViewModel,
    perms: Perms,
    onNavigate: (Int) -> Unit,
    onAdd: (Direction, Purpose?) -> Unit,
    onEdit: (Txn) -> Unit,
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val month by vm.thisMonthTxns.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val salary by vm.salary.collectAsStateWithLifecycle()
    val companyPending by vm.companyPending.collectAsStateWithLifecycle()
    var salaryDialog by remember { mutableStateOf(false) }
    vm.usdRate.collectAsStateWithLifecycle().value

    if (salaryDialog) {
        SalaryDialog(salary, onDismiss = { salaryDialog = false }) { payer, day ->
            vm.setSalary(payer, day); salaryDialog = false
        }
    }

    // الإجمالي من غير الحسابات الوسيطة (InstaPay/Wallet)، والمصروف من غير التحويلات الداخلية ومصاريف الشركة
    val total = accounts.filter { it.includeInTotal }.sumOf { vm.toEgp(it.balance, it.currency) }
    val counted = month.filter { it.countsInTotals() }
    val spent = counted.filter { it.direction == Direction.OUT }.sumOf { vm.txnEgp(it) }
    val income = counted.filter { it.direction == Direction.IN }.sumOf { vm.txnEgp(it) }
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val names = accounts.associate { it.id to it.name }

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            HomeHeader(
                unread = insights.count { !it.read },
                themeMode = themeMode,
                onTheme = {
                    vm.setTheme(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> if (NabdColors.isDark) ThemeMode.LIGHT else ThemeMode.DARK
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.LIGHT
                        }
                    )
                },
                onBell = { vm.markInsightsRead() }
            )
        }
        updateCard(update, vm)
        item { HeroCard(total, income, spent, accounts.count { it.includeInTotal }, onAdd) }
        if (!perms.allGranted) item { PermissionCard(perms) }
        item { SalaryCard(salary) { salaryDialog = true } }
        item {
            CompanyCard(companyPending.sumOf { vm.txnEgp(it) }, companyPending.size) {
                onNavigate(com.nabd.money.ui.COMPANY_TAB)
            }
        }

        item { SectionHeader("حساباتك ومحافظك", "الكل") { onNavigate(2) } }
        item {
            if (accounts.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("لسه مافيش حسابات", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "هتظهر لوحدها مع أول رسالة من البنك أو المحفظة.",
                        style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(accounts, key = { it.id }) { a -> AccountMiniCard(a) { onNavigate(2) } }
                }
            }
        }

        if (insights.isNotEmpty()) {
            item { SectionHeader("تنبيهات الوكلاء") }
            items(insights.take(3), key = { "i" + it.id }) { InsightRow(it) { vm.dismissInsight(it) } }
        }

        item { PurposeBreakdown(counted.filter { it.direction == Direction.OUT }, vm::txnEgp) { onNavigate(4) } }

        item { SectionHeader("آخر العمليات", "عرض الكل") { onNavigate(1) } }
        if (recent.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.ReceiptLong, "مافيش عمليات لسه",
                    "هتتسجل لوحدها، أو دوس المايك وقول المصروف."
                )
            }
        } else {
            items(recent.take(12), key = { "t" + it.id }) { t -> TxnRow(t, names[t.accountId]) { onEdit(t) } }
        }
    }
}

@Composable
private fun HomeHeader(unread: Int, themeMode: ThemeMode, onTheme: () -> Unit, onBell: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(BrandGradient),
            contentAlignment = Alignment.Center
        ) {
            PulseLine(Modifier.size(30.dp, 20.dp), NabdColors.OnBrand)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("نبض", style = MaterialTheme.typography.headlineSmall)
            Text("نبض فلوسك لحظة بلحظة", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
        }
        IconButton(onClick = onTheme) {
            Icon(
                if (NabdColors.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                if (NabdColors.isDark) "الوضع الفاتح" else "الوضع الداكن",
                tint = NabdColors.TextDim
            )
        }
        Box {
            IconButton(onClick = onBell) { Icon(Icons.Rounded.Notifications, "التنبيهات", tint = NabdColors.Text) }
            if (unread > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp).clip(CircleShape)
                        .background(NabdColors.Danger),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (unread > 9) "9+" else "$unread", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(total: Double, income: Double, spent: Double, count: Int, onAdd: (Direction, Purpose?) -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HeroGradient)
            .border(1.dp, if (NabdColors.isDark) Color.White.copy(alpha = 0.08f) else NabdColors.Stroke, shape)
            .padding(20.dp)
    ) {
        Text("إجمالي فلوسك", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
        Spacer(Modifier.height(4.dp))
        Text(
            Money.fmt(total),
            style = MaterialTheme.typography.displaySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "في $count حساب ومحفظة • بالجنيه المصري",
            style = MaterialTheme.typography.bodySmall,
            color = NabdColors.TextDim
        )
        PulseLine(Modifier.fillMaxWidth().height(36.dp).padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowStat("دخل الشهر", Money.fmt(income, decimals = false), Icons.Rounded.ArrowDownward, NabdColors.Mint, Modifier.weight(1f))
            FlowStat("مصروف الشهر", Money.fmt(spent, decimals = false), Icons.Rounded.ArrowUpward, NabdColors.Pink, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton("مصروف جديد", Modifier.weight(1f), Icons.Rounded.Add) { onAdd(Direction.OUT, null) }
            PillButton("إضافة فلوس", Icons.Rounded.ArrowDownward, NabdColors.Mint, Modifier.weight(1f)) {
                onAdd(Direction.IN, null)
            }
        }
    }
}

@Composable
private fun FlowStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(icon, color, 32.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
            Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun PermissionCard(perms: Perms) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("شغّل الوكلاء", style = MaterialTheme.typography.titleMedium)
        Text(
            "علشان يقروا رسائل البنك وإشعارات المحافظ. بياناتك بتفضل على موبايلك.",
            style = MaterialTheme.typography.bodySmall,
            color = NabdColors.TextDim
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!perms.sms || !perms.postNotifications) {
                PillButton("الرسائل", Icons.Rounded.Sms, NabdColors.Mint, Modifier.weight(1f), perms.requestSms)
            }
            if (!perms.listener) {
                PillButton("الإشعارات", Icons.Rounded.NotificationsActive, NabdColors.Cyan, Modifier.weight(1f), perms.openListenerSettings)
            }
        }
    }
}

@Composable
private fun AccountMiniCard(a: Account, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .width(180.dp)
            .height(118.dp)
            .clip(shape)
            .background(Brush.linearGradient(a.colors()))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(a.type.icon(), null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                a.name, color = Color.White, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            Money.fmt(a.balance, a.currency),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            (if (a.balanceKnown) "رصيد مؤكد" else "رصيد تقديري") + " • " + Dates.relative(a.updatedAt),
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun InsightRow(i: Insight, onDismiss: () -> Unit) {
    val agent = AgentRegistry.byId(i.agentId)
    val c = when (i.severity) {
        2 -> NabdColors.Danger
        1 -> NabdColors.Gold
        else -> agent?.let { agentTint(it.color) } ?: NabdColors.Cyan
    }
    GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IconBubble(agentIcon(agent?.iconKey ?: ""), c, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(i.title, style = MaterialTheme.typography.titleSmall)
                Text(i.body, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                Text(
                    "${agent?.name ?: ""} • ${Dates.relative(i.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, "إخفاء", tint = NabdColors.TextFaint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PurposeBreakdown(
    out: List<Txn>,
    egp: (Txn) -> Double,
    title: String = "مصاريف الشهر رايحة فين؟",
    onPurpose: ((Purpose) -> Unit)? = null,
) {
    val byPurpose = Purpose.entries.map { p -> p to out.filter { it.purpose == p }.sumOf(egp) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    val total = byPurpose.sumOf { it.second }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = byPurpose.map { it.second.toFloat() to it.first.color() },
                modifier = Modifier.size(130.dp),
                centerTop = Money.compact(total),
                centerBottom = "ج.م"
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (byPurpose.isEmpty()) {
                    Text("مافيش مصاريف الشهر ده", color = NabdColors.TextDim, style = MaterialTheme.typography.bodySmall)
                }
                byPurpose.forEach { (p, v) ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .then(if (onPurpose != null) Modifier.clickable { onPurpose(p) } else Modifier)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(p.color()))
                        Spacer(Modifier.width(8.dp))
                        Text(p.ar, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "${(v / total * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = NabdColors.TextDim
                        )
                    }
                }
            }
        }
    }
}

/** كارت "في تحديث جديد" فوق الرئيسية */
private fun androidx.compose.foundation.lazy.LazyListScope.updateCard(update: UpdateState, vm: AppViewModel) {
    val release = when (update) {
        is UpdateState.Available -> update.release
        is UpdateState.Ready -> update.release
        else -> null
    }
    if (release == null && update !is UpdateState.Downloading) return
    item(key = "update") {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.SystemUpdate, NabdColors.Mint, 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (release != null) "تحديث جديد: ${release.name}" else "بينزل التحديث…",
                        style = MaterialTheme.typography.titleSmall
                    )
                    release?.notes?.lineSequence()?.firstOrNull { it.isNotBlank() }?.let {
                        Text(it.trim().removePrefix("-").trim(), style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when (update) {
                is UpdateState.Downloading -> ProgressBar(update.progress, NabdColors.Mint)
                is UpdateState.Ready -> GradientButton("ثبّت دلوقتي", Modifier.fillMaxWidth()) {
                    com.nabd.money.util.Updater.install(vm.getApplication(), update.file)
                }
                is UpdateState.Available -> GradientButton("نزّل وثبّت", Modifier.fillMaxWidth()) { vm.downloadAndInstall(update.release) }
                else -> Unit
            }
        }
    }
}
