package com.nabd.money.shots

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.agents.ReceiptParser
import com.nabd.money.data.StockCatalog
import com.nabd.money.data.StockItem
import com.nabd.money.data.StockPrice
import com.nabd.money.ui.ReceiptSession
import com.nabd.money.ui.ReceiptSource
import com.nabd.money.ui.screens.ReceiptReviewContent
import com.nabd.money.ui.screens.StockContent
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.ui.theme.NabdTheme
import com.nabd.money.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** لقطات المخزون ومراجعة الفاتورة بالوضعين، وRTL زي التطبيق */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-w392dp-h1500dp-xhdpi")
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class StockShots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    private fun item(id: Long, name: String, cat: String, isOut: Boolean = false) =
        StockItem(id = id, name = name, key = StockCatalog.key(name), category = cat, isOut = isOut, outAt = if (isOut) now - day else null)
    private val items = listOf(
        item(1, "سكر الضحى 1 كيلو", StockCatalog.FOOD), item(2, "زيت كريستال 1 لتر", StockCatalog.KITCHEN, true),
        item(3, "بانيه فراخ مجمد", StockCatalog.MEAT, true), item(4, "مسحوق اريال 2 كيلو", StockCatalog.CLEANING),
        item(5, "صابون لوكس", StockCatalog.BATH, true), item(6, "شوكولاتة", StockCatalog.LUXURY), item(7, "لمبات ليد", "كهربا البيت"),
    )
    private fun p(id: Long, itemId: Long, price: Double, days: Long, store: String? = "كازيون") = StockPrice(id, itemId, price, at = now - days * day, store = store)
    private val prices = listOf(p(1, 1, 36.0, 40), p(2, 1, 38.5, 3), p(3, 2, 110.0, 30), p(4, 2, 92.0, 2), p(5, 3, 170.0, 20), p(6, 3, 210.0, 1), p(7, 4, 210.0, 9), p(8, 5, 21.75, 5))
        .groupBy { it.itemId }.mapValues { (_, l) -> l.sortedByDescending { it.at } }

    private fun capture(mode: ThemeMode, name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            NabdTheme(mode) {
                // الـ Surface بيدّي لون النص زي الـ Scaffold والشيت في التطبيق
                androidx.compose.material3.Surface(color = NabdColors.Bg) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
                }
            }
        }
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(android.graphics.Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(out, "$name.png").length() > 1000)
    }

    private fun stock(mode: ThemeMode, page: Int, name: String) = capture(mode, name) {
        StockContent(
            items, prices, page, {}, null, {}, "", {}, reading = false,
            onBack = {}, onAdd = {}, onCamera = {}, onGallery = {}, onManualReceipt = {}, onToggle = {}, onOpen = {}, onBought = {},
            modifier = Modifier.fillMaxSize().background(NabdColors.Bg),
        )
    }

    private fun quickAdd(mode: ThemeMode, name: String) = capture(mode, name) {
        StockContent(
            items, prices, 0, {}, null, {}, "مخلل", {}, reading = false,
            onBack = {}, onAdd = {}, onCamera = {}, onGallery = {}, onManualReceipt = {}, onToggle = {}, onOpen = {}, onBought = {},
            modifier = Modifier.fillMaxSize().background(NabdColors.Bg), undoCount = 7,
        )
    }

    private fun carrefour(mode: ThemeMode, name: String) = capture(mode, name) {
        val shopItems = items + item(8, "لبن جهينة", StockCatalog.DAIRY) + item(9, "مياه نستله", StockCatalog.DRINKS, true)
        StockContent(
            shopItems, prices, 0, {}, StockCatalog.DAIRY, {}, "", {}, reading = false,
            onBack = {}, onAdd = {}, onCamera = {}, onGallery = {}, onManualReceipt = {}, onToggle = {}, onOpen = {}, onBought = {},
            modifier = Modifier.fillMaxSize().background(NabdColors.Bg),
        )
    }

    private fun review(mode: ThemeMode, name: String) {
        val text = javaClass.getResource("/receipts/supermarket_ar.txt")!!.readText()
        val session = ReceiptSession(ReceiptParser.parse(text), items, 78, ReceiptSource.CAMERA)
        capture(mode, name) {
            ReceiptReviewContent(session, items, prices, {}, {}, {}, {}, Modifier.fillMaxSize().background(NabdColors.Surface))
        }
    }

    @Test fun homeLight() = stock(ThemeMode.LIGHT, 0, "stock_home_light")
    @Test fun homeDark() = stock(ThemeMode.DARK, 0, "stock_home_dark")
    @Test fun shoppingLight() = stock(ThemeMode.LIGHT, 1, "stock_shopping_light")
    @Test fun shoppingDark() = stock(ThemeMode.DARK, 1, "stock_shopping_dark")
    @Test fun quickAddLight() = quickAdd(ThemeMode.LIGHT, "stock_quick_add_light")
    @Test fun quickAddDark() = quickAdd(ThemeMode.DARK, "stock_quick_add_dark")
    @Test fun carrefourLight() = carrefour(ThemeMode.LIGHT, "stock_carrefour_light")
    @Test fun carrefourDark() = carrefour(ThemeMode.DARK, "stock_carrefour_dark")
    @Test fun reviewLight() = review(ThemeMode.LIGHT, "stock_review_light")
    @Test fun reviewDark() = review(ThemeMode.DARK, "stock_review_dark")
}
