package com.nabd.money.ui.screens

import com.nabd.money.ui.components.agentTint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.agents.AgentKind
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.agents.Categorizer
import com.nabd.money.agents.TrackerAgent
import com.nabd.money.data.AgentStat
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.Perms
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.ScreenTitle
import com.nabd.money.ui.components.SectionHeader
import com.nabd.money.ui.components.agentIcon
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates

@Composable
fun AgentsScreen(vm: AppViewModel, perms: Perms) {
    val disabled by vm.disabledAgents.collectAsStateWithLifecycle()
    val stats by vm.agentStats.collectAsStateWithLifecycle()
    val insightStats by vm.insightStats.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val scanResult by vm.scanResult.collectAsStateWithLifecycle()
    val active = AgentRegistry.all.count { it.id !in disabled }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = ScreenPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = full) {
            ScreenTitle("الوكلاء", "$active من ${AgentRegistry.all.size} شغالين")
        }
        item(span = full) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("مركز التحكم", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PermLine("قراءة رسائل البنوك والمحافظ (SMS)", perms.sms)
                PermLine("إشعارات التطبيقات (InstaPay، Binance…)", perms.listener)
                PermLine("إرسال تنبيهات الوكلاء", perms.postNotifications)
                Spacer(Modifier.height(12.dp))
                if (!perms.sms || !perms.postNotifications || !perms.listener) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!perms.sms || !perms.postNotifications) {
                            PillButton("فعّل الرسائل", null, NabdColors.Mint, Modifier.weight(1f), perms.requestSms)
                        }
                        if (!perms.listener) {
                            PillButton("فعّل الإشعارات", null, NabdColors.Cyan, Modifier.weight(1f), perms.openListenerSettings)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (scanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = NabdColors.Mint)
                        Spacer(Modifier.width(10.dp))
                        Text("الوكلاء بيقروا رسائلك القديمة...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    GradientButton("افحص رسائل آخر 4 شهور", Modifier.fillMaxWidth(), Icons.Rounded.ManageSearch) {
                        if (perms.sms) vm.scanInbox() else perms.requestSms()
                    }
                }
                scanResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = NabdColors.Mint, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item(span = full) { SectionHeader("المصادر (${AgentRegistry.sources.size})") }
        items(AgentRegistry.sources, key = { it.id }) { a ->
            AgentCard(a, a.id !in disabled, stats[a.id]) { vm.toggleAgent(a.id, it) }
        }
        item(span = full) { SectionHeader("التحليل (${AgentRegistry.insights.size})") }
        items(AgentRegistry.insights, key = { it.id }) { a ->
            AgentCard(a, a.id !in disabled, insightStats[a.id]) { vm.toggleAgent(a.id, it) }
        }
    }
}

@Composable
private fun PermLine(label: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
            tint = if (ok) NabdColors.Mint else NabdColors.Gold, modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (ok) NabdColors.Text else NabdColors.TextDim)
    }
}

@Composable
private fun AgentCard(agent: TrackerAgent, enabled: Boolean, stat: AgentStat?, onToggle: (Boolean) -> Unit) {
    val c = agentTint(agent.color)
    GlassCard(Modifier.fillMaxWidth().height(178.dp), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(agentIcon(agent.iconKey), if (enabled) c else NabdColors.TextFaint, 40.dp)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = c.copy(alpha = 0.8f),
                    uncheckedTrackColor = NabdColors.Surface,
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(agent.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            agent.description,
            style = MaterialTheme.typography.bodySmall,
            color = NabdColors.TextDim,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        val footer = when {
            !enabled -> "متوقف"
            agent.kind == AgentKind.SOURCE && stat != null -> "التقط ${stat.cnt} عملية • ${Dates.relative(stat.last)}"
            agent.kind == AgentKind.SOURCE -> "مستني أول عملية..."
            agent.id == Categorizer.ID -> "شغال على كل عملية"
            stat != null -> "${stat.cnt} تنبيه • ${Dates.relative(stat.last)}"
            else -> "بيراقب • مفيش تنبيهات"
        }
        Column {
            Text(footer, style = MaterialTheme.typography.labelSmall, color = if (enabled) c else NabdColors.TextFaint, maxLines = 1)
        }
    }
}
