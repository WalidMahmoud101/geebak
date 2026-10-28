package com.nabd.money.shots

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.NabdApp
import com.nabd.money.util.CompanyInvoice
import com.nabd.money.util.InvoiceInfo
import com.nabd.money.util.InvoiceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InvoiceShots {
    private val app get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val out = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }
    private val day = 86_400_000L

    private fun info(n: Int): InvoiceInfo {
        val now = System.currentTimeMillis()
        val items = (1..n).map { i ->
            InvoiceItem(
                ts = now - (n - i) * day, title = listOf("طابعة ليزر HP", "ورق A4 (علبة)", "تاكسي للعميل", "غداء اجتماع مع العميل - مطعم النيل الكبير")[i % 4],
                category = "أدوات شغل", qty = 1 + i % 3, unit = 150.0 * i, total = 150.0 * i * (1 + i % 3), currency = "EGP",
                reimbursed = i == 2, note = null,
            )
        }
        return InvoiceInfo(CompanyInvoice.number(), "ACME LTD", "وليد محمود", now, "المستحقة", items)
    }

    @Test
    fun rendersInvoicePages() {
        val pages = CompanyInvoice.renderPages(app, info(6))
        assertEquals(1, pages.size)
        FileOutputStream(File(out, "invoice_1.png")).use { pages[0].compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val long = CompanyInvoice.renderPages(app, info(40))
        assertTrue(long.size >= 2)
        long.forEachIndexed { i, b -> FileOutputStream(File(out, "invoice_long_${i + 1}.png")).use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @Test
    fun excelFileIsAValidWorkbook() {
        val file = CompanyInvoice.xlsx(app, info(5))
        ZipFile(file).use { z ->
            val names = z.entries().toList().map { it.name }
            assertTrue("[Content_Types].xml" in names && "xl/workbook.xml" in names && "xl/worksheets/sheet1.xml" in names)
            val sheet = z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertTrue(sheet.contains("rightToLeft=\"1\""))
            assertTrue(sheet.contains("SUMIF("))
            assertTrue(sheet.contains("<f>E"))
            // XML سليم
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")))
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(z.getInputStream(z.getEntry("xl/styles.xml")))
        }
        File(out, "invoice.xlsx").let { file.copyTo(it, overwrite = true) }
    }

}
