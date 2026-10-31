package com.nabd.money

import com.nabd.money.agents.ReceiptDraft
import com.nabd.money.agents.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الملفات في resources/receipts خرج Tesseract الحقيقي على فواتير تجريبية متعمّد إن صورها وحشة
 * (ميل، غباش، ضل، JPEG ضعيف)، بنفس إعدادات التطبيق وبنفس نماذج ara+eng.
 */
class ReceiptParserTest {
    private fun ocr(name: String) = javaClass.getResource("/receipts/$name.txt")!!.readText()

    private fun ReceiptDraft.price(part: String) = lines.first { it.name.contains(part) }

    @Test
    fun arabicSupermarketWithNameThenQuantityLine() {
        val r = ReceiptParser.parse(ocr("supermarket_ar"))
        assertEquals("سوبر ماركت خيرزمان", r.store)
        assertEquals(java.time.LocalDate.of(2026, 10, 12), r.date)
        assertEquals(r.lines.toString(), 6, r.lines.size)
        assertEquals(2.0, r.price("سكر").qty, 0.0)
        assertEquals(38.5, r.price("سكر").unitPrice!!, 0.001)
        assertEquals(92.0, r.price("كريستال").unitPrice!!, 0.001)
        assertEquals(3.0, r.price("صابون").qty, 0.0)
        assertEquals(21.75, r.price("صابون").unitPrice!!, 0.001)
        assertEquals(210.0, r.price("مسحوق").unitPrice!!, 0.001)
        // الإجمالي "485.50" اتقرى غلط، بس الكمية × السعر بتصلّحه
        assertEquals(185.5, r.price("بانيه").unitPrice!!, 0.001)
        assertEquals(929.75, r.sum, 0.01)
    }

    /** نفس الصور بعد التكبير اللي التطبيق بيعمله: الكمية بتتلزق في الإجمالي ("x 38.50" و"277.00") */
    @Test
    fun upscaledPhotoGluesQuantityIntoTotal() {
        val r = ReceiptParser.parse(ocr("supermarket_ar_device"))
        assertEquals(6, r.lines.size)
        assertEquals(2.0, r.lines[0].qty, 0.0)
        assertEquals(38.5, r.lines[0].unitPrice!!, 0.001)
        assertEquals(1.0, r.lines[1].qty, 0.0)
        assertEquals(1.25, r.lines[4].qty, 0.0)
        assertEquals(1.0, r.lines[5].qty, 0.0)
        assertEquals(929.75, r.total!!, 0.001)
        assertEquals(true, r.matchesTotal())
        val en = ReceiptParser.parse(ocr("columns_en_device"))
        assertEquals("Lipton Tea 100 bags", en.lines[0].name)
        assertEquals(true, en.matchesTotal())
    }

    /** نص طالع من Tesseract4Android نفسه على محاكي أندرويد */
    @Test
    fun readingsFromTheRealLibraryAddUp() {
        val first = ReceiptParser.parse(ocr("emulator_first"))
        assertEquals(6, first.lines.size)
        assertEquals(929.75, first.sum, 0.01)
        assertEquals(true, first.matchesTotal())

        // هنا كلمة الإجمالي نفسها اتقرت "الاجماني" وإجمالي السطر "2020"
        val second = ReceiptParser.parse(ocr("emulator_higher_prices"))
        assertEquals(6, second.lines.size)
        assertEquals(210.0, second.price("مسحوق").unitPrice!!, 0.001)
        assertEquals(1.0, second.price("مسحوق").qty, 0.0)
        assertEquals(44.0, second.price("سكر").unitPrice!!, 0.001)
        assertEquals(929.5, second.total!!, 0.001)
        assertEquals(true, second.matchesTotal())
    }

    @Test
    fun columnsOnOneLineWithPackSizeInTheName() {
        val r = ReceiptParser.parse(ocr("columns_en"))
        assertEquals(5, r.lines.size)
        assertEquals("Lipton Tea 100 bags", r.lines[0].name)
        assertEquals(1.0, r.lines[0].qty, 0.0)
        assertEquals(145.0, r.lines[0].unitPrice!!, 0.001)
        assertEquals("Ariel Powder 2.5kg", r.lines[1].name)
        // "4 4250 170.00": النقطة وقعت، والكمية × الإجمالي بيرجّعوها
        assertEquals(4.0, r.lines[2].qty, 0.0)
        assertEquals(42.5, r.lines[2].unitPrice!!, 0.001)
        assertEquals(2.0, r.price("Tide").qty, 0.0)
        assertEquals(118.25, r.price("Tide").unitPrice!!, 0.001)
        assertEquals(888.5, r.subtotal!!, 0.001)
        assertEquals(124.39, r.tax!!, 0.001)
        assertEquals(888.5, r.sum, 0.01)
        assertEquals(true, r.matchesTotal())
    }

