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

    private fun t(id: Long, amount: Double, daysAgo: Int) = Txn(id = id, accountId = null, amount = amount, direction = Direction.OUT, category = "أخرى",
        purpose = Purpose.WORK, timestamp = now - daysAgo * 86_400_000L, source = TxnSource.MANUAL, agentId = "manual", fingerprint = "m$id", reimbursable = true)

    @Test
    fun matchesTheCompanyExpensesAnIncomingTransferSettles() {
        val items = listOf(t(1, 200.0, 5), t(2, 300.0, 4), t(3, 150.0, 3), t(4, 350.0, 2))
        // كلهم أقل من المبلغ: كلهم
        assertEquals(setOf(1L, 2L, 3L, 4L), com.nabd.money.agents.CompanyMatch.pick(items, 1000.0))
        // مجموعة بالظبط: 200 + 300 = 500
        assertEquals(setOf(1L, 2L), com.nabd.money.agents.CompanyMatch.pick(items, 500.0))
        // 350 + 150 = 500 كمان، بس 200+300 أقدم؛ المهم المجموع 500 بالظبط
        assertEquals(500.0, com.nabd.money.agents.CompanyMatch.sum(items, com.nabd.money.agents.CompanyMatch.pick(items, 500.0)), 0.001)
        // مفيش مجموعة بالظبط: الأقدم الأول من غير ما يعدّي المبلغ
        val g = com.nabd.money.agents.CompanyMatch.pick(items, 520.0)
        assertTrue(com.nabd.money.agents.CompanyMatch.sum(items, g) <= 520.5)
        assertTrue(1L in g)
    }

    @Test
    fun incomingFromCompanySettlesPendingExpensesAndIsNotIncome() = runBlocking {
        val dao = app.db.dao()
        val acc = dao.insertAccount(Account(name = "الأهلي", type = AccountType.BANK, agentId = "nbe", accountKey = "9767", balance = 30_000.0, balanceKnown = true))
        app.engine.addManual(200.0, Direction.OUT, Purpose.WORK, "أدوات شغل", "طابعة", null, now - 86_400_000L, null, reimbursable = true)
        app.engine.addManual(300.0, Direction.OUT, Purpose.WORK, "أدوات شغل", "ورق", null, now - 3_600_000L, null, reimbursable = true)
        val inc = dao.insertTxn(Txn(accountId = acc, amount = 500.0, direction = Direction.IN, category = "دخل", purpose = Purpose.OTHER,
            merchant = "مؤسسة سيفتي", timestamp = now, source = TxnSource.SMS, agentId = "nbe", fingerprint = "refund-in"))
        val pending = dao.companyPendingOnce()
        assertEquals(2, pending.size)
        val vm = com.nabd.money.ui.AppViewModel(app)
        val job = vm.updateTxn(dao.allTxnsOnce().first { it.id == inc }, hide = null, quantity = 1, settle = pending.map { it.id })
        val end = System.currentTimeMillis() + 10_000
        while (!job.isCompleted && System.currentTimeMillis() < end) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20) }
        val after = dao.allTxnsOnce().associateBy { it.id }
        assertEquals("استرداد مصاريف الشركة", after.getValue(inc).category)
        assertTrue(after.getValue(inc).excluded) // مش دخل
        assertEquals(0, dao.companyPendingOnce().size)
        assertEquals(2, dao.allTxnsOnce().count { it.reimbursable && it.reimbursedAt != null })
        assertEquals(30_000.0, dao.account(acc)!!.balance, 0.001) // الفلوس وصلت فعلاً، الرصيد مابيتغيرش
        assertEquals(0.0, FilterTotals.of(dao.allTxnsOnce(), { it.amount }).income, 0.0)
    }

    @Test
    fun companyMoneyIHoldIsCustodyNotIncomeAndOffsetsWhatTheyOwe() {
        val dao = app.db.dao()
        val acc = runBlocking { dao.insertAccount(Account(name = "الأهلي", type = AccountType.BANK, agentId = "nbe", accountKey = "main", balance = 40_000.0, balanceKnown = true)) }
        val inc = runBlocking { dao.insertTxn(Txn(accountId = acc, amount = 26_800.0, direction = Direction.IN, category = "دخل", purpose = Purpose.OTHER,
            merchant = "مؤسسة الشركة", timestamp = now, source = TxnSource.SMS, agentId = "nbe", fingerprint = "custody-in")) }
        runBlocking {
            app.engine.addManual(20_000.0, Direction.OUT, Purpose.WORK, "أدوات شغل", "معدات", null, now, null, reimbursable = true)
            app.engine.addManual(5_000.0, Direction.OUT, Purpose.WORK, "مواصلات", "رحلات", null, now, null, reimbursable = true)
        }
        val vm = com.nabd.money.ui.AppViewModel(app)
        fun wait(j: kotlinx.coroutines.Job) { val end = System.currentTimeMillis() + 10_000; while (!j.isCompleted && System.currentTimeMillis() < end) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(20) } }

        // ١- الفلوس دي بتاعة الشركة: عهدة، مش دخل
        wait(vm.updateTxn(runBlocking { dao.allTxnsOnce() }.first { it.id == inc }, role = "custody"))
        var all = runBlocking { dao.allTxnsOnce() }
        assertEquals(26_800.0, com.nabd.money.agents.CompanyMatch.custody(all), 0.001)
        assertEquals(0.0, FilterTotals.of(all, { it.amount }).income, 0.0)
        assertEquals(40_000.0, runBlocking { dao.account(acc)!!.balance }, 0.001) // الفلوس دخلت فعلاً
        val pending = runBlocking { dao.companyPendingOnce() }
        assertEquals(25_000.0, pending.sumOf { it.amount }, 0.001)

        // ٢- خصم المصاريف المستحقة من العهدة: بتتقفل والعهدة بتقل
        wait(vm.useCustody(pending.map { it.id }))
        all = runBlocking { dao.allTxnsOnce() }
        assertEquals(0, runBlocking { dao.companyPendingOnce() }.size)
        assertEquals(1_800.0, com.nabd.money.agents.CompanyMatch.custody(all), 0.001)
        assertTrue(all.single { it.category == AgentEngine.CUSTODY_USED }.excluded)
        assertEquals(0.0, FilterTotals.of(all, { it.amount }).spent, 0.0)

        // ٣- رديت الباقي للشركة (تحويل خارج)
        val out = runBlocking { dao.insertTxn(Txn(accountId = acc, amount = 1_800.0, direction = Direction.OUT, category = "تحويلات", purpose = Purpose.OTHER,
            timestamp = now, source = TxnSource.SMS, agentId = "nbe", fingerprint = "custody-out")) }
        wait(vm.updateTxn(runBlocking { dao.allTxnsOnce() }.first { it.id == out }, role = "return"))
        assertEquals(0.0, com.nabd.money.agents.CompanyMatch.custody(runBlocking { dao.allTxnsOnce() }), 0.001)

        // ٤- تراجع عن قفل المصاريف: عملية الخصم من العهدة بتتشال والعهدة ترجع
        wait(vm.setReimbursed(pending.map { it.id }, false))
        all = runBlocking { dao.allTxnsOnce() }
        assertEquals(2, runBlocking { dao.companyPendingOnce() }.size)
        assertTrue(all.none { it.category == AgentEngine.CUSTODY_USED })
        assertEquals(26_800.0 - 1_800.0, com.nabd.money.agents.CompanyMatch.custody(all), 0.001)

        // ٥- ورجّعها دخل عادي
        wait(vm.updateTxn(runBlocking { dao.allTxnsOnce() }.first { it.id == inc }, role = "clear"))
        assertEquals("دخل", runBlocking { dao.allTxnsOnce() }.first { it.id == inc }.category)
    }
}
