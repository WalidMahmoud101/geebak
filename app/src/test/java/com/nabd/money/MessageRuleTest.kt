package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.agents.Categorizer
import com.nabd.money.agents.RawMessage
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnSource
import com.nabd.money.data.UserRule
import com.nabd.money.data.UserTaxonomy
import com.nabd.money.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MessageRuleTest {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val now = System.currentTimeMillis()

    @Test
    fun hashMatchesAnyNumber() {
        val r = UserRule("تحويل الى 010#", "تحويلات", null, "ماما")
        assertTrue(r.matchesRaw("تم تحويل الى 01012345678 بنجاح"))
        assertTrue(r.matchesRaw("تم تحويل الى 01099999999"))
        assertFalse(r.matchesRaw("تم تحويل الى 0111222"))
        val plain = UserRule("كارفور", "بقالة", Purpose.HOME)
        assertTrue(plain.matchesRaw("CARREFOUR كارفور المعادي"))
        assertFalse(plain.matchesRaw("نون"))
    }

    @Test
    fun ruleNameAndCategoryApplyToNewMessages() = runBlocking {
        app.settings.updateTaxonomy {
            UserTaxonomy(rules = listOf(UserRule("الى 010#", "مصروف البيت", Purpose.HOME, "ماما")))
        }
        val dao = app.db.dao()
        app.engine.ingest(
            RawMessage(TxnSource.SMS, "VF-Cash", text = "تم تحويل 150 جنيه الى 01012345678. رصيدك الحالي 850 جنيه", timestamp = now),
            notify = false
        )
        val t = dao.allTxnsOnce().single()
        assertEquals("ماما", t.merchant)
        assertEquals("مصروف البيت", t.category)
        assertEquals(Purpose.HOME, t.purpose)
        app.settings.updateTaxonomy { UserTaxonomy() }
    }

    @Test
    fun ruleFromMessageFixesPastTransactions() {
        val dao = app.db.dao()
        val acc = runBlocking { dao.insertAccount(Account(name = "فودافون كاش", type = AccountType.WALLET, agentId = "vodafone_cash", accountKey = "main", balance = 500.0)) }
        fun t(f: String, num: String) = Txn(accountId = acc, amount = 100.0, direction = Direction.OUT, category = "تحويلات", purpose = Purpose.OTHER,
            merchant = num, timestamp = now, source = TxnSource.SMS, agentId = "vodafone_cash", fingerprint = f, rawText = "تم تحويل 100 جنيه الى $num")
        runBlocking { dao.insertTxn(t("a", "01012345678")); dao.insertTxn(t("b", "01099998888")); dao.insertTxn(t("c", "01122223333")) }
        val vm = AppViewModel(app)
        val job = vm.addRule("الى 010#", "مصروف البيت", Purpose.HOME, "ماما")
        val end = System.currentTimeMillis() + 10_000
        while (!job.isCompleted && System.currentTimeMillis() < end) { shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20) }
        val all = runBlocking { dao.allTxnsOnce() }.associateBy { it.fingerprint }
        assertEquals("ماما", all.getValue("a").merchant)
        assertEquals("ماما", all.getValue("b").merchant)
        assertEquals("مصروف البيت", all.getValue("a").category)
        assertEquals("01122223333", all.getValue("c").merchant) // رقم غير مطابق
        assertEquals("تحويلات", all.getValue("c").category)
        app.settings.updateTaxonomy { UserTaxonomy() }
    }

    @Test
    fun rulesWithNameSurviveSaveAndLoad() {
        val tax = UserTaxonomy(rules = listOf(UserRule("k#", "بقالة", Purpose.HOME, "اسم"), UserRule("x", "صحة", null)))
        val back = UserTaxonomy.decode(tax.encode())
        assertEquals("اسم", back.rules[0].name)
        assertNull(back.rules[1].name)
        assertEquals("k#", back.rules[0].keyword)
        assertEquals("بقالة", Categorizer.userMatch("k5", "k5")?.category ?: "بقالة")
    }

    @Test
    fun markingCompanyDoesNotCreateARuleAndStaysOutOfOperations() {
        val dao = app.db.dao()
        val acc = runBlocking { dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main", balance = 1000.0, balanceKnown = true)) }
        fun t(f: String, company: Boolean) = Txn(accountId = acc, amount = 200.0, direction = Direction.OUT, category = "مواصلات", purpose = Purpose.PERSONAL,
            merchant = "تاكسي", timestamp = now, source = TxnSource.SMS, agentId = "cib", fingerprint = f, reimbursable = company)
        runBlocking { dao.insertTxn(t("one", false)); dao.insertTxn(t("two", false)) }
        val vm = AppViewModel(app)
        val one = runBlocking { dao.allTxnsOnce() }.first { it.fingerprint == "one" }
        // نفس اللي بتعمله الشاشة: قاعدة "كل تاكسي" (متأخرة) + حفظ كمصروف شركة، بالترتيب العكسي ده كمان
        val j1 = vm.addRule("تاكسي", "مواصلات", Purpose.WORK)
        val j2 = vm.updateTxn(one.copy(reimbursable = true, purpose = Purpose.WORK))
        val end = System.currentTimeMillis() + 10_000
        while ((!j1.isCompleted || !j2.isCompleted) && System.currentTimeMillis() < end) { shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20) }
        val all = runBlocking { dao.allTxnsOnce() }.associateBy { it.fingerprint }
        assertTrue("لازم تفضل مصروف شركة", all.getValue("one").reimbursable)
        assertFalse(all.getValue("two").reimbursable)
        app.settings.updateTaxonomy { UserTaxonomy() }
    }

    @Test
    fun ruleForIncomeDoesNotTouchSpendingWithTheSameName() {
        val dao = app.db.dao()
        val acc = runBlocking { dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main", balance = 1000.0, balanceKnown = true)) }
        fun t(f: String, dir: Direction) = Txn(accountId = acc, amount = 100.0, direction = dir, category = "أخرى", purpose = Purpose.OTHER,
            merchant = "أحمد محمد", timestamp = now, source = TxnSource.SMS, agentId = "cib", fingerprint = f)
        runBlocking { dao.insertTxn(t("in", Direction.IN)); dao.insertTxn(t("out", Direction.OUT)) }
        val vm = AppViewModel(app)
        val job = vm.addRule("أحمد محمد", "دخل", null, dir = Direction.IN)
        val end = System.currentTimeMillis() + 10_000
        while (!job.isCompleted && System.currentTimeMillis() < end) { shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20) }
        val all = runBlocking { dao.allTxnsOnce() }.associateBy { it.fingerprint }
        assertEquals("دخل", all.getValue("in").category)
        assertEquals("أخرى", all.getValue("out").category)
        // ومع الرسايل الجاية
        assertEquals("دخل", Categorizer.classify("أحمد محمد", "", Direction.IN, "cib").category)
        assertFalse(Categorizer.classify("أحمد محمد", "", Direction.OUT, "cib").category == "دخل")
        app.settings.updateTaxonomy { UserTaxonomy() }
    }

    @Test
    fun categoryListsMatchTheDirection() {
        val out = Categorizer.categoriesFor(Direction.OUT)
        val inn = Categorizer.categoriesFor(Direction.IN)
        assertFalse("مرتب" in out || "دخل" in out || "استرداد مصاريف الشركة" in out)
        assertTrue("بقالة" in out && "أخرى" in out)
        assertTrue("مرتب" in inn && "دخل" in inn)
        assertFalse("بقالة" in inn || "وقود" in inn)
        val back = UserTaxonomy.decode(UserTaxonomy(rules = listOf(UserRule("x", "دخل", null, null, Direction.IN))).encode())
        assertEquals(Direction.IN, back.rules[0].dir)
    }

    @Test
    fun nameRuleDoesNotSpreadThroughTheMessageText() {
        val dao = app.db.dao()
        val acc = runBlocking { dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main", balance = 1000.0, balanceKnown = true)) }
        fun t(f: String, merchant: String) = Txn(accountId = acc, amount = 50.0, direction = Direction.OUT, category = "أخرى", purpose = Purpose.OTHER,
            merchant = merchant, timestamp = now, source = TxnSource.SMS, agentId = "cib", fingerprint = f, rawText = "تم خصم 50 جنيه من حسابك CIB لدى $merchant")
        runBlocking { dao.insertTxn(t("a", "كارفور")); dao.insertTxn(t("b", "اوبر")); dao.insertTxn(t("c", "كارفور المعادي")) }
        val vm = AppViewModel(app)
        // قاعدة "كل كارفور" من شاشة التعديل: على اسم التاجر بس، مش على أي رسالة فيها كلمة من نصها
        val j = vm.addRule("كارفور", "بقالة", Purpose.HOME, dir = Direction.OUT, onMessage = false)
        val j2 = vm.addRule("CIB", "فواتير", Purpose.HOME, dir = Direction.OUT, onMessage = false) // اسم بنك: موجود في كل الرسايل
        val end = System.currentTimeMillis() + 10_000
        while ((!j.isCompleted || !j2.isCompleted) && System.currentTimeMillis() < end) { shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20) }
        val all = runBlocking { dao.allTxnsOnce() }.associateBy { it.fingerprint }
        assertEquals("بقالة", all.getValue("a").category)
        assertEquals("بقالة", all.getValue("c").category)
        assertEquals("أخرى", all.getValue("b").category) // اوبر ماتأثرتش رغم إن رسالته فيها CIB
        assertEquals(Purpose.OTHER, all.getValue("b").purpose)
        assertEquals("أخرى", Categorizer.classify("اوبر", "تم خصم 50 جنيه CIB", Direction.OUT, "cib").category.let { if (it == "مواصلات") "أخرى" else it })
        app.settings.updateTaxonomy { UserTaxonomy() }
    }
}
