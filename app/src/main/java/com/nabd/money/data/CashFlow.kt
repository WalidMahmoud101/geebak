package com.nabd.money.data

/** سطر في كشف الداخل والخارج، وممكن يكون جواه تفاصيل (الغرض → التصنيفات) */
data class FlowLine(
    val key: String,
    val label: String,
    val amount: Double,
    val count: Int,
    val purpose: Purpose? = null,
    val children: List<FlowLine> = emptyList(),
)

/** داخل وخارج حساب واحد في الفترة */
data class AccountFlow(
    val accountId: Long?,
    val inflow: Double,
    val outflow: Double,
) {
    val net get() = inflow - outflow
}

/**
 * كشف الداخل والخارج زي كشف الحساب:
 * - الداخل: مرتب، تحويلات واردة، دخل تاني…
 * - الخارج: حسب الغرض (البيت، العربية…) وجوه كل غرض التصنيفات
 * - الصافي = الداخل − الخارج
 * - "برا الحساب": حاجات بتتحرك بس مابتغيرش فلوسك (تحويل بين حساباتك، مصاريف الشركة اللي هتسترد، الاسترداد نفسه)
 */
data class CashFlow(
    val inflow: List<FlowLine>,
    val outflow: List<FlowLine>,
    val totalIn: Double,
    val totalOut: Double,
    val transfers: Double,
    val transfersCount: Int,
    val companyOut: Double,
    val companyCount: Int,
    val companyRefund: Double,
    val perAccount: List<AccountFlow>,
) {
    val net get() = totalIn - totalOut

    /** نسبة اللي فضل من الدخل */
    val savingsRate: Double? get() = if (totalIn > 0) net / totalIn else null

    companion object {
        const val TRANSFER = "تحويل بين حساباتي"
        const val REFUND = "استرداد مصاريف الشركة"

        fun of(txns: List<Txn>, egp: (Txn) -> Double): CashFlow {
            val counted = txns.filter { it.countsInTotals() }
            val ins = counted.filter { it.direction == Direction.IN }
            val outs = counted.filter { it.direction == Direction.OUT }

            val inflow = ins.groupBy { it.category }.map { (cat, list) ->
                FlowLine("in:$cat", cat, list.sumOf(egp), list.size)
            }.sortedByDescending { it.amount }

            val outflow = outs.groupBy { it.purpose }.map { (p, list) ->
                FlowLine(
                    key = "out:${p.name}",
                    label = p.ar,
                    amount = list.sumOf(egp),
                    count = list.size,
                    purpose = p,
                    children = list.groupBy { it.category }
                        .map { (cat, l) -> FlowLine("out:${p.name}:$cat", cat, l.sumOf(egp), l.size, p) }
                        .sortedByDescending { it.amount },
                )
            }.sortedByDescending { it.amount }

            // التحويل بين حساباتك ليه طرفين (خارج من حساب وداخل لحساب)، بنعد الطرف الخارج بس
            val transfers = txns.filter { it.excluded && it.category == TRANSFER && it.direction == Direction.OUT }
            val company = txns.filter { it.reimbursable && it.direction == Direction.OUT }
            val refunds = txns.filter { it.category == REFUND && it.direction == Direction.IN }

            // لكل حساب: كل الفلوس اللي دخلت وخرجت فعلاً (حتى التحويلات بين حساباتك)
            val perAccount = txns.groupBy { it.accountId }.map { (id, list) ->
                AccountFlow(
                    accountId = id,
                    inflow = list.filter { it.direction == Direction.IN }.sumOf(egp),
                    outflow = list.filter { it.direction == Direction.OUT }.sumOf(egp),
                )
            }.sortedByDescending { it.inflow + it.outflow }

            return CashFlow(
                inflow = inflow,
                outflow = outflow,
                totalIn = ins.sumOf(egp),
                totalOut = outs.sumOf(egp),
                transfers = transfers.sumOf(egp),
                transfersCount = transfers.size,
                companyOut = company.sumOf(egp),
                companyCount = company.size,
                companyRefund = refunds.sumOf(egp),
                perAccount = perAccount,
            )
        }
    }
}
