package com.nabd.money

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.NetWorth
import com.nabd.money.ui.screens.HiddenAccountsCard
import com.nabd.money.ui.screens.InstitutionCard
import com.nabd.money.ui.theme.NabdTheme
import com.nabd.money.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** حساب برصيد مؤكد أو مش داخل في الإجمالي لازم يفضل ينفع تفتحه وتعدّله */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AccountCardsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val binance = Account(id = 7, name = "Binance", type = AccountType.CRYPTO, agentId = "binance", accountKey = "USDT", balance = 100.0, currency = "USD", balanceKnown = true, includeInTotal = false)
    private val egp: (Account) -> Double = { it.balance * 50 }

    @Test
    fun singleAccountCardOpensEvenWhenNotInTheTotal() {
        val inst = NetWorth.institutions(listOf(binance), egp).single()
        var opened: Account? = null
        rule.setContent { NabdTheme(ThemeMode.LIGHT) { InstitutionCard(inst, egp, { opened = it }, {}) } }
        rule.onNodeWithText("مش محسوب").performClick()
        rule.waitForIdle()
        assertEquals(binance, opened)
    }

    @Test
    fun hiddenAccountOpensForEditingAndCanBeRestored() {
        var opened: Account? = null
        var restored: Account? = null
        rule.setContent { NabdTheme(ThemeMode.LIGHT) { HiddenAccountsCard(listOf(binance), true, {}, { opened = it }) { restored = it } } }
        rule.onNodeWithText("Binance").performClick()
        rule.waitForIdle()
        assertEquals(binance, opened)
        rule.onNodeWithText("رجّعه").performClick()
        rule.waitForIdle()
        assertEquals(binance, restored)
    }
}
