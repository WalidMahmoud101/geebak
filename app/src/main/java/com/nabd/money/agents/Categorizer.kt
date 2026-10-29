package com.nabd.money.agents

import com.nabd.money.data.Direction
import com.nabd.money.data.Purpose

data class Classification(val category: String, val purpose: Purpose)

object Categorizer {
    const val ID = "categorizer"

    /** تصنيفاتك وقواعدك (بتتحدث من الإعدادات) */
    @Volatile
    var user: com.nabd.money.data.UserTaxonomy = com.nabd.money.data.UserTaxonomy()

    /** كل التصنيفات: الأساسية + اللي ضفتها، و"أخرى" في الآخر */
    fun allCategories(): List<String> =
        (CATEGORIES.filter { it != "أخرى" } + user.customCategories + "أخرى").distinct()

    /** قاعدة من عندك تنطبق على العملية دي؟ */
    fun userMatch(merchant: String?, text: String): Classification? {
        if (user.rules.isEmpty()) return null
        val hay = ArabicNumbers.norm((merchant ?: "") + " " + text)
        // الكلمة الأطول الأول: "costa maadi" قبل "costa"
        val rule = user.rules.sortedByDescending { it.keyword.length }
            .firstOrNull { r -> ArabicNumbers.norm(r.keyword).trim().let { k -> k.isNotEmpty() && hay.contains(k) } }
            ?: return null
        return Classification(rule.category, rule.purpose ?: purposeOf(rule.category) ?: Purpose.OTHER)
    }

    /** الغرض الثابت للتصنيف: اللي انت حددته، وإلا الافتراضي */
    fun purposeOf(category: String): Purpose? = user.categoryPurpose[category] ?: suggestPurposeBuiltIn(category)

    val CATEGORIES = listOf(
        "بقالة", "فواتير", "إيجار", "مصروف البيت", "وقود", "صيانة عربية", "مطاعم وكافيهات", "ترفيه",
        "مواصلات", "اشتراكات", "أدوات شغل", "صحة", "تسوق", "تحويلات", "سحب كاش", "كريبتو",
        "رسوم وعمولات", "مرتب", "دخل", "تحويل بين حساباتي", "استرداد مصاريف الشركة", "أخرى",
    )

    private data class Rule(val category: String, val purpose: Purpose, val words: List<String>)

    private val rules = listOf(
        Rule("رسوم وعمولات", Purpose.PERSONAL, listOf(
            "رسوم", "عمولة", "عموله", "اصدار كارت", "اصدار الكارت", "إصدار كارت", "إصدار الكارت", "اصدار بطاقة", "إصدار بطاقة",
            "كارت الدفع", "كارت دفع", "كارت افتراضي", "issuance", "virtual card", "fee", "fees"
        )),
        Rule("بقالة", Purpose.HOME, listOf(
            "carrefour", "كارفور", "kazyon", "كازيون", "spinneys", "سبينيس", "hyper", "هايبر", "seoudi", "سعودي ماركت",
            "metro market", "مترو ماركت", "oscar", "أوسكار", "kheir zaman", "خير زمان", "gourmet", "جورميه", "ragab",
            "رجب", "سوبر ماركت", "supermarket", "بقالة", "خضار", "فاكهة", "lulu", "لولو", "breadfast", "بريدفاست",
            "rabbit", "talabat mart", "instashop", "عيش", "لحمه", "لحمة", "فراخ", "فاكهه", "جبنه", "جبنة", "لبن", "بيض", "رز", "سكر", "زيت", "طماطم", "بطاطس", "تموين", "بقاله", "خضاري", "الفكهاني", "الجزار", "جزار"
        )),
        Rule("فواتير", Purpose.HOME, listOf(
            "كهرباء", "electricity", "فاتورة غاز", "natural gas", "town gas", "مياه", "water bill", "انترنت",
            "internet", "we home", "فاتورة", "bill payment", "telecom egypt", "خط أرضي", "كهربا", "الكهربا", "النت", "الانترنت", "غاز", "الغاز", "عداد", "كارت شحن", "شحن رصيد"
        )),
        Rule("إيجار", Purpose.HOME, listOf("ايجار", "إيجار", "rent")),
        Rule("وقود", Purpose.CAR, listOf(
            "بنزين", "benzine", "fuel", "petrol", "wataniya", "وطنية", "shell", "totalenergies", "chillout",
            "emarat misr", "مصر للبترول", "misr petroleum", "سولار", "gas station", "محطة", "بنزينه", "بنزينة", "تفويل"
        )),
        Rule("صيانة عربية", Purpose.CAR, listOf(
            "service center", "مركز خدمة", "ميكانيكي", "tires", "كاوتش", "car wash", "غسيل عربية", "ترخيص",
            "مرور", "parking", "جراج", "باركينج", "autozone", "قطع غيار", "غيار زيت", "سمكري", "الميكانيكي", "غسيل العربيه", "منايفلا"
        )),
        Rule("مطاعم وكافيهات", Purpose.OUTING, listOf(
            "restaurant", "مطعم", "cafe", "café", "كافيه", "coffee", "قهوة", "starbucks", "costa", "talabat",
            "طلبات", "elmenus", "mcdonald", "ماكدونالدز", "kfc", "pizza", "بيتزا", "buffalo", "burger",
            "cilantro", "dunkin", "hardee", "cook door", "مؤمن", "حضرموت", "قهوه", "قهوة", "شيشه", "شيشة", "عشا برا", "اكل برا", "كشري", "شاورما", "برجر", "حلويات", "ايس كريم"
        )),
        Rule("ترفيه", Purpose.OUTING, listOf(
            "cinema", "سينما", "vox", "dream park", "دريم", "club", "نادي", "mall", "مول", "ticket", "تذكرة",
            "tazkarti", "تذكرتي", "bowling", "playstation"
        )),
        Rule("مواصلات", Purpose.PERSONAL, listOf(
            "uber", "أوبر", "اوبر", "careem", "كريم", "indrive", "اندرايف", "didi", "swvl", "سويفل", "مترو", "قطار", "train", "تاكسي", "ميكروباص", "توك توك", "تكتك", "اتوبيس", "مواصلات"
        )),
        Rule("اشتراكات", Purpose.PERSONAL, listOf(
            "netflix", "نتفليكس", "spotify", "سبوتيفاي", "youtube", "shahid", "شاهد", "watch it", "osn",
            "apple.com", "itunes", "google play", "icloud", "anghami", "أنغامي", "playstation network", "xbox"
        )),
        Rule("أدوات شغل", Purpose.WORK, listOf(
            "aws", "amazon web services", "digitalocean", "github", "upwork", "fiverr", "linkedin", "zoom",
            "microsoft", "office 365", "adobe", "godaddy", "namecheap", "hosting", "canva", "notion", "figma",
            "openai", "chatgpt", "anthropic", "claude", "google cloud", "slack", "jetbrains", "cloudflare"
        )),
        Rule("صحة", Purpose.PERSONAL, listOf(
            "صيدلية", "pharmacy", "seif", "العزبي", "el ezaby", "ezaby", "hospital", "مستشفى", "clinic",
            "عيادة", "معمل", "vezeeta", "فيزيتا", "دكتور", "الدكتور", "دوا", "الدوا", "دواء", "علاج", "تحاليل", "اشعه", "أشعة", "كشف"
        )),
        Rule("تسوق", Purpose.PERSONAL, listOf(
            "amazon", "أمازون", "noon", "نون", "jumia", "جوميا", "shein", "temu", "aliexpress", "zara",
            "h&m", "defacto", "lc waikiki", "max fashion", "b.tech", "بي تك", "هدوم", "لبس", "جزمه", "جزمة", "شنطه", "شنطة"
        )),
        Rule("كريبتو", Purpose.OTHER, listOf("binance", "usdt", "usdc", "crypto", "p2p", "bybit", "okx")),
        Rule("سحب كاش", Purpose.OTHER, listOf("atm", "سحب نقدي", "cash withdrawal", "ماكينة", "صراف")),
        Rule("تحويلات", Purpose.OTHER, listOf(
            "instapay", "ipn", "تحويل", "transfer", "sent", "ارسال", "إرسال", "محفظة", "wallet"
        )),
    )

