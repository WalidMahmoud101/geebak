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
import com.nabd.money.ui.AppViewModel
import com.nabd.money.util.Dates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ResetTest {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()

    /** الشغل بيتم على الـ main looper، فبنلفه لحد ما يخلص بدل ما نستنى عليه */
    private fun await(job: kotlinx.coroutines.Job) {
        val end = System.currentTimeMillis() + 10_000
        while (!job.isCompleted && System.currentTimeMillis() < end) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        check(job.isCompleted) { "الشغل ماخلصش" }
    }

    @Test
    fun startFromThisMonthThenUndo() {
        val dao = app.db.dao()
        val acc = runBlocking { dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main", balance = 1000.0, balanceKnown = true)) }
        val monthStart = Dates.monthRange(YearMonth.now()).first
        fun t(ts: Long, f: String) = Txn(accountId = acc, amount = 100.0, direction = Direction.OUT, category = "بقالة", purpose = Purpose.HOME,
            timestamp = ts, source = TxnSource.SMS, agentId = "cib", fingerprint = f)
        runBlocking {
            dao.insertTxn(t(monthStart - 86_400_000L * 40, "old1"))
            dao.insertTxn(t(monthStart - 86_400_000L, "old2"))
            dao.insertTxn(t(monthStart + 3_600_000L, "new1"))
        }

        val vm = AppViewModel(app)
        await(vm.reset(AppViewModel.ResetScope.BEFORE_THIS_MONTH))
        assertEquals(listOf("new1"), runBlocking { dao.allTxnsOnce() }.map { it.fingerprint })
        assertEquals(1, runBlocking { dao.accountsOnce() }.size)

        // رسالة قديمة من قبل الشهر مابتتسجلش تاني
        val old = RawMessage(TxnSource.SMS, "CIB", text = "تم خصم 250 جنيه من حسابك الرصيد 700 جنيه", timestamp = monthStart - 86_400_000L * 5)
        assertNull(runBlocking { app.engine.ingest(old, notify = false) })

        await(vm.undoReset())
        assertEquals(3, runBlocking { dao.allTxnsOnce() }.size)
        assertEquals(0L, app.settings.ignoreBefore)
    }

    @Test
    fun everythingClearsSettingsButKeepsTheme() {
        val dao = app.db.dao()
        runBlocking { dao.insertAccount(Account(name = "CIB", type = AccountType.BANK, agentId = "cib", accountKey = "main", balance = 1000.0, balanceKnown = true)) }
        app.settings.updateSalary { it.copy(payer = "ACME") }
        app.settings.setTheme(com.nabd.money.ui.theme.ThemeMode.DARK)
        val vm = AppViewModel(app)
        await(vm.reset(AppViewModel.ResetScope.EVERYTHING))
        assertEquals(0, runBlocking { dao.accountsOnce() }.size)
        assertEquals("", app.settings.salary.value.payer)
        assertEquals(com.nabd.money.ui.theme.ThemeMode.DARK, app.settings.themeMode.value)
    }
}
