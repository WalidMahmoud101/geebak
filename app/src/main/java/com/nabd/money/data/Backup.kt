package com.nabd.money.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** ملخص نسخة قبل ما ترجعلها */
data class BackupInfo(
    val createdAt: Long,
    val appVersion: String,
    val accounts: Int,
    val transactions: Int,
    val firstTxn: Long?,
    val lastTxn: Long?,
)

/**
 * النسخة الاحتياطية: ملف JSON واحد فيه كل حاجة
 * (الحسابات، العمليات، الميزانيات، التنبيهات، المخزون، والإعدادات زي المرتب والقواعد والتصنيفات).
 * الرجوع بيحط النسخة مكان البيانات الحالية بالظبط، وبنفس الأرقام التعريفية علشان العمليات تفضل مربوطة بحساباتها.
 */
object Backup {
    const val FORMAT = 1
    private const val MAGIC = "nabd-backup"

    suspend fun export(db: NabdDatabase, settings: Settings, appVersion: String): String {
        val dao = db.dao()
        return JSONObject().apply {
            put("magic", MAGIC)
            put("format", FORMAT)
            put("createdAt", System.currentTimeMillis())
            put("appVersion", appVersion)
            put("accounts", JSONArray(dao.accountsOnce().map(::accountJson)))
            put("transactions", JSONArray(dao.allTxnsOnce().map(::txnJson)))
            put("budgets", JSONArray(dao.budgetsOnce().map { JSONObject().put("category", it.category).put("limit", it.monthlyLimit) }))
            put("insights", JSONArray(dao.insightsOnce().map(::insightJson)))
            put("invoices", JSONArray(dao.invoicesOnce().map(::invoiceJson)))
            put("stockItems", JSONArray(dao.stockItemsOnce().map(::stockItemJson)))
            put("stockPrices", JSONArray(dao.stockPricesOnce().map(::stockPriceJson)))
            put("settings", settings.exportPrefs())
        }.toString()
    }

    /** بيرجع null لو الملف مش نسخة نبض */
    fun info(json: String): BackupInfo? = runCatching {
        val o = JSONObject(json)
        if (o.optString("magic") != MAGIC) return null
        val txns = o.getJSONArray("transactions")
        val times = (0 until txns.length()).map { txns.getJSONObject(it).getLong("timestamp") }
        BackupInfo(
            createdAt = o.getLong("createdAt"),
            appVersion = o.optString("appVersion"),
            accounts = o.getJSONArray("accounts").length(),
            transactions = txns.length(),
            firstTxn = times.minOrNull(),
            lastTxn = times.maxOrNull(),
        )
    }.getOrNull()

