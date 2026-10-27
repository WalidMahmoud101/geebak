package com.nabd.money.data

import android.content.Context
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

    fun setUsdRate(v: Double) {
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
        com.nabd.money.agents.Categorizer.user = _taxonomy.value
    }

    fun updateTaxonomy(transform: (UserTaxonomy) -> UserTaxonomy) {
        val t = transform(_taxonomy.value)
        _taxonomy.value = t
        com.nabd.money.agents.Categorizer.user = t
        sp.edit().putString("taxonomy", t.encode()).apply()
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
    /** عملات الكريبتو الغير مستقرة (BTC...) مابتتحسبش في الإجمالي */
    fun toEgp(amount: Double, currency: String, usd: Double): Double = when (currency.uppercase()) {
        "EGP" -> amount
        "USD", "USDT", "USDC" -> amount * usd
        "EUR" -> amount * usd * 1.08
        "GBP" -> amount * usd * 1.27
        "SAR" -> amount * usd / 3.75
        "AED" -> amount * usd / 3.6725
        else -> 0.0
    }
}
