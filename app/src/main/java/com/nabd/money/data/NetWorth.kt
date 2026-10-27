package com.nabd.money.data

import com.nabd.money.agents.AgentRegistry
import com.nabd.money.agents.GenericBankAgent

/** ليه حساب مش داخل في إجمالي فلوسك */
enum class Exclusion(val ar: String) {
    UNKNOWN("الرصيد مش مؤكد، اكتب رصيدك الحقيقي"),
    CREDIT("كارت ائتمان: ده الحد المتاح مش فلوسك"),
    INTERMEDIATE("حساب وسيط: فلوسه محسوبة في البنك"),
    CURRENCY("عملة سعرها مش محسوب"),
    USER("انت شيلته من الإجمالي"),
}

/** بنك أو تطبيق واحد (CIB، فودافون كاش، Binance…) بكل حساباته وكروته */
data class Institution(
    val key: String,
    val name: String,
    val agentId: String,
    val accounts: List<Account>,
    /** مجموع الحسابات المحسوبة بس، بالجنيه */
    val total: Double,
    val countedCount: Int,
) {
    val needsBalance get() = accounts.any { NetWorth.exclusion(it, 1.0) == Exclusion.UNKNOWN }
}

/**
 * إجمالي فلوسك = مجموع الحسابات اللي رصيدها مؤكد (من رسالة البنك أو انت كتبته).
 * الرصيد التقديري (محسوب من العمليات من غير نقطة بداية) مابيدخلش، علشان ممكن يكون بعيد جداً عن الحقيقة.
 */
object NetWorth {

    fun exclusion(a: Account, egpValue: Double): Exclusion? = when {
        a.isCredit -> Exclusion.CREDIT
        !a.includeInTotal -> if (a.agentId in AgentRegistry.MIRROR_AGENTS) Exclusion.INTERMEDIATE else Exclusion.USER
        !a.balanceKnown -> Exclusion.UNKNOWN
        a.balance != 0.0 && egpValue == 0.0 -> Exclusion.CURRENCY
        else -> null
    }

    fun counts(a: Account, egp: (Account) -> Double) = exclusion(a, egp(a)) == null

    fun total(accounts: List<Account>, egp: (Account) -> Double): Double =
        accounts.filter { counts(it, egp) }.sumOf(egp)

    fun institutionKey(a: Account): String = when (a.agentId) {
        AgentRegistry.MANUAL_ID -> "manual:${a.id}"
        // البنوك الأخرى: مفتاح الحساب = اسم المرسل + آخر 4 أرقام
        "other_banks" -> "other:" + GenericBankAgent.bankKey(a.accountKey.substringBefore('-'))
        else -> a.agentId
    }

    fun institutionName(a: Account): String = when (a.agentId) {
        AgentRegistry.MANUAL_ID -> a.name
        "other_banks" -> a.accountKey.substringBefore('-').let { key ->
            // اسم المرسل زي ما البنك كاتبه ("HSBC-EG") لو هو نفس البنك، وإلا المفتاح
            a.name.substringBefore(" ••••").trim().takeIf { GenericBankAgent.bankKey(it) == GenericBankAgent.bankKey(key) } ?: key
        }
        else -> AgentRegistry.byId(a.agentId)?.name?.removePrefix("وكيل ") ?: a.name
    }

    fun institutions(accounts: List<Account>, egp: (Account) -> Double): List<Institution> =
        accounts.groupBy(::institutionKey).map { (key, list) ->
            val counted = list.filter { counts(it, egp) }
            Institution(
                key = key,
                name = institutionName(list.first()),
                agentId = list.first().agentId,
                accounts = list.sortedWith(compareBy({ it.isCredit }, { it.name })),
                total = counted.sumOf(egp),
                countedCount = counted.size,
            )
        }.sortedWith(compareByDescending<Institution> { it.total }.thenBy { it.name })
}
