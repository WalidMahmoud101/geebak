package com.nabd.money.agents

import com.nabd.money.data.Account
import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** عملية متحضرة من الكلام أو الكتابة، قبل ما تتحفظ */
data class Draft(
    val amount: Double,
    val currency: String = "EGP",
    val direction: Direction,
    val purpose: Purpose,
    val category: String,
    val merchant: String?,
    val accountId: Long?,
    val timestamp: Long,
    val note: String?,
    val company: Boolean,
)

/**
 * بيفهم الجمل بالعامية المصرية:
 * "صرفت خمسمية جنيه في كارفور للبيت امبارح"
 * "دفعت فاتورة الكهربا 350"
 * "قبضت المرتب 15 ألف"
 * "صرفت 50 عيش و100 بنزين للعربية"
 * "دفعت 200 تاكسي على حساب الشركة الساعة 3 العصر"
 */
object VoiceParser {

    private fun n(s: String) = ArabicNumbers.norm(s)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val outVerbs = rx("""(صرفت|صرفنا|دفعت|دفعنا|اشتريت|اشترينا|جبت|جبنا|سددت|خلصت|اتخصم|خصم|دافع|حاسبت|ادفع|فاتوره|اكلت|شربت|ركبت|مونت|فولت|شحنت)""")
    private val inWords = rx("""(قبضت|استلمت|جالي|جالى|جاتلي|دخلي|دخل لي|حولولي|حولي|اتحولي|اتحول لي|كسبت|رجعولي|رجعلي|ايداع|المرتب|مرتب|راتب|عموله|بونص|مكافاه)""")
    private val addWords = rx("""(^|\s)(ضيف|اضف|زود|حط|ضيفلي)(\s|$)""")
    private val salaryWords = rx("""(مرتب|راتب|المرتب|الراتب)""")

    private val companyRx = rx("""(علي|على) حساب (الشركه|الشغل|المكتب)|للشركه|للمكتب|هسترده|هاخده من الشركه|مصاريف شغل|من جيبي للشغل""")

    private val purposeRx = listOf(
        Purpose.HOME to rx("""(^|\s)(ل|لل)?(البيت|بيت)(\s|$)|مصروف البيت"""),
        Purpose.CAR to rx("""(^|\s)(ل|لل)?(العربيه|عربيه)(\s|$)"""),
        Purpose.OUTING to rx("""(خروجه|خروجات|خرجت|فسحه|سهره|خرجنا)"""),
        Purpose.WORK to rx("""(^|\s)(ل|لل)?(الشغل|شغل|الشركه|المكتب)(\s|$)"""),
        Purpose.PERSONAL to rx("""(^|\s)(ليا|لنفسي|شخصي)(\s|$)"""),
    )

    private val quantityAfter = rx("""^\s*(كيلو|كيلوات|جرام|جم|علبه|علب|ازازه|ازايز|حته|حتت|قطعه|قطع|لتر|متر|كرتونه|كيس|اكياس|رغيف|ارغفه|مره|مرات|يوم|ايام|ساعه|ساعات|دقيقه|%|x)""")
    private val timeBefore = rx("""(الساعه|ساعه|يوم|رقم|الدور)\s*$""")
    private val numberRx = Regex("""\d+(?:\.\d+)?""")

    private val splitRx = rx("""\s+(?:وكمان|كمان|وبعدين|بعدين|و)\s*(?=\d|صرفت|دفعت|اشتريت|جبت)""")

    private val dayNames = mapOf(
        "السبت" to DayOfWeek.SATURDAY, "الحد" to DayOfWeek.SUNDAY, "الاحد" to DayOfWeek.SUNDAY,
        "الاتنين" to DayOfWeek.MONDAY, "الاثنين" to DayOfWeek.MONDAY, "التلات" to DayOfWeek.TUESDAY,
        "الثلاثاء" to DayOfWeek.TUESDAY, "التلاتاء" to DayOfWeek.TUESDAY, "الاربع" to DayOfWeek.WEDNESDAY,
        "الاربعاء" to DayOfWeek.WEDNESDAY, "الخميس" to DayOfWeek.THURSDAY, "الجمعه" to DayOfWeek.FRIDAY,
    )

