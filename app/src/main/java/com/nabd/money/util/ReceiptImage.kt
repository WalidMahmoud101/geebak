package com.nabd.money.util

import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * تجهيز صورة الفاتورة قبل القراية. الصور الحقيقية فيها سجادة أو ترابيزة وصوابع وضل، والفاتورة مايلة،
 * وده اللي بيبوّظ Tesseract مش الخط نفسه. فبنلاقي حرفي الورقة، نفردها، نشيل الضل، ونمسح الخطوط الفاصلة.
 * كله حساب على مصفوفات من غير أندرويد، علشان يتجرب على الكمبيوتر.
 */
object ReceiptImage {
    /** عرض الورقة بعد الفرد: كفاية للحروف من غير بطء */
    const val PAPER_WIDTH = 1000
    private const val MAX_HEIGHT = 8000
    private const val SEARCH_SIDE = 800

    /** خط حرف الورقة: x = a*y + b */
    data class Edge(val a: Double, val b: Double) { fun at(y: Double) = a * y + b }
    data class Paper(val left: Edge, val right: Edge, val top: Int, val bottom: Int)
    class Gray(val pixels: ByteArray, val width: Int, val height: Int) {
        operator fun get(x: Int, y: Int) = pixels[y * width + x].toInt() and 0xFF
    }

    fun prepare(argb: IntArray, width: Int, height: Int): Gray {
        val gray = toGray(argb, width, height)
        val paper = findPaper(argb, width, height)
        val flat = flatten(if (paper != null) straighten(gray, paper) else gray)
        return removeRules(flat)
    }

    private fun toGray(argb: IntArray, w: Int, h: Int): Gray {
        val out = ByteArray(w * h)
        for (i in out.indices) {
            val p = argb[i]
            out[i] = (((p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29) shr 8).toByte()
        }
        return Gray(out, w, h)
    }

    /**
     * الورقة مش دايماً أفتح حاجة، والضل بيقطّعها، فبنجرب كذا عتبة إضاءة ولون وناخد منها خطوط مرشحة.
     * الحرف الحقيقي هو اللي بيفصل فاتح جواه عن غامق براه بطول الصورة. null لو مالقيناش حرفين واضحين.
     */
    fun findPaper(argb: IntArray, width: Int, height: Int): Paper? {
        val scale = max(1.0, max(width, height).toDouble() / SEARCH_SIDE)
        val w = (width / scale).toInt()
        val h = (height / scale).toInt()
        if (w < 40 || h < 40) return null
        val light = IntArray(w * h)
        val tint = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = argb[min(height - 1, (y * scale).toInt()) * width + min(width - 1, (x * scale).toInt())]
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            light[y * w + x] = (r + g + b) / 3
            tint[y * w + x] = max(r, max(g, b)) - min(r, min(g, b))
        }
        val lefts = LinkedHashSet<Edge>()
        val rights = LinkedHashSet<Edge>()
        val starts = IntArray(h)
        val ends = IntArray(h)
        for (maxTint in listOf(32, 48, 64)) for (level in 90 until 220 step 10) {
            var rows = 0
            for (y in 0 until h) {
                longestRun(light, tint, y, w, level, maxTint, starts, ends)
                if (ends[y] - starts[y] > w / 10) rows++
            }
            if (rows < h / 4) continue
            val ys = IntArray(rows)
            var n = 0
            for (y in 0 until h) if (ends[y] - starts[y] > w / 10) ys[n++] = y
            fit(ys, starts)?.let { lefts += rounded(it) }
            fit(ys, ends)?.let { rights += rounded(it) }
        }
        val scoredLeft = lefts.map { it to stepScore(it, light, tint, w, h, inside = 1) }
        val scoredRight = rights.map { it to stepScore(it, light, tint, w, h, inside = -1) }
        var best: Pair<Edge, Edge>? = null
        var bestScore = h / 2 - 1
        for ((l, sl) in scoredLeft) for ((r, sr) in scoredRight) {
            val middle = r.at(h / 2.0) - l.at(h / 2.0)
            if (middle < w / 10 || abs(l.a - r.a) > 0.12 || sl < h / 4 || sr < h / 4) continue
            if (sl + sr > bestScore) { bestScore = sl + sr; best = l to r }
        }
        val (l, r) = best ?: return null
        val rowsInside = BooleanArray(h) { y -> mostlyPaper(l, r, y, light, tint, w) }
        var top = h / 2
        var bottom = h / 2
        while (top > 0 && (max(0, top - 6) until top).any { rowsInside[it] }) top--
        while (bottom < h - 1 && (bottom until min(h, bottom + 6)).any { rowsInside[it] }) bottom++
        if (bottom - top < h / 5) return null
        return Paper(Edge(l.a, l.b * scale), Edge(r.a, r.b * scale), (top * scale).toInt(), (bottom * scale).toInt())
    }

