package com.nabd.money.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.data.Settings
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.SelectChip
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Gold
import com.nabd.money.util.Money
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val fmt0 = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private val fmt2 = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private val GoldColor = Color(0xFFD9A21B)

/** تغيّر سعر الأوقية عن آخر يوم قبل النهاردة، بالنسبة المئوية (null لو مفيش مقارنة) */
fun goldChangePct(g: Settings.GoldState): Double? {
    val today = Dates.dayKey(System.currentTimeMillis())
    val prev = g.history.lastOrNull { it.first < today }?.second ?: return null
    return if (g.ounceUsd > 0 && prev > 0) (g.ounceUsd - prev) / prev * 100 else null
}

/** كارت الدهب في الرئيسية */
@Composable
fun GoldCard(g: Settings.GoldState, usdEgp: Double, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Savings, GoldColor)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("سعر الدهب", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (g.ounceUsd > 0) "جرام عيار 21 • اتحدّث ${Dates.relative(g.updatedAt)}" else "دوس علشان تجيب السعر",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
                )
            }
            if (g.ounceUsd > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.plain("", Gold.gram(g.ounceUsd, usdEgp, 21, g.premiumPct), "EGP", decimals = false), style = MaterialTheme.typography.titleSmall)
                    goldChangePct(g)?.let { c ->
                        Text(
                            (if (c >= 0) "▲ " else "▼ ") + fmt2.format(kotlin.math.abs(c)) + "%",
                            style = MaterialTheme.typography.labelSmall, color = if (c >= 0) NabdColors.Mint else NabdColors.Danger
                        )
                    }
                }
            }
        }
    }
}

/** أسعار الدهب بالجنيه: العيارات والجنيه الدهب والأوقية، وآلة حاسبة، ورسم لآخر الأيام */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoldSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val g by vm.gold.collectAsStateWithLifecycle()
    val rate by vm.usdRate.collectAsStateWithLifecycle()
    val message by vm.goldMessage.collectAsStateWithLifecycle()
    var premium by remember { mutableStateOf(if (g.premiumPct != 0.0) g.premiumPct.toString() else "") }
    var grams by remember { mutableStateOf("1") }
    var karat by remember { mutableStateOf(Gold.Karat.K21) }
    LaunchedEffect(Unit) { vm.goldMessage.value = null; if (g.ounceUsd <= 0) vm.syncGoldNow() }
    val oz = g.ounceUsd

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("سعر الدهب بالجنيه", style = MaterialTheme.typography.titleLarge)

            if (oz <= 0) {
                Text(message ?: "بنجيب السعر…", style = MaterialTheme.typography.bodyMedium, color = NabdColors.TextDim)
            } else {
                // الرقم الكبير: جرام عيار 21 (الأشهر في مصر)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NabdColors.SurfaceTop).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("جرام عيار 21", style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim)
                    Text("${fmt0.format(Gold.gram(oz, rate, 21, g.premiumPct))} ج.م", style = MaterialTheme.typography.headlineSmall, color = GoldColor)
                    goldChangePct(g)?.let { c ->
                        Text(
                            (if (c >= 0) "▲ " else "▼ ") + fmt2.format(kotlin.math.abs(c)) + "% عن امبارح",
                            style = MaterialTheme.typography.labelMedium, color = if (c >= 0) NabdColors.Mint else NabdColors.Danger
                        )
                    }
                    Text(
                        "الأوقية ${fmt2.format(oz)}$ • الدولار ${fmt2.format(rate)} ج.م • اتحدّث ${Dates.relative(g.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
                    )
                }

                Sparkline(g.history.map { it.second }, Modifier.fillMaxWidth().height(70.dp))
                if (g.history.size < 2) Text("الرسم بيبان بعد ما نجمع سعر يومين.", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)

                GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                    Gold.Karat.entries.forEachIndexed { i, k ->
                        if (i > 0) HorizontalDivider(color = NabdColors.Stroke, modifier = Modifier.padding(horizontal = 8.dp))
                        PriceRow(k.ar, "للجرام", "${fmt0.format(Gold.gram(oz, rate, k.k, g.premiumPct))} ج.م")
                    }
                    HorizontalDivider(color = NabdColors.Stroke, modifier = Modifier.padding(horizontal = 8.dp))
                    PriceRow("الجنيه الدهب", "8 جرام عيار 21", "${fmt0.format(Gold.pound(oz, rate, g.premiumPct))} ج.م")
                    HorizontalDivider(color = NabdColors.Stroke, modifier = Modifier.padding(horizontal = 8.dp))
                    PriceRow("الأوقية", "31.1 جرام عيار 24", "${fmt0.format(Gold.ounceEgp(oz, rate, g.premiumPct))} ج.م")
                }

                // آلة حاسبة: وزنك بكام
                Text("وزنك بكام؟", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        grams, { grams = it }, modifier = Modifier.weight(1f), label = { Text("الوزن بالجرام") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)
                    )
                    val w = Money.parseInput(grams)
                    Text(
                        if (w != null && w > 0) "${fmt0.format(Gold.gram(oz, rate, karat.k, g.premiumPct) * w)} ج.م" else "—",
                        style = MaterialTheme.typography.titleMedium, color = GoldColor
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Gold.Karat.entries.forEach { k -> SelectChip(k.ar, k == karat, GoldColor) { karat = k } }
                }

                OutlinedTextField(
                    premium, { premium = it; Money.parseInput(it.ifBlank { "0" })?.let(vm::setGoldPremium) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("علاوة أو خصم السوق المحلي % (اختياري)") }, singleLine = true,
                    placeholder = { Text("مثلاً 2 أو -1.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp)
                )
                Text(
                    "ده السعر العالمي للدهب × سعر الدولار اللي في «العملات». محلات الصاغة بتزود المصنعية والدمغة، وساعات السوق المحلي بيزيد أو ينقص عن السعر ده، فاضبط العلاوة لو عايز تقرّب لسعر محلك.",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
                )
            }
            PillButton("حدّث السعر دلوقتي", Icons.Rounded.Sync, NabdColors.Mint, Modifier.fillMaxWidth()) { vm.syncGoldNow() }
            message?.let { if (oz > 0) Text(it, style = MaterialTheme.typography.labelMedium, color = NabdColors.Mint) }
        }
    }
}

@Composable
private fun PriceRow(title: String, sub: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
        }
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** رسم خط بسيط لآخر الأسعار */
@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier) {
    if (values.size < 2) return
    Canvas(modifier) {
        val lo = values.min(); val hi = values.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - lo) / span * (size.height * 0.8f) + size.height * 0.1f).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, GoldColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        val last = values.last()
        drawCircle(GoldColor, 4.dp.toPx(), Offset(size.width, size.height - ((last - lo) / span * (size.height * 0.8f) + size.height * 0.1f).toFloat()))
    }
}
