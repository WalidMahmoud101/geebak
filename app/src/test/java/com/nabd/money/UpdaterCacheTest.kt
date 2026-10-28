package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.util.Updater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdaterCacheTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private fun release(code: Int, size: Long) = Updater.Release(code, "نبض", "", "https://example.invalid/nabd.apk", size)
    private fun dir() = File(ctx.cacheDir, "updates").apply { mkdirs() }

    @Test
    fun noFileMeansDownload() {
        dir().listFiles()?.forEach { it.delete() }
        assertNull(runBlocking { Updater.cached(ctx, release(9, 100)) })
    }

    @Test
    fun partialOrBrokenFileIsDeletedNotReused() {
        val f = File(dir(), "nabd-9.apk").apply { writeBytes(ByteArray(50)) }
        // ناقص: حجمه أقل من المتوقع
        assertNull(runBlocking { Updater.cached(ctx, release(9, 100)) })
        assertFalse(f.exists())
        // حجمه تمام بس مش APK سليم
        File(dir(), "nabd-9.apk").writeBytes(ByteArray(100))
        assertNull(runBlocking { Updater.cached(ctx, release(9, 100)) })
        assertFalse(File(dir(), "nabd-9.apk").exists())
    }

    @Test
    fun oldFilesAreCleanedAfterUpdating() {
        File(dir(), "nabd-5.apk").writeBytes(ByteArray(10))
        File(dir(), "nabd-9.part").writeBytes(ByteArray(10))
        File(dir(), "nabd-12.apk").writeBytes(ByteArray(10))
        Updater.cleanup(ctx, currentVersionCode = 9)
        assertEquals(listOf("nabd-12.apk"), dir().listFiles()!!.map { it.name })
        assertTrue(true)
    }
}
