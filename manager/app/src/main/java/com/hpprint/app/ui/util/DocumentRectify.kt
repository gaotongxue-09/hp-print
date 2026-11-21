package com.hpprint.app.ui.util

import android.graphics.Bitmap
import android.graphics.PointF
import com.hpprint.app.data.scan.core.NativeScanEngine
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 扫描文档「自动裁边 + 透视校正」，对齐 HP Smart 的 PageLift 管线：
 *   detectQuadrilateral（找文档四角，等价 [detectCorners]）
 *   → rectify（4 点单应性透视校正，等价 [rectify]）
 *
 * 纯 Kotlin 实现，不引 OpenCV/TFLite 重依赖：
 *  灰度 + 大津阈值 → 文档掩码 → 凸包 + 旋转卡壳最小外接矩形 → 4 个有序角点
 *  → 4 点直接线性变换(DLT)求单应性 → 双线性采样透视映射。
 *
 * 检测不到清晰文档边界时返回 null（调用方保持原图，绝不破坏扫描结果）。
 */
object DocumentRectify {

    private const val DETECT_MAX_DIM = 320

    /** 在整图坐标下检测文档四角，[x0,y0,x1,y1,x2,y2,x3,y3]（TL,TR,BR,BL）；失败返回 null。 */
    fun detectCorners(bitmap: Bitmap): FloatArray? {
        if (bitmap.width < 24 || bitmap.height < 24) return null
        // 原生扫描模型优先；不可用时回退下面的纯 Kotlin 检测
        NativeScanEngine.detectBitmap(bitmap)?.let { c ->
            return floatArrayOf(c[0].x, c[0].y, c[1].x, c[1].y, c[2].x, c[2].y, c[3].x, c[3].y)
        }
        val (sx, sw, sh) = downscale(bitmap.width, bitmap.height)
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== bitmap) small.recycle()

