package com.nabd.money.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Txn::class, Budget::class, Insight::class, Invoice::class, StockItem::class, StockPrice::class],
    version = 6,
    exportSchema = false,
)
abstract class NabdDatabase : RoomDatabase() {
    abstract fun dao(): NabdDao

    companion object {
        fun build(context: Context): NabdDatabase =
            Room.databaseBuilder(context, NabdDatabase::class.java, "nabd.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()

        /** المخزون: حاجات البيت وأسعارها. جداول جديدة بس، فمفيش حاجة قديمة بتتلمس */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`key` TEXT NOT NULL, `category` TEXT NOT NULL, `isOut` INTEGER NOT NULL, `outAt` INTEGER, " +
                        "`aliases` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_items_key` ON `stock_items` (`key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_items_category` ON `stock_items` (`category`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_prices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, " +
                        "`price` REAL NOT NULL, `qty` REAL NOT NULL, `store` TEXT, `at` INTEGER NOT NULL, `fromReceipt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_prices_itemId` ON `stock_prices` (`itemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_prices_at` ON `stock_prices` (`at`)")
            }
        }

        /** v4.1: الفواتير المحفوظة ورقم الفاتورة على العمليات */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN invoiceNo TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `company` TEXT NOT NULL, `employee` TEXT NOT NULL, `scope` TEXT NOT NULL, " +
                        "`total` REAL NOT NULL, `itemCount` INTEGER NOT NULL, `itemsJson` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_invoices_number` ON `invoices` (`number`)")
            }
        }

        /** v2.6: كمية مصاريف الشركة */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
            }
        }

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
