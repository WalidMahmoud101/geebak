package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Backup
import com.nabd.money.data.Budget
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnSource
import com.nabd.money.data.UserRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** حفظ نسخة ورجوعها: كل حاجة ترجع زي ما هي بالظبط */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupTest {
    @Test
    fun roundTrip() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<NabdApp>()
        val dao = app.db.dao()
        val accId = dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main", balance = 24_350.5, balanceKnown = true, balanceAt = 5))
        dao.insertTxn(Txn(accountId = accId, amount = 1_250.0, direction = Direction.OUT, merchant = "كارفور", category = "بقالة", purpose = Purpose.HOME,
            timestamp = 1_000, source = TxnSource.SMS, agentId = "cib", rawText = "تم خصم", fingerprint = "a"))
        dao.insertTxn(Txn(accountId = null, amount = 200.0, direction = Direction.OUT, merchant = "تاكسي", category = "مواصلات", purpose = Purpose.WORK,
            timestamp = 2_000, source = TxnSource.MANUAL, agentId = "manual", note = "شغل", fingerprint = "b", reimbursable = true))
        dao.upsertBudget(Budget("HOME", 6_000.0))
        app.settings.updateSalary { it.copy(payer = "ACME", amount = 15_000.0, exactOnly = true) }
        app.settings.updateTaxonomy { it.copy(customCategories = listOf("جيم"), rules = listOf(UserRule("costa", "مطاعم وكافيهات", Purpose.OUTING))) }
        app.settings.setUsdRate(51.5)

        val json = Backup.export(app.db, app.settings, "test")
        val info = Backup.info(json)!!
        assertEquals(2, info.transactions)
        assertEquals(1, info.accounts)

        // نمسح كل حاجة كأنه موبايل جديد
        dao.clearTxns(); dao.clearAccounts(); dao.clearBudgets()
        app.settings.updateSalary { it.copy(payer = "", amount = 0.0, exactOnly = false) }
        app.settings.updateTaxonomy { it.copy(customCategories = emptyList(), rules = emptyList()) }
        app.settings.setUsdRate(50.0)

        Backup.restore(app.db, app.settings, json)
        val a = dao.accountsOnce().single()
        assertEquals(accId, a.id)
        assertEquals(24_350.5, a.balance, 0.0)
        val txns = dao.allTxnsOnce().sortedBy { it.timestamp }
        assertEquals(2, txns.size)
        assertEquals(accId, txns[0].accountId)        // العملية لسه مربوطة بحسابها
        assertEquals("كارفور", txns[0].merchant)
        assertNull(txns[1].accountId)
        assertTrue(txns[1].reimbursable)
        assertEquals("شغل", txns[1].note)
        assertEquals(6_000.0, dao.budgetsOnce().single().monthlyLimit, 0.0)
        assertEquals("ACME", app.settings.salary.value.payer)
        assertTrue(app.settings.salary.value.exactOnly)
        assertEquals(listOf("جيم"), app.settings.taxonomy.value.customCategories)
        assertEquals(51.5, app.settings.usdRate.value, 0.01)

        // ملف مش من جيبك مايتقبلش
        assertNull(Backup.info("""{"hello":1}"""))
    }
}
