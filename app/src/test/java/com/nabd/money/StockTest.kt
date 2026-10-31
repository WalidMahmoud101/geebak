package com.nabd.money

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.database.sqlite.SQLiteDatabase
import com.nabd.money.agents.PriceCheck
import com.nabd.money.agents.PriceLevel
import com.nabd.money.data.Backup
import com.nabd.money.data.CarrefourCatalog
import com.nabd.money.data.NabdDatabase
import com.nabd.money.data.ReceiptEntry
import com.nabd.money.data.StockCatalog
import com.nabd.money.data.StockItem
import com.nabd.money.data.StockPrice
import com.nabd.money.data.StockStore
import com.nabd.money.ui.ReceiptSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StockTest {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val dao get() = app.db.dao()
    private val store get() = StockStore(app.db)

    private fun p(price: Double, at: Long, id: Long = at) = StockPrice(id = id, itemId = 1, price = price, at = at)
    private fun item(name: String, id: Long = 1) = StockItem(id = id, name = name, key = StockCatalog.key(name), category = StockCatalog.FOOD)

    // ---------- غالي ولا رخيص ----------
    @Test
    fun verdictComparesWithLastPurchase() {
        val h = listOf(p(100.0, 1), p(120.0, 2))
        assertEquals(PriceLevel.FIRST, PriceCheck.check(50.0, emptyList()).level)
        assertEquals(PriceLevel.EXPENSIVE, PriceCheck.check(135.0, h).level)
        assertEquals(12.5, PriceCheck.check(135.0, h).percent, 0.01)
        assertEquals(PriceLevel.CHEAP, PriceCheck.check(105.0, h).level)
        assertEquals(PriceLevel.SAME, PriceCheck.check(122.0, h).level)
        assertEquals(100.0, PriceCheck.check(135.0, h).lowest!!, 0.0)
    }

    @Test
    fun clearingEverythingCanBeUndoneWithPrices() = runBlocking {
        store.clear()
        val a = store.add("رز", StockCatalog.FOOD)!!
        val b = store.add("صابون", StockCatalog.BATH, out = true)!!
        store.addPrice(a.id, 40.0)
        val snap = store.clear()
        assertEquals(2, snap.items.size)
        assertTrue(app.db.dao().stockItemsOnce().isEmpty())
        assertTrue(app.db.dao().stockPricesOnce().isEmpty())
        store.restore(snap)
        assertEquals(setOf(a.id, b.id), app.db.dao().stockItemsOnce().map { it.id }.toSet())
        assertEquals(40.0, app.db.dao().stockPricesOnce().single().price, 0.0)
    }

    @Test
    fun clearingOneCategoryLeavesTheRest() = runBlocking {
        store.clear()
        val a = store.add("رز", StockCatalog.FOOD)!!
        val b = store.add("صابون", StockCatalog.BATH)!!
        store.addPrice(a.id, 40.0)
        store.addPrice(b.id, 20.0)
        val snap = store.clear(StockCatalog.BATH)
        assertEquals(listOf("صابون"), snap.items.map { it.name })
        assertEquals(listOf("رز"), app.db.dao().stockItemsOnce().map { it.name })
        assertEquals(40.0, app.db.dao().stockPricesOnce().single().price, 0.0)
    }

    @Test
    fun undoKeepsAnItemAddedAgainAndMovesItsOldPrices() = runBlocking {
        store.clear()
        val a = store.add("رز", StockCatalog.FOOD)!!
        store.addPrice(a.id, 40.0)
        val snap = store.clear()
        val again = store.add("رز", StockCatalog.KITCHEN)!!
        store.addPrice(again.id, 45.0)
        store.restore(snap)
        val item = app.db.dao().stockItemsOnce().single()
        assertEquals(again.id, item.id)
        assertEquals(StockCatalog.KITCHEN, item.category)
        val prices = app.db.dao().stockPricesOnce()
        assertEquals(2, prices.size)
        assertTrue(prices.all { it.itemId == again.id })
    }

    @Test
    fun carrefourSectionGuessesDepartmentsAndFilters() = runBlocking {
        assertEquals(StockCatalog.FROZEN, StockCatalog.guessCarrefour("بانيه فراخ مجمد"))
        assertEquals(StockCatalog.DAIRY, StockCatalog.guessCarrefour("جبنة رومي"))
        assertEquals(StockCatalog.PANTRY, StockCatalog.guessCarrefour("مخلل"))
        assertEquals(StockCatalog.PANTRY, StockCatalog.targetFor("مخلل", StockCatalog.CARREFOUR))
        assertEquals(StockCatalog.DAIRY, StockCatalog.targetFor("مخلل", StockCatalog.DAIRY))
        assertTrue(StockCatalog.inFilter(StockCatalog.DRINKS, StockCatalog.CARREFOUR))
        assertFalse(StockCatalog.inFilter(StockCatalog.KITCHEN, StockCatalog.CARREFOUR))
        store.clear()
        store.add("جبنة", StockCatalog.DAIRY)
        store.add("شاي", StockCatalog.DRINKS)
        store.add("زيت", StockCatalog.KITCHEN)
        assertEquals(2, store.clear(StockCatalog.CARREFOUR).items.size)
        assertEquals(listOf("زيت"), app.db.dao().stockItemsOnce().map { it.name })
    }

    @Test
    fun carrefourCatalogCoversEveryDepartmentAndSearches() {
        assertEquals(StockCatalog.carrefour.toSet(), CarrefourCatalog.items.keys)
        CarrefourCatalog.items.values.forEach { names ->
            assertTrue(names.size >= 20)
            assertEquals(names.size, names.map(StockCatalog::key).toSet().size)
        }
        assertTrue(CarrefourCatalog.search("جهينة").any { it.second == StockCatalog.DAIRY })
        assertTrue(CarrefourCatalog.search("ا").isEmpty())
        // فلتر النوع: نفس الكلمة في قسم واحد بس
        assertTrue(CarrefourCatalog.search("دجاج").map { it.second }.toSet().size > 1)
        assertEquals(setOf(StockCatalog.FROZEN), CarrefourCatalog.search("دجاج", StockCatalog.FROZEN).map { it.second }.toSet())
        assertEquals(CarrefourCatalog.search("دجاج").size, CarrefourCatalog.search("دجاج", StockCatalog.CARREFOUR).size)
    }

    @Test
    fun addSearchFindsItemsAcrossCategoriesAndTheCategoriesThemselves() {
        val hits = CarrefourCatalog.searchEverywhere("فويل")
        assertTrue(hits.any { it.second == StockCatalog.KITCHEN })
        assertEquals(hits.size, hits.map { StockCatalog.key(it.first) }.toSet().size)
        assertTrue(CarrefourCatalog.searchEverywhere("جهينة").any { it.second == StockCatalog.DAIRY })
        assertEquals(listOf(StockCatalog.DAIRY), CarrefourCatalog.categoriesMatching("الألبان"))
        assertEquals(listOf("كهربا البيت"), CarrefourCatalog.categoriesMatching("كهربا", listOf("كهربا البيت")))
    }

    @Test
    fun spicesHaveTheirOwnCategory() {
        assertTrue(StockCatalog.SPICES in StockCatalog.builtIn)
        assertEquals(StockCatalog.SPICES, StockCatalog.guessCategory("ملح طعام"))
        assertEquals(StockCatalog.SPICES, StockCatalog.guessCategory("بهارات مشكلة"))
        assertEquals(StockCatalog.SPICES, StockCatalog.guessCategory("كمون"))
        assertEquals(StockCatalog.KITCHEN, StockCatalog.guessCategory("زيت عباد"))
        assertTrue(CarrefourCatalog.searchEverywhere("كركم").any { it.second == StockCatalog.SPICES })
    }

    @Test
    fun quantityFollowsTheShoppingList() = runBlocking {
        store.clear()
        val a = store.add("رز", StockCatalog.FOOD, quantity = 2.0)!!
        assertEquals(2.0, a.quantity!!, 0.0)
        // الصفر بينزّله على قايمة الشرا، وأكتر من صفر بيرجّعه
        store.setQuantity(a.id, 0.0)
        assertTrue(dao.stockItem(a.id)!!.isOut)
        store.setQuantity(a.id, 3.0)
        assertFalse(dao.stockItem(a.id)!!.isOut)
        // علّمته خلصت يدوي: صفر. ورجعته من غير كمية: مش محددة مش صفر
        store.setOut(a.id, true)
        assertEquals(0.0, dao.stockItem(a.id)!!.quantity!!, 0.0)
        store.setOut(a.id, false)
        assertNull(dao.stockItem(a.id)!!.quantity)
    }

    @Test
    fun buyingAddsToWhatIsAtHomeAndUnsetStaysUnset() = runBlocking {
        store.clear()
        val a = store.add("زيت", StockCatalog.KITCHEN, quantity = 1.0)!!
        val b = store.add("سكر", StockCatalog.FOOD)!!
        assertNull(b.quantity)
        // سعر من غير شرا مابيغيرش الكمية
        store.addPrice(a.id, 90.0)
        assertEquals(1.0, dao.stockItem(a.id)!!.quantity!!, 0.0)
        store.addPrice(a.id, 95.0, bought = 2.0)
        assertEquals(3.0, dao.stockItem(a.id)!!.quantity!!, 0.0)
        store.saveReceipt(listOf(ReceiptEntry("زيت", 2.0, 92.0, StockCatalog.KITCHEN, a.id), ReceiptEntry("جبنة رومي", 0.258, 280.0, StockCatalog.FOOD)), "كازيون", 5)
        assertEquals(5.0, dao.stockItem(a.id)!!.quantity!!, 0.0)
        assertEquals(0.258, dao.stockItemsOnce().first { it.name == "جبنة رومي" }.quantity!!, 0.0)
        assertEquals("0.258", StockCatalog.quantityText(0.258))
        assertEquals("3", StockCatalog.quantityText(3.0))
    }

    @Test
    fun smallPiasterChangesAreNotExpensive() {
        assertEquals(PriceLevel.SAME, PriceCheck.check(5.25, listOf(p(5.0, 1))).level)
        assertEquals(PriceLevel.EXPENSIVE, PriceCheck.check(6.0, listOf(p(5.0, 1))).level)
    }

    @Test
    fun latestComparesNewestWithTheOneBefore() {
        assertNull(PriceCheck.latest(emptyList()))
        assertEquals(PriceLevel.FIRST, PriceCheck.latest(listOf(p(10.0, 1)))!!.level)
        assertEquals(PriceLevel.EXPENSIVE, PriceCheck.latest(listOf(p(20.0, 2), p(10.0, 1)))!!.level)
    }

    // ---------- نفس الصنف ----------
    @Test
    fun keyIgnoresSpellingDifferences() {
        assertEquals(StockCatalog.key("سُكّر أبيض"), StockCatalog.key("سكر ابيض"))
        assertEquals(StockCatalog.key("زيت ٢ لتر"), StockCatalog.key("زيت 2 لتر"))
        assertEquals(StockCatalog.key("بانيه  فراخ."), StockCatalog.key("بانيه فراخ"))
    }

    @Test
    fun matchFindsSameItemButNeverADifferentSize() {
        val items = listOf(item("زيت كريستال 1 لتر", 1), item("زيت كريستال 2 لتر", 2), item("سكر", 3))
        assertEquals(1L, StockCatalog.match("زيت كريستال ١ لتر", items)?.id)
        assertEquals(2L, StockCatalog.match("كريستال زيت 2 لتر", items)?.id)
        assertNull(StockCatalog.match("زيت كريستال 5 لتر", items))
        assertNull(StockCatalog.match("فول", items))
        assertEquals(9L, StockCatalog.match("سكر الضحى 1ك", listOf(item("سكر", 9).copy(aliases = StockCatalog.key("سكر الضحى 1ك"))))?.id)
    }

    @Test
    fun genericItemCatchesBrandedLineOnReceipt() {
        val items = listOf(item("زيت", 1), item("فراخ", 2))
        assertEquals(1L, StockCatalog.match("زيت كريستال 1 لتر", items)?.id)
        assertNull(StockCatalog.match("صدور فراخ", items))
    }

    @Test
    fun categoriesAreGuessed() {
        assertEquals(StockCatalog.MEAT, StockCatalog.guessCategory("بانيه فراخ مجمد"))
        assertEquals(StockCatalog.CLEANING, StockCatalog.guessCategory("مسحوق أريال 2 كيلو"))
        assertEquals(StockCatalog.BATH, StockCatalog.guessCategory("صابون لوكس"))
        assertEquals(StockCatalog.FOOD, StockCatalog.guessCategory("Juhayna Milk 1L"))
        assertEquals(StockCatalog.OTHER, StockCatalog.guessCategory("لمبة ليد"))
    }

    // ---------- القايمة والحفظ ----------
    @Test
    fun addMarkOutAndShoppingList() = runBlocking {
        val sugar = store.add("سكر", StockCatalog.FOOD)!!
        store.add("شامبو", StockCatalog.BATH, out = true)
        assertEquals(setOf("شامبو"), dao.stockItems().first().filter { it.isOut }.map { it.name }.toSet())
        store.setOut(sugar.id, true)
        val out = dao.stockItems().first().filter { it.isOut }
        assertEquals(2, out.size)
        assertNotNull(out.first().outAt)
        // نفس الاسم بكتابة تانية مش صنف جديد
        store.add("سُكّر", StockCatalog.KITCHEN)
        assertEquals(2, dao.stockItemsOnce().size)
    }

    @Test
    fun manualPriceBringsItemBack() = runBlocking {
        val i = store.add("زيت", StockCatalog.KITCHEN, out = true)!!
        store.addPrice(i.id, 90.0, "كارفور")
        val saved = dao.stockItem(i.id)!!
        assertFalse(saved.isOut)
        assertNull(saved.outAt)
        assertEquals("كارفور", dao.stockPricesOnce().single().store)
    }

    @Test
    fun receiptCreatesItemsLearnsAliasesAndComparesNextTime() = runBlocking {
        val oil = store.add("زيت", StockCatalog.KITCHEN, out = true)!!
        store.saveReceipt(
            listOf(ReceiptEntry("زيت كريستال", 1.0, 90.0, StockCatalog.KITCHEN, oil.id), ReceiptEntry("بانيه فراخ", 2.0, 180.0, StockCatalog.MEAT)),
            "سوبر ماركت", 1_000,
        )
        assertEquals(2, dao.stockItemsOnce().size)
        assertFalse(dao.stockItem(oil.id)!!.isOut)

        // المرة الجاية نفس الاسم على الفاتورة بيروح لنفس الصنف من غير ربط، ويتقارن بسعره
        store.saveReceipt(listOf(ReceiptEntry("زيت كريستال", 1.0, 110.0, StockCatalog.KITCHEN)), null, 2_000)
        assertEquals(2, dao.stockItemsOnce().size)
        val history = dao.stockPricesOnce().filter { it.itemId == oil.id }
        assertEquals(2, history.size)
        assertEquals(PriceLevel.EXPENSIVE, PriceCheck.latest(history)!!.level)
        val breaded = dao.stockPricesOnce().first { it.itemId != oil.id }
        assertEquals(180.0, breaded.price, 0.0)
        assertEquals(2.0, breaded.qty, 0.0)
    }

    @Test
    fun renameRefusesDuplicateAndDeleteRemovesPrices() = runBlocking {
        val a = store.add("سكر", StockCatalog.FOOD)!!
        val b = store.add("رز", StockCatalog.FOOD)!!
        assertFalse(store.edit(b, "سكر", StockCatalog.FOOD))
        assertTrue(store.edit(b, "رز مصري", StockCatalog.KITCHEN))
        store.addPrice(a.id, 40.0)
        store.delete(a.id)
        assertEquals(1, dao.stockItemsOnce().size)
        assertTrue(dao.stockPricesOnce().isEmpty())
    }

    // ---------- النسخة الاحتياطية ----------
    @Test
    fun backupCarriesStockAndOldBackupsLeaveItAlone() = runBlocking {
        val i = store.add("سكر", StockCatalog.FOOD, out = true)!!
        store.addPrice(i.id, 40.0, "كازيون")
        store.setOut(i.id, true)
        store.setQuantity(i.id, 0.0)
        val json = Backup.export(app.db, app.settings, "test")
        store.add("حاجة بعد النسخة", StockCatalog.OTHER)
        Backup.restore(app.db, app.settings, json)
        assertEquals(listOf("سكر"), dao.stockItemsOnce().map { it.name })
        assertTrue(dao.stockItemsOnce().single().isOut)
        assertEquals(0.0, dao.stockItemsOnce().single().quantity!!, 0.0)
        assertEquals(40.0, dao.stockPricesOnce().single().price, 0.0)

        val old = org.json.JSONObject(json).apply { remove("stockItems"); remove("stockPrices") }.toString()
        Backup.restore(app.db, app.settings, old)
        assertEquals(1, dao.stockItemsOnce().size)
    }

    // ---------- الترقية ----------
    @Test
    fun upgradeFromV5KeepsDataAndAddsStock() = runBlocking {
        val ctx = app
        val file = ctx.getDatabasePath("mig5.db").apply { parentFile?.mkdirs(); delete() }
        // نفتح بالإصدار الحالي علشان نجيب شكل الجداول، وبعدين نرجّعها v5 بالظبط
        Room.databaseBuilder(ctx, NabdDatabase::class.java, "mig5.db").allowMainThreadQueries().build().also { it.openHelper.writableDatabase; it.close() }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP TABLE stock_items")
            db.execSQL("DROP TABLE stock_prices")
            db.execSQL("INSERT INTO budgets VALUES ('HOME', 6000.0)")
            db.execSQL("INSERT INTO invoices (number, createdAt, company, employee, scope, total, itemCount, itemsJson) VALUES ('NBD-1', 1, 'ACME', 'x', 'y', 10.0, 1, '[]')")
            db.version = 5
        }
        val room = Room.databaseBuilder(ctx, NabdDatabase::class.java, "mig5.db")
            .addMigrations(NabdDatabase.MIGRATION_5_6, NabdDatabase.MIGRATION_6_7).allowMainThreadQueries().build()
        val d = room.dao()
        assertEquals(6000.0, d.budgetsOnce().single().monthlyLimit, 0.0)
        assertEquals("NBD-1", d.invoicesOnce().single().number)
        // Room بيتأكد من شكل الجداول، والكتابة والقراية شغالين
        val id = d.insertStockItem(StockItem(name = "سكر", key = "سكر", category = StockCatalog.FOOD, quantity = 2.5))
        d.insertStockPrice(StockPrice(itemId = id, price = 40.0, at = 1))
        assertEquals(1, d.stockPricesOnce().size)
        assertEquals(2.5, d.stockItemsOnce().single().quantity!!, 0.0)
        room.close()
    }

    @Test fun onlyCameraPhotosClaimToBeDeleted() {
        assertTrue(ReceiptSource.CAMERA.note.contains("اتمسحت"))
        assertFalse(ReceiptSource.GALLERY.note.contains("اتمسحت"))
        assertTrue(ReceiptSource.GALLERY.note.contains("معرض الصور"))
        assertFalse(ReceiptSource.MANUAL.note.contains("الصورة"))
    }
}