    private val stop: Set<String> = setOf(
        "صرفت", "صرفنا", "دفعت", "دفعنا", "اشتريت", "اشترينا", "جبت", "جبنا", "سددت", "خلصت", "اتخصم", "خصم",
        "دافع", "حاسبت", "ادفع", "قبضت", "استلمت", "جالي", "جالى", "جاتلي", "دخلي", "حولولي", "حولي", "كسبت",
        "رجعولي", "رجعلي", "ضيف", "ضيفلي", "اضف", "زود", "حط", "سجل", "سجلي", "انا", "احنا", "النهارده", "النهاردة",
        "امبارح", "اول", "بكره", "الصبح", "صباحا", "الضهر", "الظهر", "العصر", "المغرب", "بالليل", "بليل", "الليل",
        "الليله", "مساء", "الساعه", "ساعه", "ونص", "وربع", "وتلت", "جنيه", "جنيهات", "جنية", "ج", "دولار",
        "usd", "usdt", "بقيمه", "بمبلغ", "مبلغ", "حوالي", "تقريبا", "كده", "يعني", "بس", "يوم", "من", "يومين",
        "ايام", "للبيت", "البيت", "بيت", "لبيت", "للشغل", "الشغل", "شغل", "للعربيه", "العربيه", "عربيه",
        "خروجه", "خروجات", "فسحه", "على", "علي", "حساب", "الشركه", "للشركه", "المكتب", "للمكتب", "هسترده",
        "كاش", "نقدي", "جيبي", "مصروف", "مصاريف", "ليا", "لنفسي", "شخصي", "و", "في", "عند", "ل", "ب",
        "ده", "دي", "اللي", "علشان", "عشان", "لما",
    ) + dayNames.keys

    private val leadingPreps = setOf("في", "من", "عند", "على", "علي", "ل", "ب", "ف", "لل", "و", "بتاع", "بتاعه")

    private val accountSynonyms = mapOf(
        "vodafone_cash" to listOf("فودافون كاش", "فودافون"),
        "orange_cash" to listOf("اورنج كاش", "اورنج"),
        "etisalat_cash" to listOf("اتصالات كاش", "اتصالات"),
        "we_pay" to listOf("وي باي"),
        "instapay" to listOf("انستاباي", "انستا باي", "انستا"),
        "fawry" to listOf("فوري"),
        "cib" to listOf("سي اي بي", "cib"),
        "nbe" to listOf("الاهلي", "البنك الاهلي"),
        "banque_misr" to listOf("بنك مصر"),
        "qnb" to listOf("كيو ان بي", "qnb"),
        "binance" to listOf("بينانس", "باينانس"),
        "redotpay" to listOf("ريدوت", "ريد دوت"),
        "kast" to listOf("كاست"),
        "wallet_cards" to listOf("والت", "جوجل باي", "سامسونج باي"),
    )

    /** من نتايج التعرف على الصوت (أكتر من احتمال): اختار أول واحد فيه مبلغ */
    fun pickBest(alternatives: List<String>, accounts: List<Account>): String =
        alternatives.firstOrNull { parse(it, accounts).isNotEmpty() } ?: alternatives.firstOrNull().orEmpty()

