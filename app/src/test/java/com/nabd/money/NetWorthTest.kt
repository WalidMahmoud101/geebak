package com.nabd.money

import com.nabd.money.agents.MoneyParser
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Exclusion
import com.nabd.money.data.NetWorth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthTest {
    private fun acc(
        id: Long, agent: String, key: String = "main", balance: Double, known: Boolean = true,
        currency: String = "EGP", include: Boolean = true, credit: Boolean = false,
    ) = Account(
        id = id, name = "$agent $key", type = AccountType.BANK, agentId = agent, accountKey = key,
        balance = balance, currency = currency, balanceKnown = known, includeInTotal = include, isCredit = credit,
    )

    private val egp: (Account) -> Double = { a ->
        when (a.currency) { "EGP" -> a.balance; "USDT", "USD" -> a.balance * 50; else -> 0.0 }
    }

    @Test
    fun onlyConfirmedMoneyCounts() {
        val accounts = listOf(
            acc(1, "cib", "main", 8_800.0),
            acc(2, "cib", "1234", 50_000.0, credit = true, include = false), // حد ائتمان
            acc(3, "vodafone_cash", balance = -1_200.0, known = false),       // تقديري من الصفر
            acc(4, "instapay", balance = -500.0, include = false),             // وسيط
            acc(5, "binance", "USDT", 100.0, currency = "USDT"),
            acc(6, "binance", "BTC", 0.01, currency = "BTC"),                  // عملة مش محسوبة
        )
        assertEquals(8_800.0 + 5_000.0, NetWorth.total(accounts, egp), 0.0)
        assertEquals(Exclusion.CREDIT, NetWorth.exclusion(accounts[1], egp(accounts[1])))
        assertEquals(Exclusion.UNKNOWN, NetWorth.exclusion(accounts[2], egp(accounts[2])))
        assertEquals(Exclusion.INTERMEDIATE, NetWorth.exclusion(accounts[3], egp(accounts[3])))
        assertEquals(Exclusion.CURRENCY, NetWorth.exclusion(accounts[5], egp(accounts[5])))
    }

    @Test
    fun groupedPerBankAndApp() {
        val accounts = listOf(
            acc(1, "cib", "main", 8_000.0),
            acc(2, "cib", "5678", 2_000.0),
            acc(3, "binance", "USDT", 100.0, currency = "USDT"),
            acc(4, "other_banks", "HSBC-1111", 1_000.0),
            acc(5, "other_banks", "HSBC-2222", 500.0),
            acc(6, "other_banks", "ADIB", 300.0),
        )
        val inst = NetWorth.institutions(accounts, egp).associateBy { it.name }
        assertEquals(10_000.0, inst.getValue("CIB").total, 0.0)
        assertEquals(2, inst.getValue("CIB").accounts.size)
        assertEquals(5_000.0, inst.getValue("Binance").total, 0.0)
        assertEquals(1_500.0, inst.getValue("HSBC").total, 0.0)
        assertEquals(300.0, inst.getValue("ADIB").total, 0.0)
    }

    @Test
    fun creditLimitDetected() {
        val p = MoneyParser.parse("تم خصم 500 جنيه من بطاقتك الائتمانية المنتهية ب 1234 لدى NOON. الحد المتاح 45,000 جنيه")!!
        assertTrue(p.isCredit)
        assertEquals(45_000.0, p.balance!!, 0.0)
        val e = MoneyParser.parse("Your credit card **9876 was charged EGP 300 at UBER. Available limit EGP 20,000")!!
        assertTrue(e.isCredit)
        assertEquals(20_000.0, e.balance!!, 0.0)
        assertFalse(MoneyParser.parse("تم خصم 250 جنيه من بطاقتك المنتهية ب 1234 لدى CARREFOUR الرصيد المتاح 4,320.50 جنيه")!!.isCredit)
    }
}
