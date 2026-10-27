package com.nabd.money.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Txn::class, Budget::class, Insight::class],
    version = 2,
    exportSchema = false,
)
abstract class NabdDatabase : RoomDatabase() {
    abstract fun dao(): NabdDao

    companion object {
        fun build(context: Context): NabdDatabase =
            Room.databaseBuilder(context, NabdDatabase::class.java, "nabd.db")
                .addMigrations(MIGRATION_1_2)
                .build()

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
