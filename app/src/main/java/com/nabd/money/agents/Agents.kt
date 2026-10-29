package com.nabd.money.agents

import com.nabd.money.data.AccountType
import com.nabd.money.data.TxnSource

enum class AgentKind { SOURCE, INSIGHT }

data class RawMessage(
    val channel: TxnSource,
    /** رقم/اسم مرسل الرسالة أو اسم الباكدج بتاع التطبيق */
    val sender: String,
    val appLabel: String = "",
    val title: String = "",
    val text: String,
    val timestamp: Long,
)

abstract class TrackerAgent(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val color: Long,
    val kind: AgentKind,
)

/** وكيل مصدر: متخصص في مصدر فلوس واحد (بنك، محفظة، كارت، منصة كريبتو) */
open class SourceAgent(
    id: String,
    name: String,
    description: String,
    iconKey: String,
    color: Long,
    val accountType: AccountType,
    val accountName: String,
    val currency: String = "EGP",
    private val keywords: List<String>,
    private val packages: Set<String> = emptySet(),
    val channels: Set<TxnSource> = setOf(TxnSource.SMS, TxnSource.NOTIFICATION),
) : TrackerAgent(id, name, description, iconKey, color, AgentKind.SOURCE) {

    open fun matches(m: RawMessage): Boolean {
        if (m.channel !in channels) return false
        if (m.sender in packages) return true
        val hay = "${m.sender} ${m.appLabel} ${m.title}".lowercase()
        val tokens = hay.split(TOKEN_SPLIT).toSet()
        return keywords.any { k -> if (k.length >= 5 || ' ' in k) hay.contains(k) else k in tokens }
    }

    open fun parse(m: RawMessage): ParsedMoney? =
        MoneyParser.parse(listOf(m.title, m.text).filter { it.isNotBlank() }.joinToString("\n"), currency)

    /**
     * البنك: كل الكروت والحسابات العادية (مش الائتمان) في نفس البنك فلوسها واحدة، فبتروح حساب واحد.
     * رسايل البنك بتيجي مرة بآخر 4 أرقام كارت ومرة برقم الحساب، والرصيد "المتاح" هو رصيد الحساب كله.
     */
    open fun mainKey(m: RawMessage): String = "main"

    open fun mainLabel(m: RawMessage): String = accountName

    open fun accountKey(m: RawMessage, p: ParsedMoney): String = p.cardLast4 ?: "main"

    open fun accountLabel(m: RawMessage, p: ParsedMoney): String =
        if (p.cardLast4 != null) "$accountName •••• ${p.cardLast4}" else accountName

    open fun accountCurrency(p: ParsedMoney): String = currency

    companion object {
        val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}&]+")
    }
}

/** Binance: حساب لكل عملة (USDT، BTC...) */
class CryptoAgent(
    id: String, name: String, description: String, iconKey: String, color: Long,
    accountName: String, keywords: List<String>, packages: Set<String>,
) : SourceAgent(
    id, name, description, iconKey, color, AccountType.CRYPTO, accountName, "USDT", keywords, packages
) {
    override fun accountKey(m: RawMessage, p: ParsedMoney) = p.currency
    override fun accountLabel(m: RawMessage, p: ParsedMoney) = "$accountName ${p.currency}"
    override fun accountCurrency(p: ParsedMoney) = p.currency
}

/** يلتقط رسائل أي بنك مش متغطي بوكيل مخصص */
class GenericBankAgent : SourceAgent(
    id = "other_banks",
    name = "وكيل البنوك الأخرى",
    description = "أي بنك تاني: HSBC، ADIB، الإسكندرية…",
    iconKey = "bank",
    color = 0xFF6C8CFF,
    accountType = AccountType.BANK,
    accountName = "بنك",
    keywords = emptyList(),
    channels = setOf(TxnSource.SMS),
) {
    private val bankish = Regex(
        """(بطاقة|بطاقتك|كارت|حساب|حسابك|بنك|card|account|a/c|acct|bank)""",
        RegexOption.IGNORE_CASE
    )
    private val phone = Regex("""^\+?[\d\s-]+$""")

    override fun matches(m: RawMessage): Boolean {
        if (m.channel != TxnSource.SMS) return false
        if (phone.matches(m.sender.trim())) return false
        return bankish.containsMatchIn(m.text)
    }

    override fun mainKey(m: RawMessage) = bankKey(m.sender)

    override fun mainLabel(m: RawMessage) = m.sender.trim()

    override fun accountKey(m: RawMessage, p: ParsedMoney) =
        bankKey(m.sender) + (p.cardLast4?.let { "-$it" } ?: "")

    override fun accountLabel(m: RawMessage, p: ParsedMoney) =
        m.sender.trim() + (p.cardLast4?.let { " •••• $it" } ?: "")

    companion object {
        private val suffix = Regex("(EGYPT|EGY|EG|BANK|SMS|ALERT|ALERTS)$")

        /** "HSBC-EG" و "hsbc" و "HSBC EGYPT" = نفس البنك */
        fun bankKey(sender: String): String {
            var k = sender.uppercase().filter { it.isLetterOrDigit() }
            while (true) {
                val cut = suffix.replace(k, "")
                if (cut == k || cut.length < 2) break
                k = cut
            }
            return k.ifEmpty { sender.uppercase() }
        }
    }
}

