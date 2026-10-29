package com.nabd.money

import com.nabd.money.data.Settings
import com.nabd.money.ui.screens.goldChangePct
import com.nabd.money.util.Dates
import com.nabd.money.util.Gold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GoldTest {
    @Test
    fun gramPricesPerKarat() {
        // أوقية 3110.34768$ = 100$ للجرام الخام (عيار 24). والدولار 50 جنيه
        val oz = 3110.34768
        assertEquals(5000.0, Gold.gram(oz, 50.0, 24), 0.01)
        assertEquals(4375.0, Gold.gram(oz, 50.0, 21), 0.01)
        assertEquals(3750.0, Gold.gram(oz, 50.0, 18), 0.01)
        assertEquals(2916.67, Gold.gram(oz, 50.0, 14), 0.01)
        assertEquals(35_000.0, Gold.pound(oz, 50.0), 0.01) // 8 جرام عيار 21
        assertEquals(oz * 50.0, Gold.ounceEgp(oz, 50.0), 0.001)
    }

    @Test
    fun localPremiumRaisesOrLowersThePrice() {
        val oz = 3110.34768
        assertEquals(5100.0, Gold.gram(oz, 50.0, 24, 2.0), 0.01)
        assertEquals(4950.0, Gold.gram(oz, 50.0, 24, -1.0), 0.01)
    }

    @Test
    fun parsesTheApisAndRejectsGarbage() {
        assertEquals(4026.9, Gold.parse("""{"currency":"USD","name":"Gold","price":4026.899902,"symbol":"XAU"}""")!!, 0.01)
        assertNull(Gold.parse("""{"price":5}"""))          // مش منطقي لأوقية
        assertNull(Gold.parse("not json"))
        assertEquals(4026.9, Gold.parseBackup("""{"items":[{"curr":"USD","xauPrice":4026.9,"xagPrice":47}]}""")!!, 0.01)
        assertNull(Gold.parseBackup("""{"items":[]}"""))
    }

    @Test
    fun changeComparesWithTheLastPreviousDay() {
        val today = Dates.dayKey(System.currentTimeMillis())
        val g = Settings.GoldState(4100.0, 0, 0.0, listOf((today - 2) to 3900.0, (today - 1) to 4000.0, today to 4100.0))
        assertEquals(2.5, goldChangePct(g)!!, 0.001)
        assertNull(goldChangePct(Settings.GoldState(4100.0, 0, 0.0, listOf(today to 4100.0)))) // مفيش يوم قبله
        assertNotNull(g)
        assertTrue(goldChangePct(g)!! > 0)
    }
}
