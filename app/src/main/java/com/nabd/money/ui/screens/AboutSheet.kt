package com.nabd.money.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.BuildConfig
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.data.TxnSource
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.components.GeebakMark
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.theme.HeroGradient
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.action
import com.nabd.money.util.status

const val REPO_URL = "https://github.com/WalidMahmoud101/nabd"

/** أرقام حقيقية من بياناتك على الموبايل (مابتتبعتش لأي مكان) */
data class AboutStats(val transactions: Int, val auto: Int, val manual: Int, val accounts: Int, val since: Long?) {
    val autoPercent get() = if (transactions == 0) 0 else auto * 100 / transactions
}

/** مكتبات وخط وخدمات جيبك بيستخدمها وتراخيصها (نفس اللي في THIRD_PARTY_LICENSES.md) */
data class LicenseItem(val name: String, val use: String, val license: String)

val licenseItems = listOf(
    LicenseItem("جيبك", "التطبيق نفسه", "MIT"),
    LicenseItem("Kotlin و Coroutines", "اللغة والتزامن", "Apache 2.0"),
    LicenseItem("Jetpack Compose + Material 3", "الواجهة", "Apache 2.0"),
    LicenseItem("AndroidX (Core، Activity، Lifecycle)", "أساسيات أندرويد", "Apache 2.0"),
    LicenseItem("Room", "قاعدة البيانات على الموبايل", "Apache 2.0"),
    LicenseItem("WorkManager", "المراجعة الدورية في الخلفية", "Apache 2.0"),
    LicenseItem("Tesseract OCR", "قراءة الفواتير على الموبايل", "Apache 2.0"),
    LicenseItem("Tesseract4Android", "تشغيل Tesseract على أندرويد", "Apache 2.0"),
    LicenseItem("نماذج tessdata_fast", "قراءة العربي والإنجليزي (ara، eng)", "Apache 2.0"),
    LicenseItem("Leptonica", "تجهيز صورة الفاتورة", "BSD-2-Clause"),
    LicenseItem("libjpeg", "فتح صور JPEG", "IJG"),
    LicenseItem("libpng", "فتح صور PNG", "libpng-2.0"),
    LicenseItem("IBM Plex Sans Arabic", "الخط • © 2019 IBM Corp.", "SIL OFL 1.1"),
    LicenseItem("Exchange Rate API", "سعر الدولار والعملات (Rates By Exchange Rate API)", "خدمة خارجية"),
    LicenseItem("gold-api.com", "سعر الأوقية العالمي للدهب", "خدمة خارجية"),
    LicenseItem("GitHub Releases", "التحديث التلقائي", "خدمة خارجية"),
)

private data class Feature(val icon: ImageVector, val color: Color, val title: String, val body: String)

private val features = listOf(
    Feature(Icons.Rounded.Sms, Color(0xFF2F9E7E), "بيسجّل لوحده", "بيقرا رسايل البنك والمحافظ وإشعارات InstaPay وBinance وغيرهم، ويطلّع اتخصم كام والباقي كام. ويتجاهل الأكواد السرية والإعلانات."),
    Feature(Icons.Rounded.AccountBalanceWallet, Color(0xFF4C7BE0), "كل حساباتك", "إجمالي فلوسك وإجمالي كل بنك ومحفظة وكارت. وتضيف Binance وVisa وغيرهم بإيدك، والكاش (جيبك) حساب حقيقي."),
    Feature(Icons.Rounded.BusinessCenter, Color(0xFF2E9BC9), "مصاريف الشركة والعهدة", "اللي دفعته للشغل، وفلوس الشركة اللي معاك، والصافي بينكم. وفاتورة PDF باسم جيبك وملف Excel للمحاسب."),
    Feature(Icons.Rounded.Inventory2, Color(0xFFC98A1B), "المخزون وقايمة الشرا", "علّم اللي خلص من البيت فمايتنسيش، وصوّر الفاتورة تتقرا على موبايلك، ويقولك السعر الجديد غالي ولا رخيص عن آخر مرة."),
    Feature(Icons.Rounded.Mic, Color(0xFFE0663E), "سجّل بصوتك", "«ضيف في بينانس 100 دولار» أو «اتخصم 250 من الفيزا»: بيفهم المصري والأرقام بالكلام."),
    Feature(Icons.Rounded.CurrencyExchange, Color(0xFFD9A21B), "الدولار والدهب", "سعر الدولار بيتحدث لوحده، واضغط على إجمالي فلوسك يتحول للدولار. وسعر الدهب بالجنيه لكل العيارات أول بأول."),
    Feature(Icons.Rounded.PieChart, Color(0xFF8E63D6), "تقارير وتحليل", "فلتر قوي، صناديق للبيت والشغل والعربية، وتصنيفات وقواعد بتظبطها بنفسك، وتنبيهات لما الرصيد يقل أو الميزانية تخلص."),
    Feature(Icons.Rounded.CloudDone, Color(0xFF3AA66B), "نسخة احتياطية وتحديث", "نسخ على Google Drive أو الموبايل، وتحديث تلقائي من GitHub بنفس بياناتك."),
)

