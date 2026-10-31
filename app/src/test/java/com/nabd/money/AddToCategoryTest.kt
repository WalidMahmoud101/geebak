package com.nabd.money

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.data.StockCatalog
import com.nabd.money.ui.screens.AddToCategory
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
@Config(sdk = [34])
class AddToCategoryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedCategoryHasAClearButtonForANewItem() {
        val picked = mutableListOf<String>()
        rule.setContent { NabdTheme(ThemeMode.LIGHT) { AddToCategory(StockCatalog.KITCHEN) { picked += it } } }
        rule.onNodeWithText("ضيف في «${StockCatalog.KITCHEN}»").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("اسم الصنف في ${StockCatalog.KITCHEN}").performTextInput("فرن صغير")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("ضيف").performClick()
        rule.waitForIdle()
        assertEquals(listOf("فرن صغير"), picked)
        // الخانة فاضية ومفتوحة للصنف اللي بعده
        rule.onNodeWithText("اسم الصنف في ${StockCatalog.KITCHEN}").assertIsDisplayed()
    }
}