    private fun rounded(e: Edge) = Edge((e.a * 1000).roundToInt() / 1000.0, e.b.roundToInt().toDouble())

    /** أطول مسافة فاتحة في الصف، ونتغاضى عن فراغات قصيرة لأن جواها كلام الفاتورة */
    private fun longestRun(light: IntArray, tint: IntArray, y: Int, w: Int, level: Int, maxTint: Int, starts: IntArray, ends: IntArray) {
        var best = 0
        var bestStart = 0
        var bestEnd = 0
        var x = 0
        val row = y * w
        fun on(i: Int) = light[row + i] > level && tint[row + i] < maxTint
        while (x < w) {
            if (!on(x)) { x++; continue }
            val start = x
            var end = x
            var miss = 0
            x++
            while (x < w && miss <= 8) {
                if (on(x)) { end = x; miss = 0 } else miss++
                x++
            }
            if (end - start > best) { best = end - start; bestStart = start; bestEnd = end }
            x = end + 1
        }
        starts[y] = bestStart
        ends[y] = bestEnd
    }

    /** أحسن خط ماشي على أغلب النقط، من غير ما الصوابع أو حتة من الخلفية تشدّه */
    private fun fit(ys: IntArray, xs: IntArray): Edge? {
        val rng = Random(1)
        var best: BooleanArray? = null
        var bestCount = 0
        val n = ys.size
        if (n < 8) return null
        repeat(300) {
            val i = ys[rng.nextInt(n)]
            val j = ys[rng.nextInt(n)]
            if (abs(i - j) < n / 4) return@repeat
            val a = (xs[i] - xs[j]).toDouble() / (i - j)
            if (abs(a) > 0.5) return@repeat
            val b = xs[i] - a * i
            val hit = BooleanArray(n) { k -> abs(xs[ys[k]] - (a * ys[k] + b)) <= 3 }
            val count = hit.count { it }
            if (count > bestCount) { bestCount = count; best = hit }
        }
        val hit = best ?: return null
        var sx = 0.0; var sy = 0.0; var sxy = 0.0; var syy = 0.0
        for (k in 0 until n) if (hit[k]) {
            val y = ys[k].toDouble()
            val x = xs[ys[k]].toDouble()
            sy += y; sx += x; sxy += x * y; syy += y * y
        }
        val d = bestCount * syy - sy * sy
        if (d == 0.0) return null
        val a = (bestCount * sxy - sx * sy) / d
        return Edge(a, (sx - a * sy) / bestCount)
    }

    /** عدد الصفوف اللي عندها الخط بيفصل ورقة فاتحة عن خلفية أغمق. inside = 1 لو الورقة يمين الخط */
    private fun stepScore(e: Edge, light: IntArray, tint: IntArray, w: Int, h: Int, inside: Int): Int {
        var hits = 0
        val a = IntArray(7)
        val b = IntArray(7)
        for (y in 0 until h) {
            val x = e.at(y.toDouble()).toInt()
            if (x - 8 < 0 || x + 8 >= w) continue
            var tintSum = 0
            for (k in 0..6) {
                val i = y * w + x + inside * (2 + k)
                a[k] = light[i]
                b[k] = light[y * w + x - inside * (2 + k)]
                tintSum += tint[i]
            }
            a.sort(); b.sort()
            if (a[3] - b[3] >= 12 && tintSum / 7 < 70) hits++
        }
        return hits
    }

    private fun mostlyPaper(l: Edge, r: Edge, y: Int, light: IntArray, tint: IntArray, w: Int): Boolean {
        val from = max(0, l.at(y.toDouble()).toInt())
        val to = min(w - 1, r.at(y.toDouble()).toInt())
        if (to <= from) return false
        val good = (from until to).count { light[y * w + it] > 72 && tint[y * w + it] < 84 }
        return good > (to - from) * 0.6
    }

