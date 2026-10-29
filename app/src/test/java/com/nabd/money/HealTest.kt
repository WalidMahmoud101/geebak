package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.agents.RawMessage
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** رسايل بنفس صيغة رسايل فودافون كاش والبنك الأهلي الحقيقية */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HealTest {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val base = System.currentTimeMillis() - 10 * 86_400_000L
    private fun msg(sender: String, text: String, day: Int) = RawMessage(TxnSource.SMS, sender, text = text, timestamp = base + day * 86_400_000L)

    @Before fun settle() { runBlocking { app.startupJob?.join() } }

    private val balanceMsg = "رصيد حسابك فى فودافون كاش الحالي300.25 جنيه؛ تاريخ العملية 20:09 26-10-27 رقم العملية000000000001.حول فلوس لأي محفظة وزود فرصك تكسب جنيه دهب! وكمان هيجيلك لحد 100ج كاش باك."
    private val promo = "إستمتع بكاش باك لحد 100 جنيه فلوس على محفظتك لما تشحن كارت الكهرباء بفودافون كاش قبل 2026-10-31 للدفع دوس على http://vf.eg/vfcash"
    private val atmPin = "الرقم السري لماكينة الـATM هو 111111 ، والحد الأقصي للسحب هو 2000.0 جنيه؛ يرجي العلم أن الرقم السري صالح للاستخدام لمرة واحدة فقط"
    private val withdraw = "تم سحب 2000.00 جنية من محفظة فودافون كاش. رصيد حسابك الحالي 777.77 جنيه. تاريخ العملية 17:27 26-06-07 رقم العملية; 000000000002."
    private val recharge = "تم شحن رصيد موبايلك ب 100.0ج بنجاح وخصم 142.86ج من محفظتك شاملة الضريبة; رصيد محفظتك الحالي 2500.5 جنيه. استخدم خدمات فودافون كاش"

    @Test
    fun realMessageTemplatesParseRight() {
        val p = com.nabd.money.agents.MoneyParser
        assertNull(p.parse(promo)); assertNull(p.parse(atmPin))
        assertNull(p.parse("برجاء عدم مشاركة بيانات الكارت مع أي شخص. بيانات كارت الدفع أونلاين: رقم الكارت 1234 الحد 500 جنيه"))
        assertNull(p.parse("هذا الكود سرى 222222. يرجى عدم مشاركته مع أي شخص آخر، وهو مخصص لعملية الشراء بمبلغ EUR 0.93 عند التاجر Oracle Ireland"))
        // رصيد بس
        val b = p.parse(balanceMsg)!!
        assertNull(b.amount); assertEquals(300.25, b.balance!!, 0.001)
        // شحن: المبلغ المخصوم مش قيمة الشحنة، والرصيد من غير عملة
        val r = p.parse(recharge)!!
        assertEquals(142.86, r.amount!!, 0.001); assertEquals(Direction.OUT, r.direction); assertEquals(2500.5, r.balance!!, 0.001)
        val w = p.parse(withdraw)!!
        assertEquals(2000.0, w.amount!!, 0.001); assertEquals(777.77, w.balance!!, 0.001)
        assertEquals("سحب كاش", com.nabd.money.agents.Categorizer.classify(w.merchant, withdraw, w.direction!!, "vodafone_cash").category)
        // رد مبلغ = فلوس رجعت (دخل)
        val refund = p.parse("تم رد مبلغ 2000.00EGP لبطاقه الخصم المباشر رقم 1234 الساعه 20:37 يوم 01/05 الذي تم خصمه من ALXB CAI")!!
        assertEquals(Direction.IN, refund.direction)
        // عندNBE ATM001 من غير مسافة + المتاح من غير عملة
        val atm = p.parse("تم خصم 1000 EGP من بطاقة الخصم المباشر رقم1234 عندNBE ATM001                يوم06/10/26 الساعة13:25 المتاح2222.22EGP  للمزيد اتصل ب ١٩٦٢٣")!!
        assertEquals("NBE ATM001", atm.merchant); assertEquals(2222.22, atm.balance!!, 0.001)
        assertEquals("سحب كاش", com.nabd.money.agents.Categorizer.classify(atm.merchant, "", Direction.OUT, "nbe").category)
        val old = p.parse("تم خصم 2000.00EGP من بطاقة الخصم المباشر رقم 1234 عند BM POLARIS MALL C يوم 07/05 الساعه 12:57 المتاح 1234.56 للمزيد إتصل ب 19623")!!
        assertEquals(1234.56, old.balance!!, 0.001)
        // تحويل لرقم: الاسم هو الرقم، واستلام: اسم المرسل المسجل
        assertEquals("01000000001", p.parse("تم تحويل 700 جنيه لرقم 01000000001 مصاريف الخدمة 0 جنيه رصيد حسابك فى فودافون كاش الحالي 1500.25.")!!.merchant)
        assertEquals("Test Sender", p.parse("تم استلام مبلغ 300.0 جنيه من رقم 01000000002؛ المسجل بإسم Test Sender. رصيدك الحالي 900.5 جنيه.")!!.merchant)
        assertEquals("Telda", p.parse("تم دفع 1500 جنيه بنجاح لTelda                 Cairo        EGYEG ; تاريخ العملية 13-07-25 19:26 رقم العملية 000000000003.")!!.merchant)
    }

    @Test
    fun vodafoneTelecomIsNotTheWallet() = runBlocking {
        app.engine.ingest(msg("Vodafone", "مبروك كسبت 40 جنيه كاش باك تستخدمها دقائق لفودافون من فودافون كاش. زور http://vf.eg/vfcash", 1), notify = false)
        app.engine.ingest(msg("Vodafone", "عفوا، لا يوجد رصيد كافي لتجديد باقة Plus 98ج-6000 ميجا اشحن دلوقتي بـ 98 جنيه", 2), notify = false)
        app.engine.ingest(msg("we-data", "العرض ده مخصوص ليك حوّل إنترنت البيت لباقة Super 400GB بـ 399 جنيه", 3), notify = false)
        assertTrue(app.db.dao().accountsOnce().isEmpty())
        assertTrue(app.db.dao().allTxnsOnce().isEmpty())
    }

    @Test
    fun healRemovesBadTransactionsAndRestoresTheBalance() = runBlocking {
        val dao = app.db.dao()
        val e = app.engine
        e.ingest(msg("VF-Cash", balanceMsg, 5), notify = false)
        assertEquals(300.25, dao.accountsOnce().single().balance, 0.001)
        val acc = dao.accountsOnce().single()
        // اللي كان بيحصل في النسخ القديمة: إعلان وكود ATM اتسجلوا عمليات وخصموا من الرصيد
        fun bad(f: String, text: String, amt: Double, day: Int) = Txn(accountId = acc.id, amount = amt, direction = Direction.OUT, category = "أخرى", purpose = Purpose.OTHER,
            timestamp = base + day * 86_400_000L, source = TxnSource.SMS, agentId = "vodafone_cash", fingerprint = f, rawText = text)
        dao.insertTxn(bad("b1", promo, 100.0, 6)); dao.insertTxn(bad("b2", atmPin, 2000.0, 7))
        dao.updateAccount(dao.account(acc.id)!!.copy(balance = 300.25 - 100.0 - 2000.0, balanceAt = base + 7 * 86_400_000L))
        // عملية حقيقية ملتقطة بمبلغ غلط (قيمة الشحنة بدل اللي اتخصم)
        dao.insertTxn(bad("g1", recharge, 260.0, 8))
        // حساب اتعمل بالغلط من إعلان WE
        dao.insertAccount(Account(name = "WE Pay", type = AccountType.WALLET, agentId = "we_pay", accountKey = "main", balance = 290.0))

        val r = e.revalidate()
        assertEquals(2, r.removed)
        assertEquals(1, r.fixed)
        val after = dao.allTxnsOnce().single()
        assertEquals(142.86, after.amount, 0.001)
        assertEquals("فواتير", after.category)

        // الرسايل الحقيقية في الصندوق: آخر رصيد 300.25 ثم شحن بعده برصيد 2500.5 (بنفترض إنها الأحدث)
        e.rebuildBalances(listOf(msg("VF-Cash", balanceMsg, 5)))
        val vf = dao.accountsOnce().single { it.agentId == "vodafone_cash" }
        assertEquals(300.25 - 142.86, vf.balance, 0.01) // الرصيد الحقيقي + العملية الصح بس
        assertTrue(dao.accountsOnce().none { it.agentId == "we_pay" })
    }
}
