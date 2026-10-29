package com.nabd.money.agents

import com.nabd.money.data.Direction

data class ParsedMoney(
    val amount: Double?,
    val currency: String,
    val direction: Direction?,
    val balance: Double?,
    val merchant: String?,
    val cardLast4: String?,
    /** الرقم اللي بعد "الحد المتاح" في كارت ائتمان: ده حد البنك مش فلوسك */
    val isCredit: Boolean = false,
)

/**
 * بيفهم رسائل البنوك والمحافظ بالعربي والإنجليزي:
 * المبلغ والعملة، خصم ولا إضافة، الرصيد الباقي، التاجر، وآخر 4 أرقام من الكارت.
 */
object MoneyParser {

    private const val CUR =
        """EGP|E\.G\.P|L\.E\.?|LE|USDT|USDC|USD|US\$|EUR|GBP|SAR|AED|BTC|ETH|BNB|SOL|\$|€|جنيهات|جني[هة](?:اً|ا)?(?:\s*مصري)?|ج\.\s?م\.?|جم|ج(?![\p{L}])|دولار"""
    private const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?"""

    private val curFirst =
        Regex("""(?<![A-Za-z])($CUR)(?![A-Za-z])\s?(?<![\d.,])($NUM)""", RegexOption.IGNORE_CASE)
    private val numFirst =
        Regex("""(?<![\d.,])($NUM)\s?(?<![A-Za-z])($CUR)(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    private val fallbackAmount =
        Regex("""(?:بمبلغ|مبلغ|بقيمة|قيمة|amount(?:\s+of)?|amt)\s*:?\s*($NUM)""", RegexOption.IGNORE_CASE)

    private val otp = Regex(
        """\b(otp|one[- ]time|verification code|passcode|is your code|cvv|pin)\b|رمز التحقق|كود التحقق|رمز التفعيل|كود التفعيل|كلمة المرور لمرة واحدة|""" +
            // رموز سرية: "هذا الكود سري"، الرقم السري لماكينة ATM، بيانات الكارت الافتراضي (فيها حد السحب مش عملية)
            """الكود سر[يى]|هذا الكود|الرقم السر[يى]|كلمة السر|بيانات (?:ال)?كارت|بيانات (?:ال)?بطاق[هة]|برجاء عدم مشاركة|يرجى عدم مشاركة|يرجي عدم مشاركة|عدم مشاركته|do not share|confidential""",
        RegexOption.IGNORE_CASE
    )

    /** كلام الإعلانات والعروض: "استمتع بكاش باك لحد 100 جنيه"، "اكسب"، "خصم 20%"… */
    private val promo = Regex(
        """(?<![\p{L}])[وفبتينسل]?(?:كسبت|مبروك|استمتع|اكسب|إكسب|اربح|إربح|هدية|هديه|عرض|عروض|لحد|لغاية|لغايه|حتى|يصل|بيوصل|استخدم|إستخدم|اطلب|حمّل|حمل|نزّل|نزل|اتصل|اشترك|سارع|فرصة|فرصه|كوبون|برومو|ماتفوتش|متفوتش)(?![\p{L}])""" +
            """|لفترة محدودة|لفتره محدوده|متاح الآن|%|up to|\boffer|\benjoy|\bwin\b|\bpromo|don'?t miss|\bdownload|limited time|\bsubscribe|\bdial\b""",
        RegexOption.IGNORE_CASE
    )
    /** علامات إن العملية حصلت فعلاً، علشان الرسالة الحقيقية اللي في آخرها إعلان ماتضيعش */
    private val completed = Regex(
        """(?<![\p{L}])(?:و|ف|و?قد)?(?:تم|تمت|اتخصم|اتحول|اتضاف|استلمت|بنجاح)(?![\p{L}])""" +
            """|(رصيدك الحالي|رصيدك المتاح|الرصيد المتاح|الرصيد الحالي|رصيدك بعد|رقم العملية|تاريخ العملية|العملية رقم|رصيد (?:حسابك|محفظتك)|المتاح|has been|was |were |successful|debited|charged|available balance|avail\.? bal|new balance|balance is|txn id|ref(?:erence)?\s*(?:no|#|:))""",
        RegexOption.IGNORE_CASE
    )

    /** دعوة لفعل: لينك أو "كلم #…" أو "اطلب *…" أو "زور": رسالة تسويقية لو مافيهاش علامة إن عملية حصلت */
    private val callToAction = Regex("""(https?://|www\.|vf\.eg|(?:كلم|كلّم|اطلب|اطلب)\s*[#*]|(?:دوس|زور|ادخل علي|اشحن|ادفع)\s)""", RegexOption.IGNORE_CASE)

    /** رسالة إعلان أو عرض مافيهاش عملية حصلت */
    fun isPromo(raw: String): Boolean {
        val tn = ArabicNumbers.norm(normalize(raw))
        return !completed.containsMatchIn(tn) && (promo.containsMatchIn(tn) || callToAction.containsMatchIn(tn))
    }

    /** أرقام جوه جملة عرض ("لحد 100ج كاش باك") مش مبلغ العملية */
    private val promoBefore = Regex("""(لحد|لغايه|حتي|ليك|جالك|هيجيلك|كسبت|هتكسب|هدي[هة]|كاش باك)\s*[:\-]?\s*$""")

    private val balanceKw = Regex(
        """(الحد المتاح|الحد الائتماني المتاح|حد الائتمان|available limit|avail\.?\s*limit|credit limit|الرصيد المتاح|الرصيد الحالي|الرصيد|رصيدك الحالي|رصيدك|رصيد|المتبقي|المتاح|avail(?:able)?\.?\s*bal(?:ance)?|balance|bal\b\.?)""",
        RegexOption.IGNORE_CASE
    )
    private val creditRx = Regex(
        """(الحد المتاح|الحد الائتماني|حد الائتمان|available limit|avail\.?\s*limit|credit limit|limit available|بطاقة ائتمان|بطاقتك الائتمانية|البطاقة الائتمانية|credit card)""",
        RegexOption.IGNORE_CASE
    )
    private val fromBalance = Regex("""(من رصيد|from (?:your )?balance)""", RegexOption.IGNORE_CASE)

    private val strongIn = Regex(
        """(received|credited|deposit(?:ed)?|refund(?:ed)?|cash ?back|incoming|تم استلام|استلمت|استلام|إيداع|ايداع|تمت إضافة|تم إضافة|تم اضافة|إضافة|اضافة|أضيف|اضيف|تحويل وارد|وارد|استرداد|مرتجع)""",
        RegexOption.IGNORE_CASE
    )
    private val strongOut = Regex(
        """(debited|purchase|spent|withdraw(?:al|n)?|charged|شحن رصيد|recharge|تم خصم|خصم|سحب|شراء|مشتريات|تحويل صادر|صادر)""",
        RegexOption.IGNORE_CASE
    )
    private val weakOut = Regex(
        """(paid|payment|sent|transfer(?:red)?|دفع|سداد|تحويل|أرسلت|ارسلت|إرسال|ارسال|حولت)""",
        RegexOption.IGNORE_CASE
    )
    /** إصدار كارت أو رسوم وعمولات: دي دايماً خصم (حتى لو الرسالة فيها كلمة زي "بنجاح" أو "إضافة") */
    private val feeRx = Regex(
        """((?:اصدار|إصدار|اصدرت|أصدرت)\s*(?:ال)?(?:كارت|بطاق[هة])|(?<![\p{L}])رسوم|تكلفة الخدمة|عمول[هة]|issuance|card issued|virtual card|\bfees?\b)""",
        RegexOption.IGNORE_CASE
    )
    /** فلوس اتستلمت فعلاً: بتغلب على الرسوم (مثلاً "تم استلام 500 جنيه بعد خصم رسوم 5") */
    private val receivedRx = Regex(
        """(تم\s*استلام|استلمت|received|credited|deposit(?:ed)?|إيداع|ايداع|تحويل\s*وارد)""",
        RegexOption.IGNORE_CASE
    )

    /** رسالة إصدار كارت أو رسوم: خصم مش إضافة */
    fun isFee(raw: String): Boolean {
        val t = normalize(raw)
        return feeRx.containsMatchIn(t) && !receivedRx.containsMatchIn(t)
    }

    private val refundRx = Regex("""(تم\s*رد\b|رد\s*مبلغ|تم\s*ارجاع|ارجاع\s*مبلغ|تم\s*عكس|refunded|reversal|reversed)""", RegexOption.IGNORE_CASE)

    private val toMe = Regex("""(إليك|اليك|لحسابك|to your account|to you\b)""", RegexOption.IGNORE_CASE)

    private const val MERCHANT_BODY =
        """(?:\s+|\s*:\s*)([A-Za-z\u0600-\u06FF][A-Za-z0-9\u0600-\u06FF &'._\-*]{1,40})"""
    // "لدى/عند/at" بتيجي قبل التاجر بالتأكيد، "من/إلى/to" ممكن تيجي قبل "بطاقتك" أو "حسابك"
    private val merchantStrong = Regex(
        // "عندBM POLARIS" (من غير مسافة) بتحصل في رسايل البنك: بنقبلها بس لو بعدها حروف إنجليزي
        """(?<![\p{L}\p{N}])(?:(?:at|لدى|لدي|عند|merchant)(?:\s+|\s*:\s*)|(?:لدى|لدي|عند)(?=[A-Za-z]))([A-Za-z\u0600-\u06FF][A-Za-z0-9\u0600-\u06FF &'._\-*]{1,40})""",
        RegexOption.IGNORE_CASE
    )
    private val merchantWeak = Regex(
        """(?<![\p{L}\p{N}])(?:to|from|إلى|الى|من|في)$MERCHANT_BODY""",
        RegexOption.IGNORE_CASE
    )
    private val merchantStop = Regex(
        """\s+(on|at|via|using|ref|date|balance|avail|بتاريخ|يوم|في|عبر|بواسطة|الرصيد|رصيد|رصيدك|بمبلغ|مبلغ|بقيمة|رقم|الساعة|برقم|تاريخ)(\s|$|(?=\d)).*""",
        RegexOption.IGNORE_CASE
    )
    private val merchantBlacklist = setOf(
        "حسابك", "حساب", "بطاقتك", "بطاقة", "كارتك", "your", "your account", "your card", "card", "account",
        "الرصيد", "رصيد", "رصيدك", "محفظتك", "wallet", "تاريخ", "the", "a", "an",
        "وجود", "مكانك", "خلال", "السحب", "عدم", "هو", "هي", "ده", "دي"
    )
    // رقم تليفون: "حولت لرقم 010…" أو "استلمت من 010…" بيبقى هو الاسم
    // "المسجل بإسم Sayed Z Salem" اسم صاحب التحويل (أحسن من رقم التليفون)
    private val registeredName = Regex("""المسجل\s*باسم\s*([^\n.؛;،]{2,40})""")
    // "تم دفع 4040 جنيه بنجاح لTelda Cairo": التاجر ملزوق في اللام
    private val paidTo = Regex("""بنجاح\s*ل\s*([A-Za-z][A-Za-z0-9&'._\-*]*(?: (?! )[A-Za-z0-9&'._\-*]+){0,3})""")
    private val phoneTo = Regex("""(?:لرقم|الى رقم|إلى رقم|علي رقم|على رقم|لـرقم|من رقم|من)\s*(01[0125]\d{8})(?!\d)""")
    // "وخصم 128.57ج من محفظتك": ده المبلغ اللي اتخصم فعلاً (مش 90ج قيمة الشحن)
    private val deductRx = Regex("""(?:خصم|خصمت|خصمنا)\s*(?:مبلغ\s*)?""")
    private val balanceNum = Regex("""^[^\d]{0,32}?(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)""")

    private val cardRx = Regex(
        """(?:\*+|x{2,}|•+|ending(?:\s+(?:in|with))?|المنتهية(?:\s*(?:برقم|بـ|ب))?|رقم)\s*(\d{4})(?!\d)""",
        RegexOption.IGNORE_CASE
    )

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                '٫' -> sb.append('.')
                '٬' -> sb.append(',')
                ' ' -> sb.append(' ')
                '‎', '‏', '‪', '‫', '‬', '‭', '‮' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun num(s: String) = s.replace(",", "").toDoubleOrNull()

    private fun normCur(raw: String, fallback: String): String {
        val c = raw.uppercase().replace(".", "").replace(" ", "")
        return when {
            c == "EGP" || c == "LE" || c.startsWith("جنيه") || c.startsWith("ج") -> "EGP"
            c == "USDT" -> "USDT"
            c == "USDC" -> "USDC"
            c == "USD" || c == "US$" || c == "$" || c == "دولار" -> "USD"
            c == "€" || c == "EUR" -> "EUR"
            c.isBlank() -> fallback
            else -> c
        }
    }

    private data class M(val start: Int, val end: Int, val value: Double, val cur: String)

    fun direction(t: String): Direction? {
        // "تم رد مبلغ 2000 لبطاقتك الذي تم خصمه": فلوس رجعتلك (مش خصم)
        if (refundRx.containsMatchIn(ArabicNumbers.norm(t))) return Direction.IN
        if (feeRx.containsMatchIn(t) && !receivedRx.containsMatchIn(t)) return Direction.OUT
        val inM = strongIn.find(t)
        val outM = strongOut.find(t)
        return when {
            inM != null && outM == null -> Direction.IN
            outM != null && inM == null -> Direction.OUT
            inM != null && outM != null -> if (inM.range.first <= outM.range.first) Direction.IN else Direction.OUT
            weakOut.containsMatchIn(t) -> if (toMe.containsMatchIn(t)) Direction.IN else Direction.OUT
            else -> null
        }
    }

    fun parse(raw: String, defaultCurrency: String = "EGP"): ParsedMoney? {
        val t = normalize(raw)
        if (otp.containsMatchIn(t)) return null
        if (isPromo(raw)) return null

        val found = mutableListOf<M>()
        curFirst.findAll(t).forEach { m ->
            val v = num(m.groupValues[2]) ?: return@forEach
            found += M(m.range.first, m.range.last + 1, v, normCur(m.groupValues[1], defaultCurrency))
        }
        numFirst.findAll(t).forEach { m ->
            val v = num(m.groupValues[1]) ?: return@forEach
            found += M(m.range.first, m.range.last + 1, v, normCur(m.groupValues[2], defaultCurrency))
        }
        val tn = ArabicNumbers.norm(t)
        val money = mutableListOf<M>()
        for (m in found.sortedBy { it.start }) {
            // "وهيجيلك لحد 100ج كاش باك" في آخر رسالة حقيقية: مش مبلغ العملية
            if (promoBefore.containsMatchIn(tn.substring((m.start - 14).coerceAtLeast(0), m.start))) continue
            if (money.none { o -> m.start < o.end && o.start < m.end }) money += m
        }

        var balance: M? = null
        for (k in balanceKw.findAll(t)) {
            // "شحن رصيد موبايلك ب 90ج" الرصيد هنا هو الشحنة مش رصيد المحفظة
            val before = t.substring((k.range.first - 8).coerceAtLeast(0), k.range.first)
            if (before.contains("شحن") || before.contains("كارت")) continue
            val kEnd = k.range.last + 1
            val m = money.firstOrNull { it.start >= kEnd && it.start - kEnd <= 40 }
            if (m != null) {
                balance = m; break
            }
        }
        // رصيد من غير عملة: "رصيد محفظتك الحالي 9312.78." أو "المتاح 17358.04"
        if (balance == null) {
            for (k in balanceKw.findAll(t)) {
                val before = t.substring((k.range.first - 8).coerceAtLeast(0), k.range.first)
                if (before.contains("شحن") || before.contains("كارت")) continue
                val kEnd = k.range.last + 1
                val rest = t.substring(kEnd)
                val nm = balanceNum.find(rest) ?: continue
                val gap = rest.substring(0, nm.range.first.coerceAtLeast(0).coerceAtMost(rest.length))
                if (gap.contains("رقم") || gap.contains("تاريخ")) continue
                val v = num(nm.groupValues[1]) ?: continue
                val s0 = kEnd + nm.range.last - nm.groupValues[1].length + 1
                if (money.any { s0 < it.end && it.start < s0 + nm.groupValues[1].length }) continue
                balance = M(s0, s0 + nm.groupValues[1].length, v, defaultCurrency)
                break
            }
        }

        val dir = direction(t)
        var amountM = money.firstOrNull { it !== balance }
        // لو فيه "خصم X": X هو المبلغ (مش أول رقم في الرسالة)
        for (d in deductRx.findAll(t)) {
            val dEnd = d.range.last + 1
            val m = money.firstOrNull { it !== balance && it.start >= dEnd && it.start - dEnd <= 3 }
            if (m != null) { amountM = m; break }
        }
        if (amountM == null && balance != null && dir != null && fromBalance.containsMatchIn(t)) {
            amountM = balance
            balance = null
        }

        var amount = amountM?.value
        val currency = amountM?.cur ?: balance?.cur ?: defaultCurrency
        var anchor = amountM?.end ?: 0
        if (amount == null) {
            fallbackAmount.find(t)?.let { f ->
                val b = balance
                if (b == null || f.range.first !in b.start until b.end) {
                    amount = num(f.groupValues[1])
                    anchor = f.range.last + 1
                }
            }
        }
        if (amount == null && balance == null) return null

        val merchant = if (amount != null) {
            registeredName.find(ArabicNumbers.norm(t).let { normalizedForName(t) })?.groupValues?.get(1)?.trim()
                ?: paidTo.find(t)?.groupValues?.get(1)?.trim()
                ?: findMerchant(merchantStrong, t, anchor) ?: findMerchant(merchantStrong, t, 0)
                ?: phoneTo.find(t)?.groupValues?.get(1)
                ?: findMerchant(merchantWeak, t, anchor) ?: findMerchant(merchantWeak, t, 0)
        } else null
        val card = cardRx.find(t)?.groupValues?.get(1)

        return ParsedMoney(
            amount = amount,
            currency = currency,
            direction = dir,
            balance = balance?.value,
            merchant = merchant,
            cardLast4 = card,
            isCredit = creditRx.containsMatchIn(t),
        )
    }

    /** "بإسم" و "باسم" نفس الحاجة: نوحّد الألف من غير ما نبوّظ الاسم نفسه */
    private fun normalizedForName(t: String) = t.replace("بإسم", "باسم").replace("بأسم", "باسم")

    private fun findMerchant(rx: Regex, t: String, from: Int): String? {
        var m = rx.find(t, from.coerceIn(0, t.length))
        while (m != null) {
            val cleaned = m.groupValues[1]
                .replace(merchantStop, "")
                .trim()
                .trimEnd('.', '-', '*', ',', ' ')
                .take(28)
                .trim()
            val lower = cleaned.lowercase()
            val firstWord = lower.substringBefore(' ')
            if (cleaned.length >= 2 && lower !in merchantBlacklist && firstWord !in merchantBlacklist &&
                !lower.contains("http") && !lower.contains("فودافون كاش") &&
                cleaned.count { it.isDigit() } < cleaned.length / 2
            ) return cleaned
            m = m.next()
        }
        return null
    }
}
