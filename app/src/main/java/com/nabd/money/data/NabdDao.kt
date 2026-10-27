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

    // ---------- مصاريف الشركة ----------
    @Query("SELECT * FROM transactions WHERE reimbursable = 1 AND reimbursedAt IS NULL ORDER BY timestamp DESC")
    fun companyPending(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE reimbursable = 1 AND reimbursedAt IS NULL")
    suspend fun companyPendingOnce(): List<Txn>

    @Query("SELECT * FROM transactions WHERE reimbursable = 1 AND reimbursedAt IS NOT NULL ORDER BY reimbursedAt DESC, timestamp DESC LIMIT 200")
    fun companyReimbursed(): Flow<List<Txn>>

    @Query("UPDATE transactions SET reimbursedAt = :at WHERE id IN (:ids)")
    suspend fun setReimbursed(ids: List<Long>, at: Long?)

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
