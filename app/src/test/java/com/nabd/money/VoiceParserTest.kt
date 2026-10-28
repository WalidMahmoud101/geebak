package com.nabd.money

import com.nabd.money.agents.ArabicNumbers
import com.nabd.money.agents.VoiceParser
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class VoiceParserTest {
    private val now = LocalDateTime.of(2026, 10, 27, 18, 30) // الثلاثاء
    private val accounts = listOf(
        Account(id = 1, name = "فودافون كاش", type = AccountType.WALLET, agentId = "vodafone_cash", accountKey = "main"),
        Account(id = 2, name = "CIB •••• 1234", type = AccountType.BANK, agentId = "cib", accountKey = "1234"),
    )

    private fun num(s: String) = ArabicNumbers.toDigits(s)

    @Test
    fun spokenNumbers() {
        assertEquals("500", num("خمسمية"))
        assertEquals("1250", num("ألف وميتين وخمسين"))
        assertEquals("2500", num("ألفين ونص"))
        assertEquals("3500", num("تلات تلاف وخمسمية"))
        assertEquals("15000", num("15 ألف"))
        assertEquals("125", num("مية وخمسة وعشرين"))
        assertEquals("1500", num("ألف ونص"))
        assertEquals("1500000", num("مليون ونص"))
        assertEquals("1500", num("ألف و500"))
        assertEquals("نص كيلو", num("نص كيلو"))
        assertEquals("50 عيش و 100 بنزين", num("50 عيش و 100 بنزين"))
        assertEquals("صرفت 350 جنيه", num("صرفت تلتمية وخمسين جنيه"))
    }

    @Test
    fun carrefourForHomeYesterday() {
        val d = VoiceParser.parse("صرفت خمسمية جنيه في كارفور للبيت امبارح", accounts, now).single()
        assertEquals(500.0, d.amount, 0.0)
        assertEquals(Direction.OUT, d.direction)
        assertEquals(Purpose.HOME, d.purpose)
        assertEquals("بقالة", d.category)
        assertEquals("كارفور", d.merchant)
        assertEquals(26, Dates.toLocal(d.timestamp).dayOfMonth)
    }

    @Test
    fun electricityBill() {
        val d = VoiceParser.parse("دفعت فاتورة الكهربا 350", accounts, now).single()
        assertEquals(350.0, d.amount, 0.0)
        assertEquals("فواتير", d.category)
        assertEquals(Purpose.HOME, d.purpose)
        assertEquals("فاتورة الكهربا", d.merchant)
    }

    @Test
    fun salaryIn() {
        val d = VoiceParser.parse("قبضت المرتب 15 ألف", accounts, now).single()
        assertEquals(15000.0, d.amount, 0.0)
        assertEquals(Direction.IN, d.direction)
        assertEquals("مرتب", d.category)
    }

    @Test
    fun twoItemsOneSentence() {
        val ds = VoiceParser.parse("صرفت 50 عيش و 100 بنزين للعربية", accounts, now)
        assertEquals(2, ds.size)
        assertEquals(50.0, ds[0].amount, 0.0)
        assertEquals("بقالة", ds[0].category)
        assertEquals(100.0, ds[1].amount, 0.0)
        assertEquals(Purpose.CAR, ds[1].purpose)
        assertEquals("وقود", ds[1].category)
    }

    @Test
    fun companyTaxiWithTime() {
        val d = VoiceParser.parse("دفعت ميتين تاكسي على حساب الشركة الساعة 3 العصر", accounts, now).single()
        assertEquals(200.0, d.amount, 0.0)
        assertTrue(d.company)
        assertEquals(Purpose.WORK, d.purpose)
        assertEquals("مواصلات", d.category)
        assertEquals(15, Dates.toLocal(d.timestamp).hour)
    }

    @Test
    fun accountAndQuantity() {
        val d = VoiceParser.parse("اشتريت 2 كيلو لحمة ب 900 من فودافون كاش", accounts, now).single()
        assertEquals(900.0, d.amount, 0.0)
        assertEquals(1L, d.accountId)
        assertEquals("بقالة", d.category)
    }

    @Test
    fun addHouseMoneyAndDayName() {
        val d = VoiceParser.parse("ضيف ألفين لمصروف البيت يوم السبت", accounts, now).single()
        assertEquals(2000.0, d.amount, 0.0)
        assertEquals(Direction.IN, d.direction)
        assertEquals(Purpose.HOME, d.purpose)
        assertEquals(24, Dates.toLocal(d.timestamp).dayOfMonth)
    }

    @Test
    fun dollars() {
        val d = VoiceParser.parse("دفعت 20 دولار نتفليكس", accounts, now).single()
        assertEquals("USD", d.currency)
        assertEquals("اشتراكات", d.category)
    }

    private val crypto = listOf(
        Account(id = 10, name = "Binance USDT", type = AccountType.CRYPTO, agentId = "binance", accountKey = "USDT", currency = "USDT"),
        Account(id = 11, name = "Trust Wallet USDT", type = AccountType.CRYPTO, agentId = "trust_wallet", accountKey = "USDT", currency = "USDT"),
        Account(id = 12, name = "Visa •••• 4242", type = AccountType.CARD, agentId = "wallet_cards", accountKey = "4242"),
        Account(id = 13, name = "Apple Pay", type = AccountType.CARD, agentId = "manual", accountKey = "ap"),
    )

    @Test
    fun addToSpecificAccount() {
        val d = VoiceParser.parse("ضيف في حساب بينانس 100 دولار", crypto, now).single()
        assertEquals(Direction.IN, d.direction)
        assertEquals(10L, d.accountId)
        assertEquals(100.0, d.amount, 0.001)
        assertEquals("USDT", d.currency) // دولار على حساب كريبتو = USDT
    }

    @Test
    fun deductedAndSentToMe() {
        val out = VoiceParser.parse("اتخصم 250 من الفيزا", crypto, now).single()
        assertEquals(Direction.OUT, out.direction)
        assertEquals(12L, out.accountId)
        assertEquals(250.0, out.amount, 0.001)

        val inA = VoiceParser.parse("اتبعتلي 300 على ترست والت", crypto, now).single()
        assertEquals(Direction.IN, inA.direction)
        assertEquals(11L, inA.accountId)

        val inB = VoiceParser.parse("اتضافلي 80 دولار في بينانس", crypto, now).single()
        assertEquals(Direction.IN, inB.direction)
        assertEquals(10L, inB.accountId)

        val applePay = VoiceParser.parse("دفعت 120 بابل باي", crypto, now).single()
        assertEquals(Direction.OUT, applePay.direction)
        assertEquals(13L, applePay.accountId)
        assertEquals(120.0, applePay.amount, 0.001)

        val sent = VoiceParser.parse("بعت 500 من بينانس", crypto, now).single()
        assertEquals(Direction.OUT, sent.direction)
        assertEquals(10L, sent.accountId)
    }
}
