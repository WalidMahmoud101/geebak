package com.nabd.money.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.agents.SalaryWatch
import com.nabd.money.data.Account
import com.nabd.money.data.Direction
import com.nabd.money.data.Exclusion
import com.nabd.money.data.Insight
import com.nabd.money.data.Institution
import com.nabd.money.data.NetWorth
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnFilter
import com.nabd.money.data.belongsTo
import com.nabd.money.data.countsInTotals
import com.nabd.money.data.isIncome
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.COMPANY_TAB
import com.nabd.money.ui.Perms
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.ProgressBar
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.SectionHeader
import com.nabd.money.ui.components.TxnRow
import com.nabd.money.ui.components.agentIcon
import com.nabd.money.ui.components.agentTint
import com.nabd.money.ui.components.color
import com.nabd.money.ui.components.colors
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.HeroGradient
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.ui.theme.ThemeMode
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import com.nabd.money.util.UpdateState
import java.time.YearMonth

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
    val update by vm.update.collectAsStateWithLifecycle()
    vm.usdRate.collectAsStateWithLifecycle().value
    var salaryDialog by remember { mutableStateOf(false) }
    var breakdown by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    if (settingsOpen) SettingsDialog(vm) { settingsOpen = false }

    val accEgp: (Account) -> Double = { vm.toEgp(it.balance, it.currency) }
    val total = NetWorth.total(accounts, accEgp)
    val institutions = NetWorth.institutions(accounts, accEgp)
    val counted = accounts.count { NetWorth.counts(it, accEgp) }
    val pending = accounts.count { NetWorth.exclusion(it, accEgp(it)) == Exclusion.UNKNOWN }
    val names = accounts.associate { it.id to it.name }
    val unread = insights.filter { !it.read }

    if (salaryDialog) {
        SalaryDialog(salary, onDismiss = { salaryDialog = false }) { payer, day, amount, exact -> vm.setSalary(payer, day, amount, exact); salaryDialog = false }
    }
    if (breakdown) NetWorthSheet(vm, accounts, onDismiss = { breakdown = false }, onOpenAccounts = { breakdown = false; onNavigate(2) })

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            HomeHeader(
                unread = unread.size,
                onAgents = { onNavigate(3) },
                onSettings = { settingsOpen = true },
                onBell = { vm.markInsightsRead() }
            )
        }
        updateCard(update, vm)
        item { BalanceCard(total, counted, pending, onClick = { breakdown = true }) }
        item {
            QuickActions(
                companyCount = companyPending.size,
                salaryLabel = salaryShort(salary),
                onExpense = { onAdd(Direction.OUT, null) },
                onIncome = { onAdd(Direction.IN, null) },
                onSalary = { salaryDialog = true },
                onCompany = { onNavigate(COMPANY_TAB) },
            )
        }
        if (!perms.allGranted) item { PermissionCard(perms) }
        if (unread.isNotEmpty()) {
            items(unread.take(2).size) { i -> InsightRow(unread[i]) { vm.dismissInsight(unread[i]) } }
        }

        item { SectionHeader("فلوسك في كل حساب", "الكل") { onNavigate(2) } }
        item {
            if (institutions.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("لسه مافيش حسابات", style = MaterialTheme.typography.titleSmall)
                    Text("هتظهر لوحدها مع أول رسالة من البنك أو المحفظة.", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                }
            } else {
                GlassCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                    institutions.filter { inst -> inst.accounts.any { NetWorth.exclusion(it, accEgp(it)) != Exclusion.INTERMEDIATE } }
                        .forEachIndexed { i, inst ->
                        if (i > 0) HorizontalDivider(color = NabdColors.Stroke, modifier = Modifier.padding(horizontal = 8.dp))
                        InstitutionLine(inst, accEgp) { onNavigate(2) }
                    }
                }
            }
        }

        item { SectionHeader("حسابات ${Dates.MONTHS[YearMonth.now().monthValue - 1]}", "التقارير") { onNavigate(4) } }
        item {
            MonthBoxes(month, vm::txnEgp) { f -> vm.setFilter(f); onNavigate(1) }
        }

        item { SectionHeader("آخر العمليات", "الكل") { onNavigate(1) } }
        item {
            if (recent.isEmpty()) {
                EmptyState(Icons.Rounded.Add, "مافيش عمليات لسه", "هتتسجل لوحدها، أو دوس المايك وقول المصروف.")
            } else {
                GlassCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                    recent.take(6).forEach { t -> TxnRow(t, names[t.accountId]) { onEdit(t) } }
                }
            }
        }
    }
}

