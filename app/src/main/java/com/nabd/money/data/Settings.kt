package com.nabd.money.data

import android.content.Context
import com.nabd.money.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Settings(context: Context) {
    private val sp = context.getSharedPreferences("nabd", Context.MODE_PRIVATE)

    private val _disabled = MutableStateFlow(sp.getStringSet("disabled_agents", emptySet())!!.toSet())
    val disabledAgents: StateFlow<Set<String>> = _disabled

    fun isEnabled(agentId: String) = agentId !in _disabled.value

    fun setEnabled(agentId: String, enabled: Boolean) {
        val next = if (enabled) _disabled.value - agentId else _disabled.value + agentId
        _disabled.value = next
        sp.edit().putStringSet("disabled_agents", next).apply()
    }

    private val _usdRate = MutableStateFlow(sp.getFloat("usd_rate", 50f).toDouble())
    /** سعر الدولار بالجنيه، علشان نجمع RedotPay و Kast و Binance مع باقي الحسابات */
    val usdRate: StateFlow<Double> = _usdRate

    /** طريقة عرض المبالغ: AUTO (بعملة كل حساب) أو EGP أو USD */
    var displayCurrency: String
        get() = sp.getString("display_currency", "AUTO")!!
        set(v) { sp.edit().putString("display_currency", v).apply(); com.nabd.money.util.Money.mode = v }

    // ---------- الدهب ----------
    private val _gold = MutableStateFlow(readGold())
    /** سعر أوقية الدهب بالدولار وآخر تحديث وتاريخ الأسعار (يوم ← سعر الأوقية) */
    data class GoldState(val ounceUsd: Double, val updatedAt: Long, val premiumPct: Double, val history: List<Pair<Long, Double>>)
    val gold: StateFlow<GoldState> = _gold

    private fun readGold(): GoldState {
        val h = runCatching {
            val o = org.json.JSONObject(sp.getString("gold_history", null) ?: return@runCatching emptyList())
            o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to o.getDouble(k) } }.sortedBy { it.first }.toList()
        }.getOrDefault(emptyList())
        return GoldState(
            sp.getFloat("gold_ounce_usd", 0f).toDouble(), sp.getLong("gold_updated_at", 0L),
            sp.getFloat("gold_premium_pct", 0f).toDouble(), h
        )
    }

    fun setGoldPremium(pct: Double) {
        sp.edit().putFloat("gold_premium_pct", pct.toFloat()).apply()
        _gold.value = _gold.value.copy(premiumPct = pct)
    }

    /** سعر الدهب من النت (بيتحدث على الأقل كل ساعة إلا لو force). بيرجع true لو نجح */
    suspend fun syncGold(force: Boolean = false): Boolean {
        if (!force && System.currentTimeMillis() - _gold.value.updatedAt < 3_600_000L) return false
        val oz = com.nabd.money.util.Gold.fetchOunceUsd() ?: return false
        val now = System.currentTimeMillis()
        val day = Dates.dayKey(now)
        val hist = (_gold.value.history.filter { it.first != day } + (day to oz)).sortedBy { it.first }.takeLast(60)
        sp.edit()
            .putFloat("gold_ounce_usd", oz.toFloat()).putLong("gold_updated_at", now)
            .putString("gold_history", org.json.JSONObject(hist.associate { it.first.toString() to it.second }).toString())
            .apply()
        _gold.value = _gold.value.copy(ounceUsd = oz, updatedAt = now, history = hist)
        return true
    }

    /** مزامنة سعر الصرف لوحدها (الافتراضي شغال). لو قفلتها السعر اللي كتبته بإيدك بيفضل ثابت */
    var autoRates: Boolean
        get() = sp.getBoolean("auto_rates", true)
        set(v) = sp.edit().putBoolean("auto_rates", v).apply()

    var ratesUpdatedAt: Long
        get() = sp.getLong("rates_updated_at", 0L)
        set(v) = sp.edit().putLong("rates_updated_at", v).apply()

    private val _ratesAt = MutableStateFlow(sp.getLong("rates_updated_at", 0L))
    val ratesAt: StateFlow<Long> = _ratesAt

    /** مزامنة من النت: بتحدّث سعر الدولار والعملات التانية. بترجع true لو نجحت */
    suspend fun syncRates(force: Boolean = false): Boolean {
        if (!force && (!autoRates || System.currentTimeMillis() - ratesUpdatedAt < 3 * 3_600_000L)) return false
        val live = com.nabd.money.util.Rates.fetch() ?: return false
        Fx.perUsd = live.perUsd
        sp.edit().putString("fx_per_usd", org.json.JSONObject(live.perUsd as Map<*, *>).toString()).apply()
        live.perUsd["EGP"]?.let { setUsdRate(it) }
        ratesUpdatedAt = System.currentTimeMillis()
        _ratesAt.value = ratesUpdatedAt
        return true
    }

    private fun loadFx() {
        Fx.perUsd = runCatching {
            val o = org.json.JSONObject(sp.getString("fx_per_usd", null) ?: return@runCatching emptyMap())
            o.keys().asSequence().associateWith { o.getDouble(it) }
        }.getOrDefault(emptyMap())
    }

    fun setUsdRate(v: Double) {
        com.nabd.money.util.Money.rate = v
        _usdRate.value = v
        sp.edit().putFloat("usd_rate", v.toFloat()).apply()
    }

    var lowBalance: Double
        get() = sp.getFloat("low_balance", 500f).toDouble()
        set(v) = sp.edit().putFloat("low_balance", v.toFloat()).apply()

    var largeSpend: Double
        get() = sp.getFloat("large_spend", 5000f).toDouble()
        set(v) = sp.edit().putFloat("large_spend", v.toFloat()).apply()

    var notifyCaptures: Boolean
        get() = sp.getBoolean("notify_captures", true)
        set(v) = sp.edit().putBoolean("notify_captures", v).apply()

    var lastDigestDay: Long
        get() = sp.getLong("last_digest_day", 0)
        set(v) = sp.edit().putLong("last_digest_day", v).apply()

    var hideBalances: Boolean
        get() = sp.getBoolean("hide_balances", false)
        set(v) { sp.edit().putBoolean("hide_balances", v).apply(); com.nabd.money.util.Money.hideBalances = v }

    private val _theme = MutableStateFlow(
        runCatching { com.nabd.money.ui.theme.ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }
            .getOrDefault(com.nabd.money.ui.theme.ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<com.nabd.money.ui.theme.ThemeMode> = _theme

    fun setTheme(m: com.nabd.money.ui.theme.ThemeMode) {
        _theme.value = m
        sp.edit().putString("theme", m.name).apply()
    }

    private val _saved = MutableStateFlow(FilterCodec.decode(sp.getString("saved_filters", null)))
    val savedFilters: StateFlow<List<SavedFilter>> = _saved

    fun setSavedFilters(list: List<SavedFilter>) {
        _saved.value = list
        sp.edit().putString("saved_filters", FilterCodec.encode(list)).apply()
    }

    // ---------- تصنيفاتك وقواعدك ----------
    private val _taxonomy = MutableStateFlow(UserTaxonomy.decode(sp.getString("taxonomy", null)))
    val taxonomy: StateFlow<UserTaxonomy> = _taxonomy

    init {
        loadFx()
        com.nabd.money.util.Money.rate = _usdRate.value
        com.nabd.money.util.Money.mode = sp.getString("display_currency", "AUTO")!!
        com.nabd.money.util.Money.hideBalances = sp.getBoolean("hide_balances", false)
        com.nabd.money.agents.Categorizer.user = _taxonomy.value
    }

    fun updateTaxonomy(transform: (UserTaxonomy) -> UserTaxonomy) {
        val t = transform(_taxonomy.value)
        _taxonomy.value = t
        com.nabd.money.agents.Categorizer.user = t
        sp.edit().putString("taxonomy", t.encode()).apply()
    }

    // ---------- النسخة الاحتياطية ----------
    /** فولدر النسخة التلقائية اليومية (Storage Access Framework) */
    var backupFolder: String?
        get() = sp.getString("backup_folder", null)
        set(v) = sp.edit().putString("backup_folder", v).apply()

    var lastBackupAt: Long
        get() = sp.getLong("last_backup_at", 0)
        set(v) = sp.edit().putLong("last_backup_at", v).apply()

    var lastAutoBackupDay: Long
        get() = sp.getLong("last_auto_backup_day", 0)
        set(v) = sp.edit().putLong("last_auto_backup_day", v).apply()

    /** بعد "ابدأ من جديد": الرسائل والإشعارات الأقدم من كده مابتتسجلش تاني */
    var ignoreBefore: Long
        get() = sp.getLong("ignore_before", 0)
        set(v) = sp.edit().putLong("ignore_before", v).apply()

    /** "امسح كل حاجة": الإعدادات ترجع زي الأول، ماعدا الشكل وسعر الدولار والأشياء الخاصة بالموبايل */
    fun resetUserSettings() {
        val keep = deviceOnly + setOf("theme", "usd_rate", "ignore_before", "last_backup_at")
        val ed = sp.edit()
        sp.all.keys.filter { it !in keep }.forEach { ed.remove(it) }
        ed.commit()
        reload()
    }

    /** مفاتيح خاصة بالموبايل ده بس، مابتتنقلش في النسخة */
    private val deviceOnly = setOf("backup_folder", "last_auto_backup_day", "did_initial_scan", "notified_update")

    fun exportPrefs(): org.json.JSONObject {
        val o = org.json.JSONObject()
        sp.all.forEach { (k, v) ->
            if (k in deviceOnly || v == null) return@forEach
            val e = org.json.JSONObject()
            when (v) {
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is String -> e.put("t", "s").put("v", v)
                is Set<*> -> e.put("t", "ss").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            o.put(k, e)
        }
        return o
    }

    fun importPrefs(o: org.json.JSONObject) {
        val ed = sp.edit()
        sp.all.keys.filter { it !in deviceOnly }.forEach { ed.remove(it) }
        o.keys().forEach { k ->
            if (k in deviceOnly) return@forEach
            val e = o.getJSONObject(k)
            when (e.getString("t")) {
                "b" -> ed.putBoolean(k, e.getBoolean("v"))
                "i" -> ed.putInt(k, e.getInt("v"))
                "l" -> ed.putLong(k, e.getLong("v"))
                "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                "s" -> ed.putString(k, e.getString("v"))
                "ss" -> e.getJSONArray("v").let { a -> ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
            }
        }
        ed.commit()
        reload()
    }

    /** بعد الرجوع لنسخة: كل القيم تتقري من جديد والشاشات تتحدث */
    fun reload() {
        _disabled.value = sp.getStringSet("disabled_agents", emptySet())!!.toSet()
        _usdRate.value = sp.getFloat("usd_rate", 50f).toDouble()
        com.nabd.money.util.Money.rate = _usdRate.value
        com.nabd.money.util.Money.mode = displayCurrency
        com.nabd.money.util.Money.hideBalances = hideBalances
        loadFx()
        _ratesAt.value = ratesUpdatedAt
        _gold.value = readGold()
        _theme.value = runCatching { com.nabd.money.ui.theme.ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }
            .getOrDefault(com.nabd.money.ui.theme.ThemeMode.SYSTEM)
        _saved.value = FilterCodec.decode(sp.getString("saved_filters", null))
        _taxonomy.value = UserTaxonomy.decode(sp.getString("taxonomy", null))
        com.nabd.money.agents.Categorizer.user = _taxonomy.value
        _salary.value = readSalary()
        _hiddenAccounts.value = readHiddenAccounts()
    }

    /** آخر نسخة اتبعت عنها إشعار تحديث */
    var notifiedUpdate: Int
        get() = sp.getInt("notified_update", 0)
        set(v) = sp.edit().putInt("notified_update", v).apply()

    /** الصوت: احفظ على طول من غير شاشة مراجعة */
    var voiceAutoSave: Boolean
        get() = sp.getBoolean("voice_auto_save", false)
        set(v) = sp.edit().putBoolean("voice_auto_save", v).apply()

    var didInitialScan: Boolean
        get() = sp.getBoolean("did_initial_scan", false)
        set(v) = sp.edit().putBoolean("did_initial_scan", v).apply()

    // ---------- الكروت والحسابات المربوطة ببعض ----------
    /** "agentId|مفتاح" → مفتاح الحساب اللي اتدمج فيه (كارت ••1234 ورقم الحساب نفسه مثلاً) */
    fun accountAliases(): Map<String, String> = runCatching {
        val o = org.json.JSONObject(sp.getString("account_aliases", null) ?: return emptyMap())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun accountAlias(agentId: String, key: String): String? = accountAliases()["$agentId|$key"]

    fun setAccountAlias(agentId: String, from: String, to: String) {
        if (from == to) return
        val all = accountAliases().toMutableMap()
        // اللي كان مربوط بالحساب القديم يتنقل للجديد
        all.keys.filter { it.startsWith("$agentId|") && all[it] == from }.forEach { all[it] = to }
        all["$agentId|$from"] = to
        all.remove("$agentId|$to")
        sp.edit().putString("account_aliases", org.json.JSONObject(all as Map<*, *>).toString()).apply()
    }

    // ---------- حسابات مخفية ----------
    private val _hiddenAccounts = MutableStateFlow(readHiddenAccounts())
    /** حسابات انت أخفيتها: مابتتحسب في الإجمالي ولا عملياتها في أي قايمة أو تقرير */
    val hiddenAccounts: StateFlow<Set<Long>> = _hiddenAccounts

    private fun readHiddenAccounts() = sp.getStringSet("hidden_accounts", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun setAccountHidden(id: Long, hidden: Boolean) {
        val next = if (hidden) _hiddenAccounts.value + id else _hiddenAccounts.value - id
        sp.edit().putStringSet("hidden_accounts", next.map { it.toString() }.toSet()).apply()
        _hiddenAccounts.value = next
    }

    /** العمليات اللي انت أخفيتها من الحساب (علشان نفرق بينها وبين التحويلات الداخلية اللي بتتستبعد لوحدها) */
    var userHidden: Set<Long>
        get() = sp.getStringSet("user_hidden", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("user_hidden", v.map { it.toString() }.toSet()).apply()

    /** بيانات فاتورة الشركة: اسمك واسم الشركة (الافتراضي من شركة المرتب) */
    var invoiceEmployee: String
        get() = sp.getString("invoice_employee", "")!!
        set(v) = sp.edit().putString("invoice_employee", v).apply()

    var invoiceCompany: String
        get() = sp.getString("invoice_company", "")!!
        set(v) = sp.edit().putString("invoice_company", v).apply()

    /** آخر حساب سجلت عليه عملية يدوي، علشان يتختار لوحده المرة الجاية (-1 = كاش) */
    var lastAccountId: Long
        get() = sp.getLong("last_account_id", -1L)
        set(v) = sp.edit().putLong("last_account_id", v).apply()

    /** ترتيب الحسابات المكررة القديمة اتعمل مرة */
    var accountsTidied: Int
        get() = sp.getInt("accounts_tidied", 0)
        set(v) = sp.edit().putInt("accounts_tidied", v).apply()

    // ---------- المرتب ----------
    private val _salary = MutableStateFlow(readSalary())
    val salary: StateFlow<SalaryConfig> = _salary

    private fun readSalary() = SalaryConfig(
        payer = sp.getString("salary_payer", "").orEmpty(),
        fixedDay = sp.getInt("salary_fixed_day", 0),
        learnedDay = sp.getInt("salary_learned_day", 0),
        lastAmount = sp.getFloat("salary_last_amount", 0f).toDouble(),
        lastAt = sp.getLong("salary_last_at", 0),
        amount = sp.getFloat("salary_amount", 0f).toDouble(),
        exactOnly = sp.getBoolean("salary_exact", false),
    )

    fun updateSalary(transform: (SalaryConfig) -> SalaryConfig) {
        val s = transform(_salary.value)
        sp.edit()
            .putString("salary_payer", s.payer)
            .putInt("salary_fixed_day", s.fixedDay)
            .putInt("salary_learned_day", s.learnedDay)
            .putFloat("salary_last_amount", s.lastAmount.toFloat())
            .putLong("salary_last_at", s.lastAt)
            .putFloat("salary_amount", s.amount.toFloat())
            .putBoolean("salary_exact", s.exactOnly)
            .apply()
        _salary.value = s
    }

    fun toEgp(amount: Double, currency: String) = Fx.toEgp(amount, currency, _usdRate.value)
}

/**
 * payer: اسم الشركة زي ما بيظهر في إشعار InstaPay أو رسالة البنك.
 * fixedDay: يوم القبض لو المستخدم حدده (0 = الوكيل يتعلمه لوحده من learnedDay).
 */
data class SalaryConfig(
    val payer: String,
    val fixedDay: Int,
    val learnedDay: Int,
    val lastAmount: Double,
    val lastAt: Long,
    /** مبلغ المرتب اللي انت كاتبه (0 = أي مبلغ) */
    val amount: Double = 0.0,
    /** true = المبلغ ده بالظبط بس، false = المبلغ ده أو أكتر */
    val exactOnly: Boolean = false,
) {
    val enabled get() = payer.isNotBlank()
    val payday get() = if (fixedDay in 1..31) fixedDay else learnedDay
}

object Fx {
    /** أسعار العملات مقابل دولار واحد من المزامنة (EUR = 0.92 يعني 1$ = 0.92€). لو فاضية بنستخدم أسعار تقريبية */
    @Volatile var perUsd: Map<String, Double> = emptyMap()

    private val fallbackPerUsd = mapOf("EUR" to 1 / 1.08, "GBP" to 1 / 1.27, "SAR" to 3.75, "AED" to 3.6725)

    private fun per(c: String): Double? = perUsd[c] ?: fallbackPerUsd[c]

    /** عملات الكريبتو الغير مستقرة (BTC...) مابتتحسبش في الإجمالي */
    fun toEgp(amount: Double, currency: String, usd: Double): Double = when (val c = currency.uppercase()) {
        "EGP" -> amount
        "USD", "USDT", "USDC" -> amount * usd
        "EUR", "GBP", "SAR", "AED" -> per(c)?.let { amount * usd / it } ?: 0.0
        else -> 0.0
    }

    /** تحويل بين أي عملتين من اللي بنعرفهم، أو null لو عملة مش معروفة */
    fun convert(amount: Double, from: String, to: String, usd: Double): Double? {
        val egp = toEgp(amount, from, usd)
        if (egp == 0.0 && amount != 0.0) return null
        val k = to.uppercase()
        val one = toEgp(1.0, k, usd)
        return if (one == 0.0) null else egp / one
    }
}
