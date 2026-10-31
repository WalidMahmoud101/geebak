package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.util.Money
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HideBalancesTest {
    private val settings get() = ApplicationProvider.getApplicationContext<NabdApp>().settings

    @After fun show() { settings.hideBalances = false }

    @Test
    fun hiddenBalanceShowsStarsAndStaysHidden() {
        val text = Money.fmt(12500.0)
        assertEquals(text, Money.masked(text))
        settings.hideBalances = true
        assertTrue(Money.hideBalances)
        assertTrue(settings.hideBalances)
        assertEquals("****", Money.masked(text))
        // باقي المبالغ زي الفواتير مابتتأثرش
        assertEquals(text, Money.fmt(12500.0))
        settings.hideBalances = false
        assertFalse(Money.hideBalances)
        assertEquals(text, Money.masked(text))
    }
}