    private val shortWordRx = HashMap<String, Regex>()

    /** الكلمات القصيرة لازم تكون كلمة كاملة: "rent" ماينفعش تطابق "current"، و"مول" ماتطابقش "المحمول" */
    private fun hit(hay: String, w: String): Boolean {
        if (w.length >= 5) return hay.contains(w)
        val rx = synchronized(shortWordRx) {
            shortWordRx.getOrPut(w) {
                Regex("(?<![\\p{L}\\p{N}])(?:ال)?" + Regex.escape(w) + "(?![\\p{L}\\p{N}])")
            }
        }
        return rx.containsMatchIn(hay)
    }

    fun classify(merchant: String?, text: String, dir: Direction, agentId: String, builtIn: Boolean = true): Classification {
        userMatch(merchant, text)?.let { return it }
        if (!builtIn) return Classification(if (dir == Direction.IN) "دخل" else "أخرى", Purpose.OTHER)
        val c = classifyBuiltIn(merchant, text, dir, agentId)
        // لو انت مثبت غرض للتصنيف ده، هو اللي يمشي
        return user.categoryPurpose[c.category]?.let { c.copy(purpose = it) } ?: c
    }

    private fun classifyBuiltIn(merchant: String?, text: String, dir: Direction, agentId: String): Classification {
        val hay = ((merchant ?: "") + " " + text).lowercase()
        if (dir == Direction.IN) {
            if (listOf("راتب", "مرتب", "salary", "payroll").any { hit(hay, it) }) {
                return Classification("مرتب", Purpose.WORK)
            }
            return Classification("دخل", Purpose.OTHER)
        }
        // اسم التاجر أولاً، بعدين نص الرسالة كله
        merchant?.lowercase()?.let { m ->
            rules.firstOrNull { r -> r.words.any { hit(m, it) } }
                ?.let { return Classification(it.category, it.purpose) }
        }
        rules.firstOrNull { r -> r.words.any { hit(hay, it) } }
            ?.let { return Classification(it.category, it.purpose) }
        if (agentId == "binance") return Classification("كريبتو", Purpose.OTHER)
        return Classification("أخرى", Purpose.OTHER)
    }

    /** اقتراح الغرض لما المستخدم يختار تصنيف يدوي */
    fun suggestPurpose(category: String): Purpose? = purposeOf(category)

    private fun suggestPurposeBuiltIn(category: String): Purpose? = when (category) {
        "بقالة", "فواتير", "إيجار", "مصروف البيت" -> Purpose.HOME
        "وقود", "صيانة عربية" -> Purpose.CAR
        "مطاعم وكافيهات", "ترفيه" -> Purpose.OUTING
        "أدوات شغل", "مرتب" -> Purpose.WORK
        "مواصلات", "اشتراكات", "صحة", "تسوق" -> Purpose.PERSONAL
        else -> null
    }
}
