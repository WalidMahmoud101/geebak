package com.nabd.money.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.AppViewModel.ResetScope
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import java.time.YearMonth

/** ابدأ من جديد: شهر جديد، أو من غير عمليات، أو من الصفر خالص */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val counts by vm.resetCounts.collectAsStateWithLifecycle()
    val canUndo by vm.canUndoReset.collectAsStateWithLifecycle()
    val message by vm.backupMessage.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<ResetScope?>(null) }
    LaunchedEffect(Unit) { vm.backupMessage.value = null; vm.loadResetCounts() }
    val month = Dates.monthLabel(YearMonth.now())
    val (before, all) = counts ?: (0 to 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ابدأ من جديد", style = MaterialTheme.typography.titleLarge)
            Text(
                "قبل أي مسح بتتعمل نسخة كاملة لوحدها، وتقدر ترجّعها بلمسة.",
                style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
            )
            ResetOption(
                Icons.Rounded.CalendarMonth, NabdColors.Mint,
                "ابدأ من أول $month",
                "يمسح ${before} عملية قبل الشهر ده، ويسيب الشهر ده وحساباتك وإعداداتك",
                enabled = before > 0,
            ) { confirm = ResetScope.BEFORE_THIS_MONTH }
            ResetOption(
                Icons.Rounded.DeleteSweep, NabdColors.Gold,
                "امسح كل العمليات",
                "يمسح ${all} عملية. الحسابات والمرتب والتصنيفات والقواعد يفضلوا",
                enabled = all > 0,
            ) { confirm = ResetScope.ALL_TXNS }
            ResetOption(
                Icons.Rounded.RestartAlt, NabdColors.Danger,
                "ابدأ من الصفر خالص",
                "كأنك لسه منزّل التطبيق: العمليات والحسابات والميزانيات والمخزون والإعدادات",
                enabled = true,
            ) { confirm = ResetScope.EVERYTHING }
            Text(
                "المسح جوا جيبك بس، رسايلك في الموبايل مش بتتمسح. والرسايل القديمة مش هتتسجل تاني.",
                style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint
            )
            if (canUndo) {
                PillButton("رجّع اللي اتمسح آخر مرة", Icons.Rounded.Undo, NabdColors.Mint, Modifier.fillMaxWidth()) { vm.undoReset() }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = NabdColors.Mint) }
        }
    }

    confirm?.let { scope ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = NabdColors.Surface,
            title = {
                Text(
                    when (scope) {
                        ResetScope.BEFORE_THIS_MONTH -> "تبدأ من أول $month؟"
                        ResetScope.ALL_TXNS -> "تمسح كل العمليات؟"
                        ResetScope.EVERYTHING -> "تبدأ من الصفر؟"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    when (scope) {
                        ResetScope.BEFORE_THIS_MONTH -> "$before عملية هتتمسح."
                        ResetScope.ALL_TXNS -> "$all عملية هتتمسح، والأرصدة التقديرية ترجع صفر."
                        ResetScope.EVERYTHING -> "كل البيانات والإعدادات هتتمسح."
                    } + " هاحتفظ بنسخة وتقدر ترجّعها.",
                    color = NabdColors.TextDim
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.reset(scope); confirm = null }) {
                    Text("امسح", color = NabdColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun ResetOption(icon: ImageVector, color: Color, title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NabdColors.SurfaceTop)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(icon, if (enabled) color else NabdColors.TextFaint, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) NabdColors.Text else NabdColors.TextFaint)
            Text(body, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
        }
    }
}
