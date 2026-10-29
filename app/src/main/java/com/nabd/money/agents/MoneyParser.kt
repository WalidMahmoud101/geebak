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
        """EGP|E\.G\.P|L\.E\.?|LE|USDT|USDC|USD|US\$|EUR|GBP|SAR|AED|BTC|ETH|BNB|SOL|\$|€|جنيهات|جني[هة](?:اً|ا)?(?:\s*مصري)?|ج\.\s?م\.?|جم|دولار"""
    private const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?"""

    private val curFirst =
        Regex("""(?<![A-Za-z])($CUR)(?![A-Za-z])\s?(?<![\d.,])($NUM)""", RegexOption.IGNORE_CASE)
    private val numFirst =
        Regex("""(?<![\d.,])($NUM)\s?(?<![A-Za-z])($CUR)(?![A-Za-z])""", RegexOption.IGNORE_CASE)
    private val fallbackAmount =
        Regex("""(?:بمبلغ|مبلغ|بقيمة|قيمة|amount(?:\s+of)?|amt)\s*:?\s*($NUM)""", RegexOption.IGNORE_CASE)

    private val otp = Regex(
        """\b(otp|one[- ]time|verification code|passcode|is your code)\b|رمز التحقق|كود التحقق|رمز التفعيل|كود التفعيل|كلمة المرور لمرة واحدة""",
        RegexOption.IGNORE_CASE
    )

    /** كلام الإعلانات والعروض: "استمتع بكاش باك لحد 100 جنيه"، "اكسب"، "خصم 20%"… */
    private val promo = Regex(
        """(?<![\p{L}])(?:و|ف|ب)?(?:استمتع|اكسب|إكسب|اربح|إربح|هدية|هديه|عرض|عروض|لحد|لغاية|لغايه|حتى|يصل|بيوصل|استخدم|إستخدم|اطلب|حمّل|حمل|نزّل|نزل|اتصل|اشترك|سارع|فرصة|فرصه|كوبون|برومو|ماتفوتش|متفوتش)(?![\p{L}])""" +
            """|لفترة محدودة|لفتره محدوده|متاح الآن|%|up to|\boffer|\benjoy|\bwin\b|\bpromo|don'?t miss|\bdownload|limited time|\bsubscribe|\bdial\b""",
        RegexOption.IGNORE_CASE
    )
    /** علامات إن العملية حصلت فعلاً، علشان الرسالة الحقيقية اللي في آخرها إعلان ماتضيعش */
    private val completed = Regex(
        """(?<![\p{L}])(?:و|ف|و?قد)?(?:تم|تمت|اتخصم|اتحول|اتضاف|استلمت|بنجاح)(?![\p{L}])""" +
            """|(رصيدك الحالي|رصيدك المتاح|الرصيد المتاح|الرصيد الحالي|رصيدك بعد|رقم العملية|العملية رقم|has been|was |were |successful|debited|charged|available balance|avail\.? bal|new balance|balance is|txn id|ref(?:erence)?\s*(?:no|#|:))""",
        RegexOption.IGNORE_CASE
    )

    /** رسالة إعلان أو عرض مافيهاش عملية حصلت */
    fun isPromo(raw: String): Boolean {
        val t = normalize(raw)
        return promo.containsMatchIn(t) && !completed.containsMatchIn(t)
    }

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
        """((?:اصدار|إصدار|اصدرت|أصدرت)\s*(?:ال)?(?:كارت|بطاق[هة])|(?:كارت|بطاق[هة])\s*(?:ال)?(?:دفع\s*)?(?:ال)?(?:اونلاين|أونلاين|online|افتراضي)|(?<![\p{L}])رسوم|عمول[هة]|issuance|card issued|virtual card|\bfees?\b)""",
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

    private val toMe = Regex("""(إليك|اليك|لحسابك|to your account|to you\b)""", RegexOption.IGNORE_CASE)

    private const val MERCHANT_BODY =
        """(?:\s+|\s*:\s*)([A-Za-z\u0600-\u06FF][A-Za-z0-9\u0600-\u06FF &'._\-*]{1,40})"""
    // "لدى/عند/at" بتيجي قبل التاجر بالتأكيد، "من/إلى/to" ممكن تيجي قبل "بطاقتك" أو "حسابك"
    private val merchantStrong = Regex(
        """(?<![\p{L}\p{N}])(?:at|لدى|لدي|عند|merchant)$MERCHANT_BODY""",
        RegexOption.IGNORE_CASE
    )
    private val merchantWeak = Regex(
        """(?<![\p{L}\p{N}])(?:to|from|إلى|الى|من|في)$MERCHANT_BODY""",
        RegexOption.IGNORE_CASE
    )
    private val merchantStop = Regex(
        """\s+(on|at|via|using|ref|date|balance|avail|بتاريخ|يوم|في|عبر|بواسطة|الرصيد|رصيد|رصيدك|بمبلغ|مبلغ|بقيمة|رقم|الساعة|برقم)(\s|$).*""",
        RegexOption.IGNORE_CASE
    )
    private val merchantBlacklist = setOf(
        "حسابك", "حساب", "بطاقتك", "بطاقة", "كارتك", "your", "your account", "your card", "card", "account",
        "الرصيد", "رصيد", "رصيدك", "محفظتك", "wallet", "تاريخ", "the", "a", "an"
    )

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
        if (promo.containsMatchIn(t) && !completed.containsMatchIn(t)) return null

        val found = mutableListOf<M>()
        curFirst.findAll(t).forEach { m ->
            val v = num(m.groupValues[2]) ?: return@forEach
            found += M(m.range.first, m.range.last + 1, v, normCur(m.groupValues[1], defaultCurrency))
        }
        numFirst.findAll(t).forEach { m ->
            val v = num(m.groupValues[1]) ?: return@forEach
            found += M(m.range.first, m.range.last + 1, v, normCur(m.groupValues[2], defaultCurrency))
        }
        val money = mutableListOf<M>()
        for (m in found.sortedBy { it.start }) {
            if (money.none { o -> m.start < o.end && o.start < m.end }) money += m
        }

        var balance: M? = null
        for (k in balanceKw.findAll(t)) {
            val kEnd = k.range.last + 1
            val m = money.firstOrNull { it.start >= kEnd && it.start - kEnd <= 40 }
            if (m != null) {
                balance = m; break
            }
        }

        val dir = direction(t)
        var amountM = money.firstOrNull { it !== balance }
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
            findMerchant(merchantStrong, t, anchor) ?: findMerchant(merchantStrong, t, 0)
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
                cleaned.count { it.isDigit() } < cleaned.length / 2
            ) return cleaned
            m = m.next()
        }
        return null
    }
}