/**
 * عن جيبك: بياناتك الحقيقية، اللي بيعمله، الخصوصية، والروابط.
 * منفصل عن الـ ViewModel علشان يتعرض في الاختبارات والمعاينة.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutContent(
    versionName: String,
    versionCode: Int,
    stats: AboutStats,
    agentNames: List<String>,
    updateText: String,
    onCheckUpdate: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    updateButton: String? = "اتحقق",
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Hero(versionName, versionCode)

        // أرقامك
        Text("جيبك لحد دلوقتي", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("عملية اتسجلت", stats.transactions, Modifier.weight(1f))
            StatCard("منها لوحدها", stats.autoPercent, Modifier.weight(1f), suffix = "%")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("حساب وكارت", stats.accounts, Modifier.weight(1f))
            StatCard("وكيل شغال", agentNames.size, Modifier.weight(1f))
        }
        stats.since?.let {
            Text("بتتابع فلوسك من ${Dates.date(it)}", style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        // اللي بيعمله
        Text("إيه اللي جيبك بيعمله", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        features.forEach { f ->
            GlassCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    IconBubble(f.icon, f.color, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(f.body, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                    }
                }
            }
        }

        // الوكلاء
        GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Text("الوكلاء (${agentNames.size})", style = MaterialTheme.typography.titleSmall)
            Text("كل وكيل متخصص في بنك أو محفظة أو تحليل", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                agentNames.forEach { n ->
                    Text(
                        n, style = MaterialTheme.typography.labelMedium, color = NabdColors.Mint,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(NabdColors.Mint.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // الخصوصية
        GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.Lock, NabdColors.Mint, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("بياناتك على موبايلك بس", style = MaterialTheme.typography.titleSmall)
                    Text("مفيش حساب ولا سيرفر ولا تتبّع", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                }
            }
            Spacer(Modifier.height(10.dp))
            listOf(
                "الرسايل والإشعارات بتتقرا على الموبايل ومابتتبعتش لأي مكان.",
                "صورة الفاتورة بتتقرا على الموبايل ومابتتبعتش لأي مكان. لقطة الكاميرا بتتمسح بعدها، وصورة المعرض بتفضل في المعرض.",
                "النت بيُستخدم لـ: تحديثات التطبيق من GitHub، وسعر الدولار، وسعر الدهب. من غير أي بيانات مالية.",
                "النسخة الاحتياطية ملف بتحفظه في المكان اللي انت تختاره.",
                "الكود مفتوح تقدر تراجعه بنفسك.",
            ).forEach { line ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•", color = NabdColors.Mint, modifier = Modifier.width(14.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim)
                }
            }
        }

        // الإصدار والتحديث
        GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.SystemUpdate, NabdColors.Cyan, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("الإصدار $versionName", style = MaterialTheme.typography.titleSmall)
                    Text(updateText, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                }
                updateButton?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelLarge, color = NabdColors.Mint,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(NabdColors.Mint.copy(alpha = 0.12f)).clickable(onClick = onCheckUpdate).padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // التراخيص
        var licensesOpen by remember { mutableStateOf(false) }
        GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { licensesOpen = !licensesOpen }, verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.Verified, NabdColors.Gold, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("التراخيص", style = MaterialTheme.typography.titleSmall)
                    Text("المكتبات والخط والخدمات اللي جيبك شايلها", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                }
                Text(if (licensesOpen) "اقفل" else "افتح", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
            }
            if (licensesOpen) {
                Spacer(Modifier.height(10.dp))
                licenseItems.forEachIndexed { i, l ->
                    if (i > 0) HorizontalDivider(color = NabdColors.Stroke)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = MaterialTheme.typography.bodyMedium)
                            Text(l.use, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                        }
                        Text(
                            l.license, style = MaterialTheme.typography.labelMedium, color = NabdColors.Mint,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(NabdColors.Mint.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "النص الكامل للتراخيص والخط على GitHub.",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.Mint,
                    modifier = Modifier.clickable { onOpen("$REPO_URL/blob/main/THIRD_PARTY_LICENSES.md") }
                )
            }
        }

        // الروابط
        PillButton("الكود على GitHub", Icons.Rounded.Code, NabdColors.Text, Modifier.fillMaxWidth()) { onOpen(REPO_URL) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("آخر نسخة", Icons.Rounded.OpenInNew, NabdColors.Mint, Modifier.weight(1f)) { onOpen("$REPO_URL/releases/latest") }
            PillButton("بلّغ عن مشكلة", Icons.Rounded.BugReport, NabdColors.Gold, Modifier.weight(1.2f)) { onOpen("$REPO_URL/issues") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Verified, null, tint = NabdColors.TextFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                "ترخيص MIT • مفتوح المصدر",
                style = MaterialTheme.typography.labelSmall, color = NabdColors.TextFaint,
                modifier = Modifier.clickable { onOpen("$REPO_URL/blob/main/LICENSE") }
            )
        }
        HorizontalDivider(color = NabdColors.Stroke)
        Text(
            "اتعمل بحب في مصر 🇪🇬", style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
    }
}

/** الكارت الكبير: الشعار + الاسم والإصدار */
@Composable
private fun Hero(versionName: String, versionCode: Int) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroGradient).padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(112.dp).clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            GeebakMark(Modifier.size(78.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("جيبك", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Geebak", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        Text("كل اللي في جيبك، لحظة بلحظة", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        Spacer(Modifier.height(12.dp))
        Text(
            "الإصدار $versionName  •  رقم $versionCode",
            style = MaterialTheme.typography.labelMedium, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier, suffix: String = "") {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(value) { go = true }
    val shown by animateIntAsState(if (go) value else 0, tween(1100, easing = FastOutSlowInEasing), label = "count")
    GlassCard(modifier, padding = 14.dp) {
        Text("%,d".format(java.util.Locale.US, shown) + suffix, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = NabdColors.Mint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim)
    }
}

/** عن جيبك (شاشة كاملة): بيحسب أرقامك من قاعدة البيانات اللي على الموبايل */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val all by vm.everything.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val hidden by vm.hiddenAccounts.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val stats = remember(all, accounts, hidden) {
        AboutStats(
            transactions = all.size,
            auto = all.count { it.source != TxnSource.MANUAL },
            manual = all.count { it.source == TxnSource.MANUAL },
            accounts = accounts.size + hidden.size,
            since = all.minOfOrNull { it.timestamp },
        )
    }
    val agentNames = remember { AgentRegistry.all.map { it.name.removePrefix("وكيل ").trim() } }
    val updateText = update.status()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NabdColors.Surface,
    ) {
        AboutContent(
            versionName = BuildConfig.VERSION_NAME, versionCode = BuildConfig.VERSION_CODE,
            stats = stats, agentNames = agentNames, updateText = updateText,
            updateButton = update.action(),
            onCheckUpdate = { vm.updateAction() },
            onOpen = { url -> runCatching { uri.openUri(url) } },
        )
    }
}
