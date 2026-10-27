package com.nabd.money.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nabd.money.agents.SalaryWatch
import com.nabd.money.data.SalaryConfig
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.ProgressBar
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money

private val SalaryGreen = androidx.compose.ui.graphics.Color(0xFF7BD389)

@Composable
fun SalaryCard(cfg: SalaryConfig, onClick: () -> Unit) {
    val st = SalaryWatch.status(cfg)
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = when {
                st == null -> NabdColors.TextDim
                st.daysLate > 0 -> NabdColors.Danger
                st.received -> SalaryGreen
                else -> NabdColors.Gold
            }
            IconBubble(Icons.Rounded.Paid, color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = when {
                    !cfg.enabled -> "حدد ميعاد قبضك"
                    st == null -> "مستني أول مرتب من ${cfg.payer}"
                    st.daysLate > 0 -> "المرتب متأخر ${st.daysLate} يوم"
                    st.received && st.daysLeft == 0 -> "المرتب الجاي النهاردة"
                    st.received -> "المرتب وصل ✓ • الجاي بعد ${st.daysLeft} يوم"
                    st.daysLeft == 0 -> "النهاردة ميعاد القبض"
                    else -> "فاضل ${st.daysLeft} يوم على القبض"
                }
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (st?.daysLate ?: 0 > 0) NabdColors.Danger else NabdColors.Text)
                val sub = when {
                    !cfg.enabled -> "اكتب اسم الشركة، والباقي عليّا"
                    st == null -> "الوكيل هيتعلم الميعاد أول ما التحويل يوصل"
                    else -> buildString {
                        append("من ${cfg.payer} • يوم ${st.payday}")
                        if (cfg.fixedDay == 0) append(" (اتعلمه لوحده)")
                        if (st.lastAmount > 0) append(" • آخر مرتب ${Money.fmt(st.lastAmount, decimals = false)}")
                    }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
            }
        }
        if (st != null && !st.received && st.daysLate == 0) {
            Spacer(Modifier.height(10.dp))
            ProgressBar(1f - st.daysLeft / 30f, NabdColors.Gold)
        }
        if (st != null && st.lastAt > 0) {
            Text(
                "آخر قبض: ${Dates.date(st.lastAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = NabdColors.TextFaint,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SalaryDialog(cfg: SalaryConfig, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var payer by remember { mutableStateOf(cfg.payer) }
    var day by remember { mutableStateOf(if (cfg.fixedDay > 0) cfg.fixedDay.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NabdColors.Surface,
        title = { Text("ميعاد القبض", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "أي تحويل من الشركة دي يتسجل مرتب، وأنبهك لو اتأخر.",
                    style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                )
                OutlinedTextField(
                    value = payer, onValueChange = { payer = it },
                    label = { Text("اسم الشركة (مثلاً: ACME LTD)") },
                    singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = day, onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("يوم القبض في الشهر (سيبه فاضي = تلقائي)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
                if (cfg.learnedDay > 0) {
                    Text("الوكيل اتعلم إن قبضك يوم ${cfg.learnedDay}", style = MaterialTheme.typography.labelSmall, color = SalaryGreen)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = day.toIntOrNull()?.takeIf { it in 1..31 } ?: 0
                onSave(payer, d)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
