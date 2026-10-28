package com.nabd.money.ui

import android.Manifest
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nabd.money.agents.Draft
import com.nabd.money.agents.VoiceParser
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.ui.screens.AccountsScreen
import com.nabd.money.ui.screens.AddTxnSheet
import com.nabd.money.ui.screens.AgentsScreen
import com.nabd.money.ui.screens.CompanyScreen
import com.nabd.money.ui.screens.EditTxnSheet
import com.nabd.money.ui.screens.HomeScreen
import com.nabd.money.ui.screens.LedgerScreen
import com.nabd.money.ui.screens.ReportsScreen
import com.nabd.money.ui.screens.VoiceSheet
import com.nabd.money.ui.theme.BrandGradient
import com.nabd.money.util.Money
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.ui.theme.NabdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as com.nabd.money.NabdApp).settings
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            NabdTheme(mode) {
                val dark = NabdColors.isDark
                DisposableEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                    else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    NabdRoot()
                }
            }
        }
    }
}

/** حالة الصلاحيات اللي الوكلاء محتاجينها */
class Perms(
    val sms: Boolean,
    val listener: Boolean,
    val postNotifications: Boolean,
    val requestSms: () -> Unit,
    val openListenerSettings: () -> Unit,
) {
    val allGranted get() = sms && listener && postNotifications
}

private fun hasPerm(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
private fun rememberPerms(onSmsGranted: () -> Unit): Perms {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        tick++
        if (res[Manifest.permission.READ_SMS] == true) onSmsGranted()
    }
    val sms = remember(tick) {
        hasPerm(ctx, Manifest.permission.READ_SMS) && hasPerm(ctx, Manifest.permission.RECEIVE_SMS)
    }
    val listener = remember(tick) {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }
    val post = remember(tick) {
        Build.VERSION.SDK_INT < 33 || hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)
    }
    return Perms(
        sms = sms,
        listener = listener,
        postNotifications = post,
        requestSms = {
            val list = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
            launcher.launch(list.toTypedArray())
        },
        openListenerSettings = {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
    )
}

const val COMPANY_TAB = 5

data class AddRequest(val direction: Direction, val purpose: Purpose?, val company: Boolean = false)

