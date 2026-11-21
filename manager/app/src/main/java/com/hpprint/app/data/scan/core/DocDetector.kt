package com.hpprint.app.data.scan.core

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.max
import kotlin.math.min

/**
 * 文档边缘自动检测。
 *
 * 流程：灰度 → 箱式模糊 → Sobel 梯度 → 自适应阈值 → 膨胀 → 最大连通域
 *      → 凸包 → 四角估计 → 几何校验
 *
 * 纯 Kotlin 实现，不依赖 OpenCV。为了速度，检测在缩略图上进行（长边约 480px）。
 */
object DocDetector {

    private const val WORK_MAX = 480

    /** 对给定 Bitmap 检测文档四角，失败返回 null */
    fun detect(bitmap: Bitmap): Quad? {
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw < 32 || bh < 32) return null

        // 原生扫描模型优先（arm64）；不可用时回退下面的纯 Kotlin 检测
        NativeScanEngine.detectBitmap(bitmap)?.let { c ->
            return Quad(c[0], c[1], c[2], c[3]).clampTo(bw.toFloat(), bh.toFloat())
        }

        val scale = WORK_MAX.toFloat() / max(bw, bh).toFloat()

        val w = max(32, (bw * scale).toInt())
        val h = max(32, (bh * scale).toInt())

        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        if (small !== bitmap) small.recycle()

