package com.nabd.money.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.nabd.money.R
import com.nabd.money.data.Txn
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** بند واحد في فاتورة الشركة */
data class InvoiceItem(
    val ts: Long,
    val title: String,
    val category: String,
    val qty: Int,
    val unit: Double,
    val total: Double,
    val currency: String,
    val reimbursed: Boolean,
    val note: String?,
)

data class InvoiceInfo(
    val number: String,
    val company: String,
    val employee: String,
    val issuedAt: Long,
    val scope: String,
    val items: List<InvoiceItem>,
) {
    val currencies get() = items.map { it.currency }.distinct()
    fun total(cur: String) = items.filter { it.currency == cur }.sumOf { it.total }
    fun received(cur: String) = items.filter { it.currency == cur && it.reimbursed }.sumOf { it.total }
    val from get() = items.minOfOrNull { it.ts }
    val to get() = items.maxOfOrNull { it.ts }
}

/**
 * فاتورة مصاريف الشركة: PDF بشكل شيك باسم جيبك (للطباعة والتسليم)، وملف Excel (.xlsx) للمحاسب.
 * من غير أي مكتبة: PDF بـ android.graphics.pdf، والـ xlsx ملف zip فيه XML.
 */
object CompanyInvoice {

    fun items(txns: List<Txn>): List<InvoiceItem> = txns.sortedBy { it.timestamp }.map { t ->
        val q = t.quantity.coerceAtLeast(1)
        InvoiceItem(
            ts = t.timestamp,
            title = t.merchant?.takeIf { it.isNotBlank() } ?: t.category,
            category = t.category,
            qty = q,
            unit = t.amount / q,
            total = t.amount,
            currency = t.currency,
            reimbursed = t.reimbursedAt != null,
            note = t.note?.takeIf { it.isNotBlank() },
        )
    }

    /** بنود الفاتورة كـ JSON علشان تتحفظ وتتفتح من جديد */
    fun itemsToJson(items: List<InvoiceItem>): String = org.json.JSONArray(items.map {
        org.json.JSONObject().put("ts", it.ts).put("title", it.title).put("category", it.category).put("qty", it.qty)
            .put("unit", it.unit).put("total", it.total).put("currency", it.currency).put("reimbursed", it.reimbursed)
            .put("note", it.note ?: org.json.JSONObject.NULL)
    }).toString()

