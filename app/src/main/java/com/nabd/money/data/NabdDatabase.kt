package com.nabd.money.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Txn::class, Budget::class, Insight::class],
    version = 3,
    exportSchema = false,
)
abstract class NabdDatabase : RoomDatabase() {
    abstract fun dao(): NabdDao

    companion object {
        fun build(context: Context): NabdDatabase =
            Room.databaseBuilder(context, NabdDatabase::class.java, "nabd.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v1.2: كروت الائتمان */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN isCredit INTEGER NOT NULL DEFAULT 0")
                // الحسابات اللي اتسجل رصيدها من رسالة فيها "الحد المتاح" = كروت ائتمان
                db.execSQL(
                    "UPDATE accounts SET isCredit = 1, includeInTotal = 0, type = 'CARD' WHERE id IN (" +
                        "SELECT DISTINCT accountId FROM transactions WHERE accountId IS NOT NULL AND (" +
                        "rawText LIKE '%الحد المتاح%' OR rawText LIKE '%حد الائتمان%' OR rawText LIKE '%الحد الائتماني%' OR " +
                        "rawText LIKE '%available limit%' OR rawText LIKE '%credit limit%' OR rawText LIKE '%credit card%'))"
                )
            }
        }

        /** v1.1: فلترة الإجمالي (excluded) والحسابات الوسيطة (includeInTotal) */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE accounts ADD COLUMN includeInTotal INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE accounts SET includeInTotal = 0 WHERE agentId IN ('instapay', 'wallet_cards')")
                db.execSQL("UPDATE transactions SET excluded = 1 WHERE category = 'استرداد مصاريف الشركة'")
            }
        }
    }
}