        val quad = detectGray(gray, w, h) ?: return null
        val sx = bw.toFloat() / w
        val sy = bh.toFloat() / h
        return quad.scaled(sx, sy).clampTo(bw.toFloat(), bh.toFloat())
    }

    private fun detectGray(gray: IntArray, w: Int, h: Int): Quad? {
        val blurred = boxBlur(gray, w, h, 1)

        // Sobel
        val mag = FloatArray(w * h)
        var maxMag = 0f
        for (y in 1 until h - 1) {
            val row = y * w
            val up = row - w
            val dn = row + w
            for (x in 1 until w - 1) {
                val i = row + x
                val p00 = blurred[up + x - 1]
                val p01 = blurred[up + x]
                val p02 = blurred[up + x + 1]
                val p10 = blurred[i - 1]
                val p12 = blurred[i + 1]
                val p20 = blurred[dn + x - 1]
                val p21 = blurred[dn + x]
                val p22 = blurred[dn + x + 1]

                val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)
                val gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02)
                val m = Math.sqrt((gx * gx + gy * gy).toDouble()).toFloat()
                mag[i] = m
                if (m > maxMag) maxMag = m
            }
        }
        if (maxMag < 30f) return null

        // 分位数阈值
        val hist = IntArray(256)
        for (m in mag) {
            val b = min(255, (m / maxMag * 255f).toInt())
            hist[b]++
        }
        var acc = 0
        val total = w * h
        var thresholdBin = 200
        for (b in 255 downTo 0) {
            acc += hist[b]
            if (acc >= total * 0.10f) {
                thresholdBin = b
                break
            }
        }
        val thr = max(18f, thresholdBin / 255f * maxMag)

        val bin = ByteArray(w * h)
        for (i in mag.indices) {
            if (mag[i] >= thr) bin[i] = 1
        }

        // 膨胀，连接断开的边缘
        var cur = bin
        repeat(2) { cur = dilate(cur, w, h) }

        // 最大连通域
        val comp = largestComponent(cur, w, h) ?: return null
        if (comp.count < (w * h * 0.004f).toInt()) return null

        val hull = convexHull(comp.xs, comp.ys, comp.count)
        if (hull.size < 4) return null

        val quad = quadFromHull(hull) ?: return null

        // 校验
        val area = quad.area()
        val ratio = area / (w * h).toFloat()
        if (ratio < 0.06f || ratio > 0.995f) return null
        if (!quad.isConvex()) return null
        val edges = quad.edgeLengths()
        if (edges.any { it < min(w, h) * 0.12f }) return null

        // 边缘图谱通常落在真实边缘内侧，稍微外扩
        return expand(quad, w, h, 0.012f)
    }

    private fun expand(q: Quad, w: Int, h: Int, ratio: Float): Quad {
        val cx = (q.tl.x + q.tr.x + q.br.x + q.bl.x) / 4f
        val cy = (q.tl.y + q.tr.y + q.br.y + q.bl.y) / 4f
        fun e(p: PointF): PointF {
            val dx = p.x - cx
            val dy = p.y - cy
            return PointF(
                (p.x + dx * ratio).coerceIn(0f, w - 1f),
                (p.y + dy * ratio).coerceIn(0f, h - 1f)
            )
        }
        return Quad(e(q.tl), e(q.tr), e(q.br), e(q.bl))
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        if (r <= 0) return src
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val win = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (i in -r..r) {
                sum += src[row + i.coerceIn(0, w - 1)]
            }
            for (x in 0 until w) {
                tmp[row + x] = sum / win
                val add = src[row + (x + r + 1).coerceIn(0, w - 1)]
                val sub = src[row + (x - r).coerceIn(0, w - 1)]
                sum += add - sub
            }
        }
        for (x in 0 until w) {
            var sum = 0
            for (i in -r..r) {
                sum += tmp[i.coerceIn(0, h - 1) * w + x]
            }
            for (y in 0 until h) {
                out[y * w + x] = sum / win
                val add = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                val sub = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                sum += add - sub
            }
        }
        return out
    }

    private fun dilate(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (src[y * w + x].toInt() == 0) continue
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        out[ny * w + nx] = 1
                    }
                }
            }
        }
        return out
    }

    private class Component(val count: Int, val xs: IntArray, val ys: IntArray)

    private fun largestComponent(bin: ByteArray, w: Int, h: Int): Component? {
        val visited = BooleanArray(bin.size)
        val stack = IntArray(bin.size)
        val xsBuf = IntArray(bin.size)
        val ysBuf = IntArray(bin.size)
        var bestCount = 0
        var bestXs: IntArray? = null
        var bestYs: IntArray? = null

        for (start in bin.indices) {
            if (bin[start].toInt() == 0 || visited[start]) continue
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            var count = 0
            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                xsBuf[count] = x
                ysBuf[count] = y
                count++
                if (x > 0) {
                    val n = idx - 1
                    if (!visited[n] && bin[n].toInt() != 0) {
                        visited[n] = true; stack[sp++] = n
                    }
                }
                if (x < w - 1) {
                    val n = idx + 1
                    if (!visited[n] && bin[n].toInt() != 0) {
                        visited[n] = true; stack[sp++] = n
                    }
                }
                if (y > 0) {
                    val n = idx - w
                    if (!visited[n] && bin[n].toInt() != 0) {
                        visited[n] = true; stack[sp++] = n
                    }
                }
                if (y < h - 1) {
                    val n = idx + w
                    if (!visited[n] && bin[n].toInt() != 0) {
                        visited[n] = true; stack[sp++] = n
                    }
                }
            }
            if (count > bestCount) {
                bestCount = count
                bestXs = xsBuf.copyOf(count)
                bestYs = ysBuf.copyOf(count)
            }
        }
        val bx = bestXs ?: return null
        val by = bestYs ?: return null
        return Component(bestCount, bx, by)
    }

    /** Andrew monotone chain */
    private fun convexHull(xs: IntArray, ys: IntArray, n: Int): List<PointF> {
        if (n < 3) return emptyList()
        val idx = (0 until n).sortedWith(compareBy({ xs[it] }, { ys[it] }))
        val pts = ArrayList<PointF>(n)
        for (i in idx) pts.add(PointF(xs[i].toFloat(), ys[i].toFloat()))

        val hull = ArrayList<PointF>(n * 2)
        for (p in pts) {
            while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }
        val lower = hull.size + 1
        for (i in pts.indices.reversed()) {
            val p = pts[i]
            while (hull.size >= lower && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }
        if (hull.isNotEmpty()) hull.removeAt(hull.size - 1)
        return hull
    }

    private fun cross(o: PointF, a: PointF, b: PointF): Float =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    /**
     * 由凸包估计四个角：取 x+y 与 x−y 的极值点，这是对倾斜矩形最稳的近似。
     */
    private fun quadFromHull(hull: List<PointF>): Quad? {
        var minS = Float.MAX_VALUE
        var maxS = -Float.MAX_VALUE
        var minD = Float.MAX_VALUE
        var maxD = -Float.MAX_VALUE
        var pTl: PointF? = null
        var pBr: PointF? = null
        var pTr: PointF? = null
        var pBl: PointF? = null

        for (p in hull) {
            val s = p.x + p.y
            val d = p.x - p.y
            if (s < minS) { minS = s; pTl = p }
            if (s > maxS) { maxS = s; pBr = p }
            if (d > maxD) { maxD = d; pTr = p }
            if (d < minD) { minD = d; pBl = p }
        }
        val tl = pTl ?: return null
        val tr = pTr ?: return null
        val br = pBr ?: return null
        val bl = pBl ?: return null

        // 四个角必须互不相同
        val set = HashSet<String>()
        for (p in listOf(tl, tr, br, bl)) set.add("${p.x.toInt()}_${p.y.toInt()}")
        if (set.size < 4) return null

        val q = Quad(PointF(tl.x, tl.y), PointF(tr.x, tr.y), PointF(br.x, br.y), PointF(bl.x, bl.y))
        // 顺序修正：确保是顺时针（图像坐标下）
        return if (q.isConvex()) q else null
    }
}
