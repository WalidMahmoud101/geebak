package com.nabd.money.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NabdDao {

    // ---------- الحسابات ----------
    @Query("SELECT * FROM accounts ORDER BY type, name")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts")
    suspend fun accountsOnce(): List<Account>

    @Query("SELECT * FROM accounts WHERE agentId = :agentId AND accountKey = :key LIMIT 1")
    suspend fun findAccount(agentId: String, key: String): Account?

    @Query("SELECT * FROM accounts WHERE agentId = :agentId ORDER BY id")
    suspend fun accountsForAgent(agentId: String): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun account(id: Long): Account?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAccount(a: Account): Long

    @Update
    suspend fun updateAccount(a: Account)

    @Delete
    suspend fun deleteAccount(a: Account)

    @Query("SELECT * FROM transactions")
    suspend fun allTxnsOnce(): List<Txn>

    // ---------- النسخة الاحتياطية ----------
    @Query("SELECT * FROM insights")
    suspend fun insightsOnce(): List<Insight>

    @Query("DELETE FROM transactions") suspend fun clearTxns()
    @Query("DELETE FROM transactions WHERE timestamp < :before") suspend fun deleteTxnsBefore(before: Long): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp < :before") suspend fun countTxnsBefore(before: Long): Int
    @Query("SELECT COUNT(*) FROM transactions") suspend fun countTxns(): Int
    /** الأرصدة التقديرية مالهاش معنى من غير العمليات، فبترجع صفر */
    @Query("UPDATE accounts SET balance = 0, balanceAt = 0 WHERE balanceKnown = 0") suspend fun zeroEstimatedBalances()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM insights") suspend fun clearInsights()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccounts(list: List<Account>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTxns(list: List<Txn>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgets(list: List<Budget>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putInsights(list: List<Insight>)

    @Query("UPDATE transactions SET category = :to WHERE category = :from")
    suspend fun renameCategory(from: String, to: String)

    @Query("UPDATE transactions SET purpose = :purpose WHERE category = :category AND excluded = 0 AND reimbursable = 0")
    suspend fun setPurposeForCategory(category: String, purpose: String)

    /** دمج حساب متكرر في حساب تاني */
    @Query("UPDATE transactions SET accountId = :to WHERE accountId = :from")
    suspend fun moveTxns(from: Long, to: Long)

    // ---------- العمليات ----------
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun between(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun betweenOnce(from: Long, to: Long): List<Txn>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun forAccount(accountId: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun txnsByIds(ids: List<Long>): List<Txn>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp")
    suspend fun forAccountOnce(accountId: Long): List<Txn>

    /** عمليات الكاش القديمة (من غير حساب) بتتنقل لحساب الكاش */
    @Query("UPDATE transactions SET accountId = :to WHERE accountId IS NULL")
    suspend fun adoptCashTxns(to: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId IS NULL")
    suspend fun countCashTxns(): Int

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteTxnsForAccount(accountId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTxn(t: Txn): Long

    @Update
    suspend fun updateTxn(t: Txn)

    @Delete
    suspend fun deleteTxn(t: Txn)

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE accountId = :accountId AND amount = :amount " +
            "AND direction = :dir AND timestamp BETWEEN :from AND :to"
    )
    suspend fun similarCount(accountId: Long, amount: Double, dir: String, from: Long, to: Long): Int

    /** نفس العملية متسجلة من وكيل تاني (رسالة البنك + إشعار InstaPay) */
    @Query(
        "SELECT * FROM transactions WHERE agentId IN (:agents) AND amount = :amount AND direction = :dir " +
            "AND timestamp BETWEEN :from AND :to LIMIT 1"
    )
    suspend fun findFromAgents(agents: List<String>, amount: Double, dir: String, from: Long, to: Long): Txn?

    /** الطرف التاني لتحويل بين حساباتك: نفس المبلغ عكس الاتجاه على حساب تاني */
    @Query(
        "SELECT * FROM transactions WHERE accountId IS NOT NULL AND accountId != :accountId AND amount = :amount " +
            "AND direction = :dir AND currency = :currency AND source != 'MANUAL' AND excluded = 0 " +
            "AND timestamp BETWEEN :from AND :to LIMIT 1"
    )
    suspend fun findCounterpart(accountId: Long, amount: Double, dir: String, currency: String, from: Long, to: Long): Txn?

    @Query("SELECT agentId, COUNT(*) AS cnt, MAX(timestamp) AS last FROM transactions GROUP BY agentId")
    fun agentStats(): Flow<List<AgentStat>>

    /** العمليات المخفية والتحويلات الداخلية (من غير استرداد الشركة اللي بيتقفل لوحده) */
    @Query("SELECT COUNT(*) FROM transactions WHERE excluded = 1 AND category NOT IN ('استرداد مصاريف الشركة', 'عهدة من الشركة', 'رد للشركة', 'خصم من العهدة')")
    fun hiddenCount(): Flow<Int>

    // ---------- الفواتير المحفوظة ----------
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun invoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices")
    suspend fun invoicesOnce(): List<Invoice>

    @Query("SELECT * FROM invoices WHERE number = :number LIMIT 1")
    suspend fun invoiceByNumber(number: String): Invoice?

    @Query("SELECT COUNT(*) FROM invoices WHERE number LIKE :prefix || '%'")
    suspend fun countInvoicesWithPrefix(prefix: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putInvoice(i: Invoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putInvoices(list: List<Invoice>)

    @Delete
    suspend fun deleteInvoice(i: Invoice)

    @Query("DELETE FROM invoices")
    suspend fun clearInvoices()

    @Query("SELECT * FROM transactions WHERE invoiceNo LIKE '%' || :number || '%'")
    suspend fun txnsOfInvoice(number: String): List<Txn>

    // ---------- عهدة الشركة (فلوسها معاك) ----------
    @Query("SELECT * FROM transactions WHERE category IN ('عهدة من الشركة', 'رد للشركة', 'خصم من العهدة') ORDER BY timestamp DESC")
    fun custodyTxns(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE category = 'خصم من العهدة'")
    suspend fun custodyUsedOnce(): List<Txn>

    // ---------- مصاريف الشركة ----------
    @Query("SELECT * FROM transactions WHERE reimbursable = 1 AND reimbursedAt IS NULL ORDER BY timestamp DESC")
    fun companyPending(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE reimbursable = 1 AND reimbursedAt IS NULL")
    suspend fun companyPendingOnce(): List<Txn>

    @Query("SELECT * FROM transactions WHERE reimbursable = 1 AND reimbursedAt IS NOT NULL ORDER BY reimbursedAt DESC, timestamp DESC LIMIT 200")
    fun companyReimbursed(): Flow<List<Txn>>

    @Query("UPDATE transactions SET reimbursedAt = :at WHERE id IN (:ids)")
    suspend fun setReimbursed(ids: List<Long>, at: Long?)

    // ---------- المخزون ----------
    @Query("SELECT * FROM stock_items ORDER BY isOut DESC, name")
    fun stockItems(): Flow<List<StockItem>>

    @Query("SELECT * FROM stock_items")
    suspend fun stockItemsOnce(): List<StockItem>

    @Query("SELECT * FROM stock_items WHERE id = :id")
    suspend fun stockItem(id: Long): StockItem?

    @Query("SELECT * FROM stock_prices ORDER BY at DESC, id DESC")
    fun stockPrices(): Flow<List<StockPrice>>

    @Query("SELECT * FROM stock_prices")
    suspend fun stockPricesOnce(): List<StockPrice>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockItem(i: StockItem): Long

    @Update
    suspend fun updateStockItem(i: StockItem)

    @Query("UPDATE stock_items SET isOut = :out, outAt = :at WHERE id IN (:ids)")
    suspend fun setStockOut(ids: List<Long>, out: Boolean, at: Long?)

    @Query("UPDATE stock_items SET quantity = :quantity WHERE id = :id")
    suspend fun setStockQuantity(id: Long, quantity: Double?)

    @Query("DELETE FROM stock_items WHERE id = :id")
    suspend fun deleteStockItem(id: Long)

    @Insert
    suspend fun insertStockPrice(p: StockPrice): Long

    @Query("DELETE FROM stock_prices WHERE id = :id")
    suspend fun deleteStockPrice(id: Long)

    @Query("DELETE FROM stock_prices WHERE itemId = :itemId")
    suspend fun deletePricesOf(itemId: Long)

    @Query("DELETE FROM stock_items") suspend fun clearStockItems()
    @Query("DELETE FROM stock_prices") suspend fun clearStockPrices()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStockItems(list: List<StockItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStockPrices(list: List<StockPrice>)

    // ---------- الميزانيات ----------
    @Query("SELECT * FROM budgets")
    fun budgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun budgetsOnce(): List<Budget>

    @Upsert
    suspend fun upsertBudget(b: Budget)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)

    // ---------- تنبيهات الوكلاء ----------
    @Query("SELECT * FROM insights ORDER BY timestamp DESC LIMIT 50")
    fun insights(): Flow<List<Insight>>

    @Query("SELECT agentId, COUNT(*) AS cnt, MAX(timestamp) AS last FROM insights GROUP BY agentId")
    fun insightStats(): Flow<List<AgentStat>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInsight(i: Insight): Long

    @Query("UPDATE insights SET read = 1")
    suspend fun markInsightsRead()

    @Delete
    suspend fun deleteInsight(i: Insight)
}
