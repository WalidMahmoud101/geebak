package com.nabd.money.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nabd.money.agents.Draft
import com.nabd.money.agents.VoiceParser
import com.nabd.money.data.Account
import com.nabd.money.data.Direction
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.color
import com.nabd.money.ui.components.icon
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money

val VOICE_EXAMPLES = listOf(
    "صرفت خمسمية جنيه في كارفور للبيت",
    "دفعت فاتورة الكهربا 350",
    "50 عيش و 100 بنزين للعربية",
    "قبضت المرتب 15 ألف",
    "دفعت 200 تاكسي على حساب الشركة",
    "اتعشينا برا امبارح بألف ومتين",
)

/** مراجعة اللي اتفهم من الكلام: تعدل النص، تشيل عملية، أو تحفظ الكل بلمسة */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceSheet(
    initialText: String,
    accounts: List<Account>,
    defaultCompany: Boolean,
    error: String?,
    autoSave: Boolean,
    onAutoSaveChange: (Boolean) -> Unit,
    onListenAgain: () -> Unit,
    onEditDraft: (Draft) -> Unit,
    onSave: (List<Draft>) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var removed by remember(initialText) { mutableStateOf(setOf<Int>()) }
    val drafts = remember(text, accounts) { VoiceParser.parse(text, accounts, defaultCompany = defaultCompany) }
    val kept = drafts.filterIndexed { i, _ -> i !in removed }
    val names = accounts.associate { it.id to it.name }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.Mic, NabdColors.Mint)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("سجّل بصوتك", style = MaterialTheme.typography.titleLarge)
                    Text("اتكلم عادي بالمصري", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                }
            }
            if (error != null) {
                Text(error, color = NabdColors.Gold, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; removed = emptySet() },
                label = { Text("اللي اتقال (تقدر تعدله أو تكتب)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
            )

            if (drafts.isEmpty()) {
                Text(
                    if (text.isBlank()) "جرب تقول مثلاً:" else "مالقيتش مبلغ في الكلام ده. جرب كده:",
                    style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VOICE_EXAMPLES.forEach { ex ->
                        Text(
                            "«$ex»",
                            style = MaterialTheme.typography.bodySmall,
                            color = NabdColors.Cyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NabdColors.SurfaceHigh)
                                .clickable { text = ex; removed = emptySet() }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                Text("فهمت ${drafts.size} عملية:", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                drafts.forEachIndexed { i, d ->
                    if (i in removed) return@forEachIndexed
                    DraftCard(
                        d, names[d.accountId],
                        onEdit = { removed = removed + i; onEditDraft(d) },
                        onRemove = { removed = removed + i }
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NabdColors.SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("احفظ على طول من غير مراجعة", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = autoSave, onCheckedChange = onAutoSaveChange)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(
                    if (kept.isEmpty()) "مافيش حاجة للحفظ" else "حفظ ${kept.size} • ${Money.fmt(kept.sumOf { it.amount }, kept.first().currency, decimals = false)}",
                    Modifier.weight(1f), Icons.Rounded.Check
                ) { if (kept.isNotEmpty()) onSave(kept) }
                PillButton("اتكلم تاني", Icons.Rounded.Mic, NabdColors.Mint, Modifier.weight(0.7f), onListenAgain)
            }
        }
    }
}

@Composable
private fun DraftCard(d: Draft, accountName: String?, onEdit: () -> Unit, onRemove: () -> Unit) {
    val out = d.direction == Direction.OUT
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NabdColors.SurfaceHigh)
            .border(1.dp, (if (out) NabdColors.Pink else NabdColors.Mint).copy(alpha = 0.4f), shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(d.purpose.icon(), d.purpose.color(), 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Money.signed(if (out) "−" else "+", d.amount, d.currency),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = if (out) NabdColors.Text else NabdColors.Mint
                )
                Text(d.merchant ?: d.category, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "تعديل", tint = NabdColors.TextDim) }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "شيل", tint = NabdColors.TextFaint) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(d.purpose.ar, d.purpose.color())
            Tag(d.category, NabdColors.Cyan)
            Tag(accountName ?: "كاش", NabdColors.Gold)
            Tag(Dates.dayHeader(d.timestamp) + " " + Dates.time(d.timestamp), NabdColors.TextDim)
            if (d.company) Tag("على حساب الشركة", NabdColors.Cyan)
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
