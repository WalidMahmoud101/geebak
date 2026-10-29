package com.nabd.money.ui

import kotlinx.coroutines.sync.withLock
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nabd.money.NabdApp
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.AgentStat
import com.nabd.money.data.Budget
import com.nabd.money.data.Direction
import com.nabd.money.data.Insight
import com.nabd.money.data.Purpose
import com.nabd.money.data.Txn
import com.nabd.money.data.TxnFilter
import com.nabd.money.data.FilterTotals
import com.nabd.money.data.SavedFilter
import com.nabd.money.agents.Categorizer
import com.nabd.money.agents.ArabicNumbers
import com.nabd.money.data.UserRule
import com.nabd.money.BuildConfig
import com.nabd.money.ui.theme.ThemeMode
import com.nabd.money.util.UpdateState
import com.nabd.money.util.Updater
import com.nabd.money.ingest.InboxScanner
import com.nabd.money.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

private const val RESET_SNAPSHOT = "before-reset.json"

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val nabd = app as NabdApp
    private val dao = nabd.db.dao()
    private val engine = nabd.engine
    val settings = nabd.settings

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private val hiddenIds = settings.hiddenAccounts
    private val allAccounts = dao.accounts()
    /** الحسابات الظاهرة بس: المخفي مابيدخل في إجمالي ولا قوايم */
    val accounts = combine(allAccounts, hiddenIds) { list, h -> list.filter { it.id !in h } }.state(emptyList())
    val hiddenAccounts = combine(allAccounts, hiddenIds) { list, h -> list.filter { it.id in h } }.state(emptyList())

    /** عمليات الحسابات المخفية مابتظهرش في أي مكان */
    private fun Flow<List<Txn>>.visible(): Flow<List<Txn>> = combine(this, hiddenIds) { list, h -> list.filter { it.accountId !in h } }

    fun setAccountHidden(a: Account, hidden: Boolean) = settings.setAccountHidden(a.id, hidden)

    // مصاريف الشركة ليها خانتها، فمابتظهرش هنا
    val recent = dao.recent(80).visible().map { list -> list.filter { !it.reimbursable }.take(40) }.state(emptyList())

    // ---------- الفلتر (مشترك بين المصاريف والتقارير) ----------
    val filter = MutableStateFlow(TxnFilter())
    private val rangeTxns = filter.map { it.range() }.distinctUntilChanged().flatMapLatest { (from, to) ->
        dao.between(from, to).visible()
    }
    val filtered = combine(rangeTxns, filter, settings.usdRate) { list, f, _ ->
        list.filter { f.matches(it, txnEgp(it)) }
    }.state(emptyList())

    /** إجماليات الفترة اللي قبلها بنفس الفلاتر، للمقارنة */
    val previousTotals = filter.flatMapLatest { f ->
        val prev = f.previous()
        val (from, to) = prev.range()
        dao.between(from, to).visible().map { list ->
            FilterTotals.of(list.filter { prev.matches(it, txnEgp(it)) }, ::txnEgp, f.companyOnly)
        }
    }.state(null)

    /** كل التصنيفات اللي ظهرت في الفترة (حتى اللي برا القايمة الأساسية) */
    val categoriesInRange = rangeTxns.map { list ->
        (Categorizer.allCategories() + list.map { it.category }).distinct()
    }.state(Categorizer.allCategories())

    val savedFilters = settings.savedFilters

    fun setFilter(f: TxnFilter) { filter.value = f }
    fun updateFilter(transform: (TxnFilter) -> TxnFilter) { filter.value = transform(filter.value) }

    fun saveFilter(name: String) {
        val list = settings.savedFilters.value.filterNot { it.name == name } + SavedFilter(name, filter.value)
        settings.setSavedFilters(list)
    }

    fun deleteSavedFilter(s: SavedFilter) = settings.setSavedFilters(settings.savedFilters.value - s)

    // ---------- الوضع ----------
    val themeMode = settings.themeMode
    fun setTheme(m: ThemeMode) = settings.setTheme(m)

    // ---------- التحديثات ----------
    val update = MutableStateFlow<UpdateState>(UpdateState.Idle)

    fun checkForUpdate(manual: Boolean = false) {
        if (update.value is UpdateState.Checking || update.value is UpdateState.Downloading) return
        viewModelScope.launch {
            update.value = UpdateState.Checking
            val app = getApplication<Application>()
            val check = Updater.check()
            val r = check.release
            // لو النسخة دي اتنزلت قبل كده وسليمة، نروح على التثبيت علطول من غير تنزيل
            val cached = r?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }?.let { Updater.cached(app, it) }
            if (r != null && r.versionCode <= BuildConfig.VERSION_CODE) Updater.cleanup(app, BuildConfig.VERSION_CODE)
            update.value = when {
                r == null -> if (manual) UpdateState.Error("مقدرتش أوصل لسيرفر التحديثات: ${check.error ?: "السبب غير معروف"}") else UpdateState.Idle
                cached != null -> UpdateState.Ready(r, cached)
                r.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(r)
                else -> if (manual) UpdateState.UpToDate else UpdateState.Idle
            }
        }
    }

    fun downloadAndInstall(r: Updater.Release) {
        viewModelScope.launch {
            val have = Updater.cached(getApplication(), r)
            if (have != null) {
                update.value = UpdateState.Ready(r, have)
                Updater.install(getApplication(), have)
                return@launch
            }
            update.value = UpdateState.Downloading(0f)
            val file = Updater.download(getApplication(), r) { p -> update.value = UpdateState.Downloading(p) }
            if (file == null) {
                update.value = UpdateState.Error("التحميل وقف، جرب تاني")
            } else {
                update.value = UpdateState.Ready(r, file)
                Updater.install(getApplication(), file)
            }
        }
    }

    val thisMonthTxns = Dates.monthRange(YearMonth.now()).let { (from, to) -> dao.between(from, to).visible() }.state(emptyList())

    val insights = dao.insights().state(emptyList())
    val budgets = dao.budgets().map { list -> list.associate { it.category to it.monthlyLimit } }.state(emptyMap())
    val agentStats = dao.agentStats().map { it.associateBy(AgentStat::agentId) }.state(emptyMap())
    val insightStats = dao.insightStats().map { it.associateBy(AgentStat::agentId) }.state(emptyMap())
    val disabledAgents = settings.disabledAgents
    val usdRate = settings.usdRate

    val scanning = MutableStateFlow(false)
    val scanResult = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) { engine.runPeriodicInsights(notify = false) }
        checkForUpdate()
        viewModelScope.launch { runCatching { settings.syncRates() } }
    }

    fun toEgp(amount: Double, currency: String) = settings.toEgp(amount, currency)
    fun txnEgp(t: Txn) = settings.toEgp(t.amount, t.currency)

    fun txnsFor(accountId: Long) = dao.forAccount(accountId)


    fun scanInbox(days: Int = 120) {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            val r = withContext(Dispatchers.IO) { InboxScanner.scan(getApplication(), engine, days) }
            scanResult.value = if (r.scanned == 0) {
                "مافيش رسائل اتقرت – اتأكد إن صلاحية الرسائل مفعلة"
            } else {
                "${r.captured} عملية جديدة من ${r.scanned} رسالة"
            }
            withContext(Dispatchers.IO) { engine.runPeriodicInsights(notify = false) }
            scanning.value = false
        }
    }

    fun addManual(
        amount: Double, direction: Direction, purpose: Purpose, category: String,
        merchant: String?, accountId: Long?, timestamp: Long, note: String?, reimbursable: Boolean,
        currency: String? = null,
        quantity: Int = 1,
    ) = viewModelScope.launch(Dispatchers.IO) {
        settings.lastAccountId = accountId ?: -1L
        engine.addManual(amount, direction, purpose, category, merchant, accountId, timestamp, note, reimbursable, currency, quantity = quantity)
    }

    fun hideTxns(ids: Collection<Long>, hide: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        engine.setHidden(ids.toList(), hide)
    }

    // ---------- تصنيفاتك وقواعدك ----------
    val taxonomy = settings.taxonomy

    fun addCategory(name: String, purpose: Purpose?) = viewModelScope.launch(Dispatchers.IO) {
        val n = name.trim()
        if (n.isEmpty()) return@launch
        settings.updateTaxonomy { t ->
            t.copy(
                customCategories = if (n in Categorizer.allCategories()) t.customCategories else t.customCategories + n,
                categoryPurpose = if (purpose != null) t.categoryPurpose + (n to purpose) else t.categoryPurpose,
            )
        }
    }

    fun renameCategory(old: String, new: String) = viewModelScope.launch(Dispatchers.IO) {
        val n = new.trim()
        if (n.isEmpty() || n == old) return@launch
        settings.updateTaxonomy { t ->
            t.copy(
                customCategories = t.customCategories.map { if (it == old) n else it },
                categoryPurpose = t.categoryPurpose.mapKeys { (k, _) -> if (k == old) n else k },
                rules = t.rules.map { if (it.category == old) it.copy(category = n) else it },
            )
        }
        dao.renameCategory(old, n)
    }

    /** بيمسح تصنيف من عندك: عملياته بترجع "أخرى" */
    fun deleteCategory(name: String) = viewModelScope.launch(Dispatchers.IO) {
        settings.updateTaxonomy { t ->
            t.copy(
                customCategories = t.customCategories - name,
                categoryPurpose = t.categoryPurpose - name,
                rules = t.rules.filterNot { it.category == name },
            )
        }
        dao.renameCategory(name, "أخرى")
    }

    /** الغرض الثابت للتصنيف: بيتطبق على القديم والجديد */
    fun setCategoryPurpose(category: String, purpose: Purpose) = viewModelScope.launch(Dispatchers.IO) {
        settings.updateTaxonomy { it.copy(categoryPurpose = it.categoryPurpose + (category to purpose)) }
        dao.setPurposeForCategory(category, purpose.name)
    }

    /** قاعدة جديدة: بتتطبق على كل العمليات اللي فاتت ولوحدها على الجاية */
    fun addRule(keyword: String, category: String, purpose: Purpose?, name: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        val k = keyword.trim()
        if (k.isEmpty()) return@launch
        val rule = UserRule(k, category, purpose, name?.trim()?.takeIf { it.isNotEmpty() })
        settings.updateTaxonomy { t ->
            t.copy(
                rules = t.rules.filterNot { ArabicNumbers.norm(it.keyword) == ArabicNumbers.norm(k) } + rule,
                customCategories = if (category in Categorizer.allCategories()) t.customCategories else t.customCategories + category,
            )
        }
        val p = purpose ?: Categorizer.purposeOf(category) ?: Purpose.OTHER
        // العمليات اللي فاتت اللي فيها الجزء ده: التصنيف والغرض والاسم.
        // بنقرا كل عملية من جديد جوه القفل، علشان ماننزلش نسخة قديمة فوق تعديل لسه حصل (زي "على حساب الشركة")
        txnWrites.withLock {
            dao.allTxnsOnce()
                .filter { t -> !t.excluded && rule.matchesRaw(t.merchant, t.rawText, t.note) }
                .forEach { old ->
                    val t = dao.txnsByIds(listOf(old.id)).firstOrNull() ?: return@forEach
                    dao.updateTxn(t.copy(category = category, purpose = if (t.reimbursable) Purpose.WORK else p, merchant = rule.name ?: t.merchant))
                }
        }
    }

    fun deleteRule(r: UserRule) = viewModelScope.launch(Dispatchers.IO) {
        settings.updateTaxonomy { it.copy(rules = it.rules - r) }
    }

    // ---------- النسخة الاحتياطية ----------
    val backupMessage = MutableStateFlow<String?>(null)
    val pendingRestore = MutableStateFlow<Pair<com.nabd.money.data.BackupInfo, String>?>(null)
    val backupBusy = MutableStateFlow(false)

    private fun backupJob(block: suspend () -> Unit) = viewModelScope.launch {
        backupBusy.value = true
        try { block() } catch (e: Exception) {
            backupMessage.value = "حصلت مشكلة: ${e.message ?: "جرب تاني"}"
        } finally { backupBusy.value = false }
    }

    fun saveBackup(uri: android.net.Uri) = backupJob {
        val json = withContext(Dispatchers.IO) { com.nabd.money.util.BackupFiles.build(nabd) }
        com.nabd.money.util.BackupFiles.writeTo(getApplication(), uri, json)
        settings.lastBackupAt = System.currentTimeMillis()
        val info = com.nabd.money.data.Backup.info(json)
        backupMessage.value = "اتحفظت النسخة: ${info?.transactions ?: 0} عملية و ${info?.accounts ?: 0} حساب"
    }

    fun shareBackup() = backupJob {
        val json = withContext(Dispatchers.IO) { com.nabd.money.util.BackupFiles.build(nabd) }
        com.nabd.money.util.BackupFiles.share(getApplication(), json)
        settings.lastBackupAt = System.currentTimeMillis()
    }

    /** قبل الرجوع: نقرا الملف ونوري ملخصه */
    fun openBackup(uri: android.net.Uri) = backupJob {
        val json = com.nabd.money.util.BackupFiles.read(getApplication(), uri)
        val info = com.nabd.money.data.Backup.info(json)
        if (info == null) backupMessage.value = "الملف ده مش نسخة احتياطية من نبض"
        else pendingRestore.value = info to json
    }

    fun confirmRestore() = backupJob {
        val (_, json) = pendingRestore.value ?: return@backupJob
        pendingRestore.value = null
        // نسخة من الحالي قبل الرجوع، علشان لو غيرت رأيك
        withContext(Dispatchers.IO) {
            val current = com.nabd.money.util.BackupFiles.build(nabd)
            java.io.File(nabd.filesDir, "before-restore.json").writeText(current)
            com.nabd.money.data.Backup.restore(nabd.db, settings, json)
        }
        backupMessage.value = "رجعت النسخة بنجاح"
    }

    fun setBackupFolder(uri: android.net.Uri?) = backupJob {
        settings.backupFolder = uri?.toString()
        settings.lastAutoBackupDay = 0
        if (uri != null) {
            val ok = com.nabd.money.util.BackupFiles.autoBackup(nabd)
            backupMessage.value = if (ok) "النسخة التلقائية اشتغلت، وأول نسخة اتحفظت" else "مقدرتش أكتب في الفولدر ده، اختار فولدر تاني"
        }
    }

    // ---------- ابدأ من جديد ----------
    enum class ResetScope { BEFORE_THIS_MONTH, ALL_TXNS, EVERYTHING }

    val resetCounts = MutableStateFlow<Pair<Int, Int>?>(null) // (قبل الشهر ده، الكل)
    val canUndoReset = MutableStateFlow(java.io.File(app.filesDir, RESET_SNAPSHOT).exists())

    fun loadResetCounts() = viewModelScope.launch(Dispatchers.IO) {
        val monthStart = Dates.monthRange(YearMonth.now()).first
        resetCounts.value = dao.countTxnsBefore(monthStart) to dao.countTxns()
    }

    fun reset(scope: ResetScope) = backupJob {
        withContext(Dispatchers.IO) {
            // نسخة كاملة قبل المسح، علشان "رجّع اللي اتمسح"
            java.io.File(nabd.filesDir, RESET_SNAPSHOT).writeText(com.nabd.money.util.BackupFiles.build(nabd))
            val now = System.currentTimeMillis()
            when (scope) {
                ResetScope.BEFORE_THIS_MONTH -> {
                    val start = Dates.monthRange(YearMonth.now()).first
                    dao.deleteTxnsBefore(start)
                    settings.ignoreBefore = start
                }
                ResetScope.ALL_TXNS -> {
                    dao.clearTxns(); dao.clearInsights(); dao.zeroEstimatedBalances()
                    settings.ignoreBefore = now
                }
                ResetScope.EVERYTHING -> {
                    dao.clearTxns(); dao.clearInsights(); dao.clearAccounts(); dao.clearBudgets()
                    settings.resetUserSettings()
                    settings.ignoreBefore = now
                }
            }
        }
        canUndoReset.value = true
        resetCounts.value = null
        backupMessage.value = when (scope) {
            ResetScope.BEFORE_THIS_MONTH -> "بدأت من أول الشهر ده"
            ResetScope.ALL_TXNS -> "العمليات اتمسحت، وحساباتك وإعداداتك زي ما هي"
            ResetScope.EVERYTHING -> "بدأت من الصفر"
        }
    }

    /** يرجّع كل اللي اتمسح بآخر "ابدأ من جديد" */
    fun undoReset() = backupJob {
        val f = java.io.File(nabd.filesDir, RESET_SNAPSHOT)
        if (!f.exists()) return@backupJob
        withContext(Dispatchers.IO) {
            com.nabd.money.data.Backup.restore(nabd.db, settings, f.readText())
            settings.ignoreBefore = 0
            f.delete()
        }
        canUndoReset.value = false
        backupMessage.value = "رجعت كل اللي اتمسح"
    }

    // ---------- الإدخال بالصوت ----------
    fun saveDrafts(drafts: List<com.nabd.money.agents.Draft>) = viewModelScope.launch(Dispatchers.IO) {
        drafts.forEach { d ->
            engine.addManual(
                d.amount, d.direction, d.purpose, d.category, d.merchant, d.accountId,
                d.timestamp, d.note, d.company, d.currency
            )
        }
    }

    // ---------- مصاريف الشركة ----------
    val companyPending = dao.companyPending().state(emptyList())
    val companyReimbursed = dao.companyReimbursed().state(emptyList())

    fun setReimbursed(ids: List<Long>, received: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        dao.setReimbursed(ids, if (received) System.currentTimeMillis() else null)
    }

    /** استلمت فلوس الشركة: نقفل العمليات ونسجل المبلغ كإضافة (اختياري على حساب معين) */
    fun receiveCompanyMoney(items: List<Txn>, accountId: Long?, recordIncome: Boolean) =
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.setReimbursed(items.map { it.id }, now)
            if (recordIncome) {
                engine.addManual(
                    items.sumOf { txnEgp(it) }, Direction.IN, Purpose.WORK,
                    com.nabd.money.agents.AgentEngine.REFUND_CATEGORY, "استرداد من الشركة",
                    accountId, now, "${items.size} مصروف", excluded = true
                )
            }
        }

    /** كل كتابة على العمليات من الشاشة بتمشي واحدة ورا التانية، علشان تعديلين مايدوسوش على بعض */
    private val txnWrites = kotlinx.coroutines.sync.Mutex()

    fun updateTxn(t: Txn, hide: Boolean? = null, quantity: Int? = null) = viewModelScope.launch(Dispatchers.IO) {
        txnWrites.withLock {
            dao.updateTxn(t)
            if (quantity != null && quantity != t.quantity) engine.setQuantity(t.id, quantity)
            if (hide != null) engine.setHidden(listOf(t.id), hide)
        }
    }
    fun deleteTxn(t: Txn) = viewModelScope.launch(Dispatchers.IO) { engine.deleteTxn(t) }

    fun addAccount(name: String, type: AccountType, currency: String, balance: Double, agentId: String? = null, last4: String? = null) =
        viewModelScope.launch(Dispatchers.IO) { engine.addManualAccount(name, type, currency, balance, agentId, last4) }

    fun accountEgp(a: Account) = settings.toEgp(a.balance, a.currency)

    fun setCredit(a: Account, credit: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        dao.updateAccount(a.copy(isCredit = credit, includeInTotal = if (credit) false else a.includeInTotal || a.isCredit))
    }

    /** دمج حساب متكرر: العمليات تتنقل، والرصيد المؤكد الأحدث يفضل */
    fun mergeAccounts(from: Account, into: Account) = viewModelScope.launch(Dispatchers.IO) {
        engine.mergeAccounts(from.id, into.id)
    }

    /** يلم الكروت والحسابات المكررة لنفس البنك */
    val tidyMessage = MutableStateFlow<String?>(null)
    fun tidyAccounts() = viewModelScope.launch(Dispatchers.IO) {
        val n = engine.tidyAccounts()
        tidyMessage.value = if (n > 0) "اتدمج $n حساب مكرر" else "مافيش حسابات مكررة"
    }

    fun setIncludeInTotal(a: Account, include: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        dao.updateAccount(a.copy(includeInTotal = include))
    }

    fun setBalance(a: Account, balance: Double) = viewModelScope.launch(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.updateAccount(a.copy(balance = balance, balanceKnown = true, balanceAt = now, updatedAt = now))
    }

    fun renameAccount(a: Account, name: String) = viewModelScope.launch(Dispatchers.IO) {
        dao.updateAccount(a.copy(name = name))
    }

    fun deleteAccount(a: Account) = viewModelScope.launch(Dispatchers.IO) {
        dao.deleteTxnsForAccount(a.id)
        dao.deleteAccount(a)
        settings.setAccountHidden(a.id, false)
    }

    fun setBudget(p: Purpose, limit: Double) = viewModelScope.launch(Dispatchers.IO) {
        if (limit <= 0) dao.deleteBudget(p.name) else dao.upsertBudget(Budget(p.name, limit))
        engine.runPeriodicInsights(notify = false)
    }

    val salary = settings.salary

    fun setSalary(payer: String, fixedDay: Int, amount: Double, exactOnly: Boolean) =
        viewModelScope.launch(Dispatchers.IO) { engine.configureSalary(payer, fixedDay, amount, exactOnly) }

    /**
     * تسجيل تلقائي من غير ما تلمس حاجة: أول مرة فحص كامل لآخر 4 شهور،
     * وبعد كده كل ما تفتح التطبيق مراجعة سريعة لآخر 3 أيام.
     */
    fun autoSync() {
        if (scanning.value) return
        if (!settings.didInitialScan) {
            settings.didInitialScan = true
            scanInbox()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                InboxScanner.scan(getApplication(), engine, days = 3)
                engine.runPeriodicInsights(notify = false)
            }
        }
    }

    fun toggleAgent(id: String, enabled: Boolean) = settings.setEnabled(id, enabled)
    fun setUsdRate(v: Double) = settings.setUsdRate(v)

    // ---------- العملات ----------
    val ratesAt = settings.ratesAt
    val rateMessage = MutableStateFlow<String?>(null)
    val autoRates = MutableStateFlow(settings.autoRates)

    fun setAutoRates(on: Boolean) {
        settings.autoRates = on
        autoRates.value = on
        if (on) syncRatesNow()
    }

    fun syncRatesNow() = viewModelScope.launch {
        rateMessage.value = "بيحدّث السعر…"
        val ok = runCatching { settings.syncRates(force = true) }.getOrDefault(false)
        rateMessage.value = if (ok) "اتحدّث السعر" else "مقدرتش أوصل للسعر الحي، اتأكد من النت"
    }

    /** سعر بتكتبه بإيدك: بيقفل المزامنة التلقائية علشان مايتغيرش */
    fun setManualRate(v: Double) {
        settings.autoRates = false
        autoRates.value = false
        settings.setUsdRate(v)
        rateMessage.value = "اتحفظ سعرك، والمزامنة التلقائية اتقفلت"
    }

    fun markInsightsRead() = viewModelScope.launch(Dispatchers.IO) { dao.markInsightsRead() }
    fun dismissInsight(i: Insight) = viewModelScope.launch(Dispatchers.IO) { dao.deleteInsight(i) }
}
