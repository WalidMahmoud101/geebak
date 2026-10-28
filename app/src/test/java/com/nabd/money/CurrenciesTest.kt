package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.agents.AgentEngine
import com.nabd.money.agents.RawMessage
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Fx
import com.nabd.money.data.Purpose
import com.nabd.money.data.TxnSource
import com.nabd.money.util.Money
import com.nabd.money.util.Rates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CurrenciesTest {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()

    @Test
    fun parsesLiveRates() {
        val live = Rates.parse("""{"result":"success","rates":{"USD":1,"EGP":48.5,"EUR":0.92,"GBP":0.79,"SAR":3.75,"AED":3.6725,"JPY":150}}""")!!
        assertEquals(48.5, live.perUsd["EGP"]!!, 0.0)
        assertEquals(1.0, live.perUsd["USDT"]!!, 0.0)
        assertNull(live.perUsd["JPY"])
        assertNull(Rates.parse("""{"result":"error"}"""))
        assertNull(Rates.parse("""{"result":"success","rates":{"EGP":0.5}}"""))
    }

    @Test
    fun convertsBothWays() {
        Fx.perUsd = mapOf("EUR" to 0.92)
        assertEquals(5000.0, Fx.convert(100.0, "USD", "EGP", 50.0)!!, 0.001)
        assertEquals(2.0, Fx.convert(100.0, "EGP", "USD", 50.0)!!, 0.001)
        assertEquals(100.0, Fx.convert(100.0, "USDT", "USD", 50.0)!!, 0.001)
        assertEquals(50.0 * 100 / 0.92, Fx.convert(100.0, "EUR", "EGP", 50.0)!!, 0.01)
        assertNull(Fx.convert(1.0, "XYZ", "EGP", 50.0))
        Fx.perUsd = emptyMap()
    }

    @Test
    fun displayModeConvertsEverythingToChosenCurrency() {
        Money.rate = 50.0
        try {
            Money.mode = "AUTO"
            assertEquals(Money.plain("", 100.0, "USD"), Money.fmt(100.0, "USD"))
            Money.mode = "EGP"
            assertEquals(Money.plain("", 5000.0, "EGP"), Money.fmt(100.0, "USD"))
            Money.mode = "USD"
            assertEquals(Money.plain("", 2.0, "USD"), Money.fmt(100.0, "EGP"))
            assertEquals(Money.plain("", 100.0, "USD"), Money.fmt(100.0, "USDT")) // نفس القيمة
            assertEquals(Money.plain("", 0.5, "BTC"), Money.fmt(0.5, "BTC")) // كريبتو متقلب مابيتحولش
        } finally { Money.mode = "AUTO" }
    }

    @Test
    fun accountAddedByHandReceivesItsOwnNotifications() = runBlocking {
        val dao = app.db.dao()
        val binance = app.engine.addManualAccount("Binance USDT", AccountType.CRYPTO, "USDT", 100.0, agentId = "binance")
        assertEquals("binance", binance.agentId)
        // إشعار Binance بيروح على نفس الحساب مش حساب جديد
        app.engine.ingest(
            RawMessage(TxnSource.NOTIFICATION, "com.binance.dev", "Binance", "", "Deposit Successful: 25 USDT has been credited to your Binance account", System.currentTimeMillis()),
            notify = false
        )
        assertEquals(1, dao.accountsOnce().count { it.agentId == "binance" })
        val txn = dao.allTxnsOnce().single()
        assertEquals(binance.id, txn.accountId)
        assertEquals(Direction.IN, txn.direction)
        assertEquals(125.0, dao.account(binance.id)!!.balance, 0.001) // رصيدك + العملية
    }

    @Test
    fun cardAddedWithLast4GetsGooglePayPayments() = runBlocking {
        val dao = app.db.dao()
        val visa = app.engine.addManualAccount("Visa •••• 4242", AccountType.CARD, "EGP", 3000.0, agentId = "wallet_cards", last4 = "4242")
        app.engine.ingest(
            RawMessage(TxnSource.NOTIFICATION, "com.google.android.apps.walletnfcrel", "Google Wallet", "", "You paid EGP 150.00 to CARREFOUR with Visa ••4242", System.currentTimeMillis()),
            notify = false
        )
        assertEquals(1, dao.accountsOnce().count { it.agentId == "wallet_cards" })
        assertEquals(2850.0, dao.account(visa.id)!!.balance, 0.001)
    }

    @Test
    fun foreignCurrencyExpenseConvertsOnTheBalance() = runBlocking {
        val dao = app.db.dao()
        app.settings.setUsdRate(50.0)
        val card = app.engine.addManualAccount("Visa •••• 1111", AccountType.CARD, "EGP", 1000.0)
        app.engine.addManual(2.0, Direction.OUT, Purpose.OTHER, "اشتراكات", "ChatGPT", card.id, System.currentTimeMillis(), null, currency = "USD")
        assertEquals(900.0, dao.account(card.id)!!.balance, 0.001)
        assertEquals("USD", dao.allTxnsOnce().single().currency)
        assertNotNull(AgentEngine.CASH_KEY)
        assertTrue(true)
    }
}