    suspend fun restore(db: NabdDatabase, settings: Settings, json: String): BackupInfo {
        val o = JSONObject(json)
        require(o.optString("magic") == MAGIC) { "الملف ده مش نسخة احتياطية من نبض" }
        require(o.optInt("format") <= FORMAT) { "النسخة دي من إصدار أحدث، حدّث التطبيق الأول" }
        // نقرا كل حاجة الأول، علشان لو في غلط في الملف مانمسحش البيانات الحالية
        val accounts = o.getJSONArray("accounts").objects().map(::account)
        val txns = o.getJSONArray("transactions").objects().map(::txn)
        val budgets = o.optJSONArray("budgets")?.objects()?.map { Budget(it.getString("category"), it.getDouble("limit")) } ?: emptyList()
        val insights = o.optJSONArray("insights")?.objects()?.map(::insight) ?: emptyList()
        val invoices = o.optJSONArray("invoices")?.objects()?.map(::invoice) ?: emptyList()
        // نسخة أقدم من المخزون مافيهاش الخانة دي: مخزونك الحالي بيفضل زي ما هو
        val stockItems = o.optJSONArray("stockItems")?.objects()?.map(::stockItem)
        val stockPrices = o.optJSONArray("stockPrices")?.objects()?.map(::stockPrice)
        val dao = db.dao()
        db.withTransaction {
            dao.clearTxns(); dao.clearAccounts(); dao.clearBudgets(); dao.clearInsights(); dao.clearInvoices()
            dao.putAccounts(accounts)
            dao.putTxns(txns)
            dao.putBudgets(budgets)
            dao.putInsights(insights)
            dao.putInvoices(invoices)
            if (stockItems != null) {
                dao.clearStockPrices(); dao.clearStockItems()
                dao.putStockItems(stockItems)
                dao.putStockPrices(stockPrices.orEmpty())
            }
        }
        o.optJSONObject("settings")?.let(settings::importPrefs)
        return info(json)!!
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optLongOrNull(k: String) = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optDoubleOrNull(k: String) = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.optStringOrNull(k: String) = if (has(k) && !isNull(k)) getString(k) else null

    private fun accountJson(a: Account) = JSONObject()
        .put("id", a.id).put("name", a.name).put("type", a.type.name).put("agentId", a.agentId)
        .put("accountKey", a.accountKey).put("balance", a.balance).put("currency", a.currency)
        .put("balanceKnown", a.balanceKnown).put("balanceAt", a.balanceAt).put("updatedAt", a.updatedAt)
        .put("includeInTotal", a.includeInTotal).put("isCredit", a.isCredit)

    private fun account(o: JSONObject) = Account(
        id = o.getLong("id"), name = o.getString("name"), type = AccountType.valueOf(o.getString("type")),
        agentId = o.getString("agentId"), accountKey = o.getString("accountKey"), balance = o.getDouble("balance"),
        currency = o.getString("currency"), balanceKnown = o.getBoolean("balanceKnown"), balanceAt = o.getLong("balanceAt"),
        updatedAt = o.getLong("updatedAt"), includeInTotal = o.optBoolean("includeInTotal", true),
        isCredit = o.optBoolean("isCredit", false),
    )

    private fun txnJson(t: Txn) = JSONObject()
        .put("id", t.id).put("accountId", t.accountId ?: JSONObject.NULL).put("amount", t.amount)
        .put("currency", t.currency).put("direction", t.direction.name).put("balanceAfter", t.balanceAfter ?: JSONObject.NULL)
        .put("merchant", t.merchant ?: JSONObject.NULL).put("category", t.category).put("purpose", t.purpose.name)
        .put("timestamp", t.timestamp).put("source", t.source.name).put("agentId", t.agentId)
        .put("rawText", t.rawText ?: JSONObject.NULL).put("note", t.note ?: JSONObject.NULL)
        .put("fingerprint", t.fingerprint).put("reimbursable", t.reimbursable)
        .put("reimbursedAt", t.reimbursedAt ?: JSONObject.NULL).put("excluded", t.excluded).put("quantity", t.quantity).put("invoiceNo", t.invoiceNo ?: JSONObject.NULL)

    private fun txn(o: JSONObject) = Txn(
        id = o.getLong("id"), accountId = o.optLongOrNull("accountId"), amount = o.getDouble("amount"),
        currency = o.getString("currency"), direction = Direction.valueOf(o.getString("direction")),
        balanceAfter = o.optDoubleOrNull("balanceAfter"), merchant = o.optStringOrNull("merchant"),
        category = o.getString("category"), purpose = Purpose.valueOf(o.getString("purpose")),
        timestamp = o.getLong("timestamp"), source = TxnSource.valueOf(o.getString("source")),
        agentId = o.getString("agentId"), rawText = o.optStringOrNull("rawText"), note = o.optStringOrNull("note"),
        fingerprint = o.getString("fingerprint"), reimbursable = o.optBoolean("reimbursable"),
        reimbursedAt = o.optLongOrNull("reimbursedAt"), excluded = o.optBoolean("excluded"), quantity = o.optInt("quantity", 1).coerceAtLeast(1), invoiceNo = o.optStringOrNull("invoiceNo"),
    )

    private fun invoiceJson(i: Invoice) = JSONObject()
        .put("id", i.id).put("number", i.number).put("createdAt", i.createdAt).put("company", i.company).put("employee", i.employee)
        .put("scope", i.scope).put("total", i.total).put("itemCount", i.itemCount).put("itemsJson", i.itemsJson)

    private fun invoice(o: JSONObject) = Invoice(
        id = o.getLong("id"), number = o.getString("number"), createdAt = o.getLong("createdAt"), company = o.getString("company"),
        employee = o.getString("employee"), scope = o.getString("scope"), total = o.getDouble("total"),
        itemCount = o.getInt("itemCount"), itemsJson = o.getString("itemsJson"),
    )

    private fun stockItemJson(i: StockItem) = JSONObject()
        .put("id", i.id).put("name", i.name).put("key", i.key).put("category", i.category).put("isOut", i.isOut)
        .put("outAt", i.outAt ?: JSONObject.NULL).put("aliases", i.aliases).put("createdAt", i.createdAt)

    private fun stockItem(o: JSONObject) = StockItem(
        id = o.getLong("id"), name = o.getString("name"), key = o.getString("key"), category = o.getString("category"),
        isOut = o.optBoolean("isOut"), outAt = o.optLongOrNull("outAt"), aliases = o.optString("aliases"), createdAt = o.getLong("createdAt"),
    )

    private fun stockPriceJson(p: StockPrice) = JSONObject()
        .put("id", p.id).put("itemId", p.itemId).put("price", p.price).put("qty", p.qty)
        .put("store", p.store ?: JSONObject.NULL).put("at", p.at).put("fromReceipt", p.fromReceipt)

    private fun stockPrice(o: JSONObject) = StockPrice(
        id = o.getLong("id"), itemId = o.getLong("itemId"), price = o.getDouble("price"), qty = o.optDouble("qty", 1.0),
        store = o.optStringOrNull("store"), at = o.getLong("at"), fromReceipt = o.optBoolean("fromReceipt"),
    )

    private fun insightJson(i: Insight) = JSONObject()
        .put("id", i.id).put("agentId", i.agentId).put("title", i.title).put("body", i.body)
        .put("severity", i.severity).put("timestamp", i.timestamp).put("dedupKey", i.dedupKey).put("read", i.read)

    private fun insight(o: JSONObject) = Insight(
        id = o.getLong("id"), agentId = o.getString("agentId"), title = o.getString("title"), body = o.getString("body"),
        severity = o.getInt("severity"), timestamp = o.getLong("timestamp"), dedupKey = o.getString("dedupKey"),
        read = o.optBoolean("read"),
    )
}