private fun salaryShort(cfg: com.nabd.money.data.SalaryConfig): String {
    val st = SalaryWatch.status(cfg) ?: return if (cfg.enabled) "مستني" else "المرتب"
    return when {
        st.daysLate > 0 -> "متأخر ${st.daysLate} يوم"
        st.daysLeft == 0 -> "النهاردة"
        else -> "بعد ${st.daysLeft} يوم"
    }
}

@Composable
private fun HomeHeader(unread: Int, onAgents: () -> Unit, onSettings: () -> Unit, onBell: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(), style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
            Text("نبض", style = MaterialTheme.typography.headlineSmall)
        }
        IconButton(onClick = onAgents) { Icon(Icons.Rounded.SmartToy, "الوكلاء", tint = NabdColors.TextDim) }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "الإعدادات", tint = NabdColors.TextDim) }
        Box {
            IconButton(onClick = onBell) { Icon(Icons.Rounded.Notifications, "التنبيهات", tint = NabdColors.TextDim) }
            if (unread > 0) {
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp).clip(CircleShape).background(NabdColors.Danger))
            }
        }
    }
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "صباح الخير"
    in 12..16 -> "يومك سعيد"
    else -> "مساء الخير"
}

/** الكارت الأساسي: إجمالي فلوسك في كل الحسابات */
@Composable
private fun BalanceCard(total: Double, counted: Int, pending: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val onCard = Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HeroGradient)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("إجمالي فلوسك", style = MaterialTheme.typography.labelLarge, color = onCard.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronLeft, null, tint = onCard.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            Money.fmt(total),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = onCard,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (pending > 0) "من $counted حساب • $pending محتاج رصيده" else "من $counted حساب مؤكد",
            style = MaterialTheme.typography.labelMedium,
            color = if (pending > 0) Color(0xFFFFE08A) else onCard.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun QuickActions(
    companyCount: Int,
    salaryLabel: String,
    onExpense: () -> Unit,
    onIncome: () -> Unit,
    onSalary: () -> Unit,
    onCompany: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickAction("مصروف", Icons.Rounded.Remove, onExpense)
        QuickAction("إضافة فلوس", Icons.Rounded.Add, onIncome)
        QuickAction(salaryLabel, Icons.Rounded.Paid, onSalary)
        QuickAction(if (companyCount > 0) "الشركة ($companyCount)" else "الشركة", Icons.Rounded.BusinessCenter, onCompany)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(NabdColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = NabdColors.Mint, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * كل حاجة في حسابها لوحده: الدخل في خانة، والبيت في خانة، والشغل في خانة…
 * الدخل (مرتب، تحويلات جاتلك) مابيدخلش في صندوق البيت أو الشغل.
 */
@Composable
private fun MonthBoxes(month: List<Txn>, egp: (Txn) -> Double, onOpen: (TxnFilter) -> Unit) {
    val counted = month.filter { it.countsInTotals() }
    val income = counted.filter { it.isIncome() }
    val topIncome = income.groupBy { it.category }.maxByOrNull { (_, l) -> l.sumOf(egp) }
    val boxes = mutableListOf<@Composable (Modifier) -> Unit>()
    boxes += { m ->
        MoneyBox(
            "الدخل", Icons.Rounded.Paid, NabdColors.Mint,
            income.sumOf(egp).let { if (it > 0) Money.signed("+", it, decimals = false) else Money.fmt(0.0, decimals = false) },
            topIncome?.let { (cat, l) -> if (income.size > l.size) "منه $cat ${Money.fmt(l.sumOf(egp), decimals = false)}" else cat }
                ?: "لسه مافيش دخل",
            m,
        ) { onOpen(TxnFilter(direction = Direction.IN)) }
    }
    Purpose.entries.forEach { p ->
        val mine = counted.filter { it.belongsTo(p) }
        if (mine.isEmpty() && p != Purpose.HOME && p != Purpose.WORK) return@forEach
        val spent = mine.filter { it.direction == Direction.OUT }.sumOf(egp)
        val added = mine.filter { it.direction == Direction.IN }.sumOf(egp)
        boxes += { m ->
            MoneyBox(
                p.ar, p.icon(), p.color(),
                if (spent > 0) Money.signed("−", spent, decimals = false) else Money.fmt(0.0, decimals = false),
                when {
                    added > 0 -> if (added >= spent) "باقي ${Money.fmt(added - spent, decimals = false)} من ${Money.fmt(added, decimals = false)}" else "زاد ${Money.fmt(spent - added, decimals = false)} عن اللي اتحط"
                    mine.isEmpty() -> "مافيش مصاريف"
                    else -> "${mine.size} عملية"
                },
                m,
            ) { onOpen(TxnFilter(purposes = setOf(p))) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        boxes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MoneyBox(title: String, icon: ImageVector, color: Color, value: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, color, 30.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** بنك أو محفظة بإجمالي فلوسه، وتحته كل حساب أو كارت ورصيده لوحده */
@Composable
private fun InstitutionLine(inst: Institution, egp: (Account) -> Double, onClick: () -> Unit) {
    val first = inst.accounts.first()
    val shown = inst.accounts.filter { NetWorth.exclusion(it, egp(it)) != Exclusion.INTERMEDIATE }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(first.colors())),
                contentAlignment = Alignment.Center
            ) { Icon(first.type.icon(), null, tint = Color.White, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(inst.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        inst.accounts.all { it.isCredit } -> "كارت ائتمان"
                        inst.countedCount == 0 -> "محتاج رصيده"
                        shown.size > 1 -> "الإجمالي"
                        else -> first.type.ar
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inst.countedCount == 0 && !inst.accounts.all { it.isCredit }) NabdColors.Gold else NabdColors.TextDim
                )
            }
            Text(
                if (inst.countedCount > 0) Money.fmt(inst.total, decimals = false) else "—",
                style = MaterialTheme.typography.titleSmall,
                color = if (inst.countedCount > 0) NabdColors.Text else NabdColors.TextFaint
            )
        }
        if (shown.size > 1) {
            shown.forEach { a ->
                val ex = NetWorth.exclusion(a, egp(a))
                Row(Modifier.padding(start = 50.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (ex == null) NabdColors.Mint else NabdColors.TextFaint))
                    Spacer(Modifier.width(8.dp))
                    Text(a.name, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        when (ex) {
                            null -> Money.fmt(a.balance, a.currency, decimals = false)
                            Exclusion.CREDIT -> "متاح ${Money.fmt(a.balance, a.currency, decimals = false)}"
                            Exclusion.UNKNOWN -> "محتاج رصيده"
                            else -> Money.fmt(a.balance, a.currency, decimals = false)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (ex == null) NabdColors.Text else NabdColors.TextFaint
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(perms: Perms) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("شغّل التسجيل التلقائي", style = MaterialTheme.typography.titleSmall)
        Text(
            "علشان يقرا رسائل البنك وإشعارات المحافظ. بياناتك بتفضل على موبايلك.",
            style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!perms.sms || !perms.postNotifications) GradientButton("الرسائل", Modifier.weight(1f), onClick = perms.requestSms)
            if (!perms.listener) GradientButton("الإشعارات", Modifier.weight(1f), onClick = perms.openListenerSettings)
        }
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
            IconBubble(agentIcon(agent?.iconKey ?: ""), c, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(i.title, style = MaterialTheme.typography.titleSmall)
                Text(i.body, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, "إخفاء", tint = NabdColors.TextFaint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** توزيع المصروف على الأغراض (بيستخدمه التقارير كمان) */
@Composable
fun PurposeBreakdown(
    out: List<Txn>,
    egp: (Txn) -> Double,
    title: String = "حسب الغرض",
    onPurpose: ((Purpose) -> Unit)? = null,
) {
    val byPurpose = Purpose.entries.map { p -> p to out.filter { it.purpose == p }.sumOf(egp) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    val total = byPurpose.sumOf { it.second }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (byPurpose.isEmpty()) {
            Text("مافيش مصاريف", color = NabdColors.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        byPurpose.forEach { (p, v) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (onPurpose != null) Modifier.clickable { onPurpose(p) } else Modifier)
                    .padding(vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(p.icon(), null, tint = p.color(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(p.ar, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${(v / total * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim, modifier = Modifier.padding(end = 10.dp))
                    Text(Money.fmt(v, decimals = false), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar((v / total).toFloat(), p.color())
            }
        }
    }
}

/** كارت "في تحديث جديد" فوق الرئيسية */
private fun LazyListScope.updateCard(update: UpdateState, vm: AppViewModel) {
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
                    Text(if (release != null) "تحديث جديد: ${release.name}" else "بينزل التحديث…", style = MaterialTheme.typography.titleSmall)
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
