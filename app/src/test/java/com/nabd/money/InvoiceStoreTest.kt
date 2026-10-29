package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.agents.AgentEngine
import com.nabd.money.data.Backup
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.TxnFilter
import com.nabd.money.ui.screens.invoiceMatches
import com.nabd.money.util.CompanyInvoice
import com.nabd.money.util.InvoiceInfo
import com.nabd.money.util.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InvoiceStoreTest {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val now = System.currentTimeMillis()

    @Before fun settle() { runBlocking { app.startupJob?.join() } }

    @Test
    fun piastersAlwaysShow() {
        assertEquals(Money.plain("", 13228.84, "EGP"), Money.fmt(13228.84, decimals = false))
        assertTrue(Money.fmt(1250.5, decimals = false).contains("1,250.50"))
        assertTrue(Money.signed("−", 99.99, decimals = false).contains("99.99"))
        assertTrue(Money.fmt(5.0, decimals = false).contains("5.00"))
    }

    private fun info(number: String, ids: List<Long>) = runBlocking {
        val txns = app.db.dao().txnsByIds(ids)
        InvoiceInfo(number, "ACME", "موظف", now, "المستحقة", CompanyInvoice.items(txns))
    }

    @Test
    fun invoicesGetSequentialNumbersAndAreFoundByThem() = runBlocking {
        val dao = app.db.dao()
        app.engine.addManual(150.75, Direction.OUT, Purpose.WORK, "أدوات شغل", "ورق", null, now, null, reimbursable = true, quantity = 2)
        app.engine.addManual(80.25, Direction.OUT, Purpose.WORK, "مواصلات", "تاكسي", null, now, null, reimbursable = true)
        val ids = dao.companyPendingOnce().map { it.id }

        val n1 = app.engine.nextInvoiceNumber()
        assertTrue(Regex("NBD-\\d{8}-001").matches(n1))
        val saved = app.engine.saveInvoice(info(n1, ids), ids)
        assertEquals(n1, saved.number)
        assertEquals(231.0, saved.total, 0.001)
        assertEquals(2, saved.itemCount)
        // الرقم الجاي 002
        val n2 = app.engine.nextInvoiceNumber()
        assertTrue(n2.endsWith("-002"))
        // حفظ نفس الرقم تاني مايكررش
        app.engine.saveInvoice(info(n1, ids), ids)
        assertEquals(1, dao.invoicesOnce().size)

        // العمليات اتعلّم عليها رقم الفاتورة، والبحث بيها بيطلّعها
        val txns = dao.allTxnsOnce()
        assertTrue(txns.all { n1 in it.invoiceNo.orEmpty() })
        assertEquals(2, txns.count { TxnFilter(query = n1).matches(it, it.amount) })
        assertEquals(2, txns.count { TxnFilter(query = "-001").matches(it, it.amount) })
        assertEquals(0, txns.count { TxnFilter(query = "NBD-19990101-001").matches(it, it.amount) })

        // بحث الفواتير: بالرقم أو الشركة أو المبلغ
        assertTrue(invoiceMatches(saved, n1))
        assertTrue(invoiceMatches(saved, "001"))
        assertTrue(invoiceMatches(saved, "acme"))
        assertTrue(invoiceMatches(saved, "231"))
        assertFalse(invoiceMatches(saved, "999"))

        // نسخة محفوظة بتتفتح من جديد بنفس البنود
        val back = CompanyInvoice.fromStored(saved)
        assertEquals(2, back.items.size)
        assertEquals(231.0, back.items.sumOf { it.total }, 0.001)
        assertEquals(2, back.items.first { it.title == "ورق" }.qty)

        // فاتورة تانية على نفس عملية: الأرقام بتتجمع
        app.engine.saveInvoice(info(n2, ids.take(1)), ids.take(1))
        assertTrue(dao.txnsByIds(ids.take(1)).single().invoiceNo!!.split(',').containsAll(listOf(n1, n2)))

        // النسخة الاحتياطية بتشيل الفواتير وأرقامها
        val json = Backup.export(app.db, app.settings, "t")
        dao.clearInvoices(); dao.clearTxns()
        Backup.restore(app.db, app.settings, json)
        assertEquals(2, dao.invoicesOnce().size)
        assertTrue(dao.allTxnsOnce().any { n1 in it.invoiceNo.orEmpty() })

        // الحذف بيمسح الفاتورة ويشيل رقمها من العمليات
        app.engine.deleteInvoice(dao.invoiceByNumber(n1)!!)
        assertNull(dao.invoiceByNumber(n1))
        assertTrue(dao.allTxnsOnce().none { n1 in it.invoiceNo.orEmpty() })
        assertTrue(dao.allTxnsOnce().any { n2 in it.invoiceNo.orEmpty() })
        assertNull(AgentEngine.CASH_KEY.takeIf { false })
    }
}