/** وكيل تحليل: بيشتغل على العمليات اللي اتجمعت */
class InsightAgent(
    id: String, name: String, description: String, iconKey: String, color: Long,
) : TrackerAgent(id, name, description, iconKey, color, AgentKind.INSIGHT)

object AgentRegistry {

    val sources: List<SourceAgent> = listOf(
        SourceAgent(
            "instapay", "وكيل InstaPay", "يتابع كل تحويلات InstaPay الصادرة والواردة لحظة بلحظة",
            "bolt", 0xFF7B5CFF, AccountType.BANK, "InstaPay",
            keywords = listOf("instapay", "ipn", "انستاباي", "إنستاباي", "انستا باي"),
            packages = setOf("com.egyptianbanks.instapay"),
        ),
        SourceAgent(
            "vodafone_cash", "وكيل فودافون كاش", "يراقب محفظة فودافون كاش: تحويلات، دفع، شحن، سحب",
            "phone", 0xFFE60000, AccountType.WALLET, "فودافون كاش",
            // رسايل فودافون العادية (باقات وعروض من "Vodafone") مش حركات محفظة، فمابنطابقش كلمة "فودافون" لوحدها
            keywords = listOf("vf-cash", "vodafone cash", "vodafonecash", "فودافون كاش"),
            packages = setOf("com.emeint.android.myservices"),
        ),
        SourceAgent(
            "orange_cash", "وكيل أورنج كاش", "يراقب محفظة أورنج كاش وكل حركة فيها",
            "phone", 0xFFFF7900, AccountType.WALLET, "أورنج كاش",
            keywords = listOf("orange cash", "orangecash", "orange money", "اورنج كاش", "أورنج كاش", "orange", "اورنج"),
        ),
        SourceAgent(
            "etisalat_cash", "وكيل e& كاش", "يراقب محفظة اتصالات / e& كاش",
            "phone", 0xFF00B140, AccountType.WALLET, "e& كاش",
            keywords = listOf("etisalat cash", "etisalatcash", "e& cash", "e&cash", "etisalat", "اتصالات كاش", "اتصالات"),
        ),
        SourceAgent(
            "we_pay", "وكيل WE Pay", "يراقب محفظة WE Pay ومدفوعات WE",
            "phone", 0xFF8A2BE2, AccountType.WALLET, "WE Pay",
            keywords = listOf("wepay", "we pay", "we-pay", "وي باي"),
        ),
        SourceAgent(
            "fawry", "وكيل فوري", "يتابع مدفوعات فوري و myFawry: فواتير، شحن، اشتراكات",
            "receipt", 0xFFFFC20E, AccountType.WALLET, "فوري",
            keywords = listOf("fawry", "myfawry", "فوري"),
            packages = setOf("com.fawry.myfawry"),
        ),
        SourceAgent(
            "wallet_cards", "وكيل كروت Wallet", "يقرأ دفعات الكروت اللي على Google Pay / Google Wallet و Samsung Pay (Apple Pay مش متاح على أندرويد)",
            "card", 0xFF4285F4, AccountType.CARD, "كروت Wallet",
            keywords = listOf("google wallet", "samsung wallet", "samsung pay", "google pay", "gpay"),
            packages = setOf(
                "com.google.android.apps.walletnfcrel",
                "com.google.android.apps.nbu.paisa.user",
                "com.samsung.android.spay",
                "com.samsung.android.samsungpay.gear",
            ),
            channels = setOf(TxnSource.NOTIFICATION),
        ),
        SourceAgent(
            "redotpay", "وكيل RedotPay", "يتابع مشتريات كارت RedotPay والشحن بالكريبتو",
            "card", 0xFFE5183A, AccountType.CARD, "RedotPay", currency = "USD",
            keywords = listOf("redotpay", "redot pay", "redot"),
            packages = setOf("com.redotpay"),
        ),
        SourceAgent(
            "kast", "وكيل Kast", "يتابع كارت Kast: المشتريات والإيداع بالـ USDC/USDT",
            "card", 0xFFB6F23A, AccountType.CARD, "Kast", currency = "USD",
            keywords = listOf("kast", "kast app", "kast.xyz"),
        ),
        CryptoAgent(
            "binance", "وكيل Binance", "يراقب الإيداع والسحب وطلبات P2P على Binance",
            "crypto", 0xFFF3BA2F, "Binance",
            keywords = listOf("binance", "بينانس"),
            packages = setOf("com.binance.dev", "com.binance.us"),
        ),
        CryptoAgent(
            "trust_wallet", "وكيل Trust Wallet", "يراقب الإرسال والاستلام على محفظة Trust Wallet",
            "crypto", 0xFF3375BB, "Trust Wallet",
            keywords = listOf("trust wallet", "trustwallet"),
            packages = setOf("com.wallet.crypto.trustapp"),
        ),
        SourceAgent(
            "cib", "وكيل CIB", "يقرأ رسائل البنك التجاري الدولي: كروت وحسابات",
            "bank", 0xFF1E73D8, AccountType.BANK, "CIB",
            keywords = listOf("cib", "cibeg", "cib egypt"),
            packages = setOf("com.cib.digital"),
        ),
        SourceAgent(
            "nbe", "وكيل البنك الأهلي", "يقرأ رسائل البنك الأهلي المصري ويحسب رصيدك",
            "bank", 0xFF00875A, AccountType.BANK, "البنك الأهلي",
            keywords = listOf("nbe", "alahly", "al ahly", "nbegypt", "ahly", "الأهلي", "الاهلي"),
        ),
        SourceAgent(
            "banque_misr", "وكيل بنك مصر", "يقرأ رسائل بنك مصر وكروت ميزة",
            "bank", 0xFFB3263A, AccountType.BANK, "بنك مصر",
            keywords = listOf("banquemisr", "banque misr", "bm", "bmonline", "بنك مصر"),
        ),
        SourceAgent(
            "qnb", "وكيل QNB", "يقرأ رسائل بنك QNB الأهلي",
            "bank", 0xFF8E1F5E, AccountType.BANK, "QNB",
            keywords = listOf("qnb", "qnbaa", "qnb alahli", "qnbalahli"),
        ),
        GenericBankAgent(),
    )

