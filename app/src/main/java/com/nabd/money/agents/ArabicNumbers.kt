package com.nabd.money.agents

/**
 * بيحول الأرقام المنطوقة بالعامية المصرية والفصحى لأرقام:
 * "خمسمية" → 500، "ألف وميتين وخمسين" → 1250، "ألفين ونص" → 2500، "تلات تلاف" → 3000، "15 ألف" → 15000.
 */
object ArabicNumbers {

    private enum class Kind { SMALL, HUNDRED, SCALE, ADD_SCALE, HALF, QUARTER, DIGIT }

    private data class W(val kind: Kind, val v: Double)

    /** توحيد الحروف: أ إ آ → ا، ة → ه، ى → ي */
    fun norm(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ة' -> 'ه'
                'ى' -> 'ي'
                'ـ' -> null
                else -> c
            } ?: continue
        )
    }.lowercase()

    private val dict: Map<String, W> = buildMap {
        fun put(kind: Kind, v: Double, vararg words: String) = words.forEach { put(norm(it), W(kind, v)) }
        put(Kind.SMALL, 1.0, "واحد", "واحده", "واحدة")
        put(Kind.SMALL, 2.0, "اتنين", "اثنين", "اثنان", "إثنين")
        put(Kind.SMALL, 3.0, "تلاتة", "تلاته", "تلات", "ثلاثة", "ثلاث")
        put(Kind.SMALL, 4.0, "اربعة", "أربعة", "اربع", "أربع", "اربعه")
        put(Kind.SMALL, 5.0, "خمسة", "خمسه", "خمس")
        put(Kind.SMALL, 6.0, "ستة", "سته", "ستّة")
        put(Kind.SMALL, 7.0, "سبعة", "سبعه", "سبع")
        put(Kind.SMALL, 8.0, "تمانية", "تمانيه", "ثمانية", "ثماني", "تمان")
        put(Kind.SMALL, 9.0, "تسعة", "تسعه", "تسع")
        put(Kind.SMALL, 10.0, "عشرة", "عشره", "عشر")
        put(Kind.SMALL, 11.0, "حداشر", "احداشر", "إحدى عشر", "احدعشر")
        put(Kind.SMALL, 12.0, "اتناشر", "اثناشر", "اثنا عشر", "اثناعشر")
        put(Kind.SMALL, 13.0, "تلتاشر", "ثلاثة عشر")
        put(Kind.SMALL, 14.0, "اربعتاشر", "أربعتاشر", "اربعطاشر")
        put(Kind.SMALL, 15.0, "خمستاشر", "خمسطاشر")
        put(Kind.SMALL, 16.0, "ستاشر", "سطاشر")
        put(Kind.SMALL, 17.0, "سبعتاشر", "سبعطاشر")
        put(Kind.SMALL, 18.0, "تمنتاشر", "تمانتاشر", "تمنطاشر")
        put(Kind.SMALL, 19.0, "تسعتاشر", "تسعطاشر")
        put(Kind.SMALL, 20.0, "عشرين", "عشرون")
        put(Kind.SMALL, 30.0, "تلاتين", "ثلاثين", "ثلاثون")
        put(Kind.SMALL, 40.0, "اربعين", "أربعين", "أربعون")
        put(Kind.SMALL, 50.0, "خمسين", "خمسون")
        put(Kind.SMALL, 60.0, "ستين", "ستون")
        put(Kind.SMALL, 70.0, "سبعين", "سبعون")
        put(Kind.SMALL, 80.0, "تمانين", "ثمانين", "ثمانون")
        put(Kind.SMALL, 90.0, "تسعين", "تسعون")
        put(Kind.HUNDRED, 100.0, "مية", "ميه", "مائة", "مئة", "ميت")
        put(Kind.HUNDRED, 200.0, "ميتين", "مئتين", "مائتين", "مئتان")
        put(Kind.HUNDRED, 300.0, "تلتمية", "تلاتمية", "تلتميه", "ثلاثمائة", "ثلاثمية")
        put(Kind.HUNDRED, 400.0, "ربعمية", "اربعمية", "ربعميه", "أربعمائة", "اربعمائة")
        put(Kind.HUNDRED, 500.0, "خمسمية", "خمسميه", "خمسمائة")
        put(Kind.HUNDRED, 600.0, "ستمية", "ستميه", "ستمائة", "سيتمية")
        put(Kind.HUNDRED, 700.0, "سبعمية", "سبعميه", "سبعمائة")
        put(Kind.HUNDRED, 800.0, "تمنمية", "تمنميه", "ثمانمائة", "تمانمية")
        put(Kind.HUNDRED, 900.0, "تسعمية", "تسعميه", "تسعمائة")
        put(Kind.SCALE, 1_000.0, "الف", "ألف", "آلاف", "الاف", "ألاف", "تلاف", "تالاف", "آلف")
        put(Kind.SCALE, 1_000_000.0, "مليون", "ملايين")
        put(Kind.SCALE, 1_000_000_000.0, "مليار")
        put(Kind.ADD_SCALE, 2_000.0, "الفين", "ألفين", "ألفان")
        put(Kind.ADD_SCALE, 2_000_000.0, "مليونين")
        put(Kind.HALF, 0.5, "نص", "نصف")
        put(Kind.QUARTER, 0.25, "ربع")
    }

    private val digitRx = Regex("""\d+(?:\.\d+)?""")

    private fun lookupPlain(n: String): W? =
        dict[n] ?: if (digitRx.matches(n)) W(Kind.DIGIT, n.toDouble()) else null

    /** بيرجع الكلمة + هل كانت لازقة فيها "و" في الأول */
    private fun lookup(token: String): Pair<W, Boolean>? {
        val n = norm(token)
        lookupPlain(n)?.let { return it to false }
        if (n.length > 1 && n[0] == 'و') lookupPlain(n.substring(1))?.let { return it to true }
        if (n.length > 1 && n[0] == 'ب') lookupPlain(n.substring(1))?.let { return it to false }
        return null
    }

    private fun value(ws: List<W>): Double {
        var total = 0.0
        var cur = 0.0
        var last = 1.0
        for (w in ws) when (w.kind) {
            Kind.SMALL, Kind.DIGIT -> { cur += w.v; last = 1.0 }
            Kind.HUNDRED -> { cur += w.v; last = 100.0 }
            Kind.SCALE -> { total += (if (cur == 0.0) 1.0 else cur) * w.v; cur = 0.0; last = w.v }
            Kind.ADD_SCALE -> { total += cur + w.v; cur = 0.0; last = w.v / 2 }
            Kind.HALF -> total += last * 0.5
            Kind.QUARTER -> total += last * 0.25
        }
        return total + cur
    }

    private fun fmt(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    /** بيستبدل كل رقم مكتوب بالكلام برقم، والباقي زي ما هو */
    fun toDigits(text: String): String {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val first = lookup(tokens[i])
            // الجروب لازم يبدأ برقم حقيقي (مش "نص" أو "ربع" لوحدهم)
            if (first == null || first.first.kind == Kind.HALF || first.first.kind == Kind.QUARTER) {
                out += tokens[i]; i++; continue
            }
            val group = mutableListOf(first.first)
            var j = i + 1
            while (j < tokens.size) {
                var k = j
                var wa = false
                if (tokens[k] == "و") {
                    if (k + 1 >= tokens.size) break
                    wa = true; k++
                }
                val (w, prefixed) = lookup(tokens[k]) ?: break
                wa = wa || prefixed
                val ok = when (w.kind) {
                    Kind.SCALE -> !wa                     // "15 ألف"، "تلات تلاف"
                    Kind.HALF, Kind.QUARTER -> wa         // "ألف ونص"
                    else -> wa && joinable(group.last(), w) // "ألف وخمسمية"
                }
                if (!ok) break
                group += w
                j = k + 1
            }
            if (first.second) out += "و"
            out += fmt(value(group))
            i = j
        }
        return out.joinToString(" ")
    }

    private fun place(w: W) = w.v

    /**
     * بنجمع لو الرقم الجاي أصغر من اللي قبله ("ألف و500"، "مية وخمسين")،
     * أو آحاد قبل عشرات ("خمسة وعشرين" = 25). رقمين بالأرقام جنب بعض = مبلغين منفصلين.
     */
    private fun joinable(last: W, w: W): Boolean {
        if (w.kind == Kind.DIGIT && last.kind == Kind.DIGIT) return false
        if (last.kind == Kind.SMALL && last.v < 10 && w.kind == Kind.SMALL && w.v >= 20 && w.v % 10 == 0.0) return true
        return w.v < place(last)
    }
}
