package com.nabd.money.shots

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.ui.screens.AboutContent
import com.nabd.money.ui.screens.AboutStats
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.ui.theme.NabdTheme
import com.nabd.money.ui.theme.ThemeMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-w392dp-h2300dp-xhdpi")
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class AboutShots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }

    private fun render(mode: ThemeMode, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            NabdTheme(mode) {
                AboutContent(
                    versionName = "3.9", versionCode = 30,
                    stats = AboutStats(transactions = 1066, auto = 1034, manual = 32, accounts = 6, since = System.currentTimeMillis() - 120L * 86_400_000),
                    agentNames = AgentRegistry.all.map { it.name.removePrefix("وكيل ").trim() },
                    updateText = "عندك آخر نسخة ✓", onCheckUpdate = {}, onOpen = {},
                    modifier = Modifier.fillMaxSize().background(NabdColors.Surface),
                )
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { root.draw(android.graphics.Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(out, "$name.png").length() > 1000)
    }

    @Test fun light() = render(ThemeMode.LIGHT, "about_light")
    @Test fun dark() = render(ThemeMode.DARK, "about_dark")
}
