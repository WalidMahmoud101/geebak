package com.nabd.money.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.data.Fx
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.SelectChip
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import com.nabd.money.util.Rates
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val rateFmt = DecimalFormat("#,##0.00##", DecimalFormatSymbols(Locale.US))

private val names = mapOf(
    "EGP" to "جنيه مصري", "USD" to "دولار", "USDT" to "USDT", "EUR" to "يورو",
    "GBP" to "جنيه إسترليني", "SAR" to "ريال سعودي", "AED" to "درهم إماراتي",
)

/** العملات: سعر الدولار بالجنيه (بيتزامن لوحده)، وتحويل بين أي عملتين */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CurrencySheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val rate by vm.usdRate.collectAsStateWithLifecycle()
    val auto by vm.autoRates.collectAsStateWithLifecycle()
    val at by vm.ratesAt.collectAsStateWithLifecycle()
    val message by vm.rateMessage.collectAsStateWithLifecycle()
    var manual by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("1") }
    var from by remember { mutableStateOf("USD") }
    LaunchedEffect(Unit) { vm.rateMessage.value = null }
    val value = Money.parseInput(amount)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("العملات وسعر الصرف", style = MaterialTheme.typography.titleLarge)

            // السعر في الاتجاهين
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NabdColors.SurfaceTop).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("1 دولار = ${rateFmt.format(rate)} جنيه", style = MaterialTheme.typography.titleMedium)
                Text("1 جنيه = ${DecimalFormat("0.0000", DecimalFormatSymbols(Locale.US)).format(1 / rate)} دولار", style = MaterialTheme.typography.bodyMedium, color = NabdColors.TextDim)
                Text(
                    if (at > 0) "اتحدّث ${Dates.relative(at)}" else if (auto) "لسه ماتزامنش" else "سعر كتبته بإيدك",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مزامنة تلقائية", style = MaterialTheme.typography.titleSmall)
                    Text("بيجيب السعر الحي كل كام ساعة. بيستخدم النت للسعر بس.", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                }
                Switch(checked = auto, onCheckedChange = { vm.setAutoRates(it) })
            }
            PillButton("حدّث السعر دلوقتي", Icons.Rounded.Sync, NabdColors.Mint, Modifier.fillMaxWidth()) { vm.syncRatesNow() }
            message?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = NabdColors.Mint) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manual, onValueChange = { manual = it }, modifier = Modifier.weight(1f),
                    label = { Text("سعر بإيدك (جنيه للدولار)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)
                )
                PillButton("احفظ", Icons.Rounded.Save, NabdColors.Gold) {
                    Money.parseInput(manual)?.takeIf { it > 1 }?.let { vm.setManualRate(it); manual = "" }
                }
            }
            Text(
                "السعر الحي هو سعر السوق الرسمي. لو بتتعامل بسعر تاني (بنك أو سوق) اكتبه بإيدك والمزامنة هتتقفل.",
                style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
            )

            HorizontalDivider(color = NabdColors.Stroke)

            Text("حوّل بين العملات", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("المبلغ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Rates.CURRENCIES.forEach { c -> SelectChip(c, c == from, NabdColors.Gold) { from = c } }
            }
            if (value != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NabdColors.SurfaceTop).padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Rates.CURRENCIES.filter { it != from && !(from in setOf("USD", "USDT") && it in setOf("USD", "USDT")) }.forEach { to ->
                        val out = Fx.convert(value, from, to, rate)
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(names[to] ?: to, style = MaterialTheme.typography.bodyMedium, color = NabdColors.TextDim, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(out?.let { Money.plain("", it, to) } ?: "—", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}
