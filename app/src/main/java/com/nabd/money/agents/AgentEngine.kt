package com.nabd.money.agents

import android.content.Context
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Direction
import com.nabd.money.data.Fx
import com.nabd.money.data.Insight
import com.nabd.money.data.Invoice
import com.nabd.money.data.NabdDao
import com.nabd.money.data.NetWorth
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
        // بعد "ابدأ من جديد" الرسائل القديمة ماترجعش
        if (m.timestamp < settings.ignoreBefore) return null
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
        val fromPayer = dir == Direction.IN && settings.isEnabled(SalaryWatch.ID) &&
            SalaryWatch.matchesPayer(salaryCfg.payer, p.merchant, m.title + " " + m.text)
        // تحويل من الشركة بنفس إجمالي مصاريف الشركة المستحقة = الشركة رجعتلك فلوسك
        val pendingCompany = if (fromPayer) dao.companyPendingOnce() else emptyList()
        val isRefund = pendingCompany.isNotEmpty() &&
            abs(pendingCompany.sumOf { settings.toEgp(it.amount, it.currency) } - settings.toEgp(amount, p.currency)) < 1.0
        // المرتب: من الشركة وبالمبلغ اللي انت محدده (أي حاجة أقل منه = دخل عادي)
        val isSalary = fromPayer && !isRefund && SalaryWatch.amountQualifies(salaryCfg, settings.toEgp(amount, p.currency))
        val cls = when {
            isRefund -> Classification(REFUND_CATEGORY, Purpose.WORK)
            isSalary -> Classification(SALARY_CATEGORY, Purpose.WORK)
            // قواعدك بتمشي دايماً، والتصنيف التلقائي لو وكيل التصنيف شغال
            else -> Categorizer.classify(p.merchant, m.text, dir, agent.id, builtIn = settings.isEnabled(Categorizer.ID))
        }

        val txn = Txn(
            accountId = account.id,
            amount = amount,
            currency = p.currency,
            direction = dir,
            balanceAfter = p.balance,
            // الاسم اللي حددته في قاعدتك بيغلب اسم التاجر اللي اتقرا من الرسالة
            merchant = cls.name ?: p.merchant ?: mirrorMerchant,
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
        if (notify && account.id !in settings.hiddenAccounts.value) {
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
    suspend fun configureSalary(payer: String, fixedDay: Int, amount: Double = 0.0, exactOnly: Boolean = false) = mutex.withLock {
        settings.updateSalary { it.copy(payer = payer.trim(), fixedDay = fixedDay, amount = amount, exactOnly = exactOnly) }
        val cfg = settings.salary.value
        // نراجع كل التحويلات اللي فاتت بالشروط الجديدة: اللي ينطبق عليه يبقى مرتب، واللي مايطابقش يرجع دخل
        dao.betweenOnce(now() - 400L * 86_400_000, now())
            .filter { it.direction == Direction.IN && it.category != REFUND_CATEGORY }
            .forEach { t ->
                val match = payer.isNotBlank() &&
                    SalaryWatch.matchesPayer(payer, t.merchant, t.rawText ?: t.merchant.orEmpty()) &&
                    SalaryWatch.amountQualifies(cfg, settings.toEgp(t.amount, t.currency))
                when {
                    match && t.category != SALARY_CATEGORY -> dao.updateTxn(t.copy(category = SALARY_CATEGORY, purpose = Purpose.WORK))
                    !match && t.category == SALARY_CATEGORY && t.source != TxnSource.MANUAL ->
                        dao.updateTxn(t.copy(category = "دخل", purpose = Purpose.OTHER))
                }
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

    /** نفس منطق resolveAccount بس من غير ما يعمل حساب جديد: بيرجع الحساب الموجود أو null */
    private suspend fun lookupAccount(agent: SourceAgent, m: RawMessage, p: ParsedMoney): Account? {
        val collapse = agent.accountType == AccountType.BANK && !p.isCredit && agent.id !in MIRROR_AGENTS
        val key = if (collapse) agent.mainKey(m) else agent.accountKey(m, p)
        return dao.findAccount(agent.id, key)
            ?: settings.accountAlias(agent.id, key)?.let { dao.findAccount(agent.id, it) }
            ?: (if (collapse) primaryBankAccount(agent, key) else sameMoneyAccount(agent, key, p))
    }

    private suspend fun resolveAccount(agent: SourceAgent, m: RawMessage, p: ParsedMoney): Account {
        // بنك (حساب/كارت خصم عادي): كله حساب واحد. كروت الائتمان بس بتفضل لوحدها بآخر 4 أرقام
        val collapse = agent.accountType == AccountType.BANK && !p.isCredit && agent.id !in MIRROR_AGENTS
        val key = if (collapse) agent.mainKey(m) else agent.accountKey(m, p)
        val found = lookupAccount(agent, m, p)?.also {
            if (it.accountKey != key && dao.findAccount(agent.id, key) == null) settings.setAccountAlias(agent.id, key, it.accountKey)
        }
        found?.let { existing ->
            if (p.isCredit && !existing.isCredit) {
                val u = existing.copy(isCredit = true, type = AccountType.CARD, includeInTotal = false)
                dao.updateAccount(u)
                return u
            }
            return existing
        }
        dao.insertAccount(
            Account(
                name = if (collapse) agent.mainLabel(m) else agent.accountLabel(m, p),
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

    // ------------------------------------------------------------------ مراجعة الرسايل القديمة وتصليحها

    private val garbageMerchant = Regex("""http|فودافون كاش|^وجود$|^السحب|^مكانك|^خلال|^التاجر\s""", RegexOption.IGNORE_CASE)

    /**
     * بيراجع العمليات الملتقطة بالـ parser الحالي: اللي طلعت رسالة مش عملية (كود سري، إعلان، بيانات كارت) بتتحذف،
     * واللي مبلغها أو اتجاهها كان غلط (مثلاً "وخصم 128.57" اتسجل 90) بتتصلح، والتصنيف الافتراضي "أخرى" و"دخل" بيتحسب من جديد.
     * اللي انت عدلته بإيدك (تصنيف غير الافتراضي، اسم) مابيتغيرش.
     */
    suspend fun revalidate(): Revalidated = mutex.withLock {
        var removed = 0; var fixed = 0
        for (t in dao.allTxnsOnce()) {
            if (t.source == TxnSource.MANUAL) continue
            val raw = t.rawText ?: continue
            val agent = AgentRegistry.byId(t.agentId) as? SourceAgent ?: continue
            val p = agent.parse(RawMessage(t.source, "", text = raw, timestamp = t.timestamp))
            if (p == null || p.amount == null || p.direction == null || p.amount <= 0.0) {
                dao.deleteTxn(t); removed++; continue
            }
            var u = t
            if (abs(p.amount - t.amount) > 0.001 && p.currency.equals(t.currency, ignoreCase = true)) u = u.copy(amount = p.amount)
            if (p.direction != t.direction && !t.excluded) u = u.copy(direction = p.direction)
            if (t.merchant.isNullOrBlank() || garbageMerchant.containsMatchIn(t.merchant)) {
                u = u.copy(merchant = p.merchant)
            }
            if (!t.excluded && !t.reimbursable && (t.category == "أخرى" || t.category == "دخل")) {
                val cls = Categorizer.classify(u.merchant, raw, u.direction, agent.id, builtIn = settings.isEnabled(Categorizer.ID))
                u = u.copy(category = cls.category, purpose = cls.purpose)
            }
            if (u != t) { dao.updateTxn(u); fixed++ }
        }
        Revalidated(removed, fixed)
    }

    data class Revalidated(val removed: Int, val fixed: Int)

    /**
     * يرجّع رصيد كل حساب لآخر رصيد حقيقي جه في رسالة بنك، ويزود عليه العمليات اللي بعده.
     * بيصلّح الأرصدة اللي اتبوظت من عمليات غلط (إعلان اتسجل 100 جنيه خصم مثلاً).
     */
    suspend fun rebuildBalances(messages: List<RawMessage>): Int = mutex.withLock {
        class Base(val ts: Long, val value: Double)
        val base = HashMap<Long, Base>()
        for (m in messages.sortedBy { it.timestamp }) {
            if (m.timestamp < settings.ignoreBefore) continue
            val agent = AgentRegistry.sources.firstOrNull { settings.isEnabled(it.id) && it.matches(m) } ?: continue
            val p = agent.parse(m) ?: continue
            val bal = p.balance ?: continue
            val account = lookupAccount(agent, m, p) ?: continue
            if (account.isCredit) continue
            val old = base[account.id]
            if (old == null || m.timestamp >= old.ts) base[account.id] = Base(m.timestamp, bal)
        }
        var changed = 0
        for ((id, b) in base) {
            val a = dao.account(id) ?: continue
            val after = dao.forAccountOnce(id).filter { !it.excluded || it.category == TRANSFER_CATEGORY }
                .filter { it.timestamp > b.ts && it.balanceAfter == null && it.currency.equals(a.currency, ignoreCase = true) }
            val balance = b.value + after.sumOf { if (it.direction == Direction.IN) it.amount else -it.amount }
            val at = maxOf(b.ts, after.maxOfOrNull { it.timestamp } ?: 0L)
            if (abs(balance - a.balance) > 0.005 || !a.balanceKnown) {
                dao.updateAccount(a.copy(balance = balance, balanceKnown = true, balanceAt = at, updatedAt = now()))
                changed++
            }
        }
        // حسابات اتعملت من رسايل مش عمليات (زي إعلانات WE) وماعدش فيها حاجة
        for (a in dao.accountsOnce()) {
            if (a.agentId == AgentRegistry.MANUAL_ID || a.balanceKnown && base.containsKey(a.id)) continue
            if (dao.forAccountOnce(a.id).isEmpty() && !a.balanceKnown) dao.deleteAccount(a)
        }
        changed
    }

    /** أحسن حساب عادي (مش ائتمان) موجود لنفس البنك: اللي رصيده مؤكد وأحدث رسالة */
    private suspend fun primaryBankAccount(agent: SourceAgent, mainKey: String): Account? {
        val probe = Account(name = "", type = agent.accountType, agentId = agent.id, accountKey = mainKey)
        return dao.accountsForAgent(agent.id)
            .filter { !it.isCredit && it.type == AccountType.BANK && NetWorth.institutionKey(it) == NetWorth.institutionKey(probe) }
            .sortedWith(compareByDescending<Account> { it.balanceKnown }.thenByDescending { it.balanceAt }.thenBy { it.id })
            .firstOrNull()
    }

    /**
     * مرة واحدة: كل حسابات البنك العادية (كارت 9767، حساب 0130، كارت قديم 1845…) تتلم في حساب واحد لكل بنك.
     * كروت الائتمان والمحافظ مابتتلمش. الرصيد بياخد من اللي رصيده مؤكد وأحدث.
     */
    suspend fun collapseBanks(): Int = mutex.withLock {
        var merged = 0
        val groups = dao.accountsOnce()
            .filter { !it.isCredit && it.type == AccountType.BANK && it.agentId != AgentRegistry.MANUAL_ID && it.agentId !in MIRROR_AGENTS }
            .groupBy { NetWorth.institutionKey(it) to it.currency.uppercase() }
        for ((_, list) in groups) {
            if (list.size < 2) continue
            val target = list.sortedWith(compareByDescending<Account> { it.balanceKnown }.thenByDescending { it.balanceAt }.thenBy { it.id }).first()
            list.filter { it.id != target.id }.forEach { mergeLocked(it.id, target.id); merged++ }
            dao.account(target.id)?.let { t ->
                if (t.name.contains("••••")) dao.updateAccount(t.copy(name = NetWorth.institutionName(t)))
            }
        }
        merged
    }

    /**
     * البنك ساعات يبعت بآخر 4 أرقام الكارت، وساعات برقم الحساب، وساعات من غير رقم خالص،
     * وكلهم نفس الفلوس. قبل ما نعمل حساب جديد نشوف لو ده نفس حساب موجود:
     * - في البنك ده حساب واحد بس (مش ائتمان) ورصيده مايتعارضش مع الرسالة، أو
     * - الرصيد اللي في الرسالة بيكمّل رصيد حساب موجود بالظبط.
     */
    private suspend fun sameMoneyAccount(agent: SourceAgent, key: String, p: ParsedMoney): Account? {
        if (p.isCredit) return null
        val probe = Account(name = "", type = agent.accountType, agentId = agent.id, accountKey = key, currency = agent.accountCurrency(p))
        val siblings = dao.accountsForAgent(agent.id).filter {
            !it.isCredit && it.currency.equals(probe.currency, ignoreCase = true) &&
                NetWorth.institutionKey(it) == NetWorth.institutionKey(probe)
        }
        if (siblings.isEmpty()) return null
        val matching = siblings.filter { balanceFits(it, p) == true }
        if (matching.size == 1) return matching.first()
        return siblings.singleOrNull()?.takeIf { balanceFits(it, p) != false }
    }

    /** true = الرصيد بيكمّل، false = بيتعارض، null = مانقدرش نعرف */
    private fun balanceFits(a: Account, p: ParsedMoney): Boolean? {
        val b = p.balance ?: return null
        if (!a.balanceKnown) return null
        val amount = p.amount ?: 0.0
        val after = when (p.direction) {
            Direction.IN -> a.balance + amount
            Direction.OUT -> a.balance - amount
            null -> a.balance
        }
        return abs(after - b) < 0.01 || abs(a.balance - b) < 0.01
    }

    /** دمج حساب في حساب تاني: العمليات بتتنقل، والرسايل الجاية للحساب القديم بتروح للجديد */
    suspend fun mergeAccounts(fromId: Long, intoId: Long) = mutex.withLock { mergeLocked(fromId, intoId) }

    private suspend fun mergeLocked(fromId: Long, intoId: Long) {
        if (fromId == intoId) return
        val from = dao.account(fromId) ?: return
        val into = dao.account(intoId) ?: return
        dao.moveTxns(from.id, into.id)
        val takeFrom = from.balanceKnown && (!into.balanceKnown || from.balanceAt > into.balanceAt)
        val merged = if (takeFrom) into.copy(balance = from.balance, balanceKnown = true, balanceAt = from.balanceAt, updatedAt = now()) else into
        dao.updateAccount(if (into.name.contains("••••") && !from.name.contains("••••")) merged.copy(name = from.name) else merged)
        if (from.agentId == into.agentId) settings.setAccountAlias(from.agentId, from.accountKey, into.accountKey)
        dao.deleteAccount(from)
        removeDuplicateTxns(into.id)
    }

    /** نفس المبلغ ونفس الاتجاه في خلال 3 دقايق على نفس الحساب = اتسجلت مرتين (رسالة الكارت + رسالة الحساب) */
    private suspend fun removeDuplicateTxns(accountId: Long) {
        val kept = mutableListOf<Txn>()
        for (t in dao.forAccountOnce(accountId)) {
            val dup = t.source != TxnSource.MANUAL && kept.any {
                it.source != TxnSource.MANUAL && it.direction == t.direction &&
                    abs(it.amount - t.amount) < 0.001 && abs(it.timestamp - t.timestamp) <= 3 * 60_000L
            }
            if (dup) dao.deleteTxn(t) else kept += t
        }
    }

    /** يشيل الإعلانات اللي اتسجلت قبل كده كأنها عمليات ("استمتع بكاش باك لحد 100 جنيه") */
    suspend fun purgeAds(): Int = mutex.withLock {
        val ads = dao.allTxnsOnce().filter { it.source != TxnSource.MANUAL && it.rawText?.let(MoneyParser::isPromo) == true }
        ads.forEach { removeMirror(it) }
        ads.size
    }

    /**
     * مرة واحدة: يلم الحسابات اللي اتكررت قبل كده لنفس البنك أو المحفظة.
     * - حسابات رصيدها نفس الرقم = نفس الفلوس
     * - حسابات مالهاش رصيد مؤكد بتتضم للحساب الوحيد اللي رصيده مؤكد (أو لأقدم حساب لو مفيش)
     * البنك اللي فيه حسابين رصيدهم مختلف فعلاً بيفضلوا منفصلين.
     */
    suspend fun tidyAccounts(): Int = mutex.withLock {
        var merged = 0
        val groups = dao.accountsOnce()
            .filter { !it.isCredit && it.agentId != AgentRegistry.MANUAL_ID && it.agentId != "binance" }
            .groupBy { NetWorth.institutionKey(it) to it.currency.uppercase() }
        for ((_, list) in groups) {
            if (list.size < 2) continue
            val pool = list.sortedBy { it.id }.toMutableList()
            // نفس الرصيد المؤكد
            val known = pool.filter { it.balanceKnown }
            for (a in known) {
                if (a !in pool) continue
                val target = pool.firstOrNull { it.id != a.id && it.balanceKnown && it.id < a.id && abs(it.balance - a.balance) < 0.01 }
                    ?: continue
                mergeLocked(a.id, target.id); pool.remove(a); merged++
            }
            val stillKnown = pool.filter { it.balanceKnown }
            val target = when {
                stillKnown.size == 1 -> stillKnown.first()
                stillKnown.isEmpty() -> pool.first()
                else -> null
            } ?: continue
            pool.filter { !it.balanceKnown && it.id != target.id }.forEach { mergeLocked(it.id, target.id); merged++ }
        }
        merged
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
        quantity: Int = 1,
    ) = mutex.withLock {
        // من غير حساب = دفعت كاش من جيبك: بيتسجل على حساب "كاش (جيبي)"
        val account = accountId?.let { dao.account(it) } ?: cashAccount()
        val txn = Txn(
            accountId = account.id,
            amount = amount,
            currency = currency ?: account.currency,
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
            quantity = quantity.coerceAtLeast(1),
        )
        val id = dao.insertTxn(txn)
        // عملة العملية غير عملة الحساب (مثلاً دولار من كارت بالجنيه): الرصيد بيتحول بسعر الصرف
        val inAccountCurrency = if (currency == null || currency.equals(account.currency, ignoreCase = true)) amount
        else Fx.convert(amount, currency, account.currency, settings.usdRate.value)
        if (inAccountCurrency != null) {
            val delta = if (direction == Direction.IN) inAccountCurrency else -inAccountCurrency
            dao.updateAccount(account.copy(balance = account.balance + delta, updatedAt = now()))
        }
        realtimeInsights(txn.copy(id = id), notify = true)
    }

    /** تغيير كمية مصروف: سعر الوحدة بيفضل زي ما هو والإجمالي بيتغير، ورصيد الحساب اليدوي/التقديري بيتظبط بالفرق */
    suspend fun setQuantity(id: Long, quantity: Int): Unit = mutex.withLock {
        val t = dao.txnsByIds(listOf(id)).firstOrNull() ?: return@withLock
        val q = quantity.coerceIn(1, 9999)
        if (q == t.quantity) return@withLock
        val unit = t.amount / t.quantity.coerceAtLeast(1)
        val updated = t.copy(quantity = q, amount = unit * q)
        dao.updateTxn(updated)
        if (!t.excluded) {
            val a = t.accountId?.let { dao.account(it) } ?: return@withLock
            if ((t.source == TxnSource.MANUAL || !a.balanceKnown) && t.currency.equals(a.currency, ignoreCase = true)) {
                val diff = updated.amount - t.amount
                dao.updateAccount(a.copy(balance = a.balance + if (t.direction == Direction.IN) diff else -diff, updatedAt = now()))
            }
        }
    }

    /** حساب الكاش (الجيب): بيتعمل أول مرة تسجل فيها حاجة كاش، ورصيده بتكتبه انت */
    private suspend fun cashAccount(): Account {
        dao.findAccount(AgentRegistry.MANUAL_ID, CASH_KEY)?.let { return it }
        dao.insertAccount(
            Account(name = "كاش (جيبي)", type = AccountType.CASH, agentId = AgentRegistry.MANUAL_ID, accountKey = CASH_KEY)
        )
        return dao.findAccount(AgentRegistry.MANUAL_ID, CASH_KEY)!!
    }

    /**
     * إخفاء عمليات من الحساب: مابتتحسبش مصروف ولا دخل في أي إجمالي أو تقرير،
     * ورصيد الحساب اللي بيتحسب من عملياتك (الكاش، الحسابات اليدوي، والأرصدة التقديرية) بيرجع كأنها ماحصلتش.
     * رصيد البنك اللي جاي من رسالة البنك نفسها مابيتغيرش لأنه الفلوس الحقيقية.
     * الرجوع بيعمل العكس بالظبط.
     */
    suspend fun setHidden(ids: List<Long>, hide: Boolean): Int = mutex.withLock {
        if (ids.isEmpty()) return@withLock 0
        var changed = 0
        val hiddenSet = settings.userHidden.toMutableSet()
        for (t in dao.txnsByIds(ids)) {
            if (hide) {
                if (t.excluded) continue // مستبعدة أصلاً (تحويل داخلي مثلاً)
                dao.updateTxn(t.copy(excluded = true))
                hiddenSet += t.id
                adjustBalance(t, reverse = true)
            } else {
                if (!t.excluded) continue
                dao.updateTxn(t.copy(excluded = false))
                // الأثر على الرصيد بيرجع بس لو انت اللي أخفيتها
                if (hiddenSet.remove(t.id)) adjustBalance(t, reverse = false)
            }
            changed++
        }
        settings.userHidden = hiddenSet
        changed
    }

    /**
     * نقل عمليات (مخفية أو تحويلات داخلية) لمصاريف الشركة: بتتشال من الإخفاء وتظهر في خانة الشركة.
     * بتتطبق على المصروف بس (الشركة مديونالك باللي خرج من جيبك). أثر الإخفاء على الرصيد بيرجع.
     */
    suspend fun moveToCompany(ids: List<Long>): Int = mutex.withLock {
        var moved = 0
        val hiddenSet = settings.userHidden.toMutableSet()
        for (t in dao.txnsByIds(ids)) {
            if (t.direction != Direction.OUT) continue
            if (t.excluded && hiddenSet.remove(t.id)) adjustBalance(t, reverse = false)
            dao.updateTxn(
                t.copy(
                    excluded = false, reimbursable = true, reimbursedAt = null, purpose = Purpose.WORK,
                    category = if (t.category == TRANSFER_CATEGORY) "أخرى" else t.category,
                )
            )
            moved++
        }
        settings.userHidden = hiddenSet
        moved
    }

    private suspend fun adjustBalance(t: Txn, reverse: Boolean) {
        val a = t.accountId?.let { dao.account(it) } ?: return
        if (!(t.source == TxnSource.MANUAL || !a.balanceKnown)) return
        if (!t.currency.equals(a.currency, ignoreCase = true)) return
        val delta = if (t.direction == Direction.IN) t.amount else -t.amount
        dao.updateAccount(a.copy(balance = a.balance + if (reverse) -delta else delta, updatedAt = now()))
    }

    /**
     * مرة واحدة: رسايل إصدار الكارت والرسوم اللي اتسجلت قبل كده كأنها إضافة، تتحول خصم.
     * الرصيد التقديري بيتظبط (من +10 لـ −10)، ورصيد البنك اللي جاي من الرسالة نفسها مابيتغيرش.
     */
    suspend fun fixFeeDirections(): Int = mutex.withLock {
        var fixed = 0
        for (t in dao.allTxnsOnce()) {
            if (t.source == TxnSource.MANUAL || t.direction != Direction.IN) continue
            val raw = t.rawText ?: continue
            if (!MoneyParser.isFee(raw)) continue
            val cls = Categorizer.classify(t.merchant, raw, Direction.OUT, t.agentId, builtIn = settings.isEnabled(Categorizer.ID))
            dao.updateTxn(t.copy(direction = Direction.OUT, category = cls.category, purpose = cls.purpose))
            val a = t.accountId?.let { dao.account(it) }
            if (a != null && !a.balanceKnown && a.currency.equals(t.currency, ignoreCase = true)) {
                dao.updateAccount(a.copy(balance = a.balance - 2 * t.amount, updatedAt = now()))
            }
            fixed++
        }
        fixed
    }

    /**
     * خصم مصاريف دفعتها من جيبك من فلوس الشركة اللي معاك (العهدة): المصاريف دي بتتقفل كمستلمة،
     * والعهدة بتقل بنفس المبلغ. التسجيل بيتعمل بعملية مستبعدة من الإجمالي، علشان الفلوس نفسها ماتتحسبش مرتين.
     */
    suspend fun useCustody(ids: List<Long>): Double = mutex.withLock {
        val items = dao.txnsByIds(ids).filter { it.reimbursable && it.reimbursedAt == null }
        if (items.isEmpty()) return@withLock 0.0
        val total = items.sumOf { settings.toEgp(it.amount, it.currency) }
        val at = now()
        dao.setReimbursed(items.map { it.id }, at)
        dao.insertTxn(
            Txn(
                accountId = null, amount = total, currency = "EGP", direction = Direction.OUT,
                category = CUSTODY_USED, purpose = Purpose.WORK, timestamp = at, source = TxnSource.MANUAL,
                agentId = AgentRegistry.MANUAL_ID, note = "ids:" + items.joinToString(",") { it.id.toString() },
                fingerprint = "custody-used|" + UUID.randomUUID(), excluded = true,
            )
        )
        total
    }

    /** تراجع: لو اتقفلت مصاريف بالعهدة، نشيل عملية الخصم من العهدة بتاعتها */
    suspend fun undoCustodyUse(ids: List<Long>) = mutex.withLock {
        val set = ids.map { it.toString() }.toSet()
        dao.custodyUsedOnce().filter { u ->
            u.note?.removePrefix("ids:")?.split(",")?.any { it in set } == true
        }.forEach { dao.deleteTxn(it) }
    }

    // ------------------------------------------------------------------ الفواتير المحفوظة

    private fun addNo(existing: String?, no: String) =
        (existing.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() } + no).distinct().joinToString(",")

    private fun removeNo(existing: String?, no: String) =
        existing.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() && it != no }.joinToString(",").ifBlank { null }

    /** رقم الفاتورة الجاية: NBD-يوم-001 ثم 002… وماتتكررش */
    suspend fun nextInvoiceNumber(at: Long = now()): String = mutex.withLock {
        val d = Dates.toLocal(at)
        val prefix = "NBD-%04d%02d%02d-".format(d.year, d.monthValue, d.dayOfMonth)
        var n = dao.countInvoicesWithPrefix(prefix) + 1
        while (dao.invoiceByNumber(prefix + "%03d".format(n)) != null) n++
        prefix + "%03d".format(n)
    }

    /**
     * يحفظ الفاتورة برقمها وبنودها (نسخة كاملة)، ويكتب رقمها على كل عملية دخلت فيها،
     * علشان البحث برقم الفاتورة يطلّعها ويطلّع عملياتها. لو الرقم اتحفظ قبل كده بيتحدّث بدل ما يتكرر.
     */
    suspend fun saveInvoice(info: com.nabd.money.util.InvoiceInfo, txnIds: List<Long>): Invoice = mutex.withLock {
        val old = dao.invoiceByNumber(info.number)
        if (old != null) dao.txnsOfInvoice(info.number).filter { info.number in it.invoiceNo.orEmpty().split(',') }
            .forEach { dao.updateTxn(it.copy(invoiceNo = removeNo(it.invoiceNo, info.number))) }
        val total = info.items.sumOf { settings.toEgp(it.total, it.currency) }
        val inv = Invoice(
            id = old?.id ?: 0, number = info.number, createdAt = old?.createdAt ?: info.issuedAt, company = info.company,
            employee = info.employee, scope = info.scope, total = total, itemCount = info.items.size,
            itemsJson = com.nabd.money.util.CompanyInvoice.itemsToJson(info.items),
        )
        dao.putInvoice(inv)
        dao.txnsByIds(txnIds).forEach { dao.updateTxn(it.copy(invoiceNo = addNo(it.invoiceNo, info.number))) }
        dao.invoiceByNumber(info.number)!!
    }

    suspend fun deleteInvoice(inv: Invoice) = mutex.withLock {
        dao.txnsOfInvoice(inv.number).filter { inv.number in it.invoiceNo.orEmpty().split(',') }
            .forEach { dao.updateTxn(it.copy(invoiceNo = removeNo(it.invoiceNo, inv.number))) }
        dao.deleteInvoice(inv)
    }

    /** مرة واحدة: عمليات الكاش القديمة تتنقل لحساب الكاش (من غير ما تغير رصيده) */
    suspend fun adoptCash(): Int = mutex.withLock {
        if (dao.countCashTxns() == 0) return@withLock 0
        dao.adoptCashTxns(cashAccount().id)
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

    /**
     * حساب بتضيفه بإيدك. لو ليه وكيل (Binance، Trust Wallet، كارت Wallet…) بيتسجل بنفس مفتاح الوكيل،
     * فأي إشعار جاي منه بيروح على نفس الحساب بدل ما يعمل حساب مكرر، ورصيدك اللي كتبته هو نقطة البداية.
     */
    suspend fun addManualAccount(
        name: String, type: AccountType, currency: String, balance: Double,
        agentId: String? = null, last4: String? = null,
    ): Account = mutex.withLock {
        val agent = agentId?.let(AgentRegistry::byId) as? SourceAgent
        val key = when {
            agent == null -> UUID.randomUUID().toString()
            agent is CryptoAgent -> currency.uppercase()
            last4 != null -> last4
            else -> "main"
        }
        val agentKey = agent?.id ?: AgentRegistry.MANUAL_ID
        val existing = dao.findAccount(agentKey, key)
        // نفس الحساب متضاف قبل كده: نحدّث رصيده بس
        val finalKey = if (existing != null && last4 == null && agent != null && agent !is CryptoAgent) UUID.randomUUID().toString() else key
        if (existing != null && finalKey == key) {
            val u = existing.copy(balance = balance, balanceKnown = true, balanceAt = now(), updatedAt = now(), includeInTotal = true, name = name)
            dao.updateAccount(u)
            return@withLock u
        }
        dao.insertAccount(
            Account(
                name = name, type = type, agentId = agentKey, accountKey = finalKey,
                balance = balance, currency = currency.uppercase(), balanceKnown = true, balanceAt = now(),
            )
        )
        dao.findAccount(agentKey, finalKey)!!
    }

    // ------------------------------------------------------------------ وكلاء التحليل

    /** العمليات من غير حسابات انت أخفيتها */
    private suspend fun betweenVisible(from: Long, to: Long): List<Txn> {
        val hidden = settings.hiddenAccounts.value
        return dao.betweenOnce(from, to).filter { it.accountId !in hidden }
    }

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
        val history = betweenVisible(since, t.timestamp - 1)
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
        val spent = betweenVisible(from, to)
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
        val out = betweenVisible(since, now()).filter { it.direction == Direction.OUT && !it.merchant.isNullOrBlank() }
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
        val txns = betweenVisible(from, to).filter { it.direction == Direction.OUT && it.countsInTotals() }
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
        const val CASH_KEY = "cash"
        const val SALARY_CATEGORY = "مرتب"
        const val REFUND_CATEGORY = "استرداد مصاريف الشركة"
        /** فلوس الشركة اللي معاك (عهدة): دخلت حسابك بس مش ملكك، لازم تصرفها على الشغل أو تردها */
        const val CUSTODY_IN = "عهدة من الشركة"
        const val CUSTODY_RETURN = "رد للشركة"
        const val CUSTODY_USED = "خصم من العهدة"
        const val TRANSFER_CATEGORY = "تحويل بين حساباتي"

        val MIRROR_AGENTS = AgentRegistry.MIRROR_AGENTS
    }
}
