package com.nabd.money.agents

import com.nabd.money.data.StockPrice

enum class PriceLevel(val ar: String) { FIRST("أول سعر"), CHEAP("رخيص"), SAME("زي اللي فات"), EXPENSIVE("غالي") }

data class PriceVerdict(
    val level: PriceLevel,
    /** آخر سعر قبل الحالي */
    val last: StockPrice?,
    /** الفرق عن آخر سعر بالنسبة المئوية: موجب = أغلى */
    val percent: Double,
    /** أقل سعر جبت بيه الحاجة دي قبل كده */
    val lowest: Double?,
)

/** السعر ده غالي ولا رخيص مقارنة بآخر مرة جبت فيها نفس الحاجة */
object PriceCheck {
    /** أقل من كده فرق عادي مش غلاء: 3% أو نص جنيه، أيهم أكبر */
    private const val SAME_PERCENT = 3.0
    private const val SAME_POUNDS = 0.5

    fun check(price: Double, history: List<StockPrice>): PriceVerdict {
        val last = history.maxWithOrNull(compareBy({ it.at }, { it.id }))
            ?: return PriceVerdict(PriceLevel.FIRST, null, 0.0, null)
        val diff = price - last.price
        val percent = if (last.price > 0) diff / last.price * 100 else 0.0
        val level = when {
            kotlin.math.abs(diff) <= maxOf(last.price * SAME_PERCENT / 100, SAME_POUNDS) -> PriceLevel.SAME
            diff > 0 -> PriceLevel.EXPENSIVE
            else -> PriceLevel.CHEAP
        }
        return PriceVerdict(level, last, percent, history.minOf { it.price })
    }

    /** آخر سعر متسجل مقارنة باللي قبله: للي بيظهر في قايمة المخزون */
    fun latest(history: List<StockPrice>): PriceVerdict? {
        val sorted = history.sortedWith(compareBy({ it.at }, { it.id }))
        val newest = sorted.lastOrNull() ?: return null
        return check(newest.price, sorted.dropLast(1))
    }
}
