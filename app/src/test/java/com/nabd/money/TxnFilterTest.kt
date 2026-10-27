package com.nabd.money

import com.nabd.money.data.CASH_ACCOUNT_ID
import com.nabd.money.data.Direction
import com.nabd.money.data.FilterTotals
import com.nabd.money.data.Period
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnFilter
import com.nabd.money.data.TxnSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TxnFilterTest {
    private val today = LocalDate.of(2026, 10, 27) // ثلاثاء

    private fun txn(
        amount: Double, dir: Direction = Direction.OUT, purpose: Purpose = Purpose.HOME, category: String = "بقالة",
        account: Long? = 1, excluded: Boolean = false, company: Boolean = false, merchant: String? = null,
    ) = Txn(
        accountId = account, amount = amount, direction = dir, purpose = purpose, category = category,
        timestamp = 0, source = TxnSource.SMS, agentId = "cib", fingerprint = "$amount", excluded = excluded,
        reimbursable = company, merchant = merchant,
    )

    @Test
    fun periods() {
        assertEquals(today to today, TxnFilter(Period.TODAY).days(today))
        assertEquals(LocalDate.of(2026, 10, 24) to today, TxnFilter(Period.THIS_WEEK).days(today)) // من السبت
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), TxnFilter(Period.LAST_MONTH).days(today))
        val custom = TxnFilter(Period.CUSTOM, fromDay = LocalDate.of(2026, 3, 5).toEpochDay(), toDay = LocalDate.of(2026, 3, 20).toEpochDay())
        assertEquals(16, custom.dayCount(today))
    }

    @Test
    fun shiftAndPrevious() {
        val m = TxnFilter(Period.THIS_MONTH).shift(-1, today)
        assertEquals(Period.MONTH, m.period)
        assertEquals(YearMonth.of(2026, 9), m.month)
        assertEquals(Period.THIS_MONTH, m.shift(1, today).period)
        // مايروحش للمستقبل
        assertEquals(TxnFilter(Period.THIS_MONTH), TxnFilter(Period.THIS_MONTH).shift(1, today))
        // الشهر ده (27 يوم) بيتقارن بأول 27 يوم من الشهر اللي فات
        val prev = TxnFilter(Period.THIS_MONTH).previous(today)
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 27), prev.days(today))
        // يوم معين ← اليوم اللي قبله
        val d = LocalDate.of(2026, 10, 10).toEpochDay()
        assertEquals(LocalDate.of(2026, 10, 9).toEpochDay(), TxnFilter(Period.CUSTOM, fromDay = d, toDay = d).shift(-1, today).fromDay)
    }

    @Test
    fun matching() {
        val f = TxnFilter(purposes = setOf(Purpose.CAR), direction = Direction.OUT)
        assertTrue(f.matches(txn(100.0, purpose = Purpose.CAR), 100.0))
        assertFalse(f.matches(txn(100.0, purpose = Purpose.HOME), 100.0))
        assertFalse(f.matches(txn(100.0, dir = Direction.IN, purpose = Purpose.CAR), 100.0))
        assertTrue(TxnFilter(accountIds = setOf(CASH_ACCOUNT_ID)).matches(txn(5.0, account = null), 5.0))
        assertTrue(TxnFilter(query = "كارفور").matches(txn(5.0, merchant = "كارفور المعادي"), 5.0))
        assertFalse(TxnFilter(minAmount = 500.0).matches(txn(100.0), 100.0))
        // التحويلات الداخلية مخفية إلا لو طلبتها
        assertFalse(TxnFilter().matches(txn(100.0, excluded = true), 100.0))
        assertTrue(TxnFilter(showExcluded = true).matches(txn(100.0, excluded = true), 100.0))
    }

    @Test
    fun totalsKeepThingsApart() {
        val list = listOf(
            txn(1000.0, dir = Direction.IN, category = "مرتب"),
            txn(200.0),
            txn(300.0, company = true),          // مصاريف شركة: لوحدها
            txn(500.0, excluded = true),         // تحويل بين حساباتي: برا الإجمالي
        )
        val t = FilterTotals.of(list, { it.amount })
        assertEquals(1000.0, t.income, 0.0)
        assertEquals(200.0, t.spent, 0.0)
        assertEquals(300.0, t.company, 0.0)
        assertEquals(300.0, FilterTotals.of(list, { it.amount }, companyOnly = true).spent, 0.0)
    }
}