    @Test
    fun badPhotoStillGivesLinesForReview() {
        val r = ReceiptParser.parse(ocr("columns_en_bad"))
        assertTrue("الأصناف اللي اتقريت: ${r.lines}", r.lines.size >= 3)
        assertEquals(42.5, r.lines.first { it.name.contains("Mik") }.unitPrice!!, 0.001)
    }

    @Test
    fun arabicIndicDigitsReadAsLatinLookAlikes() {
        val r = ReceiptParser.parse(ocr("indic_digits"))
        // جبنة رومي ١٣٢٫٧٥ اتقرت \TY,Vo
        val cheese = r.lines.first { it.name.contains("جبنة") }
        assertEquals(132.75, cheese.unitPrice!!, 0.001)
        assertTrue(cheese.doubtful)
        assertEquals(748.25, r.total!!, 2.0)
    }

    @Test
    fun realArabicIndicDigits() {
        val r = ReceiptParser.parse("بقالة الأمانة\nرز مصري ٥ كيلو    ٢١٠٫٠٠\nمكرونة    ٢٨٫٥٠\nالمجموع  ٢٣٨٫٥٠")
        assertEquals("رز مصري 5 كيلو", r.lines[0].name)
        assertEquals(210.0, r.lines[0].unitPrice!!, 0.001)
        assertEquals(28.5, r.lines[1].unitPrice!!, 0.001)
        assertEquals(true, r.matchesTotal())
    }

    @Test
    fun pricesOnTheLeftOfRightToLeftLine() {
        val r = ReceiptParser.parse("190.00 2 95.00 زيت عباد 1 لتر\n50.00 لبن جهينة")
        assertEquals("زيت عباد 1 لتر", r.lines[0].name)
        assertEquals(2.0, r.lines[0].qty, 0.0)
        assertEquals(95.0, r.lines[0].unitPrice!!, 0.001)
        assertEquals("لبن جهينة", r.lines[1].name)
    }

    @Test
    fun commaDecimalsThousandsAndBarcodes() {
        val r = ReceiptParser.parse("6221007012345 Olive Oil 1L 1,250.00\nTea 12,50\nTOTAL 1,262.50")
        assertEquals("Olive Oil 1L", r.lines[0].name)
        assertEquals(1250.0, r.lines[0].unitPrice!!, 0.001)
        assertEquals(12.5, r.lines[1].unitPrice!!, 0.001)
        assertEquals(1262.5, r.total!!, 0.001)
    }

    @Test
    fun itemDiscountComesOffTheUnitPrice() {
        val r = ReceiptParser.parse("شامبو 100.00\nخصم -20.00\nالاجمالي 80.00")
        assertEquals(1, r.lines.size)
        assertEquals(80.0, r.lines[0].unitPrice!!, 0.001)
        assertEquals(true, r.matchesTotal())
    }

    @Test
    fun footerAndHeaderAreNotItems() {
        val r = ReceiptParser.parse(
            """
            Item Qty Price Total
            Milk 2 20.00 40.00
            TOTAL 40.00
            Cash 100.00
            Change 60.00
            Tel 0225551234
            """.trimIndent()
        )
        assertEquals(1, r.lines.size)
        assertEquals(40.0, r.total!!, 0.0)
    }

    @Test
    fun itemWordsNearReceiptWordsAreKept() {
        val r = ReceiptParser.parse("سمن صافي 1 كيلو 180.00\nكاشو محمص 95.00\nفرعون شاي 30.00\nالاجمالي 305.00")
        assertEquals(3, r.lines.size)
        assertEquals(305.0, r.total!!, 0.0)
    }

    @Test
    fun nameWithoutPriceIsKeptForTheUser() {
        val r = ReceiptParser.parse("سكر 40.00\nزيت\nرز 30.00\nالاجمالي 170.00")
        val oil = r.lines.first { it.name == "زيت" }
        assertNull(oil.unitPrice)
        assertTrue(oil.doubtful)
        assertEquals(false, r.matchesTotal())
    }