    val insights: List<InsightAgent> = listOf(
        InsightAgent(
            Categorizer.ID, "وكيل التصنيف",
            "يصنف كل عملية: بيت، شغل، عربية، خروجات... من اسم التاجر", "category", 0xFF2EF2B5
        ),
        InsightAgent(
            SalaryWatch.ID, "وكيل المرتب",
            "يعرف ميعاد قبضك وينبهك لو اتأخر", "salary", 0xFF7BD389
        ),
        InsightAgent(
            "balance_guard", "حارس الرصيد",
            "ينبهك لما رصيد أي حساب يقل عن الحد اللي تحدده", "shield", 0xFF3DB8FF
        ),
        InsightAgent(
            "subscriptions", "صياد الاشتراكات",
            "يكتشف الاشتراكات والمدفوعات المتكررة كل شهر", "repeat", 0xFFFF6FB5
        ),
        InsightAgent(
            "budget", "مراقب الميزانية",
            "يحذرك لما توصل 80% و 100% من ميزانية البيت أو الخروجات...", "savings", 0xFFF5C451
        ),
        InsightAgent(
            "anomaly", "كاشف المصاريف الغريبة",
            "يلقط أي مبلغ كبير أو مش طبيعي مقارنة بعادتك", "warning", 0xFFFF5C7A
        ),
    )

    val all: List<TrackerAgent> = sources + insights

    private val byIdMap = all.associateBy { it.id } + mapOf(
        MANUAL_ID to InsightAgent(MANUAL_ID, "إدخال يدوي", "", "edit", 0xFF8C9AB8)
    )

    fun byId(id: String): TrackerAgent? = byIdMap[id]

    const val MANUAL_ID = "manual"

    /** وكلاء بيكرروا عمليات البنك (نفس الفلوس، مصدر تاني) وحساباتهم وسيطة */
    val MIRROR_AGENTS = setOf("instapay", "wallet_cards")
}
