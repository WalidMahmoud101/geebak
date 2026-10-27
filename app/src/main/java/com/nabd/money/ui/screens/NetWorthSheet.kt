package com.nabd.money.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabd.money.data.Account
import com.nabd.money.data.Exclusion
import com.nabd.money.data.NetWorth
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Money

/** إجمالي فلوسك جاي منين: كل بنك وتطبيق بقيمته، واللي مش محسوب وليه */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthSheet(vm: AppViewModel, accounts: List<Account>, onDismiss: () -> Unit, onOpenAccounts: () -> Unit) {
    val egp: (Account) -> Double = { vm.toEgp(it.balance, it.currency) }
    val institutions = NetWorth.institutions(accounts, egp).filter { it.countedCount > 0 }
    val excluded = accounts.mapNotNull { a -> NetWorth.exclusion(a, egp(a))?.let { a to it } }
    val total = NetWorth.total(accounts, egp)
    var setFor by remember { mutableStateOf<Account?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("إجمالي فلوسك", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
            Text(Money.fmt(total), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "مجموع الحسابات اللي رصيدها مؤكد • الدولار بـ ${Money.fmt(vm.settings.usdRate.value, decimals = true)}",
                style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
            )
            Spacer(Modifier.height(4.dp))
            institutions.forEach { inst ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(inst.accounts.first().type.icon(), NabdColors.Mint, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(inst.name, style = MaterialTheme.typography.titleSmall)
                        inst.accounts.filter { NetWorth.counts(it, egp) }.takeIf { it.size > 1 }?.let { list ->
                            Text(
                                list.joinToString(" + ") { Money.fmt(it.balance, it.currency, decimals = false) },
                                style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.fmt(inst.total, decimals = false), style = MaterialTheme.typography.titleSmall)
                        if (total > 0) {
                            Text("${(inst.total / total * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                        }
                    }
                }
            }
            if (institutions.isEmpty()) {
                Text("لسه مافيش حساب رصيده مؤكد.", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
            }

            if (excluded.isNotEmpty()) {
                HorizontalDivider(color = NabdColors.Stroke, modifier = Modifier.padding(vertical = 6.dp))
                Text("مش داخل في الإجمالي", style = MaterialTheme.typography.titleSmall)
                excluded.forEach { (a, why) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { if (why == Exclusion.UNKNOWN) setFor = a else onOpenAccounts() }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(why.ar, style = MaterialTheme.typography.labelSmall, color = if (why == Exclusion.UNKNOWN) NabdColors.Gold else NabdColors.TextFaint)
                        }
                        if (why == Exclusion.UNKNOWN) {
                            Text(
                                "اكتب الرصيد",
                                style = MaterialTheme.typography.labelMedium,
                                color = NabdColors.Gold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(NabdColors.Gold.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        } else {
                            Text(Money.fmt(a.balance, a.currency, decimals = false), style = MaterialTheme.typography.labelLarge, color = NabdColors.TextFaint)
                        }
                    }
                }
            }
        }
    }

    setFor?.let { a ->
        AmountDialog("رصيدك الحقيقي دلوقتي في ${a.name} (${a.currency})", 0.0, onDismiss = { setFor = null }) {
            vm.setBalance(a, it); setFor = null
        }
    }
}
