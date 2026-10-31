package com.nabd.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.util.UpdateState
import com.nabd.money.util.Updater
import com.nabd.money.util.action
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** سيرفر محلي بيبعت حتة وبيقف، علشان نجرب زرار الإيقاف والنت البطيء من غير GitHub */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UpdaterDownloadTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<NabdApp>()
    private val updates get() = File(ctx.cacheDir, "updates")

    private fun server(size: Int, send: Int, connections: AtomicInteger): ServerSocket {
        val s = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching {
                while (true) {
                    val c = s.accept()
                    connections.incrementAndGet()
                    thread(isDaemon = true) {
                        runCatching {
                            c.getInputStream().read(ByteArray(4096))
                            val o = c.getOutputStream()
                            o.write("HTTP/1.1 200 OK\r\nContent-Length: $size\r\nConnection: close\r\n\r\n".toByteArray())
                            o.write(ByteArray(send))
                            o.flush()
                            if (send < size) Thread.sleep(60_000)
                            c.close()
                        }
                    }
                }
            }
        }
        return s
    }

    private fun release(s: ServerSocket, size: Long) = Updater.Release(99, "جيبك", "", "http://127.0.0.1:${s.localPort}/nabd.apk", size)

    @Test
    fun stoppingEndsAStalledDownloadRightAwayWithoutRetrying() = runBlocking {
        val connections = AtomicInteger()
        val s = server(1_000_000, 100_000, connections)
        val started = CompletableDeferred<Unit>()
        var result: File? = null
        val job = launch(Dispatchers.IO) { result = Updater.download(ctx, release(s, 1_000_000)) { started.complete(Unit) } }
        withTimeout(10_000) { started.await() }
        job.cancel()
        withTimeout(5_000) { job.join() }
        assertNull(result)
        assertEquals(1, connections.get())
        assertFalse(updates.listFiles().orEmpty().any { it.name.endsWith(".part") })
        s.close()
    }

    @Test
    fun finishedDownloadKeepsTheFile() = runBlocking {
        val connections = AtomicInteger()
        val s = server(200_000, 200_000, connections)
        val file = Updater.download(ctx, release(s, 200_000)) {}
        assertNotNull(file)
        assertEquals(200_000L, file!!.length())
        s.close()
    }

    @Test
    fun theButtonAlwaysDoesSomethingUseful() {
        val r = Updater.Release(9, "جيبك", "", "", 0)
        assertEquals("اتحقق", UpdateState.Idle.action())
        assertEquals("اتحقق", UpdateState.UpToDate.action())
        assertEquals("اتحقق", UpdateState.Error("x").action())
        assertNull(UpdateState.Checking.action())
        assertEquals("نزّل وثبّت", UpdateState.Available(r).action())
        assertEquals("وقّف", UpdateState.Downloading(r, 0.4f).action())
        assertEquals("ثبّت", UpdateState.Ready(r, File("x")).action())
    }
}
