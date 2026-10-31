package com.nabd.money.agents

import com.nabd.money.data.StockCatalog
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

data class ReceiptLine(
    val name: String,
    val qty: Double,
    /** سعر الوحدة. null لو القراية ماطلعتش سعر، والمستخدم يكمّله */
    val unitPrice: Double?,
    /** الأرقام مش راكبة على بعض أو الاسم ناقص: محتاج عين في المراجعة */
    val doubtful: Boolean = false,
)

data class ReceiptDraft(
    val store: String?,
    val date: LocalDate?,
    val lines: List<ReceiptLine>,
    /** الإجمالي المكتوب على الفاتورة */
    val total: Double?,
    val subtotal: Double? = null,
    val tax: Double? = null,
) {
    val sum get() = lines.sumOf { (it.unitPrice ?: 0.0) * it.qty }

    /** مجموع الأصناف بيساوي الإجمالي المكتوب؟ أحسن دليل إن القراية سليمة. null لو مفيش إجمالي */
    fun matchesTotal(sum: Double = this.sum): Boolean? {
        val targets = listOfNotNull(total, subtotal, total?.minus(tax ?: 0.0))
        if (targets.isEmpty()) return null
        return targets.any { abs(it - sum) <= maxOf(0.5, it * 0.005) }
    }
}

/**
 * بيحوّل نص الفاتورة اللي طالع من OCR لأصناف وأسعار. النص نفسه بييجي بعيوب ثابتة:
 * الأعمدة ممكن تبقى بأي ترتيب (يمين أو شمال)، الأرقام لازقة في بعض أو في الاسم، النقطة العشرية بتقع،
 * والأرقام الهندية (٧٤٨٫٢٥) بتتقري حروف لاتيني (VEA,TO). فبنفهم السطر بمعناه مش بمكانه:
 * الكمية × السعر = الإجمالي هي اللي بتقول مين مين، واللي مايركبش بيتعلّم للمراجعة.
 */
object ReceiptParser {

    private val grandTotal = Regex("""^(?:ال)?صافي|(?<!\p{L})(?:ال)?(?:اجمالي|مجموع|مطلوب)(?!\p{L})|\bgrand\b|\btotal\b|^net\b|\bamount due\b""")
    private val subtotalWord = Regex("""sub\s?-?total|قبل الضريب|المجموع الفرعي|الاجمالي الفرعي|قبل الخصم""")
    /** "القيمة المستحقة": الإجمالي في فواتير بيت الجملة، وساعات في نفس سطر "عدد الأصناف" */
    private val dueWord = Regex("""(?<!\p{L})(?:ال)?مستحق(?:ه)?(?!\p{L})""")
    private val itemCount = Regex("""عدد\s*الاصناف""")
    private val taxWord = Regex("""ضريب|القيمه المضافه|\bvat\b|\btax\b""")
    private val discountWord = Regex("""خصم|تخفيض|\bdiscount\b|\bdisc\b|\bpromo\b""")
    /** سطور مش أصناف، حتى لو فيها أرقام */
    private val skipWord = Regex(
        """(?<!\p{L})(?:ال)?(?:تليفون|تلفون|تليفرن|هاتف|موبايل|موبيل|فاكس|واتس|فاتوره|ايصال|رقم|كاشير|خزينه|تاريخ|وقت|عنوان|شارع|فرع|شكرا|زيارتكم|نقدي|نقدا|كاش|باقي|متبقي|مدفوع|فيزا|بطاقه|ماستر|ميزه|ضريبي|استبدال|استرجاع)(?!\p{L})|سجل تجاري|عدد الاصناف|رقم\d|فرع\p{L}{3,}|التاريخ|شكرا|زيارتكم""" +
            """|\b(?:order|hot\s?line|tel|phone|mob|mobile|fax|www|http|invoice|receipt|bill|cashier|date|time|address|branch|thank|thanks|cash|change|tender|paid|visa|master|mastercard|card|meeza|items?|qty|quantity|price|description|tax\s?id|tin|vat\s?no|pos|terminal|ref)\b"""
    )
    private val headerWord = Regex("""الصنف|البيان|الكميه|السعر|القيمه|\bitem\b|\bqty\b|\bprice\b|\bamount\b|\btotal\b|\bdescription\b""")
    private val dateRx = Regex("""(?<!\d)(\d{1,2})([/\-.])(\d{1,2})\2(\d{4}|\d{2})(?!\d)|(?<!\d)(\d{4})([/\-.])(\d{1,2})\6(\d{1,2})(?!\d)""")
    private val timeRx = Regex("""(?<!\d)\d{1,2}:\d{2}(?::\d{2})?(?!\d)""")
    private val moneyWithDecimals = Regex("""(?<![\d.])\d{1,5}\.\d{2}(?!\d)""")
    private val invisible = Regex("""[‎‏‪-‮⁦-⁩؜﻿]""")