        val gray = IntArray(sw * sh)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        val th = otsuThreshold(gray) ?: return null
        val pts = ArrayList<Point>()
        var idx = 0
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                if (gray[idx++] > th) pts.add(Point(x.toFloat(), y.toFloat()))
            }
        }
        if (pts.size < sw * sh / 20) return null

        val hull = convexHull(pts)
        if (hull.size < 4) return null
        val rect = minAreaRect(hull)
        val corners = orderCorners(rect)
        // 缩放回原始图坐标
        return floatArrayOf(
            corners[0].x * sx, corners[0].y * sx,
            corners[1].x * sx, corners[1].y * sx,
            corners[2].x * sx, corners[2].y * sx,
            corners[3].x * sx, corners[3].y * sx,
        )
    }

    /** 用四个角点把 [src] 透视校正为规整矩形（宽=上下边均值，高=左右边均值）。 */
    fun rectify(src: Bitmap, corners: FloatArray, maxDim: Int = 2400): Bitmap {
        if (corners.size >= 8) {
            // 原生扫描模型优先；不可用时回退下面的纯 Kotlin 单应性实现
            val native = NativeScanEngine.rectify(
                src,
                listOf(
                    PointF(corners[0], corners[1]), PointF(corners[2], corners[3]),
                    PointF(corners[4], corners[5]), PointF(corners[6], corners[7]),
                ),
            )
            if (native != null) return limitLongEdge(native, maxDim)
        }
        val tl = Point(corners[0], corners[1])
        val tr = Point(corners[2], corners[3])
        val br = Point(corners[4], corners[5])
        val bl = Point(corners[6], corners[7])
        val rawW = ((dist(tl, tr) + dist(bl, br)) / 2).roundToInt().coerceAtLeast(1)
        val rawH = ((dist(tl, bl) + dist(tr, br)) / 2).roundToInt().coerceAtLeast(1)
        // 按长边等比缩放：宽高各自独立裁剪会把 2480x3508 的扫描页压成正方形
        val scale = min(1f, maxDim.toFloat() / max(rawW, rawH))
        val outW = (rawW * scale).roundToInt().coerceAtLeast(1)
        val outH = (rawH * scale).roundToInt().coerceAtLeast(1)
        if (outW < 8 || outH < 8) return src
        val H = homographyDstToSrc(
            Point(0f, 0f), Point(outW - 1f, 0f), Point(outW - 1f, outH - 1f), Point(0f, outH - 1f),
            tl, tr, br, bl,
        ) ?: return src

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val spx = IntArray(src.width * src.height)
        src.getPixels(spx, 0, src.width, 0, 0, src.width, src.height)
        val opx = IntArray(outW * outH)

        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val w = H[6] * x + H[7] * y + H[8]
                if (w == 0f) continue
                val sx = (H[0] * x + H[1] * y + H[2]) / w
                val sy = (H[3] * x + H[4] * y + H[5]) / w
                opx[y * outW + x] = sampleBilinear(spx, src.width, src.height, sx, sy)
            }
        }
        out.setPixels(opx, 0, outW, 0, 0, outW, outH)
        return out
    }

    /** 原生矫正不限制输出尺寸，统一把长边压到 maxDim，避免超大图占内存。 */
    private fun limitLongEdge(src: Bitmap, maxDim: Int): Bitmap {
        val long = max(src.width, src.height)
        if (long <= maxDim || maxDim <= 0) return src
        val k = maxDim.toFloat() / long
        val out = Bitmap.createScaledBitmap(
            src,
            (src.width * k).roundToInt().coerceAtLeast(1),
            (src.height * k).roundToInt().coerceAtLeast(1),
            true,
        )
        if (out !== src) src.recycle()
        return out
    }

    // ------------------------------------------------------------ 基础几何

    private data class Point(val x: Float, val y: Float)

    private fun dist(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)

    private fun downscale(w: Int, h: Int): Triple<Float, Int, Int> {
        val s = min(1f, DETECT_MAX_DIM.toFloat() / max(w, h))
        return Triple(1f / s, (w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1))
    }

    private fun otsuThreshold(gray: IntArray): Int? {
        val hist = IntArray(256)
        for (g in gray) hist[g]++
        val total = gray.size
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * hist[i]
        var sumB = 0.0
        var wB = 0
        var maxVar = -1.0
        var threshold = 0
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = t
            }
        }
        return if (maxVar < 0) null else threshold
    }

    private fun convexHull(input: List<Point>): List<Point> {
        val pts = input.sortedWith(compareBy({ it.x }, { it.y }))
        if (pts.size < 3) return pts
        fun cross(o: Point, a: Point, b: Point): Float =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Point>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
            lower.add(p)
        }
        val upper = ArrayList<Point>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        lower.addAll(upper)
        return lower
    }

    /** 旋转卡壳求最小外接矩形，返回 4 个按序顶点。 */
    private fun minAreaRect(hull: List<Point>): Array<Point> {
        val n = hull.size
        var bestArea = Float.MAX_VALUE
        var best: Array<Point> = arrayOf(hull[0], hull[1], hull[2], hull[3])
        for (i in 0 until n) {
            val edge = Point(hull[(i + 1) % n].x - hull[i].x, hull[(i + 1) % n].y - hull[i].y)
            val ex = edge.x / hypot(edge.x, edge.y)
            val ey = edge.y / hypot(edge.x, edge.y)
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (p in hull) {
                val px = p.x * ex + p.y * ey
                val py = -p.x * ey + p.y * ex
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
            }
            val area = (maxX - minX) * (maxY - minY)
            if (area < bestArea) {
                bestArea = area
                best = arrayOf(
                    Point(minX * ex - minY * ey, minX * ey + minY * ex),
                    Point(maxX * ex - minY * ey, maxX * ey + minY * ex),
                    Point(maxX * ex - maxY * ey, maxX * ey + maxY * ex),
                    Point(minX * ex - maxY * ey, minX * ey + maxY * ex),
                )
            }
        }
        return best
    }

    /** 把四个矩形顶点规整为 TL,TR,BR,BL 顺序。 */
    private fun orderCorners(c: Array<Point>): Array<Point> {
        val sorted = c.sortedBy { it.y }
        val top = sorted.take(2).sortedBy { it.x }
        val bottom = sorted.drop(2).sortedBy { it.x }
        return arrayOf(top[0], top[1], bottom[1], bottom[0])
    }

    // ------------------------------------------------------------ 单应性

    /** 求「目标坐标 → 源坐标」单应性（用于反向采样），返回 3x3 行主序 [h0..h8]，h8=1。 */
    private fun homographyDstToSrc(
        d0: Point, d1: Point, d2: Point, d3: Point,
        s0: Point, s1: Point, s2: Point, s3: Point,
    ): FloatArray? {
        val dst = arrayOf(d0, d1, d2, d3)
        val src = arrayOf(s0, s1, s2, s3)
        val a = Array(8) { FloatArray(9) }
        for (i in 0..3) {
            val x = dst[i].x; val y = dst[i].y
            val u = src[i].x; val v = src[i].y
            a[2 * i] = floatArrayOf(x, y, 1f, 0f, 0f, 0f, -u * x, -u * y, u)
            a[2 * i + 1] = floatArrayOf(0f, 0f, 0f, x, y, 1f, -v * x, -v * y, v)
        }
        val h = gaussSolve(a) ?: return null
        return floatArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1f)
    }

    private fun gaussSolve(a: Array<FloatArray>): FloatArray? {
        val n = a.size
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            if (abs(a[pivot][col]) < 1e-9f) return null
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val pv = a[col][col]
            for (j in col until n + 1) a[col][j] = a[col][j] / pv
            for (r in 0 until n) {
                if (r == col) continue
                val f = a[r][col]
                if (f != 0f) for (j in col until n + 1) a[r][j] = a[r][j] - f * a[col][j]
            }
        }
        return FloatArray(n) { a[it][n] }
    }

    private fun sampleBilinear(src: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        if (x < 0 || y < 0 || x > w - 1 || y > h - 1) return 0
        val x0 = x.toInt().coerceIn(0, w - 1)
        val y0 = y.toInt().coerceIn(0, h - 1)
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val fx = x - x0; val fy = y - y0
        val c00 = src[y0 * w + x0]
        val c10 = src[y0 * w + x1]
        val c01 = src[y1 * w + x0]
        val c11 = src[y1 * w + x1]
        val w00 = (1 - fx) * (1 - fy)
        val w10 = fx * (1 - fy)
        val w01 = (1 - fx) * fy
        val w11 = fx * fy
        val a = ((c00 ushr 24) and 0xff) * w00 + ((c10 ushr 24) and 0xff) * w10 +
            ((c01 ushr 24) and 0xff) * w01 + ((c11 ushr 24) and 0xff) * w11
        val r = ((c00 shr 16) and 0xff) * w00 + ((c10 shr 16) and 0xff) * w10 +
            ((c01 shr 16) and 0xff) * w01 + ((c11 shr 16) and 0xff) * w11
        val g = ((c00 shr 8) and 0xff) * w00 + ((c10 shr 8) and 0xff) * w10 +
            ((c01 shr 8) and 0xff) * w01 + ((c11 shr 8) and 0xff) * w11
        val b = (c00 and 0xff) * w00 + (c10 and 0xff) * w10 + (c01 and 0xff) * w01 + (c11 and 0xff) * w11
        return (a.roundToInt() shl 24) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
    }
}