    fun itemsFromJson(json: String): List<InvoiceItem> = runCatching {
        val a = org.json.JSONArray(json)
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            InvoiceItem(
                ts = o.getLong("ts"), title = o.getString("title"), category = o.optString("category"), qty = o.optInt("qty", 1),
                unit = o.getDouble("unit"), total = o.getDouble("total"), currency = o.optString("currency", "EGP"),
                reimbursed = o.optBoolean("reimbursed"), note = if (o.isNull("note")) null else o.optString("note"),
            )
        }
    }.getOrDefault(emptyList())

    fun fromStored(inv: com.nabd.money.data.Invoice) = InvoiceInfo(
        inv.number, inv.company, inv.employee, inv.createdAt, inv.scope, itemsFromJson(inv.itemsJson)
    )

    fun number(now: LocalDateTime = LocalDateTime.now()) =
        "NBD-%04d%02d%02d-%02d%02d".format(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute)

    private fun exportsDir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    // ------------------------------------------------------------------ PDF

    private const val W = 595
    private const val H = 842
    private const val LEFT = 40f
    private const val RIGHT = 555f
    private const val ROW_H = 30f
    private const val FIRST_TABLE_Y = 214f
    private const val NEXT_TABLE_Y = 78f
    private const val BOTTOM = 770f
    private const val FOOTER_BLOCK = 200f

    private val GREEN = Color.parseColor("#0F6B4F")
    private val GREEN_SOFT = Color.parseColor("#E6F4EE")
    private val INK = Color.parseColor("#1B2430")
    private val DIM = Color.parseColor("#6B7686")
    private val LINE = Color.parseColor("#D9DEE5")
    private val ZEBRA = Color.parseColor("#F6F8FA")

    // الأعمدة من اليمين للشمال: م | التاريخ | البيان | الكمية | سعر الوحدة | الإجمالي
    private val colW = floatArrayOf(28f, 78f, 188f, 44f, 82f, 95f)
    private val heads = listOf("م", "التاريخ", "البيان", "الكمية", "سعر الوحدة", "الإجمالي")

    private class Fonts(ctx: Context) {
        val regular: Typeface = ResourcesCompat.getFont(ctx, R.font.ibm_plex_arabic_regular) ?: Typeface.DEFAULT
        val bold: Typeface = ResourcesCompat.getFont(ctx, R.font.ibm_plex_arabic_bold) ?: Typeface.DEFAULT_BOLD
        val medium: Typeface = ResourcesCompat.getFont(ctx, R.font.ibm_plex_arabic_semibold) ?: Typeface.DEFAULT
    }

    private fun tp(face: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face; textSize = size; this.color = color
    }

    /** نص عربي صح الاتجاه جوه صندوق، محاذي يمين/شمال/وسط، وبيتقص لو طويل */
    private fun cell(
        c: Canvas, text: String, paint: TextPaint, left: Float, top: Float, width: Float,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, maxLines: Int = 1,
    ): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save(); c.translate(left, top); layout.draw(c); c.restore()
        return layout.height.toFloat()
    }

    private val RIGHT_ALIGN = Layout.Alignment.ALIGN_NORMAL   // في فقرة RTL: يمين
    private val LEFT_ALIGN = Layout.Alignment.ALIGN_OPPOSITE   // شمال
    private val CENTER = Layout.Alignment.ALIGN_CENTER

    private data class PagePlan(val rows: IntRange, val first: Boolean, val last: Boolean)

    private fun plan(n: Int): List<PagePlan> {
        val perFirst = ((BOTTOM - FIRST_TABLE_Y - ROW_H) / ROW_H).toInt()
        val perNext = ((BOTTOM - NEXT_TABLE_Y - ROW_H) / ROW_H).toInt()
        val pages = mutableListOf<IntRange>()
        var i = 0
        var cap = perFirst
        while (i < n || pages.isEmpty()) {
            val end = minOf(n, i + cap)
            pages += i until end
            i = end
            cap = perNext
            if (i >= n) break
        }
        // مكان إجمالي الفاتورة والتوقيعات: لو مش كفاية في آخر صفحة نعمل صفحة جديدة ليهم
        val lastRows = pages.last().count()
        val lastTop = if (pages.size == 1) FIRST_TABLE_Y else NEXT_TABLE_Y
        val usedBottom = lastTop + ROW_H + lastRows * ROW_H
        val needsExtra = usedBottom + FOOTER_BLOCK > BOTTOM + 40
        val out = pages.mapIndexed { idx, r -> PagePlan(r, idx == 0, idx == pages.lastIndex && !needsExtra) }.toMutableList()
        if (needsExtra) out += PagePlan(IntRange.EMPTY, first = false, last = true)
        return out
    }

    fun pdf(ctx: Context, info: InvoiceInfo): File {
        val f = Fonts(ctx)
        val doc = PdfDocument()
        val pages = plan(info.items.size)
        pages.forEachIndexed { idx, p ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, idx + 1).create())
            drawPage(page.canvas, f, info, p, idx + 1, pages.size)
            doc.finishPage(page)
        }
        val file = File(exportsDir(ctx), "nabd-invoice-${info.number}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** صور الصفحات (للمعاينة جوه التطبيق وللاختبارات) بنفس رسم الـ PDF بالظبط */
    fun renderPages(ctx: Context, info: InvoiceInfo, scale: Float = 1.6f): List<android.graphics.Bitmap> {
        val f = Fonts(ctx)
        val pages = plan(info.items.size)
        return pages.mapIndexed { idx, p ->
            val bmp = android.graphics.Bitmap.createBitmap((W * scale).toInt(), (H * scale).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            c.scale(scale, scale)
            drawPage(c, f, info, p, idx + 1, pages.size)
            bmp
        }
    }

    private fun drawPage(c: Canvas, f: Fonts, info: InvoiceInfo, p: PagePlan, pageNo: Int, pageCount: Int) {
        val white = tp(f.bold, 12f, Color.WHITE)
        var y: Float

        if (p.first) {
            // الهيدر: شريط أخضر بالشعار واسم التطبيق
            val band = RectF(LEFT, 34f, RIGHT, 124f)
            c.drawRoundRect(band, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN })
            // الشعار: دايرة ونبضة
            val cx = RIGHT - 34f
            val cy = 79f
            c.drawCircle(cx, cy, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 40 })
            c.drawCircle(cx, cy, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f })
            val pulse = Path().apply {
                moveTo(cx - 14, cy + 2); lineTo(cx - 7, cy + 2); lineTo(cx - 3, cy - 9); lineTo(cx + 2, cy + 11)
                lineTo(cx + 6, cy - 3); lineTo(cx + 14, cy - 3)
            }
            c.drawPath(pulse, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.6f
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })
            cell(c, "جيبك", tp(f.bold, 28f, Color.WHITE), RIGHT - 70f - 160f, 54f, 160f, RIGHT_ALIGN)
            cell(c, "إدارة فلوسك بذكاء", tp(f.regular, 10f, Color.parseColor("#CFEBDD")), RIGHT - 70f - 160f, 92f, 160f, RIGHT_ALIGN)
            // يسار الهيدر: نوع المستند ورقمه
            cell(c, "فاتورة مصاريف", tp(f.bold, 17f, Color.WHITE), LEFT + 16f, 50f, 190f, LEFT_ALIGN)
            cell(c, "رقم ${info.number}", tp(f.regular, 10f, Color.parseColor("#CFEBDD")), LEFT + 16f, 78f, 190f, LEFT_ALIGN)
            cell(c, "بتاريخ ${Dates.date(info.issuedAt)}", tp(f.regular, 10f, Color.parseColor("#CFEBDD")), LEFT + 16f, 94f, 190f, LEFT_ALIGN)

            // بيانات: الشركة، المقدّم، الفترة
            val label = tp(f.regular, 9f, DIM)
            val value = tp(f.bold, 12f, INK)
            val colWd = (RIGHT - LEFT) / 2f - 10f
            cell(c, "الشركة", label, RIGHT - colWd, 140f, colWd, RIGHT_ALIGN)
            cell(c, info.company.ifBlank { "—" }, value, RIGHT - colWd, 153f, colWd, RIGHT_ALIGN)
            cell(c, "مقدّم الفاتورة", label, RIGHT - colWd, 174f, colWd, RIGHT_ALIGN)
            cell(c, info.employee.ifBlank { "—" }, value, RIGHT - colWd, 187f, colWd, RIGHT_ALIGN)
            val period = if (info.from != null) "${Dates.date(info.from!!)} → ${Dates.date(info.to!!)}" else "—"
            cell(c, "الفترة", label, LEFT, 140f, colWd, RIGHT_ALIGN)
            cell(c, period, value, LEFT, 153f, colWd, RIGHT_ALIGN)
            cell(c, "البنود • ${info.scope}", label, LEFT, 174f, colWd, RIGHT_ALIGN)
            cell(c, "${info.items.size} بند", value, LEFT, 187f, colWd, RIGHT_ALIGN)
            y = FIRST_TABLE_Y
        } else {
            cell(c, "جيبك • فاتورة ${info.number}", tp(f.medium, 10f, DIM), LEFT, 40f, RIGHT - LEFT, RIGHT_ALIGN)
            c.drawLine(LEFT, 60f, RIGHT, 60f, Paint().apply { color = LINE; strokeWidth = 1f })
            y = NEXT_TABLE_Y
        }

        if (p.rows.isEmpty() && !p.first) {
            // صفحة الإجمالي بس: من غير جدول
        } else {
            // رأس الجدول
            c.drawRoundRect(RectF(LEFT, y, RIGHT, y + 24f), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN_SOFT })
            var x = RIGHT
            val hp = tp(f.bold, 10f, GREEN)
            heads.forEachIndexed { i, h ->
                x -= colW[i]
                cell(c, h, hp, x + 3f, y + 5f, colW[i] - 6f, if (i == 2) RIGHT_ALIGN else CENTER)
            }
            y += 24f

            val rowPaint = tp(f.regular, 9.5f, INK)
            val boldPaint = tp(f.bold, 9.5f, INK)
            val dim = tp(f.regular, 8f, DIM)
            val zebra = Paint().apply { color = ZEBRA }
            val line = Paint().apply { color = LINE; strokeWidth = 0.6f }
            for (i in p.rows) {
                val it = info.items[i]
                if ((i - p.rows.first) % 2 == 1) c.drawRect(LEFT, y, RIGHT, y + ROW_H, zebra)
                var cx = RIGHT
                cx -= colW[0]; cell(c, "${i + 1}", rowPaint, cx + 2f, y + 9f, colW[0] - 4f, CENTER)
                cx -= colW[1]; cell(c, Dates.date(it.ts), rowPaint, cx + 2f, y + 9f, colW[1] - 4f, CENTER)
                cx -= colW[2]
                val h = cell(c, it.title, rowPaint, cx + 3f, y + 4f, colW[2] - 6f, RIGHT_ALIGN, maxLines = 2)
                if (h <= 16f) cell(c, it.category, dim, cx + 3f, y + 4f + h + 1f, colW[2] - 6f, RIGHT_ALIGN)
                cx -= colW[3]; cell(c, "${it.qty}", rowPaint, cx + 2f, y + 9f, colW[3] - 4f, CENTER)
                cx -= colW[4]; cell(c, plain(it.unit, it.currency), rowPaint, cx + 2f, y + 9f, colW[4] - 4f, CENTER)
                cx -= colW[5]; cell(c, plain(it.total, it.currency), boldPaint, cx + 2f, y + 9f, colW[5] - 4f, CENTER)
                y += ROW_H
                c.drawLine(LEFT, y, RIGHT, y, line)
            }
        }

        if (p.last) {
            y += 18f
            val boxH = 20f + info.currencies.size * 24f + (if (info.items.any { it.reimbursed }) info.currencies.size * 16f else 0f)
            c.drawRoundRect(RectF(LEFT, y, RIGHT, y + boxH), 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN })
            var ty = y + 10f
            info.currencies.forEach { cur ->
                cell(c, "إجمالي المبلغ المستحق", tp(f.medium, 11f, Color.parseColor("#CFEBDD")), RIGHT - 190f, ty + 3f, 175f, RIGHT_ALIGN)
                cell(c, plain(info.total(cur), cur) + " " + curName(cur), tp(f.bold, 16f, Color.WHITE), LEFT + 14f, ty, 250f, LEFT_ALIGN)
                ty += 24f
                val got = info.received(cur)
                if (got > 0) {
                    cell(c, "اتسلم منه ${plain(got, cur)} • المتبقي ${plain(info.total(cur) - got, cur)}", tp(f.regular, 9f, Color.parseColor("#CFEBDD")), LEFT + 14f, ty - 4f, RIGHT - LEFT - 28f, RIGHT_ALIGN)
                    ty += 16f
                }
            }
            // التوقيعات: تلات خانات متساوية
            val sy = y + boxH + 46f
            val sig = Paint().apply { color = INK; strokeWidth = 0.8f }
            val sp = tp(f.regular, 10f, DIM)
            val gap = 24f
            val wd = (RIGHT - LEFT - 2 * gap) / 3f
            listOf("توقيع مقدّم الفاتورة", "اعتماد المحاسب", "التاريخ").forEachIndexed { i, label ->
                val r = RIGHT - i * (wd + gap)
                c.drawLine(r - wd, sy, r, sy, sig)
                cell(c, label, sp, r - wd, sy + 4f, wd, CENTER)
            }
        }

        // الفوتر
        c.drawLine(LEFT, 806f, RIGHT, 806f, Paint().apply { color = LINE; strokeWidth = 0.8f })
        cell(c, "اتعملت بتطبيق جيبك", tp(f.regular, 9f, DIM), LEFT, 812f, RIGHT - LEFT, CENTER)
        cell(c, "صفحة $pageNo من $pageCount", tp(f.regular, 9f, DIM), LEFT, 812f, 120f, LEFT_ALIGN)
    }

    private fun plain(v: Double, cur: String): String =
        java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(v)

    private fun curName(c: String) = when (c.uppercase()) {
        "EGP" -> "ج.م"; "USD" -> "دولار"; "EUR" -> "يورو"; else -> c
    }

    // ------------------------------------------------------------------ Excel

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it.code >= 32 || it == '\n' }

    private fun col(i: Int) = ('A' + i).toString()

    private fun excelDate(ts: Long): Double {
        val d = Dates.toLocal(ts)
        return d.toLocalDate().toEpochDay() + 25569.0 + (d.toLocalTime().toSecondOfDay() / 86400.0)
    }

    /** ملف Excel حقيقي (.xlsx) بصفحة من اليمين للشمال: عنوان، بيانات، جدول البنود، وإجمالي بمعادلة */
    fun xlsx(ctx: Context, info: InvoiceInfo): File {
        val rows = StringBuilder()
        var r = 1
        fun inl(ref: String, text: String, style: Int) = """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${esc(text)}</t></is></c>"""
        fun num(ref: String, v: Double, style: Int) = """<c r="$ref" s="$style"><v>$v</v></c>"""

        rows.append("""<row r="$r" ht="26" customHeight="1">${inl("A$r", "جيبك — فاتورة مصاريف الشركة", 5)}</row>"""); r++
        fun meta(label: String, value: String) {
            rows.append("""<row r="$r">${inl("A$r", label, 6)}${inl("B$r", value, 2)}</row>"""); r++
        }
        meta("رقم الفاتورة", info.number)
        meta("الشركة", info.company.ifBlank { "—" })
        meta("مقدّم الفاتورة", info.employee.ifBlank { "—" })
        meta("تاريخ الإصدار", Dates.date(info.issuedAt))
        meta("النطاق", info.scope)
        r++

        val heads = listOf("م", "التاريخ", "البيان", "التصنيف", "الكمية", "سعر الوحدة", "الإجمالي", "العملة", "الحالة", "ملاحظة")
        val headRow = r
        rows.append("""<row r="$r" ht="22" customHeight="1">${heads.mapIndexed { i, h -> inl("${col(i)}$r", h, 1) }.joinToString("")}</row>"""); r++
        val first = r
        info.items.forEachIndexed { i, it ->
            rows.append(
                """<row r="$r">""" +
                    num("A$r", (i + 1).toDouble(), 2) +
                    num("B$r", excelDate(it.ts), 7) +
                    inl("C$r", it.title, 2) + inl("D$r", it.category, 2) +
                    num("E$r", it.qty.toDouble(), 2) +
                    num("F$r", it.unit, 3) +
                    // الإجمالي بمعادلة: لو المحاسب غيّر الكمية أو السعر يتحدّث لوحده
                    """<c r="G$r" s="3"><f>E$r*F$r</f><v>${it.total}</v></c>""" +
                    inl("H$r", it.currency, 2) +
                    inl("I$r", if (it.reimbursed) "اتسلم" else "مستحق", 2) +
                    inl("J$r", it.note.orEmpty(), 2) +
                    "</row>"
            ); r++
        }
        val last = r - 1
        r++
        info.currencies.forEach { cur ->
            // مجموع كل عملة لوحدها
            val sum = info.total(cur)
            rows.append(
                """<row r="$r">""" + inl("F$r", "الإجمالي ($cur)", 4) +
                    """<c r="G$r" s="4"><f>SUMIF(H$first:H$last,"$cur",G$first:G$last)</f><v>$sum</v></c></row>"""
            ); r++
            val got = info.received(cur)
            if (got > 0) {
                rows.append("""<row r="$r">${inl("F$r", "اتسلم ($cur)", 2)}${num("G$r", got, 3)}</row>"""); r++
                rows.append("""<row r="$r">${inl("F$r", "المتبقي ($cur)", 2)}${num("G$r", sum - got, 3)}</row>"""); r++
            }
        }
        r++
        rows.append("""<row r="$r">${inl("A$r", "اتعملت بتطبيق جيبك", 6)}</row>""")

        val widths = listOf(6, 16, 34, 18, 9, 14, 14, 9, 11, 30)
        val cols = widths.mapIndexed { i, w -> """<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""" }.joinToString("")
        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"><pane ySplit="$headRow" topLeftCell="A${headRow + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>$cols</cols><sheetData>$rows</sheetData><pageSetup orientation="landscape" paperSize="9"/></worksheet>"""

        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="yyyy\-mm\-dd\ hh:mm"/></numFmts>
<fonts count="5"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="16"/><color rgb="FF0F6B4F"/><name val="Calibri"/></font><font><sz val="10"/><color rgb="FF6B7686"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0F6B4F"/></patternFill></fill></fills>
<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFD9DEE5"/></left><right style="thin"><color rgb="FFD9DEE5"/></right><top style="thin"><color rgb="FFD9DEE5"/></top><bottom style="thin"><color rgb="FFD9DEE5"/></bottom><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="8">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center" wrapText="1"/></xf>
<xf numFmtId="4" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="4" fontId="2" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center"/></xf>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="right"/></xf>
<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
</cellXfs>
</styleSheet>"""

        val file = File(exportsDir(ctx), "nabd-invoice-${info.number}.xlsx")
        ZipOutputStream(FileOutputStream(file)).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView/></bookViews><sheets><sheet name="مصاريف الشركة" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", styles)
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return file
    }

    // ------------------------------------------------------------------ مشاركة وطباعة

    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun share(ctx: Context, file: File, mime: String, subject: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "ابعتها لـ…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** طباعة من شاشة الطباعة بتاعة أندرويد (طابعة أو "حفظ كـ PDF") */
    fun print(ctx: Context, file: File, jobName: String) {
        val pm = ctx.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        pm.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(
                    PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true
                )
            }

            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                runCatching {
                    file.inputStream().use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { cb.onWriteFailed(it.message) }
            }
        }, null)
    }
}