    fun parse(
        raw: String,
        accounts: List<Account>,
        now: LocalDateTime = LocalDateTime.now(),
        defaultCompany: Boolean = false,
    ): List<Draft> {
        if (raw.isBlank()) return emptyList()
        var t = MoneyParser.normalize(raw).replace(Regex("""(?<=\d),(?=\d{3})"""), "")
        t = t.replace(Regex("""[؟?!.،,]+"""), " ")
        t = ArabicNumbers.toDigits(t)
        val tn = n(t)

        // السياق العام للجملة كلها (الوقت، الحساب، الشركة...)
        val globalTime = parseTime(tn, now)
        val globalCompany = defaultCompany || companyRx.containsMatchIn(tn)
        val globalPurpose = purposeRx.firstOrNull { it.second.containsMatchIn(tn) }?.first
        val globalAccount = findAccount(tn, accounts)

        // تقسيم: "50 عيش و 100 بنزين" = عمليتين
        val rawSegments = splitRx.split(t).map { it.trim() }.filter { it.isNotEmpty() }
        val segments = mutableListOf<String>()
        for (s in rawSegments) {
            if (amountIn(n(s)) == null && segments.isNotEmpty()) segments[segments.lastIndex] += " " + s
            else segments += s
        }
        // جزء من غير مبلغ في الأول يتضم للي بعده
        if (segments.size > 1 && amountIn(n(segments[0])) == null) {
            segments[1] = segments[0] + " " + segments[1]; segments.removeAt(0)
        }

        val firstVerbIn = inWords.containsMatchIn(tn) && !outVerbs.containsMatchIn(tn)
        return segments.mapNotNull { seg ->
            val sn = n(seg)
            val amount = amountIn(sn) ?: return@mapNotNull null
            if (amount <= 0) return@mapNotNull null

            val direction = when {
                outVerbs.containsMatchIn(sn) -> Direction.OUT
                inWords.containsMatchIn(sn) -> Direction.IN
                addWords.containsMatchIn(sn) -> if (sn.contains("فاتوره")) Direction.OUT else Direction.IN
                firstVerbIn -> Direction.IN
                else -> Direction.OUT
            }
            val company = direction == Direction.OUT && (globalCompany || companyRx.containsMatchIn(sn))
            val account = findAccount(sn, accounts) ?: globalAccount
            val currency = when {
                Regex("usdt|تيثر|يو اس دي تي", RegexOption.IGNORE_CASE).containsMatchIn(sn) -> "USDT"
                Regex("دولار|\\$|usd", RegexOption.IGNORE_CASE).containsMatchIn(sn) -> "USD"
                else -> account?.currency ?: "EGP"
            }
            val desc = describe(seg, account)
            val explicitPurpose = purposeRx.firstOrNull { it.second.containsMatchIn(sn) }?.first ?: globalPurpose

            val (category, purpose) = if (direction == Direction.IN) {
                when {
                    salaryWords.containsMatchIn(sn) -> "مرتب" to Purpose.WORK
                    explicitPurpose == Purpose.HOME -> "مصروف البيت" to Purpose.HOME
                    else -> "دخل" to (explicitPurpose ?: Purpose.OTHER)
                }
            } else {
                val cls = Categorizer.classify(desc, seg, Direction.OUT, AgentRegistry.MANUAL_ID)
                val p = when {
                    company -> Purpose.WORK
                    explicitPurpose != null -> explicitPurpose
                    else -> cls.purpose
                }
                val c = if (cls.category == "أخرى" && p == Purpose.HOME) "مصروف البيت" else cls.category
                c to p
            }

            Draft(
                amount = amount,
                currency = currency,
                direction = direction,
                purpose = purpose,
                category = category,
                merchant = desc,
                accountId = account?.id,
                timestamp = (parseTime(sn, now) ?: globalTime ?: now)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                note = "بالصوت: ${raw.trim().take(120)}",
                company = company,
            )
        }
    }

    /** المبلغ = أكبر رقم في الجملة، من غير أرقام الساعة والكميات (2 كيلو) */
    private fun amountIn(sn: String): Double? = numberRx.findAll(sn)
        .filter { m ->
            val before = sn.substring(0, m.range.first)
            val after = sn.substring(m.range.last + 1)
            !timeBefore.containsMatchIn(before) && !quantityAfter.containsMatchIn(after)
        }
        .mapNotNull { it.value.toDoubleOrNull() }
        .maxOrNull()