    /** فاتورة جملة: الأعمدة بالعكس، الكمية لازقة في الإجمالي، موزون بكسور، وخصم تحت الصنف */
    @Test
    fun wholesaleReceiptWithDiscountLinesAndWeights() {
        val r = ReceiptParser.parse(
            """
            >. CT a tO eee,
            5 02525 eu
            سوق الخير 11 المعادي
            الصنف السعر الكمية القيمة
            200.00     1200.00 ج م فراخ بانيه
            50.00              خصم
            90.00 0.25   360.00 جبنة رومي
            70.00 0.25870.00
            280.0084 0.25872.24 لانشون بق
            10.32              pod
            1120.00  1 120.00 زيت شمس
            4. 0               خصم
            0 .149  1 149.00 قهوة وسط
            50 .17  1 17.50 700 ملح طعام
            4-20.00  60.00 شوكولاتة
            لبن بودرة 6.00 646.00
            99.00 | 99.00 vs مربى
            220     | 22.00 صلصة
            عددالأصناف: 8 القيمة المستحقة: 1016.42
            كارت الائتمان: 1016.40
            العروض وفرت لك 64.32 EGP
            """.trimIndent()
        )
        assertEquals("سوق الخير المعادي", r.store)
        assertEquals(1016.42, r.total!!, 0.001)
        assertEquals(150.0, r.price("فراخ").unitPrice!!, 0.001)
        assertEquals(1.0, r.price("فراخ").qty, 0.0)
        assertEquals(0.25, r.price("رومي").qty, 0.0)
        assertEquals(360.0, r.price("رومي").unitPrice!!, 0.001)
        // سعر الكيلو بعد الخصم، والوزن زي ما هو
        assertEquals(0.258, r.price("لانشون").qty, 0.0)
        assertEquals(240.0, r.price("لانشون").unitPrice!!, 0.001)
        assertEquals(116.0, r.price("شمس").unitPrice!!, 0.001)
        assertEquals(149.0, r.price("قهوة").unitPrice!!, 0.001)
        assertEquals("700 ملح طعام".split(' ').toSet(), r.price("ملح").name.split(' ').toSet())
        assertEquals(17.5, r.price("ملح").unitPrice!!, 0.001)
        assertEquals(3.0, r.price("شوكولاتة").qty, 0.0)
        assertEquals(6.0, r.price("بودرة").qty, 0.0)
        assertEquals(6.0, r.price("بودرة").unitPrice!!, 0.001)
        assertTrue(r.price("بودرة").doubtful)
        assertEquals(99.0, r.price("مربى").unitPrice!!, 0.001)
        assertEquals(22.0, r.price("صلصة").unitPrice!!, 0.001)
    }

    /** مطعم: الاسم في النص بين الأعمدة، والكمية مطبوعة من غير كسور فالوزن هو الإجمالي ÷ السعر */
    @Test
    fun restaurantReceiptWithNameBetweenColumnsAndCutQuantities() {
        val r = ReceiptParser.parse(
            """
            | مشويات النيل oy |
            Order # 77         CASH
            Date   30/09/2026  09:12 PM
            Cashier  كريم
            Qty  U.P  Item  Total
            0 X 300.00  كفتة  180.00
            1 X 300.00  طرب  330.00
            2 X 100.00  سلطة  200.00
            Net Sales   710.00
            VAT 14 %
            TOTAL DUE   710.00
            CASH 0.00  Change 0.00
            Hot Line
            15280
            """.trimIndent()
        )
        assertEquals("مشويات النيل", r.store)
        assertEquals(java.time.LocalDate.of(2026, 9, 30), r.date)
        assertEquals(3, r.lines.size)
        assertEquals("كفتة", r.lines[0].name)
        assertEquals(0.6, r.lines[0].qty, 0.0001)
        assertEquals(300.0, r.lines[0].unitPrice!!, 0.001)
        assertEquals(1.1, r.lines[1].qty, 0.0001)
        assertEquals(2.0, r.lines[2].qty, 0.0)
        assertNull(r.tax)
        assertEquals(710.0, r.total!!, 0.001)
        assertEquals(true, r.matchesTotal())
    }

    @Test
    fun lostUnitNextToTheMarkKeepsPriceAndTotalApart() {
        val r = ReceiptParser.parse("اسم المحل\n30.00 x 45.00 مكرونة\nالاجمالي 45.00")
        assertEquals(30.0, r.lines[0].unitPrice!!, 0.001)
        assertEquals(1.5, r.lines[0].qty, 0.001)
    }

    @Test
    fun wrongDatesAreIgnored() {
        assertNull(ReceiptParser.parse("التاريخ 45/13/2026").date)
        assertNull(ReceiptParser.parse("التاريخ 12/10/2099").date)
    }

    @Test
    fun emptyTextIsEmptyDraft() {
        val r = ReceiptParser.parse("")
        assertTrue(r.lines.isEmpty())
        assertNull(r.matchesTotal())
        assertFalse(r.total != null)
    }
}
