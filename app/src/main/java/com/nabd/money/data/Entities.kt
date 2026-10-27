package com.nabd.money.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType(val ar: String) {
    BANK("بنك"), WALLET("محفظة"), CARD("كارت"), CRYPTO("كريبتو"), CASH("كاش")
}

enum class Direction { IN, OUT }

/** الغرض من الفلوس: البيت، الشغل، العربية، الخروجات... */
enum class Purpose(val ar: String) {
    HOME("البيت"), WORK("الشغل"), CAR("العربية"), OUTING("الخروجات"), PERSONAL("شخصي"), OTHER("أخرى")
}

enum class TxnSource(val ar: String) { SMS("رسالة"), NOTIFICATION("إشعار"), MANUAL("يدوي") }

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["agentId", "accountKey"], unique = true)]
)
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val agentId: String,
    val accountKey: String,
    val balance: Double = 0.0,
    val currency: String = "EGP",
    /** true لما الرصيد جاي من رسالة البنك نفسها أو اتعدل يدوي، false لو محسوب تقديري */
    val balanceKnown: Boolean = false,
    /** وقت آخر رسالة أثرت على الرصيد، علشان الرسائل القديمة ماتغيرش رصيد أحدث */
    val balanceAt: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    /** حسابات وسيطة زي InstaPay و Wallet مابتتحسبش في إجمالي فلوسك */
    @ColumnInfo(defaultValue = "1") val includeInTotal: Boolean = true,
    /** كارت ائتمان: الرصيد هنا = الحد المتاح، ومابيتحسبش فلوس */
    @ColumnInfo(defaultValue = "0") val isCredit: Boolean = false,
)

@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("timestamp"), Index(value = ["fingerprint"], unique = true)]
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long?,
    val amount: Double,
    val currency: String = "EGP",
    val direction: Direction,
    val balanceAfter: Double? = null,
    val merchant: String? = null,
    val category: String = "أخرى",
    val purpose: Purpose = Purpose.OTHER,
    val timestamp: Long,
    val source: TxnSource,
    val agentId: String,
    val rawText: String? = null,
    val note: String? = null,
    val fingerprint: String,
    /** مصروف دفعته من جيبك للشركة/المكتب وهتسترده */
    val reimbursable: Boolean = false,
    /** وقت ما استلمت الفلوس دي من الشركة (null = لسه مستحقة) */
    val reimbursedAt: Long? = null,
    /** مايتحسبش في الإجمالي: تحويل بين حساباتك، استرداد من الشركة، أو المستخدم استبعده */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
)

/** العملية دي تدخل في إجمالي الدخل والمصروف الشخصي؟ مصاريف الشركة ليها إجمالي لوحدها */
fun Txn.countsInTotals(): Boolean = !excluded && !reimbursable

/** ميزانية شهرية لكل غرض (category = اسم الـ Purpose) */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val monthlyLimit: Double,
)

@Entity(tableName = "insights", indices = [Index(value = ["dedupKey"], unique = true)])
data class Insight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val agentId: String,
    val title: String,
    val body: String,
    /** 0 معلومة، 1 تحذير، 2 تنبيه مهم */
    val severity: Int,
    val timestamp: Long,
    val dedupKey: String,
    val read: Boolean = false,
)

data class AgentStat(val agentId: String, val cnt: Int, val last: Long?)