    private fun describe(seg: String, account: Account?): String? {
        var tokens = seg.split(Regex("\\s+")).filter { it.isNotBlank() }
        val accountWords = account?.let { a -> (listOf(a.name) + (accountSynonyms[a.agentId] ?: emptyList())).flatMap { n(it).split(" ") }.toSet() } ?: emptySet()
        tokens = tokens.filterNot { tok ->
            val x = n(tok)
            val bare = x.removePrefix("و").removePrefix("ب").removePrefix("ل")
            numberRx.matches(x) || numberRx.matches(bare) || x in stop || x in accountWords ||
                (bare.length >= 2 && bare in stop) || x == "$" || x.startsWith("ج.م")
        }
        while (tokens.isNotEmpty() && n(tokens.first()) in leadingPreps) tokens = tokens.drop(1)
        while (tokens.isNotEmpty() && n(tokens.last()) in leadingPreps) tokens = tokens.dropLast(1)
        val d = tokens.take(5).joinToString(" ").trim()
        return d.ifBlank { null }
    }

    private fun findAccount(sn: String, accounts: List<Account>): Account? {
        if (Regex("""(^|\s)(كاش|نقدي|من جيبي)(\s|$)""").containsMatchIn(sn) &&
            accounts.none { n(it.name).contains("كاش") && sn.contains(n(it.name)) }
        ) return null
        return accounts.firstOrNull { a ->
            val names = listOf(a.name) + (accountSynonyms[a.agentId] ?: emptyList())
            names.any { name ->
                val nn = n(name).replace(Regex("[•\\d]"), "").trim()
                nn.length >= 3 && sn.contains(nn)
            }
        }
    }

    private val hourRx = Regex("""(?:الساعه|ساعه)\s*(\d{1,2})(?:[:.](\d{2}))?(\s*(?:و\s*)?(ونص|نص|وربع|ربع|وتلت|تلت))?""")

    /** "امبارح"، "اول امبارح"، "يوم الخميس"، "من 3 ايام"، "الساعه 3 العصر"، "بالليل" */
    fun parseTime(sn: String, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        var date: LocalDate? = when {
            sn.contains("اول امبارح") -> today.minusDays(2)
            sn.contains("امبارح") || sn.contains("امس") -> today.minusDays(1)
            sn.contains("من يومين") -> today.minusDays(2)
            else -> Regex("""من (\d{1,2}) ايام""").find(sn)?.groupValues?.get(1)?.toLongOrNull()?.let { today.minusDays(it) }
        }
        if (date == null) {
            dayNames.entries.firstOrNull { (k, _) -> Regex("""(^|\s)(يوم\s+)?$k(\s|$)""").containsMatchIn(sn) }?.let { (_, dow) ->
                var d = today
                while (d.dayOfWeek != dow) d = d.minusDays(1)
                date = d
            }
        }
        val pm = Regex("""(الضهر|الظهر|العصر|المغرب|بالليل|بليل|الليل|مساء|العشا)""").find(sn)?.value
        val am = Regex("""(الصبح|صباحا|الفجر)""").containsMatchIn(sn)
        var time: LocalTime? = hourRx.find(sn)?.let { m ->
            var h = m.groupValues[1].toInt()
            var min = m.groupValues[2].toIntOrNull() ?: 0
            when (m.groupValues[4].removePrefix("و")) {
                "نص" -> min = 30
                "ربع" -> min = 15
                "تلت" -> min = 20
            }
            if (h in 1..11 && (pm != null || (!am && h <= 6))) h += 12
            if (h == 12 && am) h = 0
            LocalTime.of(h.coerceIn(0, 23), min.coerceIn(0, 59))
        }
        if (time == null) {
            time = when {
                am -> LocalTime.of(9, 0)
                pm == "الضهر" || pm == "الظهر" -> LocalTime.of(13, 0)
                pm == "العصر" -> LocalTime.of(16, 0)
                pm == "المغرب" -> LocalTime.of(18, 0)
                pm != null -> LocalTime.of(21, 0)
                else -> null
            }
        }
        if (date == null && time == null) return null
        val d = date ?: today
        val tm = time ?: if (d == today) now.toLocalTime() else LocalTime.NOON
        return LocalDateTime.of(d, tm)
    }
}
