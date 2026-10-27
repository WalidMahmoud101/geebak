package com.nabd.money

import com.nabd.money.agents.Categorizer
import com.nabd.money.agents.VoiceParser
import com.nabd.money.data.CashFlow
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnSource
import com.nabd.money.data.UserRule
import com.nabd.money.data.UserTaxonomy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class RulesAndFlowTest {
    @After fun reset() { Categorizer.user = UserTaxonomy() }

    @Test
    fun userRulesWin() {
        Categorizer.user = UserTaxonomy(
            customCategories = listOf("جيم"),
            categoryPurpose = mapOf("ترفيه" to Purpose.OUTING, "جيم" to Purpose.PERSONAL, "أخرى" to Purpose.OUTING),
            rules = listOf(UserRule("Gold's Gym", "جيم", null), UserRule("costa", "مطاعم وكافيهات", Purpose.WORK)),
        )
        val gym = Categorizer.classify("GOLD'S GYM MAADI", "", Direction.OUT, "cib")
        assertEquals("جيم", gym.category); assertEquals(Purpose.PERSONAL, gym.purpose)
        // قاعدتك بتغلب التصنيف التلقائي
        assertEquals(Purpose.WORK, Categorizer.classify("Costa Coffee", "", Direction.OUT, "cib").purpose)
        // الغرض الثابت للتصنيف: "أخرى" بقت خروجات
        assertEquals(Purpose.OUTING, Categorizer.classify("محل مش معروف", "", Direction.OUT, "cib").purpose)
        // وبتمشي مع الصوت كمان
        val d = VoiceParser.parse("دفعت 500 جولدز جيم", emptyList()).single()
        assertEquals(500.0, d.amount, 0.0)
        assertEquals("جيم" in Categorizer.allCategories(), true)
    }

    private fun txn(amount: Double, dir: Direction, cat: String, p: Purpose = Purpose.HOME, acc: Long? = 1,
                    excluded: Boolean = false, company: Boolean = false) = Txn(
        accountId = acc, amount = amount, direction = dir, category = cat, purpose = p, timestamp = 0,
        source = TxnSource.SMS, agentId = "cib", fingerprint = "$amount$cat", excluded = excluded, reimbursable = company,
    )

    @Test
    fun cashFlowStatement() {
        val f = CashFlow.of(listOf(
            txn(10_000.0, Direction.IN, "مرتب", Purpose.WORK),
            txn(1_000.0, Direction.IN, "دخل", Purpose.OTHER),
            txn(2_000.0, Direction.OUT, "بقالة"),
            txn(500.0, Direction.OUT, "فواتير"),
            txn(700.0, Direction.OUT, "وقود", Purpose.CAR),
            txn(3_000.0, Direction.OUT, CashFlow.TRANSFER, Purpose.OTHER, acc = 1, excluded = true),
            txn(3_000.0, Direction.IN, CashFlow.TRANSFER, Purpose.OTHER, acc = 2, excluded = true),
            txn(400.0, Direction.OUT, "مواصلات", Purpose.WORK, company = true),
        )) { it.amount }
        assertEquals(11_000.0, f.totalIn, 0.0)
        assertEquals(3_200.0, f.totalOut, 0.0)
        assertEquals(7_800.0, f.net, 0.0)
        assertEquals("مرتب", f.inflow.first().label)
        assertEquals(Purpose.HOME, f.outflow.first().purpose)
        assertEquals(2_500.0, f.outflow.first().amount, 0.0)
        assertEquals(2, f.outflow.first().children.size)
        assertEquals(3_000.0, f.transfers, 0.0)
        assertEquals(400.0, f.companyOut, 0.0)
        // الحساب 2 استلم التحويل
        assertEquals(3_000.0, f.perAccount.first { it.accountId == 2L }.inflow, 0.0)
    }
}
