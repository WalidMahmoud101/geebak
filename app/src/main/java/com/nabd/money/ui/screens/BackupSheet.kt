package com.nabd.money.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.BackupFiles
import com.nabd.money.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val busy by vm.backupBusy.collectAsStateWithLifecycle()
    val message by vm.backupMessage.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    var folder by remember { mutableStateOf(vm.settings.backupFolder) }
    val lastAt = vm.settings.lastBackupAt

    // Google Drive بيظهر في شاشة الملفات دي جنب فولدرات الموبايل
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFiles.MIME)) { uri ->
        if (uri != null) vm.saveBackup(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openBackup(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            folder = uri.toString()
            vm.setBackupFolder(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { vm.backupMessage.value = null; onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("النسخة الاحتياطية", style = MaterialTheme.typography.titleLarge)
            Text(
                if (lastAt > 0) "آخر نسخة: ${Dates.full(lastAt)}" else "لسه ماعملتش نسخة",
                style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
            )

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NabdColors.SurfaceTop).padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                IconBubble(Icons.Rounded.CloudDone, NabdColors.Mint, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("محمية على حساب Google", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "أندرويد بياخد نسخة لوحده كل يوم على حسابك. لو مسحت التطبيق أو غيرت الموبايل، بترجع لوحدها لما تثبته بنفس الحساب.",
                        style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                    )
                }
            }

            GradientButton("احفظ نسخة (Drive أو الموبايل)", Modifier.fillMaxWidth(), Icons.Rounded.Save) {
                saveLauncher.launch(BackupFiles.suggestedName())
            }
            PillButton("ابعتها (واتساب، إيميل…)", Icons.Rounded.Share, NabdColors.Mint, Modifier.fillMaxWidth()) { vm.shareBackup() }

            HorizontalDivider(color = NabdColors.Stroke)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("نسخة تلقائية كل يوم", style = MaterialTheme.typography.titleSmall)
                    Text(
                        BackupFiles.folderName(ctx, folder)?.let { "في فولدر «$it» • آخر 7 نسخ" } ?: "اختار فولدر مرة واحدة",
                        style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                    )
                }
                Switch(
                    checked = folder != null,
                    onCheckedChange = { on ->
                        if (on) folderLauncher.launch(null)
                        else { folder = null; vm.setBackupFolder(null) }
                    }
                )
            }
            if (folder != null) {
                PillButton("غيّر الفولدر", Icons.Rounded.FolderOpen, NabdColors.TextDim, Modifier.fillMaxWidth()) { folderLauncher.launch(null) }
            }

            HorizontalDivider(color = NabdColors.Stroke)

            PillButton("رجّع من نسخة", Icons.Rounded.Restore, NabdColors.Gold, Modifier.fillMaxWidth()) {
                openLauncher.launch(arrayOf(BackupFiles.MIME, "application/octet-stream", "text/plain", "*/*"))
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NabdColors.Mint)
                    Spacer(Modifier.width(10.dp))
                    Text("لحظة…", style = MaterialTheme.typography.bodySmall)
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = NabdColors.Mint) }
        }
    }

    pending?.let { (info, _) ->
        AlertDialog(
            onDismissRequest = { vm.pendingRestore.value = null },
            containerColor = NabdColors.Surface,
            title = { Text("ترجع للنسخة دي؟", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("اتعملت: ${Dates.full(info.createdAt)}", style = MaterialTheme.typography.bodyMedium)
                    Text("${info.transactions} عملية • ${info.accounts} حساب", style = MaterialTheme.typography.bodyMedium)
                    if (info.firstTxn != null && info.lastTxn != null) {
                        Text("من ${Dates.date(info.firstTxn)} لحد ${Dates.date(info.lastTxn)}", style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                    }
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        "البيانات الحالية هتتبدل بالنسخة دي. هاحتفظ بنسخة من الحالي على الموبايل احتياطي.",
                        style = MaterialTheme.typography.bodySmall, color = NabdColors.Gold
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmRestore() }) { Text("رجّع") } },
            dismissButton = { TextButton(onClick = { vm.pendingRestore.value = null }) { Text("إلغاء") } },
        )
    }
}
