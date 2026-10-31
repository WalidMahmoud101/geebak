package com.nabd.money

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.data.StockCatalog
import com.nabd.money.data.StockItem
import com.nabd.money.ui.screens.StockContent
import com.nabd.money.ui.theme.NabdTheme
import com.nabd.money.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w392dp-h1500dp")
class ShoppingListActionsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun item(id: Long, name: String) = StockItem(id = id, name = name, key = name, category = StockCatalog.FOOD, isOut = true)

    private fun show(onRemove: (List<Long>) -> Unit, onBought: (StockItem) -> Unit = {}) = rule.setContent {
        NabdTheme(ThemeMode.LIGHT) {
            StockContent(
                listOf(item(1, "رز"), item(2, "سكر")), emptyMap(), 1, {}, null, {}, "", {}, reading = false,
                onBack = {}, onAdd = {}, onCamera = {}, onGallery = {}, onManualReceipt = {}, onToggle = {}, onOpen = {}, onBought = onBought,
                onRemoveFromList = onRemove,
            )
        }
    }

    @Test
    fun removeEverythingAtOnce() {
        var removed = emptyList<Long>()
        show({ removed = it })
        rule.onNodeWithText("شيل الكل").performClick()
        rule.waitForIdle()
        assertEquals(listOf(1L, 2L), removed)
    }

    @Test
    fun removeOnlyWhatIsSelectedWithoutOpeningBought() {
        var removed = emptyList<Long>()
        var bought = 0
        show({ removed = it }, { bought++ })
        rule.onNodeWithText("حدد").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("سكر").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("شيل المحدد (1)").performClick()
        rule.waitForIdle()
        assertEquals(listOf(2L), removed)
        assertEquals(0, bought)
    }
}
