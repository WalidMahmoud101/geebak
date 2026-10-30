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
    private val taxWord = Regex("""ضريب|القيمه المضافه|\bvat\b|\btax\b""")
    private val discountWord = Regex("""خصم|تخفيض|\bdiscount\b|\bdisc\b|\bpromo\b""")
    /** سطور مش أصناف، حتى لو فيها أرقام */
    private val skipWord = Regex(
        """(?<!\p{L})(?:ال)?(?:تليفون|تلفون|تليفرن|هاتف|موبايل|موبيل|فاكس|واتس|فاتوره|ايصال|رقم|كاشير|خزينه|تاريخ|وقت|عنوان|شارع|فرع|شكرا|زيارتكم|نقدي|نقدا|كاش|باقي|متبقي|مدفوع|فيزا|بطاقه|ماستر|ميزه|ضريبي|استبدال|استرجاع)(?!\p{L})|سجل تجاري|عدد الاصناف|رقم\d|فرع\p{L}{3,}|التاريخ|شكرا|زيارتكم""" +
            """|\b(?:tel|phone|mob|mobile|fax|www|http|invoice|receipt|bill|cashier|date|time|address|branch|thank|thanks|cash|change|tender|paid|visa|master|mastercard|card|meeza|items?|qty|quantity|price|description|tax\s?id|tin|vat\s?no|pos|terminal|ref)\b"""
    )
    private val headerWord = Regex("""الصنف|البيان|الكميه|السعر|القيمه|\bitem\b|\bqty\b|\bprice\b|\bamount\b|\btotal\b|\bdescription\b""")
    private val dateRx = Regex("""(?<!\d)(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})(?!\d)|(?<!\d)(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})(?!\d)""")
    private val timeRx = Regex("""(?<!\d)\d{1,2}:\d{2}(?::\d{2})?(?!\d)""")
    private val moneyWithDecimals = Regex("""(?<![\d.])\d{1,5}\.\d{2}(?!\d)""")
    private val invisible = Regex("""[‎‏‪-‮⁦-⁩؜﻿]""")

    private val fullNumber = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+[.,]\d{1,3}|\d+""")
    private val gluedMoney = Regex("""\d+\.\d{2}""")
    private val unitWord = Regex("""^(?:ك|كيلو|كجم|كج|جم|جرام|جرامات|لتر|ل|مل|قطعه|قطع|عبوه|علبه|كيس|حبه|kg|k|g|gm|gr|l|lt|ltr|ml|pc|pcs|pack)$""")
    private val quantityWithUnit = Regex("""^\d+(?:[.,]\d+)?(?:كيلو|كجم|كج|جم|جرام|لتر|مل|قطعه|kg|gm|gr|g|l|ltr|ml|pcs?)$""", RegexOption.IGNORE_CASE)
    private val xMark = Regex("""^[xX×*@]$""")

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
        // الأرقام العشرية موجودة في الفاتورة؟ يبقى رقم زي 4250 نقطته وقعت مش سعر بأربع تلاف
        val decimals = rows.any { moneyWithDecimals.containsMatchIn(it) }

        for (row in rows) {
            val hay = StockCatalog.key(spaced(row))
            val tokens = tokenize(row)
            val numbers = tokens.mapNotNull { it.value }.filter { it < 10_000_000 }
            val money = moneyOf(tokens)

            if (headerWord.findAll(hay).count() >= 2 && money == null) continue
            when {
                subtotalWord.containsMatchIn(hay) -> { if (money != null) subtotal = money; continue }
                taxWord.containsMatchIn(hay) && !looksLikeItem(hay) -> { if (money != null) tax = money; continue }
                grandTotal.containsMatchIn(hay) && !hay.contains("عدد") && tokens.count { it.value == null } <= 3 -> {
                    if (money != null) { total = money; ended = true }
                    continue
                }
                discountWord.containsMatchIn(hay) -> {
                    if (!ended && money != null) items.lastOrNull()?.let { l ->
                        val line = (l.unitPrice ?: 0.0) * l.qty
                        if (money < line) items[items.lastIndex] = l.copy(unitPrice = round2((line - money) / l.qty))
                    }
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

    private fun looksLikeItem(hay: String) = hay.split(' ').size > 5

    // علامات الاتجاه بتقع بين عمودين، فبتتحول مسافة مش بتتشال: "210.00‏2020" رقمين مش رقم واحد
    private fun clean(s: String) = MoneyParser.normalize(s.replace(invisible, " ")).replace('¢', '0').replace(Regex("""\s+"""), " ").trim()

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
        .replace(Regex("""(?<=\d)([xX×*@])(?=\s|\d)"""), " $1 ")
        .replace(Regex("""(?<=\s|^)([xX×*@])(?=\d)"""), "$1 ")

    private fun tokenize(row: String): List<Tok> {
        val out = mutableListOf<Tok>()
        for (raw in spaced(row).split(' ')) {
            val w = raw.trim('-', '=', ':', '_', '"', '\'', '‘', '’', '“', '”', '(', ')', '[', ']', '#', '$', '%', ';')
            if (w.isEmpty()) continue
            when {
                xMark.matches(w) -> out += Tok(w, null)
                w.length >= 8 && w.all(Char::isDigit) -> Unit // باركود
                quantityWithUnit.matches(w) -> out += Tok(w, null)
                fullNumber.matches(w) -> out += Tok(w, value(w))
                w.all { it.isDigit() || it == '.' || it == ',' } && gluedMoney.findAll(w).count() >= 2 ->
                    gluedMoney.findAll(w).forEach { out += Tok(it.value, value(it.value), glued = true) }
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

    /** الأرقام اللي في طرف السطر هي الأعمدة، واللي جوه الكلام جزء من الاسم (زي "شاي 100 كيس") */
    private fun columnRange(tokens: List<Tok>): IntRange? {
        fun isCol(i: Int) = tokens[i].value != null || xMark.matches(tokens[i].text)
        var end = tokens.lastIndex
        if (end >= 0 && isCol(end)) {
            var start = end
            while (start > 0 && isCol(start - 1)) start--
            // "سكر 1 كيلو 45": الرقم اللي قبل وحدة جوه الاسم، والكلام ده مش بيأثر هنا لأن الوحدة بتقطع العمود
            return start..end
        }
        if (tokens.isNotEmpty() && isCol(0)) {
            end = 0
            while (end < tokens.lastIndex && isCol(end + 1)) end++
            return 0..end
        }
        return tokens.indices.lastOrNull { tokens[it].value != null }?.let { it..it }
    }

    private fun nameOf(tokens: List<Tok>): String? {
        val r = columnRange(tokens)
        val rest = tokens.filterIndexed { i, _ -> r == null || i !in r }.map { it.text }
            .filter { t -> t.any(Char::isLetterOrDigit) }
        val name = rest.joinToString(" ").trim()
        return name.takeIf { letters(it) >= 2 }
    }

    private fun columns(tokens: List<Tok>): List<Tok>? =
        columnRange(tokens)?.let { r -> tokens.slice(r).filter { it.value != null } }?.takeIf { it.isNotEmpty() }

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

    /**
     * في السطر العربي الكمية بتتلزق في أول الإجمالي: "2 x 38.50" و"77.00" بيتقروا "x 38.50" و"277.00".
     * بنجرب نقسم الرقم لكمية + إجمالي يركبوا على سعر الوحدة. بيرجع الكمية، وهل محتاجة مراجعة.
     */
    private fun split(glued: String, unit: Double): Pair<Double, Boolean>? {
        for (n in 1..minOf(4, glued.length - 1)) {
            val q = glued.take(n).toDoubleOrNull() ?: continue
            val t = glued.drop(n).toDoubleOrNull() ?: continue
            if (close(q * unit, t)) return q to false
            if (close(unit, t)) return 1.0 to true
        }
        return null
    }

    private fun line(name: String, cols: List<Tok>, tokens: List<Tok>, decimals: Boolean): ReceiptLine {
        val shaky = cols.any { it.repaired } || name.isEmpty()
        val hasX = tokens.any { xMark.matches(it.text) }
        val v = cols.map { it.value!! }
        val noDot = cols.filter { '.' !in it.text && ',' !in it.text }.map { it.value!! }
        fun make(q: Double, u: Double, doubt: Boolean = false): ReceiptLine {
            val lost = decimals && u >= 1000 && u in noDot
            return ReceiptLine(name, q, round2(if (lost) u / 100 else u), doubtful = doubt || shaky || lost)
        }

        if (v.size >= 3) {
            val last3 = v.takeLast(3)
            triple(last3[0], last3[1], last3[2])?.let { (q, u, _) -> return make(q, u) }
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
            // السعر والإجمالي متساويين يبقى قطعة واحدة، حتى لو الكمية اتقرت غلط ("41x 92.00 92.00")
            for (i in 0..2) for (j in i + 1..2) if (close(last3[i], last3[j])) return make(1.0, last3[i], doubt = true)
            // مفيش تركيبة ماشية: الكمية الصغيرة × السعر الجنبها، ونسيب المراجعة تحكم
            val q = last3.minOrNull()!!
            val rest = last3.toMutableList().also { it.remove(q) }
            return if (q <= 100 && q == round(q)) make(q, rest.min(), doubt = true) else make(1.0, last3.max(), doubt = true)
        }
        if (v.size == 2) {
            val (a, b) = v
            if (hasX) {
                split(cols.last().text, a)?.let { (q, doubt) -> return make(q, a, doubt) }
                split(cols.first().text, b)?.let { (q, doubt) -> return make(q, b, doubt) }
                val q = minOf(a, b)
                return make(q, maxOf(a, b), doubt = q != round(q))
            }
            if (close(a, b)) return make(1.0, a)
            val small = minOf(a, b)
            val big = maxOf(a, b)
            val ratio = big / small
            // سعر وإجمالي: 38.50 و 77.00 = 2 × 38.50
            if (abs(ratio - round(ratio)) < 0.01 && ratio <= 100) return make(round(ratio), small)
            // كمية وسعر: 3 و 24.00
            if (small == round(small) && small <= 50 && big != round(big)) return make(small, big)
            return make(1.0, big, doubt = true)
        }
        val only = v.single()
        val glued = tokens.size >= 2 && only == round(only) && only < 10
        return make(1.0, only, doubt = glued)
    }

    private fun storeOf(rows: List<String>): String? = rows.take(4).firstOrNull { r ->
        val k = StockCatalog.key(r)
        letters(r) >= 3 && letters(r) >= r.length / 2 && !skipWord.containsMatchIn(k) && !headerWord.containsMatchIn(k) && !r.any(Char::isDigit)
    }?.take(40)

    private fun dateOf(rows: List<String>): LocalDate? {
        val today = LocalDate.now()
        for (r in rows) {
            val m = dateRx.find(timeRx.replace(r, " ")) ?: continue
            val g = m.groupValues
            val d = runCatching {
                if (g[4].isNotEmpty()) LocalDate.of(g[4].toInt(), g[5].toInt(), g[6].toInt())
                else LocalDate.of(g[3].toInt().let { if (it < 100) 2000 + it else it }, g[2].toInt(), g[1].toInt())
            }.getOrNull() ?: continue
            if (!d.isAfter(today.plusDays(1)) && d.isAfter(today.minusYears(3))) return d
        }
        return null
    }
}
