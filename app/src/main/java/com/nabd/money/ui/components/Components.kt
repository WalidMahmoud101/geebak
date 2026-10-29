package com.nabd.money.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.ui.theme.BrandGradient
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import java.time.YearMonth

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // كارت هادي: من غير إطار في الفاتح، وإطار خفيف جداً في الداكن
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(NabdColors.SurfaceHigh)
            .then(if (NabdColors.isDark) Modifier.border(1.dp, NabdColors.Stroke, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
            }
        }
        trailing()
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = NabdColors.TextDim,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun IconBubble(icon: ImageVector, color: Color, size: Dp = 36.dp) {
    // مربع بحواف ناعمة وخلفية هادية بنفس لون الأيقونة: شكل واحد في كل التطبيق
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(color.copy(alpha = if (NabdColors.isDark) 0.15f else 0.11f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    // الزرار الأساسي: لون واحد سادة
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NabdColors.Mint)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = NabdColors.OnAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = NabdColors.OnAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // زرار تاني: خلفية رمادي خفيف من غير إطار
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NabdColors.SurfaceTop)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (color == NabdColors.Danger) color else NabdColors.Text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    color: Color = NabdColors.Mint,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = if (NabdColors.isDark) 0.18f else 0.12f) else NabdColors.SurfaceTop)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) color else NabdColors.TextDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) color else NabdColors.Text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun PurposeChips(selected: Purpose?, includeAll: Boolean, onSelect: (Purpose?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (includeAll) SelectChip("الكل", selected == null) { onSelect(null) }
        Purpose.entries.forEach { p ->
            SelectChip(p.ar, selected == p, p.color(), p.icon()) { onSelect(p) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TxnRow(
    t: Txn, accountName: String?,
    selecting: Boolean = false, selected: Boolean = false, onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val out = t.direction == Direction.OUT
    val muted = t.excluded || t.reimbursable
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.background(NabdColors.Mint.copy(alpha = 0.10f)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) NabdColors.Mint else NabdColors.TextFaint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        IconBubble(t.purpose.icon(), t.purpose.color(), 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.merchant ?: t.category,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    t.category.takeIf { t.merchant != null },
                    accountName ?: "كاش",
                    Dates.time(t.timestamp),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = NabdColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.signed(if (out) "−" else "+", t.amount, t.currency),
                color = when {
                    muted -> NabdColors.TextDim
                    out -> NabdColors.Text
                    else -> NabdColors.Mint
                },
                style = MaterialTheme.typography.titleSmall
            )
            t.invoiceNo?.takeIf { it.isNotBlank() }?.let { Text("فاتورة $it", style = MaterialTheme.typography.labelSmall, color = NabdColors.Cyan, maxLines = 1) }
            when {
                t.reimbursable -> Text("على الشركة", style = MaterialTheme.typography.labelSmall, color = NabdColors.Cyan)
                t.excluded -> Text(when (t.category) { "تحويل بين حساباتي" -> "تحويل بين حساباتك"; "عهدة من الشركة" -> "فلوس شركة معاك (عهدة)"; "رد للشركة" -> "رديتها للشركة"; "استرداد مصاريف الشركة" -> "استرداد من الشركة"; else -> "مخفية" }, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
            }
        }
    }
}

@Composable
fun DonutChart(
    slices: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    stroke: Dp = 18.dp,
    centerTop: String = "",
    centerBottom: String = "",
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val d = size.minDimension - w
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arcSize = Size(d, d)
            drawArc(NabdColors.Stroke, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
            val total = slices.sumOf { it.first.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            var start = -90f
            val gap = if (slices.count { it.first > 0 } > 1) 4f else 0f
            slices.forEach { (v, c) ->
                if (v <= 0f) return@forEach
                val sweep = v / total * 360f
                drawArc(
                    c, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize,
                    style = Stroke(w, cap = StrokeCap.Round)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (centerBottom.isNotEmpty()) {
                Text(centerBottom, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
            }
        }
    }
}

@Composable
fun BarChart(values: List<Float>, modifier: Modifier = Modifier, highlight: Int? = null) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val slot = size.width / values.size
        val barW = (slot * 0.6f).coerceAtMost(14.dp.toPx())
        values.forEachIndexed { i, v ->
            // الرسم من اليمين لليسار علشان يتماشى مع RTL
            val x = size.width - (i + 1) * slot + (slot - barW) / 2
            val h = (v / max) * size.height
            drawRoundRect(
                color = NabdColors.Stroke.copy(alpha = 0.5f),
                topLeft = Offset(x, 0f),
                size = Size(barW, size.height),
                cornerRadius = CornerRadius(barW / 2)
            )
            if (h > 0f) {
                drawRoundRect(
                    brush = if (i == highlight) Brush.verticalGradient(listOf(NabdColors.Gold, NabdColors.Pink))
                    else Brush.verticalGradient(listOf(NabdColors.Mint, NabdColors.Cyan)),
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h.coerceAtLeast(barW)),
                    cornerRadius = CornerRadius(barW / 2)
                )
            }
        }
    }
}

@Composable
fun PulseLine(modifier: Modifier = Modifier, color: Color = NabdColors.Mint) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2
        val p = Path().apply {
            moveTo(0f, mid)
            lineTo(w * 0.30f, mid)
            lineTo(w * 0.36f, mid - h * 0.25f)
            lineTo(w * 0.43f, mid + h * 0.40f)
            lineTo(w * 0.50f, mid - h * 0.45f)
            lineTo(w * 0.56f, mid + h * 0.15f)
            lineTo(w * 0.60f, mid)
            lineTo(w, mid)
        }
        drawPath(
            p,
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color, color.copy(alpha = 0f))),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(NabdColors.Stroke)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
fun MonthSwitcher(ym: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NabdColors.Surface)
            .border(1.dp, NabdColors.Stroke, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // في الـ RTL أول عنصر بيبقى على اليمين = الشهر اللي فات
        IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronRight, "الشهر اللي فات") }
        Text(
            Dates.monthLabel(ym),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onNext, enabled = ym < YearMonth.now()) {
            Icon(Icons.Rounded.ChevronLeft, "الشهر الجاي")
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBubble(icon, NabdColors.Mint, 64.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = NabdColors.TextDim, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatColumn(label: String, value: String, color: Color, modifier: Modifier = Modifier, labelColor: Color = NabdColors.TextDim) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

val ScreenPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 104.dp)
