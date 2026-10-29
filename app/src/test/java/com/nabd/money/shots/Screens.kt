package com.nabd.money.shots

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.NabdApp
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Insight
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnSource
import com.nabd.money.ui.MainActivity
import com.nabd.money.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * بيشغل التطبيق الحقيقي ببيانات تجريبية ويحفظ صورة لكل شاشة في build/shots.
 * ./gradlew testDebugUnitTest --tests "com.nabd.money.shots.*"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-w392dp-h1700dp-xhdpi")
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class Screens {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val out = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }

    private fun ts(daysAgo: Long, h: Int, m: Int = 0) =
        LocalDateTime.now().withHour(h).withMinute(m).minusDays(daysAgo).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun seed() = runBlocking {
        val dao = app.db.dao()
        if (dao.accountsOnce().isNotEmpty()) return@runBlocking
        fun acc(name: String, type: AccountType, agent: String, key: String, bal: Double, known: Boolean = true, cur: String = "EGP", credit: Boolean = false, include: Boolean = true) =
            Account(name = name, type = type, agentId = agent, accountKey = key, balance = bal, currency = cur, balanceKnown = known, balanceAt = ts(0, 9), isCredit = credit, includeInTotal = include)
        val cib = dao.insertAccount(acc("CIB", AccountType.BANK, "cib", "main", 24_350.0))
        val cibCard = dao.insertAccount(acc("CIB •••• 4821", AccountType.CARD, "cib", "4821", 38_000.0, credit = true, include = false))
        val vf = dao.insertAccount(acc("فودافون كاش", AccountType.WALLET, "vodafone_cash", "main", 1_240.0))
        val nbe = dao.insertAccount(acc("البنك الأهلي", AccountType.BANK, "nbe", "main", 0.0, known = false))
        val bin = dao.insertAccount(acc("Binance USDT", AccountType.CRYPTO, "binance", "USDT", 310.0, cur = "USDT"))
        val ip = dao.insertAccount(acc("InstaPay", AccountType.BANK, "instapay", "main", 0.0, known = false, include = false))
        var n = 0
        suspend fun t(acc: Long?, amount: Double, dir: Direction, merchant: String?, cat: String, p: Purpose, d: Long, h: Int, agent: String = "cib",
                      excluded: Boolean = false, company: Boolean = false, src: TxnSource = TxnSource.SMS) {
            dao.insertTxn(Txn(accountId = acc, amount = amount, direction = dir, merchant = merchant, category = cat, purpose = p,
                timestamp = ts(d, h, (n * 7) % 60), source = src, agentId = agent, fingerprint = "s${n++}", excluded = excluded, reimbursable = company))
        }
        t(cib, 18_500.0, Direction.IN, "ACME LTD", "مرتب", Purpose.WORK, 20, 10)
        t(cib, 1_250.0, Direction.OUT, "كارفور المعادي", "بقالة", Purpose.HOME, 0, 13)
        t(vf, 350.0, Direction.OUT, "فاتورة الكهربا", "فواتير", Purpose.HOME, 0, 11, "vodafone_cash")
        t(cibCard, 640.0, Direction.OUT, "Costa Coffee", "مطاعم وكافيهات", Purpose.OUTING, 1, 20)
        t(cib, 900.0, Direction.OUT, "وطنية", "وقود", Purpose.CAR, 1, 9)
        t(null, 200.0, Direction.OUT, "تاكسي", "مواصلات", Purpose.WORK, 2, 15, "manual", company = true, src = TxnSource.MANUAL)
        t(cib, 2_000.0, Direction.OUT, null, "تحويل بين حساباتي", Purpose.OTHER, 3, 12, excluded = true)
        t(vf, 2_000.0, Direction.IN, null, "تحويل بين حساباتي", Purpose.OTHER, 3, 12, "vodafone_cash", excluded = true)
        t(cib, 3_200.0, Direction.OUT, "إيجار", "إيجار", Purpose.HOME, 6, 10)
        t(bin, 50.0, Direction.IN, "P2P", "دخل", Purpose.OTHER, 4, 18, "binance")
        t(cibCard, 299.0, Direction.OUT, "Netflix", "اشتراكات", Purpose.PERSONAL, 8, 3)
        t(cib, 1_800.0, Direction.OUT, "سينما ودريم بارك", "ترفيه", Purpose.OUTING, 9, 19)
        t(vf, 1_500.0, Direction.IN, "أحمد محمد", "دخل", Purpose.OTHER, 10, 16, "vodafone_cash")
        t(cib, 450.0, Direction.OUT, "صيدلية العزبي", "صحة", Purpose.PERSONAL, 12, 21)
        dao.insertInsight(Insight(agentId = "budget", title = "قربت تخلص ميزانية الخروجات", body = "صرفت 2,440 من 3,000 ج.م (81%) الشهر ده.", severity = 1, timestamp = ts(0, 8), dedupKey = "x1"))
        app.settings.updateTaxonomy { t ->
            t.copy(
                customCategories = listOf("جيم"),
                categoryPurpose = mapOf("ترفيه" to Purpose.OUTING),
                rules = listOf(com.nabd.money.data.UserRule("Costa", "مطاعم وكافيهات", Purpose.OUTING)),
            )
        }
        app.settings.updateSalary { it.copy(payer = "ACME LTD", learnedDay = 25, lastAmount = 18_500.0, lastAt = ts(20, 10)) }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(400)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(android.graphics.Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(text: String) {
        runCatching {
            rule.waitUntil(8_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        }.onFailure {
            val dao = app.db.dao()
            val n = runBlocking { dao.accountsOnce().size }
            println("SHOTDEBUG missing '$text' accounts=$n")
            rule.onRoot().printToLog("SHOTDEBUG")
        }
    }

    private fun tab(label: String) {
        // آخر عنصر بالاسم ده = اللي في الشريط تحت
        rule.onAllNodesWithText(label).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
    }

    private fun all(mode: ThemeMode, suffix: String) {
        app.settings.setTheme(mode)
        seed()
        waitText("حساباتك")
        shot("1_home_$suffix")
        tab("العمليات"); waitText("كارفور"); shot("2_txns_$suffix")
        tab("التقارير"); waitText("الداخل والخارج"); shot("3_reports_$suffix")
        rule.onAllNodesWithText("الحسابات")[0].performClick(); waitText("حركة كل حساب"); shot("3b_reports_accounts_$suffix")
        tab("الحسابات"); waitText("CIB"); shot("4_accounts_$suffix")
        tab("الرئيسية"); waitText("حساباتك")
        rule.onAllNodesWithText("الشركة", substring = true)[0].performClick(); waitText("الشركة مديونالك"); shot("5_company_$suffix")
        // الشاشات المنبثقة (dialogs/sheets) في window لوحدها ومش بتتصور هنا
    }

    @Test fun light() = all(ThemeMode.LIGHT, "light")
    @Test fun dark() = all(ThemeMode.DARK, "dark")
}
