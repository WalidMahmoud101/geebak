package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.agents.AgentEngine
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.FilterTotals
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
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
class CashAndHideTest {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val now = System.currentTimeMillis()

    @Test
    fun cashExpenseCreatesCashAccountAndMovesItsBalance() = runBlocking {
        val dao = app.db.dao()
        app.engine.addManual(100.0, Direction.OUT, Purpose.HOME, "بقالة", "سوبر ماركت", null, now, null)
        val cash = dao.findAccount(AgentRegistry.MANUAL_ID, AgentEngine.CASH_KEY)!!
        assertEquals(AccountType.CASH, cash.type)
        assertEquals(-100.0, cash.balance, 0.001)
        assertEquals(cash.id, dao.allTxnsOnce().single().accountId)
        // إضافة فلوس كاش
        app.engine.addManual(500.0, Direction.IN, Purpose.HOME, "مصروف البيت", null, cash.id, now, null)
        assertEquals(400.0, dao.account(cash.id)!!.balance, 0.001)
        assertEquals(1, dao.accountsOnce().count { it.type == AccountType.CASH })
    }

    @Test
    fun oldCashTransactionsAreAdopted() = runBlocking {
        val dao = app.db.dao()
        dao.insertTxn(Txn(accountId = null, amount = 50.0, direction = Direction.OUT, category = "بقالة", purpose = Purpose.HOME,
            timestamp = now, source = TxnSource.MANUAL, agentId = AgentRegistry.MANUAL_ID, fingerprint = "old-cash"))
        app.engine.adoptCash()
        val cash = dao.findAccount(AgentRegistry.MANUAL_ID, AgentEngine.CASH_KEY)!!
        assertEquals(cash.id, dao.allTxnsOnce().single { it.fingerprint == "old-cash" }.accountId)
        assertEquals(0.0, cash.balance, 0.001) // الرصيد مابيتغيرش بالنقل
    }

    @Test
    fun hiddenIsNeitherSpentNorAddedAndCanBeRestored() = runBlocking {
        val dao = app.db.dao()
        app.engine.addManual(100.0, Direction.OUT, Purpose.HOME, "بقالة", null, null, now, null)
        app.engine.addManual(300.0, Direction.IN, Purpose.HOME, "مصروف البيت", null, null, now, null)
        val cash = dao.findAccount(AgentRegistry.MANUAL_ID, AgentEngine.CASH_KEY)!!
        assertEquals(200.0, cash.balance, 0.001)
        val ids = dao.allTxnsOnce().map { it.id }

        app.engine.setHidden(ids, true)
        assertEquals(0.0, dao.account(cash.id)!!.balance, 0.001)
        val hidden = dao.allTxnsOnce()
        assertTrue(hidden.all { it.excluded })
        val totals = FilterTotals.of(hidden, { it.amount })
        assertEquals(0.0, totals.income, 0.0)
        assertEquals(0.0, totals.spent, 0.0)

        app.engine.setHidden(ids, false)
        assertEquals(200.0, dao.account(cash.id)!!.balance, 0.001)
        assertFalse(dao.allTxnsOnce().any { it.excluded })
    }

    @Test
    fun hidingBankTxnKeepsRealBankBalance() = runBlocking {
        val dao = app.db.dao()
        val acc = dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main", balance = 1000.0, balanceKnown = true, balanceAt = now))
        val id = dao.insertTxn(Txn(accountId = acc, amount = 100.0, direction = Direction.OUT, category = "بقالة", purpose = Purpose.HOME,
            timestamp = now, source = TxnSource.SMS, agentId = "cib", fingerprint = "bank1"))
        app.engine.setHidden(listOf(id), true)
        assertEquals(1000.0, dao.account(acc)!!.balance, 0.001) // رصيد البنك الحقيقي
        assertTrue(dao.allTxnsOnce().single().excluded)
        // تحويل داخلي مستبعد لوحده مايتأثرش رصيده لو رجعته
        val t = dao.allTxnsOnce().single()
        dao.updateTxn(t.copy(excluded = true))
        app.engine.setHidden(listOf(id), false)
        assertEquals(1000.0, dao.account(acc)!!.balance, 0.001)
    }

    @Test
    fun hiddenAccountsArePersistedAndSurviveBackup() = runBlocking {
        val dao = app.db.dao()
        val a = dao.insertAccount(Account(name = "InstaPay تجربة", type = AccountType.BANK, agentId = "cib", accountKey = "x1", balance = 10.0, balanceKnown = true))
        app.settings.setAccountHidden(a, true)
        assertEquals(setOf(a), app.settings.hiddenAccounts.value)
        val json = com.nabd.money.data.Backup.export(app.db, app.settings, "test")
        app.settings.setAccountHidden(a, false)
        assertTrue(app.settings.hiddenAccounts.value.isEmpty())
        com.nabd.money.data.Backup.restore(app.db, app.settings, json)
        app.settings.reload()
        assertEquals(setOf(a), app.settings.hiddenAccounts.value)
    }

    @Test
    fun hidingFromEditSheetPathKeepsBalanceConsistent() = runBlocking {
        val dao = app.db.dao()
        app.engine.addManual(200.0, Direction.OUT, Purpose.WORK, "أدوات شغل", "طابعة", null, now, null, reimbursable = true)
        val cash = dao.findAccount(AgentRegistry.MANUAL_ID, AgentEngine.CASH_KEY)!!
        assertEquals(-200.0, cash.balance, 0.001)
        val t = dao.allTxnsOnce().single()
        // نفس مسار زرار "اخفيها" في شاشة التعديل: حفظ ثم إخفاء
        dao.updateTxn(t.copy(note = "x"))
        app.engine.setHidden(listOf(t.id), true)
        assertEquals(0.0, dao.account(cash.id)!!.balance, 0.001)
        assertEquals("x", dao.allTxnsOnce().single().note)
        assertTrue(dao.allTxnsOnce().single().reimbursable)
    }

