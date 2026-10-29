package com.nabd.money

import com.nabd.money.agents.AgentRegistry
import com.nabd.money.agents.Categorizer
import com.nabd.money.agents.MoneyParser
import com.nabd.money.agents.RawMessage
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.TxnSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {

    @Test
    fun arabicCardPurchaseWithBalance() {
        val p = MoneyParser.parse(
            "تم خصم 250.00 جنيه من بطاقتك المنتهية ب 1234 لدى CARREFOUR MAADI يوم 27/10 الرصيد المتاح 4,320.50 جنيه"
        )!!
        assertEquals(250.0, p.amount!!, 0.001)
        assertEquals(Direction.OUT, p.direction)
        assertEquals(4320.5, p.balance!!, 0.001)
        assertEquals("1234", p.cardLast4)
        assertEquals("CARREFOUR MAADI", p.merchant)
        assertEquals("EGP", p.currency)
    }

    @Test
    fun englishCibCharge() {
        val p = MoneyParser.parse(
            "Your CIB card **5678 was charged EGP 1,200.00 at UBER TRIP on 27-10. Available balance EGP 8,800.00"
        )!!
        assertEquals(1200.0, p.amount!!, 0.001)
        assertEquals(Direction.OUT, p.direction)
        assertEquals(8800.0, p.balance!!, 0.001)
        assertEquals("5678", p.cardLast4)
        assertEquals("UBER TRIP", p.merchant)
    }

    @Test
    fun instapayIncoming() {
        val p = MoneyParser.parse("تم استلام 500 جنيه من AHMED MOHAMED عبر InstaPay. رصيدك 1500 جنيه")!!
        assertEquals(500.0, p.amount!!, 0.001)
        assertEquals(Direction.IN, p.direction)
        assertEquals(1500.0, p.balance!!, 0.001)
        assertEquals("AHMED MOHAMED", p.merchant)
    }

    @Test
    fun binanceDeposit() {
        val p = MoneyParser.parse("Deposit Successful: 100 USDT has been credited to your Binance account", "USDT")!!
        assertEquals(100.0, p.amount!!, 0.001)
        assertEquals("USDT", p.currency)
        assertEquals(Direction.IN, p.direction)
    }

    @Test
    fun otpIgnored() {
        assertNull(MoneyParser.parse("Your OTP for purchase of EGP 500 is 123456"))
    }

    @Test
    fun adsAreNotTransactions() {
        assertNull(MoneyParser.parse("استمتع بكاش باك لحد ١٠٠ جنية فلوس على محفظتك"))
        assertNull(MoneyParser.parse("اكسب 50 جنيه هدية لما تدفع فواتيرك بفودافون كاش! العرض لفترة محدودة"))
        assertNull(MoneyParser.parse("خصم 20% لحد 200 جنيه على مشترياتك من كارفور بكارتك"))
        assertNull(MoneyParser.parse("Enjoy up to EGP 100 cashback on your first payment. Download the app now"))
        // عملية حقيقية في آخرها إعلان بتتسجل عادي
        val real = MoneyParser.parse("تم خصم 150 جنيه من محفظتك. رصيدك الحالي 850 جنيه. استمتع بكاش باك لحد 100 جنيه")!!
        assertEquals(150.0, real.amount!!, 0.001)
        assertEquals(850.0, real.balance!!, 0.001)
        // "الحد المتاح" في كارت الائتمان مش "لحد" بتاعة الإعلانات
        assertEquals(500.0, MoneyParser.parse("Purchase EGP 500 at ZARA. الحد المتاح 9,500 جنيه")!!.amount!!, 0.001)
        assertEquals(1000.0, MoneyParser.parse("تم تحويل 1000 جنيه لحسابك")!!.amount!!, 0.001)
        // "جنية" بالتاء المربوطة
        assertEquals(75.0, MoneyParser.parse("تم خصم ٧٥ جنية من محفظتك")!!.amount!!, 0.001)
    }

    @Test
    fun cardIssuanceAndFeesAreDeductions() {
        val a = MoneyParser.parse("تم اصدار كارت الدفع اونلاين بنجاح. تم خصم 10 جنيه رصيدك الحالي 90.5 جنيه")!!
        assertEquals(Direction.OUT, a.direction)
        assertEquals(10.0, a.amount!!, 0.001)
        assertEquals(90.5, a.balance!!, 0.001)
        // حتى لو الرسالة فيها كلمة "إضافة"
        val b = MoneyParser.parse("تم إصدار كارت الدفع الاونلاين بنجاح وتم إضافة الكارت لمحفظتك. رسوم الإصدار 10 جنيه")!!
        assertEquals(Direction.OUT, b.direction)
        assertEquals(10.0, b.amount!!, 0.001)
        // رسوم أي عملية
        assertEquals(Direction.OUT, MoneyParser.parse("Card issuance fee EGP 10.00 charged. Balance EGP 40")!!.direction)
        // فلوس استلمتها بتفضل إضافة حتى لو فيها رسوم
        val c = MoneyParser.parse("تم استلام تحويل 500 جنيه بعد خصم رسوم 5 جنيه. رصيدك 800 جنيه")!!
        assertEquals(Direction.IN, c.direction)
        assertEquals(true, MoneyParser.isFee("تم اصدار كارت الدفع اونلاين بنجاح"))
        assertEquals(false, MoneyParser.isFee("تم استلام 500 جنيه"))
        assertEquals("رسوم وعمولات", Categorizer.classify(null, "تم اصدار كارت الدفع اونلاين بنجاح تم خصم 10 جنيه", Direction.OUT, "vodafone_cash").category)
    }

    @Test
    fun redotpayDollarSpend() {
        val p = MoneyParser.parse("You spent \$12.50 at NETFLIX.COM", "USD")!!
        assertEquals(12.5, p.amount!!, 0.001)
        assertEquals("USD", p.currency)
        assertEquals(Direction.OUT, p.direction)
        assertEquals("NETFLIX.COM", p.merchant)
    }

    @Test
    fun arabicIndicDigits() {
        val p = MoneyParser.parse("تم تحويل ٣٠٠ ج.م إلى محمد")!!
        assertEquals(300.0, p.amount!!, 0.001)
        assertEquals(Direction.OUT, p.direction)
        assertEquals("محمد", p.merchant)
    }

    @Test
    fun vodafoneCashTransfer() {
        val p = MoneyParser.parse("تم تحويل 200 جنيه لرقم 01012345678 ; رصيدك الحالي 350.5 جنيه")!!
        assertEquals(200.0, p.amount!!, 0.001)
        assertEquals(Direction.OUT, p.direction)
        assertEquals(350.5, p.balance!!, 0.001)
    }

    @Test
    fun balanceOnlyMessage() {
        val p = MoneyParser.parse("رصيد حسابك الحالي 12,000.00 جنيه")!!
        assertNull(p.amount)
        assertEquals(12000.0, p.balance!!, 0.001)
    }

    @Test
    fun agentRouting() {
        fun route(sender: String, text: String, ch: TxnSource = TxnSource.SMS, label: String = "") =
            AgentRegistry.sources.first { it.matches(RawMessage(ch, sender, label, "", text, 0)) }.id

        assertEquals("cib", route("CIB", "Your card was charged EGP 10"))
        assertEquals("nbe", route("NBE", "تم خصم 10 جنيه من حسابك"))
        assertEquals("vodafone_cash", route("VF-Cash", "تم تحويل 200 جنيه"))
        assertEquals("instapay", route("com.egyptianbanks.instapay", "تم استلام 500 جنيه", TxnSource.NOTIFICATION, "InstaPay"))
        assertEquals("binance", route("com.binance.dev", "Deposit 100 USDT", TxnSource.NOTIFICATION, "Binance"))
        assertEquals("redotpay", route("com.redotpay", "You spent \$5", TxnSource.NOTIFICATION, "RedotPay"))
        assertEquals("kast", route("xyz.kast.app", "Card payment \$5", TxnSource.NOTIFICATION, "Kast"))
        assertEquals("other_banks", route("HSBC", "Your account was debited with EGP 100"))
        assertEquals(22, AgentRegistry.all.size)
    }

    @Test
    fun salaryAmountRules() {
        val base = com.nabd.money.data.SalaryConfig("ACME", 0, 25, 0.0, 0)
        val sw = com.nabd.money.agents.SalaryWatch
        assertEquals(true, sw.amountQualifies(base, 300.0))                       // من غير مبلغ: أي مبلغ
        val min = base.copy(amount = 15_000.0)
        assertEquals(false, sw.amountQualifies(min, 2_000.0))                     // أقل من المرتب = مش مرتب
        assertEquals(true, sw.amountQualifies(min, 16_500.0))                     // أكتر = مرتب (بونص مثلاً)
        val exact = min.copy(exactOnly = true)
        assertEquals(true, sw.amountQualifies(exact, 15_000.0))
        assertEquals(false, sw.amountQualifies(exact, 16_500.0))                  // بالظبط بس
    }

    @Test
    fun salaryWatch() {
        val today = java.time.LocalDate.of(2026, 10, 29)
        val received = java.time.LocalDate.of(2026, 10, 25).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sep = java.time.LocalDate.of(2026, 9, 25).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val cfg = com.nabd.money.data.SalaryConfig("ACME LTD", 0, 25, 15000.0, sep)
        val late = com.nabd.money.agents.SalaryWatch.status(cfg, today)!!
        assertEquals(false, late.received)
        assertEquals(4, late.daysLate)
        val ok = com.nabd.money.agents.SalaryWatch.status(cfg.copy(lastAt = received), today)!!
        assertEquals(true, ok.received)
        assertEquals(27, ok.daysLeft)
        assertEquals(25, com.nabd.money.agents.SalaryWatch.learnDay(listOf(25, 27, 25)))
        assertEquals(true, com.nabd.money.agents.SalaryWatch.matchesPayer("acme ltd", null, "تم استلام 15000 جنيه من ACME  LTD عبر InstaPay"))
    }

    @Test
    fun categorizer() {
        assertEquals(Purpose.HOME, Categorizer.classify("CARREFOUR MAADI", "", Direction.OUT, "cib").purpose)
        assertEquals(Purpose.CAR, Categorizer.classify("WATANIYA PETROLEUM", "", Direction.OUT, "cib").purpose)
        assertEquals(Purpose.OUTING, Categorizer.classify("STARBUCKS", "", Direction.OUT, "cib").purpose)
        // "current balance" ماينفعش تتصنف إيجار
        assertEquals("أخرى", Categorizer.classify(null, "charged EGP 10. current balance EGP 5", Direction.OUT, "cib").category)
    }
}