@Composable
fun NabdRoot(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addRequest by remember { mutableStateOf<AddRequest?>(null) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val perms = rememberPerms(onSmsGranted = { vm.autoSync() })
    LaunchedEffect(perms.sms) { if (perms.sms) vm.autoSync() }

    // ---------- الإدخال بالصوت ----------
    val ctx = LocalContext.current
    var voiceText by remember { mutableStateOf<String?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var voiceAutoSave by remember { mutableStateOf(vm.settings.voiceAutoSave) }
    var editDraft by remember { mutableStateOf<Draft?>(null) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val alternatives = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        if (alternatives.isEmpty()) return@rememberLauncherForActivityResult
        val best = VoiceParser.pickBest(alternatives, accounts)
        val drafts = VoiceParser.parse(best, accounts, defaultCompany = tab == COMPANY_TAB)
        if (voiceAutoSave && drafts.isNotEmpty()) {
            vm.saveDrafts(drafts)
            val total = Money.fmt(drafts.sumOf { it.amount }, drafts.first().currency)
            Toast.makeText(ctx, "اتسجل ${drafts.size} عملية • $total", Toast.LENGTH_LONG).show()
        } else {
            voiceError = null
            voiceText = best
        }
    }
    val startVoice: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "قول المصروف… مثلاً: صرفت 200 جنيه بنزين للعربية")
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            voiceError = "الصوت مش متاح هنا. ثبّت تطبيق Google، أو اكتب المصروف."
            voiceText = ""
        }
    }

    // الرجوع من أي شاشة بيودي للرئيسية بدل ما يقفل التطبيق
    androidx.activity.compose.BackHandler(enabled = tab != 0) { tab = 0 }

    val onAdd: (Direction, Purpose?) -> Unit = { d, p -> addRequest = AddRequest(d, p) }
    val onEdit: (Txn) -> Unit = { editing = it }

    Scaffold(
        containerColor = NabdColors.Bg,
        bottomBar = { BottomBar(if (tab == COMPANY_TAB || tab == 3) 0 else tab, onMic = startVoice) { tab = it } },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            when (tab) {
                0 -> HomeScreen(vm, perms, onNavigate = { tab = it }, onAdd = onAdd, onEdit = onEdit)
                COMPANY_TAB -> CompanyScreen(
                    vm,
                    onBack = { tab = 0 },
                    onAdd = { addRequest = AddRequest(Direction.OUT, Purpose.WORK, company = true) },
                    onEdit = onEdit,
                )
                1 -> LedgerScreen(vm, onAdd = onAdd, onEdit = onEdit, onCompany = { tab = COMPANY_TAB })
                2 -> AccountsScreen(vm, onEdit = onEdit)
                3 -> AgentsScreen(vm, perms)
                else -> ReportsScreen(vm)
            }
        }
    }

    addRequest?.let { req ->
        AddTxnSheet(
            initialDirection = req.direction,
            initialPurpose = req.purpose,
            accounts = accounts,
            onDismiss = { addRequest = null },
            initialCompany = req.company,
            defaultAccountId = vm.settings.lastAccountId.takeIf { it > 0 },
            onNewCategory = { name, p -> vm.addCategory(name, p) },
            onSave = { amount, dir, purpose, category, merchant, accountId, ts, note, company, cur ->
                vm.addManual(amount, dir, purpose, category, merchant, accountId, ts, note, company, cur)
                addRequest = null
            }
        )
    }
    voiceText?.let { initial ->
        VoiceSheet(
            initialText = initial,
            accounts = accounts,
            defaultCompany = tab == COMPANY_TAB,
            error = voiceError,
            autoSave = voiceAutoSave,
            onAutoSaveChange = { voiceAutoSave = it; vm.settings.voiceAutoSave = it },
            onListenAgain = { voiceText = null; startVoice() },
            onEditDraft = { editDraft = it },
            onSave = { drafts ->
                vm.saveDrafts(drafts)
                voiceText = null
                Toast.makeText(ctx, "اتسجل ${drafts.size} عملية", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { voiceText = null },
        )
    }
    editDraft?.let { d ->
        AddTxnSheet(
            initialDirection = d.direction,
            initialPurpose = d.purpose,
            accounts = accounts,
            onDismiss = { editDraft = null },
            draft = d,
            onNewCategory = { name, p -> vm.addCategory(name, p) },
            onSave = { amount, dir, purpose, category, merchant, accountId, ts, note, company, cur ->
                vm.addManual(amount, dir, purpose, category, merchant, accountId, ts, note, company, cur)
                editDraft = null
            }
        )
    }
    editing?.let { t ->
        EditTxnSheet(
            txn = t,
            accountName = accounts.firstOrNull { it.id == t.accountId }?.name,
            onDismiss = { editing = null },
            onSave = { t2, hide -> vm.updateTxn(t2, hide); editing = null },
            onDelete = { vm.deleteTxn(it); editing = null },
            onNewCategory = { name, p -> vm.addCategory(name, p) },
            onRule = { k, c, p -> vm.addRule(k, c, p) },
        )
    }
}

private data class NavItem(val label: String, val icon: ImageVector, val tab: Int)

// الوكلاء بقوا من أيقونة في الرئيسية، علشان الشريط يبقى أبسط
private val navItems = listOf(
    NavItem("الرئيسية", Icons.Rounded.Home, 0),
    NavItem("العمليات", Icons.Rounded.ReceiptLong, 1),
    NavItem("التقارير", Icons.Rounded.PieChart, 4),
    NavItem("الحسابات", Icons.Rounded.AccountBalanceWallet, 2),
)

@Composable
private fun BottomBar(selected: Int, onMic: () -> Unit, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(NabdColors.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(NabdColors.Stroke))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { i, item ->
                if (i == 2) MicButton(onMic)
                val active = item.tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(item.tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        item.icon, item.label,
                        tint = if (active) NabdColors.Mint else NabdColors.TextFaint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) NabdColors.Text else NabdColors.TextFaint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** زرار المايك في نص الشريط: اتكلم والمصروف يتسجل */
@Composable
private fun MicButton(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(NabdColors.Mint)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Mic, "سجّل بصوتك", tint = NabdColors.OnAccent, modifier = Modifier.size(22.dp))
    }
}