    private val fullNumber = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+[.,]\d{1,3}|\d+""")
    private val gluedMoney = Regex("""\d+\.\d{2}""")
    private val unitWord = Regex("""^(?:ك|كيلو|كجم|كج|جم|جرام|جرامات|لتر|ل|مل|قطعه|قطع|عبوه|علبه|كيس|حبه|kg|k|g|gm|gr|l|lt|ltr|ml|pc|pcs|pack)$""")
    private val quantityWithUnit = Regex("""^\d+(?:[.,]\d+)?(?:كيلو|كجم|كج|جم|جرام|لتر|مل|قطعه|kg|gm|gr|g|l|ltr|ml|pcs?)$""", RegexOption.IGNORE_CASE)
    private val xMark = Regex("""^[xX×*@]$""")
    /** حرف لازق في السعر اتقرى أرقام: "280.0084" */
    private val longDecimals = Regex("""^\d+\.\d{4,}$""")
    private val percent = Regex("""\d+(?:\.\d+)?\s*%""")
    /** كمية موزونة لازقة في الإجمالي: "0.25872.24" يعني 0.258 و72.24 */
    private val gluedWeight = Regex("""^(0\.\d{3})(\d+\.\d{2})$""")
    /** كمية لازقة في السعر بشرطة: "4-20.00" */
    private val dashedQty = Regex("""^(\d{1,2})-(\d+\.\d{2})$""")

    /** الأرقام الهندية لما تتقري حروف لاتيني: الشكل قريب جداً */
    private val lookAlike = mapOf(
        'V' to '7', 'v' to '7', 'E' to '4', 'A' to '8', 'Y' to '2', 'T' to '3', 'O' to '5', 'o' to '5',
        '\\' to '1', '|' to '1', '/' to '1', 'l' to '1', 'I' to '1', '*' to '0', '•' to '0', '.' to '0',
    )
    private val lookAlikeMoney = Regex("""^[VvEAYTOo\\|/lI*•.\d]{1,4},[VvEAYTOo\\|/lI*•.\d]{1,2}$""")

    private data class Tok(val text: String, val value: Double?, val glued: Boolean = false, val repaired: Boolean = false)

    fun parse(text: String): ReceiptDraft {
        val rows = text.lines().map(::clean).filter { it.isNotBlank() }
        val items = mutableListOf<ReceiptLine>()
        var pending: String? = null
        var total: Double? = null
        var subtotal: Double? = null
        var tax: Double? = null
        var ended = false
        // الفاتورة دي بتكتب "خصم" تحت الأصناف؟ يبقى السطر القصير اللي الكلمة باظت فيه خصم برضو
        val discounts = rows.count { discountWord.containsMatchIn(StockCatalog.key(spaced(it))) } >= 2
        // الأرقام العشرية موجودة في الفاتورة؟ يبقى رقم زي 4250 نقطته وقعت مش سعر بأربع تلاف
        val decimals = rows.any { moneyWithDecimals.containsMatchIn(it) }
        // فوق أول صنف بيبقى شعار وعنوان. لو الفاتورة بكسور، أول صنف لازم يبقى فيه مبلغ بكسور
        fun beforeItems(row: String, hasNumbers: Boolean) =
            decimals && items.isEmpty() && hasNumbers && !moneyWithDecimals.containsMatchIn(row)

        for (row in rows) {
            val hay = StockCatalog.key(spaced(row))
            val tokens = tokenize(row)
            val numbers = tokens.mapNotNull { it.value }.filter { it < 10_000_000 }
            val money = moneyOf(tokens)

            if (headerWord.findAll(hay).count() >= 2 && money == null) {
                // اللي قبل راس الجدول اسم المحل، مش صنف مستني سعره
                if (items.isEmpty()) pending = null
                continue
            }
            when {
                subtotalWord.containsMatchIn(hay) -> { if (money != null) subtotal = money; continue }
                dueWord.containsMatchIn(hay) || itemCount.containsMatchIn(hay) -> { if (money != null) { total = money; ended = true }; continue }
                taxWord.containsMatchIn(hay) && !looksLikeItem(hay) -> {
                    // "VAT 14 %" نسبة مش مبلغ
                    moneyOf(tokenize(percent.replace(row, " ")))?.let { tax = it }
                    continue
                }
                grandTotal.containsMatchIn(hay) && !hay.contains("عدد") && tokens.count { it.value == null } <= 3 -> {
                    if (money != null) { total = money; ended = true }
                    continue
                }
                discountWord.containsMatchIn(hay) -> {
                    if (!ended && money != null) takeDiscount(items, money)
                    continue
                }
                skipWord.containsMatchIn(hay) || dateRx.containsMatchIn(row) -> continue
            }
            if (ended) continue
            // كلمة "الإجمالي" نفسها ممكن تتقري غلط، بس رقم بيساوي مجموع اللي فات هو الإجمالي
            if (items.size >= 2 && numbers.size == 1 && pending == null && close(numbers.single(), items.sumOf { (it.unitPrice ?: 0.0) * it.qty })) {
                total = numbers.single(); ended = true
                continue
            }

            if (discounts && pending == null && numbers.size == 1 && tokens.sumOf { letters(it.text) } <= 4 && takeDiscount(items, numbers.single())) continue

            if (beforeItems(row, numbers.isNotEmpty())) continue

            val name = nameOf(tokens)
            // اسم من غير سعر بعد ما الأصناف بدأت: يمكن السعر وقع، فمايضيعش
            fun flush() {
                pending?.let { if (items.isNotEmpty()) items += ReceiptLine(it, 1.0, null, doubtful = true) }
                pending = null
            }
            when {
                // "1x 185.50 عه": سطر كمية، والحرفين زبالة من القراية. لكن "رز 30.00" صنف اسمه قصير
                name != null && numbers.isNotEmpty() && pending != null && letters(name) <= 3 && tokens.any { xMark.matches(it.text) } -> {
                    val cols = columns(tokens) ?: continue
                    items += line(pending!!, cols, tokens, decimals)
                    pending = null
                }
                name != null && numbers.isNotEmpty() -> {
                    val cols = columns(tokens) ?: continue
                    flush()
                    items += line(name, cols, tokens, decimals)
                }
                name != null -> { flush(); pending = name }
                numbers.isNotEmpty() && pending != null -> {
                    val cols = columns(tokens) ?: continue
                    items += line(pending!!, cols, tokens, decimals)
                    pending = null
                }
                money != null && tokens.none { it.value == null && letters(it.text) >= 2 } -> {
                    // سعر من غير اسم مقروء: نسيبه فاضي علشان الإجمالي يفضل مظبوط
                    columns(tokens)?.let { items += line("", it, tokens, decimals) }
                }
            }
        }
        if (!ended) pending?.let { if (items.isNotEmpty()) items += ReceiptLine(it, 1.0, null, doubtful = true) }
        return ReceiptDraft(storeOf(rows), dateOf(rows), items, total, subtotal, tax)
    }

    /** الخصم بينزل من الصنف اللي فوقه. لو أكبر منه يبقى النقطة وقعت ("410" بدل 41.0) */
    private fun takeDiscount(items: MutableList<ReceiptLine>, amount: Double): Boolean {
        val l = items.lastOrNull() ?: return false
        val price = l.unitPrice ?: return false
        val line = price * l.qty
        val cut = listOf(amount, amount / 10, amount / 100).firstOrNull { it < line } ?: return false
        items[items.lastIndex] = l.copy(unitPrice = round2((line - cut) / l.qty), doubtful = l.doubtful || cut != amount)
        return true
    }

    private fun looksLikeItem(hay: String) = hay.split(' ').size > 5

    // علامات الاتجاه بتقع بين عمودين، فبتتحول مسافة مش بتتشال: "210.00‏2020" رقمين مش رقم واحد
    private fun clean(s: String) = unflip(MoneyParser.normalize(s.replace(invisible, " ")).replace('¢', '0').replace(Regex("""\s+"""), " ").trim())

    /**
     * جوه سطر عربي الرقم ساعات بيتقلب نصين: "139.00" بتطلع "0 .139" و"17.50" بتطلع "50 .17"،
     * و"0.338 59.15" بتطلع "338 .059.15". والنقطة ساعات بتتقري شرطة: "113-00".
     */
    private fun unflip(s: String) = s
        .replace(Regex("""(?<=\d)[.,] (?=\d{2}(?!\d))"""), ".")
        .replace(Regex("""(?<![\d.])(\d{3}) \.0(\d+\.\d{2})(?![\d.])"""), "0.$1 $2")
        .replace(Regex("""(?<![\d.])(\d{1,2}) +\.(\d{2,4})(?![\d.])""")) { "${it.groupValues[2]}.${it.groupValues[1].padEnd(2, '0')}" }
        .replace(Regex("""(?<![\d.\-])(\d{1,4})-(\d{2})(?![\d.])"""), "$1.$2")

    private fun letters(s: String) = s.count { it.isLetter() }

    private fun round2(v: Double) = round(v * 100) / 100

    private fun value(s: String): Double? {
        val t = s.replace(",", "")
        val comma = Regex("""^\d+,\d{1,2}$""").matches(s)
        return (if (comma) s.replace(',', '.') else t).toDoubleOrNull()
    }

    /** "العروسة67.00" اسم لازق في سعر: نفصلهم. لكن "1كيلو" و"2.5kg" حجم وجزء من الاسم */
    private fun spaced(row: String) = row
        .replace(Regex("""(?<=\p{L})(?=\d)"""), " ")
        .replace(Regex("""(?<=[\u0600-\u06FF])(?=[A-Za-z\\|*])"""), " ")
        .replace(Regex("""(?<=\d\.\d{2})(?=[\u0600-\u06FF])"""), " ")
        .replace(Regex("""(?<=\d)([xX×*@])(?=\s|\d)"""), " $1 ")
        .replace(Regex("""(?<=\s|^)([xX×*@])(?=\d)"""), "$1 ")

    private fun tokenize(row: String): List<Tok> {
        val out = mutableListOf<Tok>()
        for (raw in spaced(row).split(' ')) {
            val w = raw.trim('-', '=', ':', '_', '"', '\'', '‘', '’', '“', '”', '(', ')', '[', ']', '#', '$', '%', ';', '«', '»').trimEnd('.')
            if (w.isEmpty() || w == "|") continue
            val pair = gluedWeight.matchEntire(w) ?: dashedQty.matchEntire(w)
            when {
                pair != null -> pair.groupValues.drop(1).forEach { out += Tok(it, value(it), glued = true) }
                xMark.matches(w) -> out += Tok(w, null)
                w.length >= 8 && w.all(Char::isDigit) -> Unit // باركود
                quantityWithUnit.matches(w) -> out += Tok(w, null)
                fullNumber.matches(w) -> out += Tok(w, value(w))
                w.all { it.isDigit() || it == '.' || it == ',' } && gluedMoney.findAll(w).count() >= 2 ->
                    gluedMoney.findAll(w).forEach { out += Tok(it.value, value(it.value), glued = true) }
                longDecimals.matches(w) -> out += Tok(w, value(w.substringBeforeLast('.') + "." + w.substringAfterLast('.').take(2)))
                lookAlikeMoney.matches(w) && letters(w) > 0 -> {
                    val digits = w.map { c -> if (c == ',') '.' else lookAlike[c] ?: c }.joinToString("")
                    out += Tok(w, digits.toDoubleOrNull(), repaired = true)
                }
                else -> out += Tok(w, null)
            }
        }
        return out
    }

    /** أكبر مبلغ في السطر (الإجمالي بييجي مع مبالغ زي "الباقي") */
    private fun moneyOf(tokens: List<Tok>): Double? =
        tokens.mapNotNull { it.value }.filter { it < 10_000_000 }.maxOrNull()

    /**
     * الأرقام اللي في طرف السطر هي الأعمدة، واللي جوه الكلام جزء من الاسم (زي "شاي 100 كيس").
     * وفيه فواتير الاسم في النص بين الأعمدة: "1 X 350.00 برجر 350.00".
     */
    private fun columnIndexes(tokens: List<Tok>): Set<Int> {
        fun isCol(i: Int) = tokens[i].value != null || xMark.matches(tokens[i].text)
        if (tokens.isEmpty()) return emptySet()
        val last = tokens.lastIndex
        // زبالة قراية بعد الأرقام: "149.00 1 149.00 طسو عرفه". أطول تتابع فيه مبلغ هو الأعمدة
        var head = -1
        while (head < last && isCol(head + 1)) head++
        var tail = last + 1
        while (tail > 0 && isCol(tail - 1)) tail--
        val front = (0..head).toSet()
        val back = (tail..last).toSet()
        val framed = front.isNotEmpty() && back.isNotEmpty() && head != last
        if (!framed) {
            var run: IntRange? = null
            var i = 0
            while (i <= last) {
                if (!isCol(i)) { i++; continue }
                var j = i
                while (j < last && isCol(j + 1)) j++
                if (j > i && (i..j).any { '.' in tokens[it].text } && (run == null || j - i >= run!!.last - run!!.first)) run = i..j
                i = j + 1
            }
            run?.let { return it.toSet() }
        }
        return when {
            head == last -> tokens.indices.toSet()
            framed ->
                if (front.any { '.' in tokens[it].text || xMark.matches(tokens[it].text) }) front + back else back
            back.isNotEmpty() -> back
            front.isNotEmpty() -> front
            // "سكر 1 كيلو 45": الرقم اللي قبل وحدة جوه الاسم، والكلام ده مش بيأثر هنا لأن الوحدة بتقطع العمود
            else -> tokens.indices.lastOrNull { tokens[it].value != null }?.let(::setOf).orEmpty()
        }
    }

    private fun nameOf(tokens: List<Tok>): String? {
        val r = columnIndexes(tokens)
        val rest = tokens.filterIndexed { i, _ -> i !in r }.map { it.text }
            .filter { t -> t.any(Char::isLetterOrDigit) }
        val name = rest.joinToString(" ").trim()
        return name.takeIf { letters(it) >= 2 }
    }

    private fun columns(tokens: List<Tok>): List<Tok>? =
        columnIndexes(tokens).sorted().map { tokens[it] }.filter { it.value != null }.takeIf { it.isNotEmpty() }

    /** لو النقطة العشرية وقعت (4250 بدل 42.50) */
    private fun dotless(v: Double) = if (v >= 100 && v == round(v)) v / 100 else null

    private fun close(a: Double, b: Double) = abs(a - b) <= maxOf(0.02, b * 0.006)

    /** يدوّر على كمية × سعر = إجمالي بأي ترتيب */
    private fun triple(a: Double, b: Double, c: Double): Triple<Double, Double, Double>? {
        val perms = listOf(Triple(a, b, c), Triple(b, a, c), Triple(a, c, b), Triple(c, a, b), Triple(b, c, a), Triple(c, b, a))
        val fits = perms.filter { (q, u, t) -> q in 0.01..500.0 && close(q * u, t) }
        // الكمية هي الأصغر، و1 × 145 = 145 مش 145 × 1 = 145
        return fits.minByOrNull { (q, u, _) -> q * 10_000 - u }
    }

    private class Split(val qty: Double, val unit: Double, val doubt: Boolean)

    /**
     * في السطر العربي الكمية بتتلزق في أول الإجمالي: "2 x 38.50" و"77.00" بيتقروا "x 38.50" و"277.00".
     * بنجرب نقسم الرقم لكمية + إجمالي يركبوا على سعر الوحدة. الإجمالي أوثق من السعر لو الاتنين قريبين.
     */
    private fun split(glued: String, unit: Double): Split? {
        if (unit <= 0) return null
        for (n in 1..minOf(4, glued.length - 1)) {
            val q = glued.take(n).toDoubleOrNull() ?: continue
            val rest = glued.drop(n)
            val t = rest.toDoubleOrNull() ?: continue
            if (q > 0 && close(q * unit, t)) return Split(q, round2(t / q), false)
            if (close(unit, t)) return Split(1.0, t, true)
            // رقم واحد اتقرى غلط في الإجمالي: 6 × 6.00 واتقرت "646.00"
            val expected = "%.2f".format(java.util.Locale.US, q * unit)
            if (q > 0 && q == round(q) && '.' in rest && rest.length == expected.length && rest.indices.count { rest[it] != expected[it] } == 1)
                return Split(q, unit, true)
        }
        return null
    }

    /**
     * بعض المطاعم بتطبع الكمية من غير كسور: "0 X 350.00 = 196.00" و"1 X 350.00 = 386.75".
     * يبقى اللي اتوزن فعلاً هو الإجمالي ÷ السعر. بيرجع الكمية والسعر.
     */
    private fun cutQuantity(a: Double, b: Double, c: Double): Pair<Double, Double>? =
        listOf(Triple(a, b, c), Triple(a, c, b), Triple(b, a, c), Triple(b, c, a), Triple(c, a, b), Triple(c, b, a))
            .firstOrNull { (q, u, t) -> q == round(q) && q <= 50 && u > 0 && t / u > q + 0.001 && t / u < q + 1 }
            ?.let { (_, u, t) -> round(t / u * 1000) / 1000 to u }

    private fun line(rawName: String, allCols: List<Tok>, tokens: List<Tok>, decimals: Boolean): ReceiptLine {
        // أكتر من 3 أرقام: اللي زيادة جزء من الاسم ("نور ملح 700 17.50 1 17.50"). الأعمدة هي اللي بتركب على بعض
        val fitting = allCols.windowed(3).firstOrNull { (a, b, c) -> triple(a.value!!, b.value!!, c.value!!) != null }
        val cols = fitting ?: (if (allCols.size > 3 && '.' !in allCols.last().text) allCols.dropLast(1) else allCols).takeLast(3)
        var name = (listOf(rawName) + allCols.filter { it !in cols }.map { it.text }).filter { it.isNotEmpty() }.joinToString(" ")
        val shaky = cols.any { it.repaired } || name.isEmpty()
        val hasX = tokens.any { xMark.matches(it.text) }
        val v = cols.map { it.value!! }
        val noDot = cols.filter { '.' !in it.text && ',' !in it.text }.map { it.value!! }
        fun make(q: Double, u: Double, doubt: Boolean = false): ReceiptLine {
            val lost = decimals && u >= 1000 && u in noDot
            val unit = round2(if (lost) u / 100 else u).takeIf { it > 0 }
            return ReceiptLine(name, q, unit, doubtful = doubt || shaky || lost || unit == null)
        }

        if (v.size >= 3) {
            val last3 = v.takeLast(3)
            triple(last3[0], last3[1], last3[2])?.let { (q, u, _) -> return make(q, u) }
            // الإجمالي اتقلب: "69.00 1 0.69" و"5.00 2 0.10"
            for (i in 0..2) if (last3[i] in 0.001..0.999) for (f in listOf(100.0, 1000.0)) {
                val alt = last3.toMutableList().also { it[i] = round2(last3[i] * f) }
                triple(alt[0], alt[1], alt[2])?.let { (q, u, _) -> return make(q, u) }
            }
            // نقطة عشرية وقعت من واحد منهم: 4 × 42.50 = 170.00
            fun dotless(i: Int) = cols.takeLast(3)[i].takeIf { '.' !in it.text && ',' !in it.text }?.value?.let(::dotless)
            for (i in 0..2) {
                val fixed = dotless(i) ?: continue
                val alt = last3.toMutableList().also { it[i] = fixed }
                triple(alt[0], alt[1], alt[2])?.let { (q, u, _) -> return make(q, u, doubt = true) }
            }
            for (i in 0..2) for (j in 0..2) if (i != j) {
                val a = dotless(i) ?: continue
                val b = dotless(j) ?: continue
                val alt = last3.toMutableList().also { it[i] = a; it[j] = b }
                triple(alt[0], alt[1], alt[2])?.let { (q, u, _) -> return make(q, u, doubt = true) }
            }
            if (hasX) cutQuantity(last3[0], last3[1], last3[2])?.let { (q, u) -> return make(q, u) }
            // "24 33.00 143.00": الكمية لازقة في الإجمالي، والرقم الأولاني من الاسم
            if ('.' !in cols[0].text) split(cols[2].text, last3[1])?.let { s ->
                name = "$name ${cols[0].text}".trim()
                return make(s.qty, s.unit, s.doubt)
            }
            // السعر والإجمالي متساويين يبقى قطعة واحدة، حتى لو الكمية اتقرت غلط ("41x 92.00 92.00")
            for (i in 0..2) for (j in i + 1..2) if (close(last3[i], last3[j])) return make(1.0, last3[i], doubt = true)
            // الكمية اتقرت غلط بس السعر والإجمالي سليمين: "4 20.00 60.00" = 3 × 20
            val money = cols.filter { '.' in it.text }.map { it.value!! }
            if (money.size == 2 && money.min() > 0) {
                val ratio = money.max() / money.min()
                if (ratio >= 2 && abs(ratio - round(ratio)) < 0.01 && ratio <= 100) return make(round(ratio), money.min(), doubt = true)
            }
            if (money.size == 2 && money.min() > 0) {
                // رقم ورا مبلغين من غير نقطة بيبقى من الاسم: "43.00 33.00 24"
                if ('.' !in cols[2].text && !hasX) {
                    name = "$name ${cols[2].text}".trim()
                    return make(1.0, money.min(), doubt = true)
                }
                // الكمية اتقرت غلط في فاتورة X: "9 X 540.00 = 326.70"، والإجمالي ÷ السعر أصدق
                if (hasX) {
                    val mark = tokens.indexOfFirst { xMark.matches(it.text) }
                    val unit = cols.filter { '.' in it.text }.minBy { abs(tokens.indexOf(it) - mark) }.value!!
                    val other = money.firstOrNull { it != unit }
                    if (other != null && other < unit) return make(round(other / unit * 1000) / 1000, unit, doubt = true)
                }
            }
            // مفيش تركيبة ماشية: الكمية الصغيرة × السعر الجنبها، ونسيب المراجعة تحكم
            val q = last3.minOrNull()!!
            val rest = last3.toMutableList().also { it.remove(q) }
            if (q < 1 && '.' in cols[last3.indexOf(q)].text) return make(q, rest.max(), doubt = true)
            return if (q <= 100 && q == round(q)) make(q, rest.min(), doubt = true) else make(1.0, money.maxOrNull() ?: last3.max(), doubt = true)
        }
        if (v.size == 2) {
            val (a, b) = v
            // "0.338" من غير سعر مقروء: الإجمالي ÷ الوزن
            if (minOf(a, b) < 1 && '.' in cols[v.indexOf(minOf(a, b))].text) return make(minOf(a, b), maxOf(a, b) / minOf(a, b), doubt = true)
            // الإجمالي وقعت نقطته: "220 22.00"
            if ('.' !in cols[0].text && '.' in cols[1].text && (close(a / 10, b) || close(a / 100, b))) return make(1.0, b, doubt = true)
            if ('.' in cols[0].text) split(cols.last().text, a)?.let { return make(it.qty, it.unit, it.doubt) }
            if (hasX) {
                split(cols.first().text, b)?.let { return make(it.qty, it.unit, it.doubt) }
                if ('.' in cols[0].text && '.' in cols[1].text) {
                    // الكمية ضاعت: اللي جنب علامة X هو السعر
                    val mark = tokens.indexOfFirst { xMark.matches(it.text) }
                    val unitFirst = abs(tokens.indexOf(cols[0]) - mark) <= abs(tokens.indexOf(cols[1]) - mark)
                    val (u, t) = if (unitFirst) a to b else b to a
                    return make(round(t / u * 1000) / 1000, u, doubt = true)
                }
                val q = minOf(a, b)
                return make(q, maxOf(a, b), doubt = q != round(q))
            }
            if (close(a, b)) return make(1.0, a)
            val small = minOf(a, b)
            val big = maxOf(a, b)
            val ratio = big / small
            // سعر وإجمالي: 38.50 و 77.00 = 2 × 38.50
            val dotted = '.' in cols[0].text && '.' in cols[1].text
            if (dotted && abs(ratio - round(ratio)) < 0.01 && ratio <= 100) return make(round(ratio), small)
            // كمية وسعر: 3 و 24.00
            if (small == round(small) && small <= 50 && (big != round(big) || !dotted)) return make(small, big)
            return make(1.0, big, doubt = true)
        }
        val only = v.single()
        val glued = tokens.size >= 2 && only == round(only) && only < 10
        return make(1.0, only, doubt = glued)
    }

    /** أول سطر كلام في الفاتورة. لو الشعار طلّع زبالة لاتيني فوقه أو جنبه، الكلام العربي أولى */
    private fun storeOf(rows: List<String>): String? {
        fun header(r: String): Boolean {
            val k = StockCatalog.key(r)
            return skipWord.containsMatchIn(k) || headerWord.containsMatchIn(k)
        }
        fun arabicWords(r: String) = r.split(' ').filter { w -> w.length >= 2 && w.all { it in '\u0600'..'\u06FF' } }.joinToString(" ")
        val arabic = rows.take(6).firstOrNull { !header(it) && !moneyWithDecimals.containsMatchIn(it) && letters(arabicWords(it)) >= 5 }
        if (arabic != null) return arabicWords(arabic).take(40)
        return rows.take(4).firstOrNull { r -> letters(r) >= 3 && letters(r) >= r.length / 2 && !header(r) && !r.any(Char::isDigit) }?.take(40)
    }

    private fun dateOf(rows: List<String>): LocalDate? {
        val today = LocalDate.now()
        for (r in rows) {
            val m = dateRx.find(timeRx.replace(r, " ")) ?: continue
            val g = m.groupValues
            val d = runCatching {
                if (g[5].isNotEmpty()) LocalDate.of(g[5].toInt(), g[7].toInt(), g[8].toInt())
                else LocalDate.of(g[4].toInt().let { if (it < 100) 2000 + it else it }, g[3].toInt(), g[1].toInt())
            }.getOrNull() ?: continue
            if (!d.isAfter(today.plusDays(1)) && d.isAfter(today.minusYears(3))) return d
        }
        return null
    }
}
