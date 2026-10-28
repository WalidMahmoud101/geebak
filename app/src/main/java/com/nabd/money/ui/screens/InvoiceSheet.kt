package com.nabd.money.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nabd.money.data.Txn
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.SelectChip
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.CompanyInvoice
import com.nabd.money.util.InvoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Scope(val ar: String) { CHOSEN("المحدد"), PENDING("المستحقة"), ALL("الكل") }

/** فاتورة مصاريف الشركة: معاينة، طباعة، PDF، وExcel للمحاسب */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun InvoiceSheet(
    vm: AppViewModel,
    pending: List<Txn>, done: List<Txn>, chosen: List<Txn>,
    defaultCompany: String,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val s = vm.settings
    var scope by remember { mutableStateOf(if (chosen.isNotEmpty()) Scope.CHOSEN else Scope.PENDING) }
    var company by remember { mutableStateOf(s.invoiceCompany.ifBlank { defaultCompany }) }
    var employee by remember { mutableStateOf(s.invoiceEmployee) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val txns = when (scope) {
        Scope.CHOSEN -> chosen
        Scope.PENDING -> pending
        Scope.ALL -> pending + done
    }
    val info = remember(txns, company, employee, scope) {
        InvoiceInfo(CompanyInvoice.number(), company.trim(), employee.trim(), System.currentTimeMillis(), scope.ar, CompanyInvoice.items(txns))
    }
    var previews by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    LaunchedEffect(info) {
        previews = if (info.items.isEmpty()) emptyList()
        else withContext(Dispatchers.Default) { runCatching { CompanyInvoice.renderPages(ctx, info, 1.2f) }.getOrDefault(emptyList()) }
    }

    fun saveNames() { s.invoiceCompany = company.trim(); s.invoiceEmployee = employee.trim() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("فاتورة مصاريف الشركة", style = MaterialTheme.typography.titleLarge)
            Text("للمحاسب: PDF للطباعة وExcel بنفس البنود.", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)

            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chosen.isNotEmpty()) SelectChip("المحدد (${chosen.size})", scope == Scope.CHOSEN, NabdColors.Cyan) { scope = Scope.CHOSEN }
                SelectChip("المستحقة (${pending.size})", scope == Scope.PENDING, NabdColors.Cyan) { scope = Scope.PENDING }
                SelectChip("الكل (${pending.size + done.size})", scope == Scope.ALL, NabdColors.Cyan) { scope = Scope.ALL }
            }
            OutlinedTextField(
                value = company, onValueChange = { company = it }, label = { Text("اسم الشركة") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = employee, onValueChange = { employee = it }, label = { Text("اسمك (مقدّم الفاتورة)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )

            if (info.items.isEmpty()) {
                Text("مافيش بنود في الاختيار ده.", style = MaterialTheme.typography.bodyMedium, color = NabdColors.Gold)
            } else {
                previews.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = "معاينة الفاتورة",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.White)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("طباعة", Icons.Rounded.Print, NabdColors.Mint, Modifier.weight(1f)) {
                        saveNames(); busy = true
                        runCatching { CompanyInvoice.print(ctx, CompanyInvoice.pdf(ctx, info), "فاتورة ${info.number}") }
                            .onFailure { note = "الطباعة ماشتغلتش: ${it.message}" }
                        busy = false
                    }
                    PillButton("PDF", Icons.Rounded.PictureAsPdf, NabdColors.Danger, Modifier.weight(1f)) {
                        saveNames()
                        runCatching { CompanyInvoice.share(ctx, CompanyInvoice.pdf(ctx, info), CompanyInvoice.MIME_PDF, "فاتورة نبض ${info.number}") }
                            .onFailure { note = "ماقدرتش أعمل الـ PDF: ${it.message}" }
                    }
                }
                GradientButton("Excel للمحاسب (.xlsx)", Modifier.fillMaxWidth(), Icons.Rounded.TableChart) {
                    saveNames()
                    runCatching { CompanyInvoice.share(ctx, CompanyInvoice.xlsx(ctx, info), CompanyInvoice.MIME_XLSX, "مصاريف الشركة ${info.number}") }
                        .onFailure { note = "ماقدرتش أعمل ملف الإكسيل: ${it.message}" }
                }
                note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = NabdColors.Danger) }
            }
        }
    }
}
