package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.agents.GenericBankAgent
import com.nabd.money.agents.RawMessage
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnFilter
import com.nabd.money.data.TxnSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AccountsTidyTest {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val now = System.currentTimeMillis()

    private fun sms(sender: String, text: String, minutesAgo: Long) =
        RawMessage(TxnSource.SMS, sender, text = text, timestamp = now - minutesAgo * 60_000)

    @Test
    fun cardAndAccountMessagesLandInOneAccount() = runBlocking {
        val e = app.engine
        e.ingest(sms("CIB", "Your CIB card **5678 was charged EGP 200.00 at UBER. Available balance EGP 9,800.00", 60), notify = false)
        // نفس البنك برقم الحساب، والرصيد بيكمّل نفس الفلوس
        e.ingest(sms("CIB", "Your account **1111 was debited with EGP 300.00. Available balance EGP 9,500.00", 30), notify = false)
        // ومن غير رقم خالص
        e.ingest(sms("CIB", "Your account was credited with EGP 1,000.00. Available balance EGP 10,500.00", 10), notify = false)
        val accounts = app.db.dao().accountsOnce()
        assertEquals(1, accounts.size)
        assertEquals(10_500.0, accounts.single().balance, 0.01)
        assertEquals(3, app.db.dao().allTxnsOnce().size)
    }

    @Test
    fun twoRealAccountsWithDifferentBalancesStayApart() = runBlocking {
        val e = app.engine
        e.ingest(sms("NBE", "تم خصم 100 جنيه من حسابك رقم 1111 الرصيد المتاح 5000 جنيه", 60), notify = false)
        e.ingest(sms("NBE", "تم خصم 100 جنيه من حسابك رقم 2222 الرصيد المتاح 70000 جنيه", 30), notify = false)
        assertEquals(2, app.db.dao().accountsOnce().size)
    }

    @Test
    fun tidyMergesOldDuplicatesAndRemembersThem() = runBlocking {
        val dao = app.db.dao()
        val a = dao.insertAccount(Account(name = "CIB •••• 5678", type = AccountType.BANK, agentId = "cib", accountKey = "5678", balance = 4000.0, balanceKnown = true, balanceAt = now - 3_600_000))
        val b = dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main"))
        val c = dao.insertAccount(Account(name = "CIB •••• 1111", type = AccountType.BANK, agentId = "cib", accountKey = "1111", balance = 4000.0, balanceKnown = true, balanceAt = now - 1_800_000))
        fun t(acc: Long, f: String, min: Long) = Txn(accountId = acc, amount = 250.0, direction = Direction.OUT, category = "بقالة",
            purpose = Purpose.HOME, timestamp = now - min * 60_000, source = TxnSource.SMS, agentId = "cib", fingerprint = f)
        dao.insertTxn(t(a, "x1", 50))
        dao.insertTxn(t(c, "x2", 49)) // نفس العملية اتسجلت على الكارت وعلى الحساب
        dao.insertTxn(t(b, "x3", 10))

        assertEquals(2, app.engine.tidyAccounts())
        val left = dao.accountsOnce()
        assertEquals(1, left.size)
        assertEquals("CIB", left.single().name)
        assertEquals(2, dao.allTxnsOnce().size)

        // رسالة جديدة برقم كارت اتدمج قبل كده بتروح لنفس الحساب
        app.engine.ingest(sms("CIB", "Your CIB card **1111 was charged EGP 10.00 at UBER", 1), notify = false)
        assertEquals(1, dao.accountsOnce().size)
    }

    @Test
    fun adsRecordedBeforeAreRemoved() = runBlocking {
        val dao = app.db.dao()
        val acc = dao.insertAccount(Account(name = "فودافون كاش", type = AccountType.WALLET, agentId = "vodafone_cash", accountKey = "main", balance = -100.0))
        fun t(text: String, f: String) = Txn(accountId = acc, amount = 100.0, direction = Direction.OUT, category = "أخرى", purpose = Purpose.OTHER,
            timestamp = now, source = TxnSource.SMS, agentId = "vodafone_cash", rawText = text, fingerprint = f)
        dao.insertTxn(t("استمتع بكاش باك لحد ١٠٠ جنية فلوس على محفظتك", "ad"))
        dao.insertTxn(t("تم تحويل 100 جنيه لرقم 01012345678", "real"))
        // التنضيف بيشتغل لوحده كمان أول ما التطبيق يفتح، فبنشوف النتيجة مش العدد
        app.engine.purgeAds()
        assertEquals(listOf("real"), dao.allTxnsOnce().map { it.fingerprint })
        assertEquals(0.0, dao.account(acc)!!.balance, 0.01) // الرصيد التقديري رجع
    }

    @Test
    fun cardIssuanceRecordedAsIncomeIsFixed() = runBlocking {
        val dao = app.db.dao()
        val acc = dao.insertAccount(Account(name = "فودافون كاش", type = AccountType.WALLET, agentId = "vodafone_cash", accountKey = "main", balance = 110.0))
        dao.insertTxn(Txn(accountId = acc, amount = 10.0, direction = Direction.IN, category = "دخل", purpose = Purpose.OTHER,
            timestamp = now, source = TxnSource.SMS, agentId = "vodafone_cash", fingerprint = "issue1",
            rawText = "تم اصدار كارت الدفع اونلاين بنجاح. رسوم الاصدار 10 جنيه"))
        dao.insertTxn(Txn(accountId = acc, amount = 500.0, direction = Direction.IN, category = "دخل", purpose = Purpose.OTHER,
            timestamp = now, source = TxnSource.SMS, agentId = "vodafone_cash", fingerprint = "real1", rawText = "تم استلام 500 جنيه من احمد"))
        app.engine.fixFeeDirections()
        val t = dao.allTxnsOnce().associateBy { it.fingerprint }
        assertEquals(Direction.OUT, t.getValue("issue1").direction)
        assertEquals("رسوم وعمولات", t.getValue("issue1").category)
        assertEquals(Direction.IN, t.getValue("real1").direction)
        assertEquals(90.0, dao.account(acc)!!.balance, 0.001) // كانت +10 بقت −10
    }

    @Test
    fun senderSpellingsAreOneBank() {
        assertEquals(GenericBankAgent.bankKey("HSBC"), GenericBankAgent.bankKey("HSBC-EG"))
        assertEquals(GenericBankAgent.bankKey("hsbc"), GenericBankAgent.bankKey("HSBC Egypt"))
        assertTrue(GenericBankAgent.bankKey("ADIB") != GenericBankAgent.bankKey("HSBC"))
    }

    @Test
    fun incomeStaysOutOfWorkAndHomeBoxes() {
        fun t(dir: Direction, cat: String, p: Purpose, src: TxnSource) = Txn(accountId = 1, amount = 100.0, direction = dir, category = cat,
            purpose = p, timestamp = now, source = src, agentId = "x", fingerprint = cat + src)
        val work = TxnFilter(purposes = setOf(Purpose.WORK))
        val home = TxnFilter(purposes = setOf(Purpose.HOME))
        assertFalse(work.matches(t(Direction.IN, "مرتب", Purpose.WORK, TxnSource.NOTIFICATION), 100.0))
        assertFalse(work.matches(t(Direction.IN, "مرتب", Purpose.WORK, TxnSource.MANUAL), 100.0))
        assertTrue(work.matches(t(Direction.OUT, "أدوات شغل", Purpose.WORK, TxnSource.SMS), 100.0))
        // فلوس انت حطيتها في صندوق البيت بإيدك = جزء من حساب البيت
        assertTrue(home.matches(t(Direction.IN, "مصروف البيت", Purpose.HOME, TxnSource.MANUAL), 100.0))
        assertFalse(home.matches(t(Direction.IN, "دخل", Purpose.HOME, TxnSource.SMS), 100.0))
    }
}
