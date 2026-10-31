package com.nabd.money

import com.nabd.money.util.ReceiptImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

/** صورة مصنوعة: ورقة مايلة شوية على خلفية منقوشة، وعليها ضل وكلام */
class ReceiptImageTest {
    private val w = 900
    private val h = 1200
    private fun left(y: Int) = 300 + y * 0.04
    private fun right(y: Int) = 640 + y * 0.03

    private fun rgb(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun photo(): IntArray {
        val rng = Random(3)
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                x < left(y) || x > right(y) -> {
                    // نقش سجادة: غامق مع بقع كريمي
                    val cream = (x / 70 + y / 70) % 3 == 0
                    if (cream) (0xFF shl 24) or (235 shl 16) or (215 shl 8) or 150 else rgb(90 + rng.nextInt(25))
                }
                y % 40 in 12..18 && x % 11 < 7 -> rgb(40)
                y in 700..900 -> rgb(165)
                else -> rgb(240)
            }
        }
    }

    @Test
    fun findsTheTiltedPaperOnAPatternedBackground() {
        val p = ReceiptImage.findPaper(photo(), w, h)
        assertNotNull(p)
        p!!
        assertTrue("الحرف الشمال ${p.left}", abs(p.left.at(600.0) - left(600)) < 10)
        assertTrue("الحرف اليمين ${p.right}", abs(p.right.at(600.0) - right(600)) < 10)
        assertTrue(p.top < 20 && p.bottom > h - 20)
    }

    @Test
    fun straightenedPaperHasFixedWidthAndNoBackground() {
        val out = ReceiptImage.prepare(photo(), w, h)
        assertEquals(ReceiptImage.PAPER_WIDTH, out.width)
        var dark = 0
        for (y in 0 until out.height) if (out[10, y] < 120) dark++
        assertTrue("خلفية باقية في الحرف: $dark", dark < out.height / 4)
    }

    @Test
    fun plainPhotoWithoutPaperEdgesKeepsItsSize() {
        val blank = IntArray(w * h) { rgb(235) }
        assertNull(ReceiptImage.findPaper(blank, w, h))
        val out = ReceiptImage.prepare(blank, w, h)
        assertEquals(w, out.width)
        assertEquals(h, out.height)
    }

    @Test
    fun shadowIsEvenedOutButTextStaysDark() {
        val px = ByteArray(400 * 400) { i ->
            val x = i % 400
            val y = i / 400
            val paper = if (y > 200) 150 else 240
            (if (x in 100..104) 30 else paper).toByte()
        }
        val out = ReceiptImage.flatten(ReceiptImage.Gray(px, 400, 400))
        assertTrue(out[300, 350] > 200)
        assertTrue(out[300, 50] > 200)
        assertTrue(out[102, 350] < 100)
    }

    @Test
    fun longRulesAreWipedAndLettersKept() {
        val px = ByteArray(1000 * 100) { 255.toByte() }
        for (x in 50..900) px[50 * 1000 + x] = 0
        for (y in 10..30) for (x in 200..215) px[y * 1000 + x] = 0
        val out = ReceiptImage.removeRules(ReceiptImage.Gray(px, 1000, 100))
        assertEquals(255, out[500, 50])
        assertEquals(0, out[205, 20])
    }
}
