package com.nabd.money.agents

import android.content.Context
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Insight
import com.nabd.money.data.NabdDao
import com.nabd.money.data.Purpose
import com.nabd.money.data.Settings
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnSource
import com.nabd.money.data.countsInTotals
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import com.nabd.money.util.Notifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth
import java.util.UUID
import kotlin.math.abs

/**
 * المنسق: بيوزع كل رسالة/إشعار على الوكيل المتخصص، يسجل العملية، يحدث الرصيد،
 * وبعدين يشغل وكلاء التحليل.
 */
class AgentEngine(
    private val context: Context,
    private val dao: NabdDao,
    private val settings: Settings,
) {
    private val mutex = Mutex()

    suspend fun ingest(m: RawMessage, notify: Boolean = true): Txn? = mutex.withLock { ingestLocked(m, notify) }

    private suspend fun ingestLocked(m: RawMessage, notify: Boolean): Txn? {
        val agent = AgentRegistry.sources.firstOrNull { settings.isEnabled(it.id) && it.matches(m) } ?: return null
        val p = agent.parse(m) ?: return null
        val account = resolveAccount(agent, m, p)

        val amount = p.amount
        val dir = p.direction
        if (amount == null || dir == null || amount <= 0.0) {
            // رسالة رصيد بس (استعلام رصيد)
            if (p.balance != null) applyKnownBalance(account, p.balance, m.timestamp)
            return null
        }

        // نفس المبلغ على نفس الحساب في خلال 3 دقايق = نفس العملية (رسالة + إشعار)
        val window = 3 * 60_000L
        if (dao.similarCount(account.id, amount, dir.name, m.timestamp - window, m.timestamp + window) > 0) return null

        // وكلاء "المرايا": إشعار InstaPay أو Wallet بيكرر رسالة البنك لنفس العملية → نسجلها مرة واحدة بس
        val mirrorWindow = 5 * 60_000L
        var mirrorMerchant: String? = null
        if (agent.id in MIRROR_AGENTS) {
            val primary = AgentRegistry.sources.map { it.id }.filter { it !in MIRROR_AGENTS }
            if (dao.findFromAgents(primary, amount, dir.name, m.timestamp - mirrorWindow, m.timestamp + mirrorWindow) != null) return null
        } else {
            dao.findFromAgents(MIRROR_AGENTS.toList(), amount, dir.name, m.timestamp - mirrorWindow, m.timestamp + mirrorWindow)
                ?.let { mirror ->
                    mirrorMerchant = mirror.merchant
                    removeMirror(mirror)
                }
        }

        val salaryCfg = settings.salary.value
        val isSalary = dir == Direction.IN && settings.isEnabled(SalaryWatch.ID) &&
            SalaryWatch.matchesPayer(salaryCfg.payer, p.merchant, m.title + " " + m.text)
        // تحويل من الشركة بنفس إجمالي مصاريف الشركة المستحقة = الشركة رجعتلك فلوسك
        val pendingCompany = if (isSalary) dao.companyPendingOnce() else emptyList()
        val isRefund = pendingCompany.isNotEmpty() &&
            abs(pendingCompany.sumOf { settings.toEgp(it.amount, it.currency) } - settings.toEgp(amount, p.currency)) < 1.0
        val cls = when {
            isRefund -> Classification(REFUND_CATEGORY, Purpose.WORK)
            isSalary -> Classification(SALARY_CATEGORY, Purpose.WORK)
            settings.isEnabled(Categorizer.ID) -> Categorizer.classify(p.merchant, m.text, dir, agent.id)
            else -> Classification(if (dir == Direction.IN) "دخل" else "أخرى", Purpose.OTHER)
        }

        val txn = Txn(
            accountId = account.id,
            amount = amount,
            currency = p.currency,
            direction = dir,
            balanceAfter = p.balance,
            merchant = p.merchant ?: mirrorMerchant,
            category = cls.category,
            purpose = cls.purpose,
            timestamp = m.timestamp,
            source = m.channel,
            agentId = agent.id,
            rawText = m.text.take(600),
            fingerprint = "${agent.id}|${m.text.trim().hashCode()}|${m.timestamp / 600_000}",
            excluded = isRefund,
        )
        val id = dao.insertTxn(txn)
        if (id <= 0) return null
        var saved = txn.copy(id = id)

        // تحويل بين حساباتك (من البنك للمحفظة): الطرفين مايتحسبوش مصروف ولا دخل
        if (!isSalary && !isRefund) {
            val opposite = if (dir == Direction.IN) Direction.OUT else Direction.IN
            val tw = 10 * 60_000L
            dao.findCounterpart(account.id, amount, opposite.name, p.currency, m.timestamp - tw, m.timestamp + tw)
                ?.let { other ->
                    dao.updateTxn(other.copy(excluded = true, category = TRANSFER_CATEGORY, purpose = Purpose.OTHER))
                    saved = saved.copy(excluded = true, category = TRANSFER_CATEGORY, purpose = Purpose.OTHER)
                    dao.updateTxn(saved)
                }
        }

        var updated = account
        if (p.balance != null) {
            updated = applyKnownBalance(account, p.balance, m.timestamp)
        } else if (p.currency.equals(account.currency, ignoreCase = true) && m.timestamp >= account.balanceAt) {
            val delta = if (dir == Direction.IN) amount else -amount
            updated = account.copy(balance = account.balance + delta, balanceAt = m.timestamp, updatedAt = now())
            dao.updateAccount(updated)
        }

        if (isRefund) {
            dao.setReimbursed(pendingCompany.map { it.id }, m.timestamp)
            emit(
                Insight(
                    agentId = SalaryWatch.ID, title = "الشركة رجعتلك مصاريفك",
                    body = "استلمت ${Money.fmt(amount, p.currency)} من ${salaryCfg.payer} واتقفلت ${pendingCompany.size} عملية من قايمة مصاريف الشركة.",
                    severity = 1, timestamp = now(), dedupKey = "refund|${saved.id}"
                ),
                notify
            )
        } else if (isSalary) onSalary(saved, notify)
        if (notify) {
            if (settings.notifyCaptures) Notifier.capture(context, saved, updated)
            realtimeInsights(saved, notify = true)
        }
        return saved
    }

    // ------------------------------------------------------------------ وكيل المرتب

    private suspend fun onSalary(t: Txn, notify: Boolean) {
        val before = settings.salary.value
        learnSalary()
        val day = Dates.toLocal(t.timestamp).toLocalDate()
        val payday = before.payday.takeIf { it in 1..31 }
        val timing = if (payday == null) {
            "هتعلم ميعاد قبضك من المرتبات الجاية."
        } else {
            val expected = SalaryWatch.expectedDate(payday, YearMonth.from(day))
            val diff = java.time.temporal.ChronoUnit.DAYS.between(expected, day).toInt()
            when {
                diff > 0 -> "وصل متأخر $diff يوم عن ميعاده (يوم $payday)."
                diff < 0 -> "وصل بدري ${-diff} يوم قبل ميعاده (يوم $payday)."
                else -> "وصل في ميعاده بالظبط."
            }
        }
        emit(
            Insight(
                agentId = SalaryWatch.ID, title = "المرتب وصل: ${Money.fmt(t.amount, t.currency)}",
                body = "من ${before.payer} في ${Dates.date(t.timestamp)}. $timing",
                severity = 1, timestamp = now(), dedupKey = "salary_in|${t.id}"
            ),
            notify
        )
    }

    /** يتعلم يوم القبض وآخر مبلغ من كل المرتبات المسجلة */
    private suspend fun learnSalary() {
        val since = now() - 400L * 86_400_000
        val salaries = dao.betweenOnce(since, now())
            .filter { it.direction == Direction.IN && it.category == SALARY_CATEGORY }
            .sortedBy { it.timestamp }
        val last = salaries.lastOrNull()
        settings.updateSalary { s ->
            s.copy(
                learnedDay = SalaryWatch.learnDay(salaries.map { Dates.toLocal(it.timestamp).dayOfMonth }),
                lastAmount = last?.amount ?: s.lastAmount,
                lastAt = last?.timestamp ?: s.lastAt,
            )
        }
    }

    /** المستخدم حدد اسم الشركة: نرجع نعلم العمليات القديمة كمرتب ونتعلم الميعاد */
    suspend fun configureSalary(payer: String, fixedDay: Int) = mutex.withLock {
        settings.updateSalary { it.copy(payer = payer.trim(), fixedDay = fixedDay) }
        if (payer.isNotBlank()) {
            dao.betweenOnce(now() - 400L * 86_400_000, now())
                .filter {
                    it.direction == Direction.IN && it.category != SALARY_CATEGORY &&
                        SalaryWatch.matchesPayer(payer, it.merchant, it.rawText ?: it.merchant.orEmpty())
                }
                .forEach { dao.updateTxn(it.copy(category = SALARY_CATEGORY, purpose = Purpose.WORK)) }
        }
        learnSalary()
        salaryWatch(notify = false)
    }

    private suspend fun salaryWatch(notify: Boolean) {
        val st = SalaryWatch.status(settings.salary.value) ?: return
        val payer = settings.salary.value.payer
        val ym = YearMonth.from(st.expected)
        when {
            !st.received && st.daysLate > 0 -> emit(
                Insight(
                    agentId = SalaryWatch.ID,
                    title = "المرتب متأخر ${st.daysLate} يوم",
                    body = "ميعاده كان ${Dates.date(Dates.toEpoch(st.expected))} ولسه مافيش تحويل من $payer.",
                    severity = if (st.daysLate >= 3) 2 else 1,
                    timestamp = now(),
                    dedupKey = "salary_late|$ym|${st.daysLate}"
                ),
                notify
            )
            !st.received && st.daysLeft == 1 -> emit(
                Insight(
                    agentId = SalaryWatch.ID, title = "بكرة ميعاد القبض",
                    body = "مستني تحويل من $payer بكرة${if (st.lastAmount > 0) " (آخر مرة ${Money.fmt(st.lastAmount)})" else ""}.",
                    severity = 1, timestamp = now(), dedupKey = "salary_soon|$ym"
                ),
                notify
            )
        }
    }

    private suspend fun removeMirror(mirror: Txn) {
        dao.deleteTxn(mirror)
        val a = mirror.accountId?.let { dao.account(it) } ?: return
        if (!a.balanceKnown && mirror.currency.equals(a.currency, ignoreCase = true)) {
            val delta = if (mirror.direction == Direction.IN) mirror.amount else -mirror.amount
            dao.updateAccount(a.copy(balance = a.balance - delta, updatedAt = now()))
        }
    }

    private suspend fun resolveAccount(agent: SourceAgent, m: RawMessage, p: ParsedMoney): Account {
        val key = agent.accountKey(m, p)
        dao.findAccount(agent.id, key)?.let { existing ->
            if (p.isCredit && !existing.isCredit) {
                val u = existing.copy(isCredit = true, type = AccountType.CARD, includeInTotal = false)
                dao.updateAccount(u)
                return u
            }
            return existing
        }
        dao.insertAccount(
            Account(
                name = agent.accountLabel(m, p),
                type = if (p.isCredit) AccountType.CARD else agent.accountType,
                agentId = agent.id,
                accountKey = key,
                currency = agent.accountCurrency(p),
                includeInTotal = agent.id !in MIRROR_AGENTS && !p.isCredit,
                isCredit = p.isCredit,
            )
        )
        return dao.findAccount(agent.id, key)!!
    }

    private suspend fun applyKnownBalance(a: Account, balance: Double, at: Long): Account {
        if (at < a.balanceAt) return a
        val u = a.copy(balance = balance, balanceKnown = true, balanceAt = at, updatedAt = now())
        dao.updateAccount(u)
        return u
    }

    // ------------------------------------------------------------------ إدخال يدوي

    suspend fun addManual(
        amount: Double,
        direction: Direction,
        purpose: Purpose,
        category: String,
        merchant: String?,
        accountId: Long?,
        timestamp: Long,
        note: String?,
        reimbursable: Boolean = false,
        currency: String? = null,
        excluded: Boolean = false,
    ) = mutex.withLock {
        val account = accountId?.let { dao.account(it) }
        val txn = Txn(
            accountId = account?.id,
            amount = amount,
            currency = currency ?: account?.currency ?: "EGP",
            direction = direction,
            merchant = merchant?.takeIf { it.isNotBlank() },
            category = category,
            purpose = purpose,
            timestamp = timestamp,
            source = TxnSource.MANUAL,
            agentId = AgentRegistry.MANUAL_ID,
            note = note?.takeIf { it.isNotBlank() },
            fingerprint = "manual|" + UUID.randomUUID(),
            reimbursable = reimbursable && direction == Direction.OUT,
            excluded = excluded,
        )
        val id = dao.insertTxn(txn)
        if (account != null && (currency == null || currency.equals(account.currency, ignoreCase = true))) {
            val delta = if (direction == Direction.IN) amount else -amount
            dao.updateAccount(account.copy(balance = account.balance + delta, updatedAt = now()))
        }
        realtimeInsights(txn.copy(id = id), notify = true)
    }

    suspend fun deleteTxn(t: Txn) = mutex.withLock {
        dao.deleteTxn(t)
        if (t.source == TxnSource.MANUAL && t.accountId != null) {
            dao.account(t.accountId)?.let { a ->
                val delta = if (t.direction == Direction.IN) -t.amount else t.amount
                dao.updateAccount(a.copy(balance = a.balance + delta, updatedAt = now()))
            }
        }
    }

    suspend fun addManualAccount(name: String, type: AccountType, currency: String, balance: Double) {
        dao.insertAccount(
            Account(
                name = name,
                type = type,
                agentId = AgentRegistry.MANUAL_ID,
                accountKey = UUID.randomUUID().toString(),
                balance = balance,
                currency = currency,
                balanceKnown = true,
                balanceAt = now(),
            )
        )
    }

    // ------------------------------------------------------------------ وكلاء التحليل

    private fun egp(t: Txn) = settings.toEgp(t.amount, t.currency)

    private suspend fun emit(i: Insight, notify: Boolean) {
        val id = dao.insertInsight(i)
        if (id > 0 && notify && i.severity >= 1) Notifier.insight(context, i)
    }

    /** بيشتغلوا بعد كل عملية: كاشف المصاريف الغريبة + مراقب الميزانية */
    private suspend fun realtimeInsights(t: Txn, notify: Boolean) {
        if (t.direction != Direction.OUT || !t.countsInTotals()) return
        if (settings.isEnabled("anomaly")) anomaly(t, notify)
        if (settings.isEnabled("budget")) budgetFor(t.purpose, notify)
    }

    private suspend fun anomaly(t: Txn, notify: Boolean) {
        val value = egp(t)
        if (value <= 0) return
        val since = t.timestamp - 90L * 86_400_000
        val history = dao.betweenOnce(since, t.timestamp - 1)
            .filter { it.direction == Direction.OUT && it.category == t.category && it.countsInTotals() }
            .map { egp(it) }
            .filter { it > 0 }
        val avg = if (history.size >= 5) history.average() else 0.0
        val isLarge = value >= settings.largeSpend
        val isUnusual = avg > 0 && value > avg * 3 && value > 300
        if (!isLarge && !isUnusual) return
        val where = t.merchant ?: t.category
        val body = if (isUnusual) {
            "صرفت ${Money.fmt(t.amount, t.currency)} على $where، ده أكتر من 3 أضعاف متوسطك (${Money.fmt(avg)}) في ${t.category}."
        } else {
            "عملية كبيرة: ${Money.fmt(t.amount, t.currency)} على $where. لو مش انت، كلم البنك فوراً."
        }
        emit(
            Insight(
                agentId = "anomaly", title = "مصروف غير معتاد", body = body, severity = 2,
                timestamp = now(), dedupKey = "anomaly|${t.id}"
            ),
            notify
        )
    }

    private suspend fun budgetFor(purpose: Purpose, notify: Boolean) {
        val limit = dao.budgetsOnce().firstOrNull { it.category == purpose.name }?.monthlyLimit ?: return
        if (limit <= 0) return
        val ym = YearMonth.now()
        val (from, to) = Dates.monthRange(ym)
        val spent = dao.betweenOnce(from, to)
            .filter { it.direction == Direction.OUT && it.purpose == purpose && it.countsInTotals() }
            .sumOf { egp(it) }
        val pct = spent / limit
        val level = when {
            pct >= 1.0 -> 100
            pct >= 0.8 -> 80
            else -> return
        }
        val title = if (level == 100) "عديت ميزانية ${purpose.ar}" else "قربت تخلص ميزانية ${purpose.ar}"
        val body = "صرفت ${Money.fmt(spent)} من ${Money.fmt(limit)} (${(pct * 100).toInt()}%) الشهر ده على ${purpose.ar}."
        emit(
            Insight(
                agentId = "budget", title = title, body = body, severity = if (level == 100) 2 else 1,
                timestamp = now(), dedupKey = "budget|${purpose.name}|$ym|$level"
            ),
            notify
        )
    }

    /** بيشتغلوا دورياً (كل 12 ساعة) ولما تفتح التطبيق */
    suspend fun runPeriodicInsights(notify: Boolean) {
        if (settings.isEnabled(SalaryWatch.ID)) salaryWatch(notify)
        if (settings.isEnabled("balance_guard")) balanceGuard(notify)
        if (settings.isEnabled("subscriptions")) subscriptions(notify)
        if (settings.isEnabled("budget")) Purpose.entries.forEach { budgetFor(it, notify) }
    }

    private suspend fun balanceGuard(notify: Boolean) {
        val threshold = settings.lowBalance
        val today = Dates.dayKey(now())
        for (a in dao.accountsOnce()) {
            if (!a.balanceKnown) continue
            val v = settings.toEgp(a.balance, a.currency)
            if (a.currency.uppercase() !in setOf("EGP", "USD", "USDT", "USDC")) continue
            if (v >= threshold) continue
            emit(
                Insight(
                    agentId = "balance_guard", title = "رصيد ${a.name} قليل",
                    body = "الباقي ${Money.fmt(a.balance, a.currency)} بس، أقل من الحد اللي حددته (${Money.fmt(threshold)}).",
                    severity = 1, timestamp = now(), dedupKey = "low|${a.id}|$today"
                ),
                notify
            )
        }
    }

    private suspend fun subscriptions(notify: Boolean) {
        val since = now() - 120L * 86_400_000
        val out = dao.betweenOnce(since, now()).filter { it.direction == Direction.OUT && !it.merchant.isNullOrBlank() }
        out.groupBy { it.merchant!!.lowercase().replace(Regex("[^\\p{L}]"), "") }
            .filterKeys { it.length >= 3 }
            .forEach { (key, list) ->
                val months = list.map { YearMonth.from(Dates.toLocal(it.timestamp)) }.distinct()
                if (months.size < 2) return@forEach
                val amounts = list.map { it.amount }
                val avg = amounts.average()
                if (amounts.any { abs(it - avg) > avg * 0.15 }) return@forEach
                val sample = list.first()
                emit(
                    Insight(
                        agentId = "subscriptions", title = "اشتراك متكرر: ${sample.merchant}",
                        body = "بيتخصم حوالي ${Money.fmt(avg, sample.currency)} كل شهر (${months.size} شهور). لو مش بتستخدمه، الغيه ووفّر.",
                        severity = 0, timestamp = now(), dedupKey = "sub|$key|${YearMonth.now()}"
                    ),
                    notify
                )
            }
    }

    /** ملخص يومي مرة واحدة في اليوم */
    suspend fun dailyDigest() {
        val today = Dates.dayKey(now())
        if (settings.lastDigestDay == today) return
        val (from, to) = Dates.dayRange(now())
        val txns = dao.betweenOnce(from, to).filter { it.direction == Direction.OUT && it.countsInTotals() }
        if (txns.isEmpty()) return
        val total = txns.sumOf { egp(it) }
        val top = txns.groupBy { it.purpose }.maxByOrNull { e -> e.value.sumOf { egp(it) } }?.key
        settings.lastDigestDay = today
        Notifier.digest(
            context,
            "ملخص النهاردة: ${Money.fmt(total)}",
            "${txns.size} عملية خصم${top?.let { "، أكتر حاجة صرفت عليها: ${it.ar}" } ?: ""}."
        )
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val SALARY_CATEGORY = "مرتب"
        const val REFUND_CATEGORY = "استرداد مصاريف الشركة"
        const val TRANSFER_CATEGORY = "تحويل بين حساباتي"

        val MIRROR_AGENTS = AgentRegistry.MIRROR_AGENTS
    }
}
