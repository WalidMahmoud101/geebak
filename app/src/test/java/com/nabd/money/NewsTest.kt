package com.nabd.money

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nabd.money.util.News
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NewsTest {
    @Test
    fun takesTheNewestMessage() {
        val m = News.parse("""{"messages":[{"id":1,"title":"قديمة","body":"x"},{"id":3,"title":"جديدة","body":"نزلنا تحديث"}]}""")!!
        assertEquals(3, m.id)
        assertEquals("نزلنا تحديث", m.body)
    }

    @Test
    fun brokenFilesAreIgnored() {
        assertNull(News.parse("""{"messages":[]}"""))
        assertNull(News.parse("<html>404</html>"))
        assertNull(News.parse("""{"messages":[{"id":0,"title":"x"},{"id":2,"title":" "}]}"""))
    }

    @Test
    fun longTextIsCut() {
        val m = News.parse("""{"messages":[{"id":1,"title":"${"ع".repeat(200)}","body":"${"ب".repeat(900)}"}]}""")!!
        assertEquals(80, m.title.length)
        assertEquals(500, m.body.length)
    }
}
