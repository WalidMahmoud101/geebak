package com.nabd.money.agents

import com.nabd.money.data.Txn
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * لما الشركة تحوّلك فلوس: أنهي مصاريف مستحقة هي بتسددها؟
 * 1) لو كلهم أقل من المبلغ أو بيساووه: كلهم
 * 2) لو في مجموعة بتساوي المبلغ بالظبط: هي
 * 3) غير كده: الأقدم الأول طول ما المجموع مايزيدش عن المبلغ
 */
object CompanyMatch {
    fun pick(pending: List<Txn>, target: Double, egp: (Txn) -> Double = { it.amount }): Set<Long> {
        val items = pending.sortedBy { it.timestamp }
        if (items.isEmpty() || target <= 0) return emptySet()
        val total = items.sumOf(egp)
        if (total <= target + 0.5) return items.map { it.id }.toSet()

        // مجموعة بالظبط (بالقرش)
        val goal = (target * 100).roundToLong()
        val reach = HashMap<Long, List<Long>>()
        reach[0L] = emptyList()
        for (t in items) {
            val v = (egp(t) * 100).roundToLong()
            if (v <= 0) continue
            for ((sum, ids) in reach.entries.toList()) {
                val ns = sum + v
                if (ns <= goal && ns !in reach && reach.size < 40_000) reach[ns] = ids + t.id
            }
        }
        reach[goal]?.let { return it.toSet() }

        var sum = 0.0
        val out = mutableSetOf<Long>()
        for (t in items) {
            val v = egp(t)
            if (sum + v <= target + 0.5) { sum += v; out += t.id }
        }
        return out
    }

    /** عهدة الشركة: اللي دخلتلك − اللي رديته − اللي خصمته من مصاريفك */
    fun custody(txns: List<Txn>, egp: (Txn) -> Double = { it.amount }): Double =
        txns.sumOf { t ->
            when (t.category) {
                AgentEngine.CUSTODY_IN -> egp(t)
                AgentEngine.CUSTODY_RETURN, AgentEngine.CUSTODY_USED -> -egp(t)
                else -> 0.0
            }
        }

    fun sum(pending: List<Txn>, ids: Set<Long>, egp: (Txn) -> Double = { it.amount }) =
        pending.filter { it.id in ids }.sumOf(egp)

    @Suppress("unused")
    private fun near(a: Double, b: Double) = abs(a - b) < 0.5
}