    @Test
    fun quantityScalesTotalAndBalance() = runBlocking {
        val dao = app.db.dao()
        // 2 قطع بـ 150 للواحدة = 300
        app.engine.addManual(300.0, Direction.OUT, Purpose.WORK, "أدوات شغل", "ورق", null, now, null, reimbursable = true, quantity = 2)
        val cash = dao.findAccount(AgentRegistry.MANUAL_ID, AgentEngine.CASH_KEY)!!
        val t = dao.allTxnsOnce().single()
        assertEquals(2, t.quantity)
        assertEquals(-300.0, cash.balance, 0.001)
        app.engine.setQuantity(t.id, 5)
        val t2 = dao.allTxnsOnce().single()
        assertEquals(5, t2.quantity)
        assertEquals(750.0, t2.amount, 0.001) // سعر الوحدة 150 ثابت
        assertEquals(-750.0, dao.account(cash.id)!!.balance, 0.001)
        app.engine.setQuantity(t.id, 1)
        assertEquals(150.0, dao.allTxnsOnce().single().amount, 0.001)
        assertEquals(-150.0, dao.account(cash.id)!!.balance, 0.001)
    }

    @Test
    fun quantitySurvivesBackup() = runBlocking {
        val dao = app.db.dao()
        app.engine.addManual(90.0, Direction.OUT, Purpose.WORK, "ورق", null, null, now, null, reimbursable = true, quantity = 3)
        val json = com.nabd.money.data.Backup.export(app.db, app.settings, "t")
        dao.clearTxns()
        com.nabd.money.data.Backup.restore(app.db, app.settings, json)
        assertEquals(3, dao.allTxnsOnce().single().quantity)
    }

    @Test
    fun hiddenAndTransfersCanBeRestoredOrMovedToCompany() = runBlocking {
        val dao = app.db.dao()
        // مصروف كاش مخفي بإيدك
        app.engine.addManual(120.0, Direction.OUT, Purpose.PERSONAL, "مواصلات", "تاكسي", null, now, null)
        val cash = dao.findAccount(AgentRegistry.MANUAL_ID, AgentEngine.CASH_KEY)!!
        val taxi = dao.allTxnsOnce().single()
        app.engine.setHidden(listOf(taxi.id), true)
        assertEquals(0.0, dao.account(cash.id)!!.balance, 0.001)
        // تحويل داخلي (خارج) ودخل جاي منه
        val acc = dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main", balance = 1000.0, balanceKnown = true))
        val out = dao.insertTxn(Txn(accountId = acc, amount = 300.0, direction = Direction.OUT, category = "تحويل بين حساباتي", purpose = Purpose.OTHER,
            timestamp = now, source = TxnSource.SMS, agentId = "cib", fingerprint = "tr-out", excluded = true))
        val inn = dao.insertTxn(Txn(accountId = acc, amount = 300.0, direction = Direction.IN, category = "تحويل بين حساباتي", purpose = Purpose.OTHER,
            timestamp = now, source = TxnSource.SMS, agentId = "cib", fingerprint = "tr-in", excluded = true))

        // الفلتر: المخفي والتحويلات بس
        val f = com.nabd.money.data.TxnFilter(onlyHidden = true)
        val all = dao.allTxnsOnce()
        assertEquals(3, all.count { f.matches(it, it.amount) })
        assertEquals(0, dao.allTxnsOnce().count { com.nabd.money.data.TxnFilter().matches(it, it.amount) })

        // المخفي والتحويل الخارج يروحوا لمصاريف الشركة. الدخل مابيتنقلش
        val moved = app.engine.moveToCompany(listOf(taxi.id, out, inn))
        assertEquals(2, moved)
        val now1 = dao.allTxnsOnce().associateBy { it.id }
        assertTrue(now1.getValue(taxi.id).reimbursable && !now1.getValue(taxi.id).excluded)
        assertEquals(Purpose.WORK, now1.getValue(taxi.id).purpose)
        assertTrue(now1.getValue(out).reimbursable && !now1.getValue(out).excluded)
        assertEquals("أخرى", now1.getValue(out).category)
        assertTrue(now1.getValue(inn).excluded && !now1.getValue(inn).reimbursable)
        // أثر الإخفاء على رصيد الكاش رجع (العملية بتتحسب في الشركة)
        assertEquals(-120.0, dao.account(cash.id)!!.balance, 0.001)
        assertEquals(2, dao.companyPendingOnce().size)

        // وترجيع عادي: مخفي → يظهر
        app.engine.addManual(50.0, Direction.OUT, Purpose.HOME, "بقالة", "سوبر", null, now, null)
        val shop = dao.allTxnsOnce().first { it.merchant == "سوبر" }
        app.engine.setHidden(listOf(shop.id), true)
        app.engine.setHidden(listOf(shop.id), false)
        assertTrue(com.nabd.money.data.TxnFilter().matches(dao.allTxnsOnce().first { it.id == shop.id }, 50.0))
        assertEquals(-170.0, dao.account(cash.id)!!.balance, 0.001)
    }
}
