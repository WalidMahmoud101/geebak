package com.nabd.money.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.agents.ArabicNumbers
import com.nabd.money.data.Invoice
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.CompanyInvoice
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** هل الفاتورة دي بتطابق البحث؟ (الرقم، الشركة، اسمك، المبلغ، التاريخ) */
fun invoiceMatches(inv: Invoice, query: String): Boolean {
    val q = ArabicNumbers.norm(query.trim())
    if (q.isEmpty()) return true
    val hay = ArabicNumbers.norm(
        listOf(inv.number, inv.company, inv.employee, inv.scope, Dates.date(inv.createdAt), "%.2f".format(java.util.Locale.US, inv.total), inv.total.toLong().toString()).joinToString(" ")
    )
    return q.split(' ').filter { it.isNotBlank() }.all { hay.contains(it) }
}

/** الفواتير المحفوظة: دوّر برقم الفاتورة أو الشركة أو المبلغ، وافتحها واطبعها تاني */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val all by vm.invoices.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Invoice?>(null) }
    val shown = all.filter { invoiceMatches(it, q) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("الفواتير المحفوظة", style = MaterialTheme.typography.titleLarge)
            Text("كل فاتورة بتطبعها أو تبعتها بتتحفظ هنا برقمها وبنودها.", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
            OutlinedTextField(
                q, { q = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text("ابحث برقم الفاتورة أو الشركة أو المبلغ") }, placeholder = { Text("مثلاً NBD-20261029-001 أو 001") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Search, null) },
            )
            if (all.isEmpty()) {
                Text("لسه مفيش فواتير. اعمل أول واحدة من خانة الشركة.", style = MaterialTheme.typography.bodyMedium, color = NabdColors.TextDim)
            } else if (shown.isEmpty()) {
                Text("مفيش فاتورة بالبحث ده.", style = MaterialTheme.typography.bodyMedium, color = NabdColors.Gold)
            }
            shown.forEach { inv ->
                GlassCard(Modifier.fillMaxWidth(), padding = 12.dp, onClick = { open = inv }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.number, style = MaterialTheme.typography.titleSmall, color = NabdColors.Cyan)
                            Text(
                                "${Dates.date(inv.createdAt)} • ${inv.company.ifBlank { "—" }} • ${inv.itemCount} بند",
                                style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
                            )
                        }
                        Text(Money.fmt(inv.total), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
    open?.let { SavedInvoiceSheet(vm, it) { open = null } }
}

/** فاتورة محفوظة: معاينة وطباعة وPDF وExcel من النسخة المحفوظة */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedInvoiceSheet(vm: AppViewModel, inv: Invoice, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember(inv) { CompanyInvoice.fromStored(inv) }
    var previews by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(inv) {
        previews = withContext(Dispatchers.Default) { runCatching { CompanyInvoice.renderPages(ctx, info, 1.2f) }.getOrDefault(emptyList()) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(inv.number, style = MaterialTheme.typography.titleLarge, color = NabdColors.Cyan)
            Text(
                "${Dates.full(inv.createdAt)} • ${inv.company.ifBlank { "—" }} • ${inv.employee.ifBlank { "—" }} • ${inv.scope}",
                style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
            )
            previews.forEach { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = "الفاتورة", contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.White)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("طباعة", Icons.Rounded.Print, NabdColors.Mint, Modifier.weight(1f)) {
                    runCatching { CompanyInvoice.print(ctx, CompanyInvoice.pdf(ctx, info), "فاتورة ${inv.number}") }.onFailure { note = "الطباعة ماشتغلتش: ${it.message}" }
                }
                PillButton("PDF", Icons.Rounded.PictureAsPdf, NabdColors.Danger, Modifier.weight(1f)) {
                    runCatching { CompanyInvoice.share(ctx, CompanyInvoice.pdf(ctx, info), CompanyInvoice.MIME_PDF, "فاتورة نبض ${inv.number}") }.onFailure { note = "ماقدرتش أعمل الـ PDF: ${it.message}" }
                }
            }
            GradientButton("Excel للمحاسب (.xlsx)", Modifier.fillMaxWidth(), Icons.Rounded.TableChart) {
                runCatching { CompanyInvoice.share(ctx, CompanyInvoice.xlsx(ctx, info), CompanyInvoice.MIME_XLSX, "مصاريف الشركة ${inv.number}") }.onFailure { note = "ماقدرتش أعمل ملف الإكسيل: ${it.message}" }
            }
            note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = NabdColors.Danger) }
            PillButton("احذف الفاتورة المحفوظة", Icons.Rounded.Delete, NabdColors.Danger, Modifier.fillMaxWidth()) { confirmDelete = true }
            Text("الحذف بيمسح الفاتورة بس، والمصاريف نفسها بتفضل.", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NabdColors.Surface,
            title = { Text("تحذف ${inv.number}؟", style = MaterialTheme.typography.titleMedium) },
            text = { Text("الفاتورة المحفوظة هتتمسح، والمصاريف اللي فيها مش هتتأثر.", color = NabdColors.TextDim) },
            confirmButton = { TextButton(onClick = { vm.deleteInvoice(inv); confirmDelete = false; onDismiss() }) { Text("احذف", color = NabdColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}
