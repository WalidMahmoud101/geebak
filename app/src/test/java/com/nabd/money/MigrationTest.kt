package com.nabd.money

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.data.NabdDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * التحديث مايمسحش بياناتك: بنعمل قاعدة بيانات زي نسخة 1.0 بالظبط ونفتحها بالنسخة الحالية.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun upgradeFromV1KeepsEverything() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = ctx.getDatabasePath("migtest.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // الجداول زي ما كانت في نسخة 1.0
            db.execSQL("CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `agentId` TEXT NOT NULL, `accountKey` TEXT NOT NULL, `balance` REAL NOT NULL, `currency` TEXT NOT NULL, `balanceKnown` INTEGER NOT NULL, `balanceAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX `index_accounts_agentId_accountKey` ON `accounts` (`agentId`, `accountKey`)")
            db.execSQL("CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER, `amount` REAL NOT NULL, `currency` TEXT NOT NULL, `direction` TEXT NOT NULL, `balanceAfter` REAL, `merchant` TEXT, `category` TEXT NOT NULL, `purpose` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `source` TEXT NOT NULL, `agentId` TEXT NOT NULL, `rawText` TEXT, `note` TEXT, `fingerprint` TEXT NOT NULL, `reimbursable` INTEGER NOT NULL, `reimbursedAt` INTEGER)")
            db.execSQL("CREATE INDEX `index_transactions_accountId` ON `transactions` (`accountId`)")
            db.execSQL("CREATE INDEX `index_transactions_timestamp` ON `transactions` (`timestamp`)")
            db.execSQL("CREATE UNIQUE INDEX `index_transactions_fingerprint` ON `transactions` (`fingerprint`)")
            db.execSQL("CREATE TABLE `budgets` (`category` TEXT NOT NULL, `monthlyLimit` REAL NOT NULL, PRIMARY KEY(`category`))")
            db.execSQL("CREATE TABLE `insights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `agentId` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `severity` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `dedupKey` TEXT NOT NULL, `read` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX `index_insights_dedupKey` ON `insights` (`dedupKey`)")
            // بياناتك القديمة
            db.execSQL("INSERT INTO accounts VALUES (1,'CIB','BANK','cib','main',24350.0,'EGP',1,1,1)")
            db.execSQL("INSERT INTO accounts VALUES (2,'CIB •••• 4821','CARD','cib','4821',38000.0,'EGP',1,1,1)")
            db.execSQL("INSERT INTO accounts VALUES (3,'InstaPay','BANK','instapay','main',-500.0,'EGP',0,1,1)")
            db.execSQL("INSERT INTO transactions VALUES (1,1,1250.0,'EGP','OUT',24350.0,'كارفور','بقالة','HOME',100,'SMS','cib','تم خصم 1250 جنيه',NULL,'f1',0,NULL)")
            db.execSQL("INSERT INTO transactions VALUES (2,2,640.0,'EGP','OUT',38000.0,'Costa','مطاعم وكافيهات','OUTING',200,'SMS','cib','خصم 640 جنيه من بطاقتك الائتمانية. الحد المتاح 38000',NULL,'f2',0,NULL)")
            db.execSQL("INSERT INTO transactions VALUES (3,NULL,200.0,'EGP','OUT',NULL,'تاكسي','مواصلات','WORK',300,'MANUAL','manual',NULL,'شغل','f3',1,NULL)")
            db.execSQL("INSERT INTO budgets VALUES ('HOME',6000.0)")
            db.execSQL("INSERT INTO insights VALUES (1,'budget','تنبيه','نص',1,100,'k1',0)")
            db.version = 1
        }

        val room = Room.databaseBuilder(ctx, NabdDatabase::class.java, "migtest.db")
            .addMigrations(NabdDatabase.MIGRATION_1_2, NabdDatabase.MIGRATION_2_3, NabdDatabase.MIGRATION_3_4, NabdDatabase.MIGRATION_4_5, NabdDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        val dao = room.dao()
        val accounts = dao.accountsOnce().associateBy { it.id }
        val txns = dao.allTxnsOnce().associateBy { it.id }

        // كل حاجة موجودة
        assertEquals(3, accounts.size)
        assertEquals(3, txns.size)
        assertEquals(24350.0, accounts.getValue(1).balance, 0.0)
        assertEquals("كارفور", txns.getValue(1).merchant)
        assertEquals("شغل", txns.getValue(3).note)
        assertTrue(txns.getValue(3).reimbursable)
        assertEquals(6000.0, dao.budgetsOnce().single().monthlyLimit, 0.0)
        // والتحسينات الجديدة اتطبقت على القديم
        assertTrue(accounts.getValue(2).isCredit)                 // كارت الائتمان اتعرف
        assertFalse(accounts.getValue(2).includeInTotal)
        assertFalse(accounts.getValue(3).includeInTotal)          // InstaPay حساب وسيط
        assertTrue(accounts.getValue(1).includeInTotal)
        assertFalse(txns.getValue(1).excluded)
        room.close()
    }
}