    /** بنفرد الورقة: كل صف من الحرف الشمال للحرف اليمين بيبقى بعرض ثابت */
    fun straighten(src: Gray, p: Paper): Gray {
        val midY = (p.top + p.bottom) / 2.0
        val paperWidth = p.right.at(midY) - p.left.at(midY)
        val scale = PAPER_WIDTH / paperWidth
        val outH = min(MAX_HEIGHT, ((p.bottom - p.top) * scale).toInt()).coerceAtLeast(1)
        val out = ByteArray(PAPER_WIDTH * outH)
        for (oy in 0 until outH) {
            val y = p.top + oy / scale
            val left = p.left.at(y)
            val span = p.right.at(y) - left
            val y0 = y.toInt().coerceIn(0, src.height - 2)
            val fy = (y - y0).coerceIn(0.0, 1.0)
            for (ox in 0 until PAPER_WIDTH) {
                val x = left + span * ox / PAPER_WIDTH
                val x0 = x.toInt().coerceIn(0, src.width - 2)
                val fx = (x - x0).coerceIn(0.0, 1.0)
                val top = src[x0, y0] * (1 - fx) + src[x0 + 1, y0] * fx
                val bottom = src[x0, y0 + 1] * (1 - fx) + src[x0 + 1, y0 + 1] * fx
                out[oy * PAPER_WIDTH + ox] = (top * (1 - fy) + bottom * fy).toInt().toByte()
            }
        }
        return Gray(out, PAPER_WIDTH, outH)
    }

    /** بنشيل الضل: كل نقطة بتتقاس بلون الورقة حواليها مش بالصورة كلها، وبعدين نقوّي الفرق بين الحبر والورق */
    fun flatten(src: Gray): Gray {
        val cell = 16
        val cw = max(1, src.width / cell)
        val ch = max(1, src.height / cell)
        var grid = IntArray(cw * ch)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val i = min(ch - 1, y / cell) * cw + min(cw - 1, x / cell)
            grid[i] = max(grid[i], src[x, y])
        }
        repeat(2) {
            val blurred = IntArray(cw * ch)
            for (y in 0 until ch) for (x in 0 until cw) {
                var sum = 0
                var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val yy = y + dy
                    val xx = x + dx
                    if (yy in 0 until ch && xx in 0 until cw) { sum += grid[yy * cw + xx]; n++ }
                }
                blurred[y * cw + x] = sum / n
            }
            grid = blurred
        }
        val out = ByteArray(src.pixels.size)
        for (y in 0 until src.height) {
            val gy = (y.toDouble() / cell - 0.5).coerceIn(0.0, ch - 1.0)
            val y0 = min(gy.toInt(), max(0, ch - 2))
            val y1 = min(y0 + 1, ch - 1)
            val fy = gy - y0
            for (x in 0 until src.width) {
                val gx = (x.toDouble() / cell - 0.5).coerceIn(0.0, cw - 1.0)
                val x0 = min(gx.toInt(), max(0, cw - 2))
                val x1 = min(x0 + 1, cw - 1)
                val fx = gx - x0
                val back = (grid[y0 * cw + x0] * (1 - fx) + grid[y0 * cw + x1] * fx) * (1 - fy) +
                    (grid[y1 * cw + x0] * (1 - fx) + grid[y1 * cw + x1] * fx) * fy
                val even = src[x, y] * 255 / max(1.0, back)
                out[y * src.width + x] = ((even - 80) * 255 / 155).toInt().coerceIn(0, 255).toByte()
            }
        }
        return Gray(out, src.width, src.height)
    }

    /** الخطوط الفاصلة بين الصفوف بتخلّي Tesseract يضيّع الصف كله، والحروف عمرها ماتبقى بالطول ده */
    fun removeRules(src: Gray): Gray {
        val out = src.pixels.copyOf()
        val longRun = src.width / 10
        for (y in 0 until src.height) {
            var x = 0
            while (x < src.width) {
                if (src[x, y] >= 170) { x++; continue }
                val start = x
                var end = x
                var miss = 0
                x++
                while (x < src.width && miss <= 4) {
                    if (src[x, y] < 170) { end = x; miss = 0 } else miss++
                    x++
                }
                if (end - start >= longRun) {
                    for (yy in max(0, y - 3)..min(src.height - 1, y + 3)) for (xx in start..end) out[yy * src.width + xx] = -1
                }
                x = end + 1
            }
        }
        return Gray(out, src.width, src.height)
    }
}
